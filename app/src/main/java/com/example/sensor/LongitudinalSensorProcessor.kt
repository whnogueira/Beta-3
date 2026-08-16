package com.example.sensor

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Output data structure from the unified sensor processing layer.
 */
data class LongitudinalProcessedSample(
    val timestampMs: Long,
    val rawLongitudinalAccel: Double, // Unfiltered projected longitudinal acceleration (m/s²)
    val filteredLongitudinalAccel: Double, // Clean, vibration-free, gravity-compensated longitudinal acceleration (m/s²)
    val lateralAccel: Double, // Vehicle lateral acceleration (m/s²)
    val verticalAccel: Double, // Vehicle vertical acceleration (m/s²)
    val estimatedGravityMag: Double, // Estimated gravity vector magnitude (m/s²)
    val dynamicPitchDeg: Double, // Dynamic pitch angle of mount/chassis (degrees)
    val dynamicRollDeg: Double, // Dynamic roll angle of mount/chassis (degrees)
    val gyroAngularSpeedRad: Double, // Gyroscope angular velocity magnitude (rad/s)
    val isOutlier: Boolean = false,
    val isExcessiveMovement: Boolean = false,
    val isStationary: Boolean = false
)

/**
 * High-performance, 100% offline 6-DOF Sensor Fusion & Longitudinal Processing Layer.
 *
 * Fuses Accelerometer and Gyroscope telemetry to extract a single, accurate,
 * gravity-compensated longitudinal acceleration axis (a_long) oriented Traseira -> Frente do carro.
 *
 * Features:
 * 1. Dynamic Gravity & Mount Tilt Compensation using a 6-DOF Complementary Fusion Filter.
 * 2. Orthogonal vehicle basis projection (Longitudinal, Lateral, Vertical).
 * 3. Two-stage digital vibration & spike rejection (Median window + Adaptive Low-Pass IIR).
 * 4. Zero-acceleration stationary baseline calibration & drift auto-nulling.
 * 5. Mount slip and excessive movement detection (> 14° angular rotation or severe shock).
 * 6. 100% Offline execution with zero external dependencies.
 */
class LongitudinalSensorProcessor(
    private var calibratedOrientation: CalibratedOrientation? = null
) {
    companion object {
        const val DEFAULT_GRAVITY = 9.80665
        const val MAX_ALLOWED_ANGULAR_JERK = 1.25 // rad/s
        const val MAX_CUMULATIVE_DRIFT_DEG = 14.0 // degrees
        const val SPIKE_OUTLIER_THRESHOLD = 5.5 // m/s² difference between raw and smoothed
        const val CUTOFF_FREQUENCY_HZ = 12.0 // Cutoff for chassis/engine vibration filtering
    }

    // Dynamic 6-DOF orientation tracking
    private var estimatedGravityVector: Vector3 = Vector3(0.0, DEFAULT_GRAVITY, 0.0)
    private var isGravityInitialized = false

    // Filtering buffers
    private var filteredLongitudinal = 0.0
    private var isFilterInitialized = false
    private val rawWindow = DoubleArray(3) { 0.0 }
    private var rawWindowIndex = 0
    private var rawWindowFilled = 0

    // Stationary auto-nulling baseline
    private var baselineOffset = 0.0
    private var stationarySampleCount = 0
    private var stationarySum = 0.0

    // Gyroscope tracking
    private var lastGyroTimestampNs = 0L
    private var currentGyroVec = Vector3()
    private var cumulativeGyroDriftRad = 0.0
    private var excessiveMovementDetected = false

    // Last processed sample
    private var lastTimestampMs = 0L

    fun setCalibration(cal: CalibratedOrientation) {
        this.calibratedOrientation = cal
        this.estimatedGravityVector = cal.gravityBaseline
        this.isGravityInitialized = true
        resetDynamicState()
    }

    fun reset() {
        resetDynamicState()
        calibratedOrientation = null
        isGravityInitialized = false
        estimatedGravityVector = Vector3(0.0, DEFAULT_GRAVITY, 0.0)
    }

    private fun resetDynamicState() {
        filteredLongitudinal = 0.0
        isFilterInitialized = false
        rawWindowIndex = 0
        rawWindowFilled = 0
        baselineOffset = 0.0
        stationarySampleCount = 0
        stationarySum = 0.0
        lastGyroTimestampNs = 0L
        currentGyroVec = Vector3()
        cumulativeGyroDriftRad = 0.0
        excessiveMovementDetected = false
        lastTimestampMs = 0L
    }

    /**
     * Ingest gyroscope reading to update attitude rates and drift tracking.
     */
    fun processGyroscope(gyroVec: Vector3, timestampNs: Long) {
        val cal = calibratedOrientation
        val gyroCalibrated = if (cal != null) gyroVec - cal.gyroBias else gyroVec
        currentGyroVec = gyroCalibrated
        val angularSpeed = gyroCalibrated.magnitude

        // Check for sudden phone shock / jerk
        if (angularSpeed > MAX_ALLOWED_ANGULAR_JERK) {
            excessiveMovementDetected = true
        }

        // Integrate angular displacement over time to monitor mount dislodgement
        if (lastGyroTimestampNs > 0L) {
            val dtSec = (timestampNs - lastGyroTimestampNs) / 1_000_000_000.0
            if (dtSec in 0.0005..0.25) {
                cumulativeGyroDriftRad += angularSpeed * dtSec
                val driftDeg = Math.toDegrees(cumulativeGyroDriftRad)
                if (driftDeg > MAX_CUMULATIVE_DRIFT_DEG) {
                    excessiveMovementDetected = true
                }

                // Predict dynamic gravity vector rotation using gyro rate: g_new = g_old + (w x g_old) * dt
                if (isGravityInitialized) {
                    val rotDelta = currentGyroVec.cross(estimatedGravityVector) * dtSec
                    estimatedGravityVector = (estimatedGravityVector + rotDelta).normalized() * DEFAULT_GRAVITY
                }
            }
        }
        lastGyroTimestampNs = timestampNs
    }

    /**
     * Unified processing step for accelerometer data.
     * Fuses accelerometer + current gyro state + calibrated orientation to produce the pure longitudinal sample.
     *
     * @param accelRaw Raw 3-axis accelerometer vector in m/s² (includes gravity)
     * @param timestampMs Epoch timestamp in milliseconds
     * @param isKnownLinear Whether input is already from TYPE_LINEAR_ACCELERATION sensor
     * @param vehicleSpeedKmh Current instantaneous vehicle speed in km/h
     */
    fun processAccelerometer(
        accelRaw: Vector3,
        timestampMs: Long,
        isKnownLinear: Boolean = false,
        vehicleSpeedKmh: Double = 0.0
    ): LongitudinalProcessedSample {
        val dtSec = if (lastTimestampMs > 0L) max(0.001, (timestampMs - lastTimestampMs) / 1000.0) else 0.02
        lastTimestampMs = timestampMs

        val cal = calibratedOrientation

        // =========================================================================
        // 1. DYNAMIC GRAVITY COMPENSATION & ATTITUDE FUSION
        // =========================================================================
        val accelMag = accelRaw.magnitude
        val linearAccelVec: Vector3

        if (isKnownLinear) {
            linearAccelVec = accelRaw
        } else {
            if (!isGravityInitialized) {
                estimatedGravityVector = if (cal != null && cal.gravityBaseline.magnitude > 7.0) {
                    cal.gravityBaseline
                } else {
                    accelRaw
                }
                isGravityInitialized = true
            } else {
                // Adaptive complementary filter for gravity tracking:
                // If total acceleration is close to 1G and vehicle is not undergoing hard pull,
                // we fuse accelerometer to slowly correct gyro integration drift.
                val isNearOneG = accelMag in 8.5..11.2 && vehicleSpeedKmh < 10.0
                val alpha = if (isNearOneG) 0.04 else 0.002 // Trust gyro much more during dynamic acceleration
                estimatedGravityVector = (estimatedGravityVector * (1.0 - alpha) + accelRaw * alpha).normalized() * DEFAULT_GRAVITY
            }

            // Subtract dynamic gravity vector
            linearAccelVec = accelRaw - estimatedGravityVector
        }

        // =========================================================================
        // 2. ORTHOGONAL VEHICLE COORDINATE PROJECTION
        // =========================================================================
        // Project onto calibrated vehicle basis:
        // uLongitudinal: Forward direction (Traseira -> Frente)
        // uLateral: Side direction (Direita do veículo)
        // uVertical: Vertical direction (Teto -> Asfalto)
        val rawLong: Double
        val lateral: Double
        val vertical: Double

        if (cal != null && cal.isCalibrated) {
            rawLong = cal.projectLongitudinal(linearAccelVec)
            lateral = cal.projectLateral(linearAccelVec)
            vertical = cal.projectVertical(linearAccelVec)
        } else {
            // Default Landscape fallback: Back of phone is front (-Z), Right edge is Lateral (+X)
            rawLong = -linearAccelVec.z
            lateral = linearAccelVec.x
            vertical = linearAccelVec.y
        }

        // =========================================================================
        // 3. STATIONARY AUTO-NULLING BASELINE CORRECTION
        // =========================================================================
        val gyroSpeed = currentGyroVec.magnitude
        val isStationary = vehicleSpeedKmh < 2.0 && gyroSpeed < 0.15 && abs(rawLong) < 0.60

        if (isStationary) {
            stationarySampleCount++
            stationarySum += rawLong
            if (stationarySampleCount >= 5) {
                baselineOffset = stationarySum / stationarySampleCount.toDouble()
            }
        }

        // Zero-nulling subtraction
        val zeroCorrectedLong = rawLong - baselineOffset

        // =========================================================================
        // 4. TWO-STAGE DIGITAL FILTERING (MEDIAN + ADAPTIVE IIR LOW-PASS)
        // =========================================================================
        // Stage A: 3-point median window to eliminate instantaneous spike noise / physical glitches
        rawWindow[rawWindowIndex] = zeroCorrectedLong
        rawWindowIndex = (rawWindowIndex + 1) % 3
        if (rawWindowFilled < 3) rawWindowFilled++

        val medianLong = if (rawWindowFilled >= 3) {
            val a = rawWindow[0]
            val b = rawWindow[1]
            val c = rawWindow[2]
            max(min(a, b), min(max(a, b), c))
        } else {
            zeroCorrectedLong
        }

        // Stage B: Adaptive IIR Low-Pass Filter
        // Cutoff ~12-14Hz designed to preserve sharp throttle onset while attenuating engine & pavement rumble
        val rc = 1.0 / (2.0 * Math.PI * CUTOFF_FREQUENCY_HZ)
        val iirAlpha = (dtSec / (rc + dtSec)).coerceIn(0.18, 0.45)

        if (!isFilterInitialized) {
            filteredLongitudinal = medianLong
            isFilterInitialized = true
        } else {
            filteredLongitudinal = iirAlpha * medianLong + (1.0 - iirAlpha) * filteredLongitudinal
        }

        // Outlier sanity check
        val isOutlier = abs(zeroCorrectedLong - filteredLongitudinal) > SPIKE_OUTLIER_THRESHOLD

        // Calculate dynamic pitch/roll angles of the mount
        val gNorm = estimatedGravityVector.normalized()
        val pitchDeg = Math.toDegrees(asin(gNorm.z.coerceIn(-1.0, 1.0)))
        val rollDeg = Math.toDegrees(atan2(gNorm.x, gNorm.y))

        return LongitudinalProcessedSample(
            timestampMs = timestampMs,
            rawLongitudinalAccel = (rawLong * 1000).toInt() / 1000.0,
            filteredLongitudinalAccel = (filteredLongitudinal * 1000).toInt() / 1000.0,
            lateralAccel = (lateral * 1000).toInt() / 1000.0,
            verticalAccel = (vertical * 1000).toInt() / 1000.0,
            estimatedGravityMag = (estimatedGravityVector.magnitude * 100).toInt() / 100.0,
            dynamicPitchDeg = (pitchDeg * 10).toInt() / 10.0,
            dynamicRollDeg = (rollDeg * 10).toInt() / 10.0,
            gyroAngularSpeedRad = (gyroSpeed * 1000).toInt() / 1000.0,
            isOutlier = isOutlier,
            isExcessiveMovement = excessiveMovementDetected,
            isStationary = isStationary
        )
    }
}
