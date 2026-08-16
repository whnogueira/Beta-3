package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * VehiclePerformanceSensorHelper
 *
 * Dedicated helper class to access the device's Accelerometer and Gyroscope sensor data
 * optimized specifically for calculating vehicle performance metrics (0-100 km/h,
 * quarter-mile acceleration, instant longitudinal G-force, lateral G-force, pitch/roll tilt,
 * and sensor-derived tractive force/wheel power).
 *
 * Features:
 * - High-rate sensor sampling with dedicated background thread (HandlerThread)
 * - Automatic fallback for gravity estimation and linear acceleration if hardware sensors are absent
 * - Real-time Kotlin StateFlows for reactive Compose UI integration
 * - Vehicle metric calculation utilities (Longitudinal G, Lateral G, Pitch/Roll, Instant Tractive Force)
 */
class VehiclePerformanceSensorHelper(
    private val context: Context,
    private val sensorDelay: Int = SensorManager.SENSOR_DELAY_FASTEST
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // Hardware Sensors
    val accelerometerSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val gyroscopeSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val gravitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    val linearAccelerationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    // Dedicated background handler thread for sensor sampling
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Tracking state
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    // Raw Sensor Readings
    private val _accelerometerData = MutableStateFlow(Vector3())
    val accelerometerData: StateFlow<Vector3> = _accelerometerData.asStateFlow()

    private val _gyroscopeData = MutableStateFlow(Vector3())
    val gyroscopeData: StateFlow<Vector3> = _gyroscopeData.asStateFlow()

    // Gravity and Linear Acceleration
    private val _gravityVector = MutableStateFlow(Vector3(0.0, 9.80665, 0.0))
    val gravityVector: StateFlow<Vector3> = _gravityVector.asStateFlow()

    private val _linearAcceleration = MutableStateFlow(Vector3())
    val linearAcceleration: StateFlow<Vector3> = _linearAcceleration.asStateFlow()

    // High-Level Vehicle Metrics Telemetry
    data class VehicleSensorMetrics(
        val timestampMs: Long = 0L,
        val longitudinalAccelMps2: Double = 0.0, // Acceleration along forward vehicle vector (m/s²)
        val longitudinalG: Double = 0.0,         // Longitudinal acceleration in Gs (1G ≈ 9.80665 m/s²)
        val lateralG: Double = 0.0,              // Cornering / side acceleration in Gs
        val verticalG: Double = 1.0,             // Normal road load in Gs
        val pitchDeg: Double = 0.0,              // Vehicle pitch angle in degrees (nose up/down)
        val rollDeg: Double = 0.0,               // Vehicle roll angle in degrees (body roll)
        val yawRateDegPerSec: Double = 0.0,      // Rotational speed around Z axis (deg/s)
        val totalAccelerationG: Double = 0.0     // Total resultant G force
    )

    private val _performanceMetrics = MutableStateFlow(VehicleSensorMetrics())
    val performanceMetrics: StateFlow<VehicleSensorMetrics> = _performanceMetrics.asStateFlow()

    // Gravity Low-Pass filter state for fallbacks
    private var lowPassGravity = Vector3(0.0, 9.80665, 0.0)
    private val alpha = 0.10 // Low-pass filter smoothing coefficient

    // Sensor Calibration Offsets
    private var calibAccelOffset = Vector3(0.0, 0.0, 0.0)
    private var calibGyroOffset = Vector3(0.0, 0.0, 0.0)
    private var isCalibrated = false

    /**
     * Checks if mandatory sensors for vehicle performance measurement are available.
     */
    fun isHardwareSupported(): Boolean {
        return accelerometerSensor != null
    }

    /**
     * Start capturing accelerometer and gyroscope readings.
     * Runs sensor callbacks on a dedicated HandlerThread to prevent UI stutter.
     */
    fun start(): Boolean {
        if (_isTracking.value || sensorManager == null) return false

        handlerThread = HandlerThread("VehiclePerfSensorThread").apply { start() }
        backgroundHandler = Handler(handlerThread!!.looper)

        var registered = false

        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, sensorDelay, backgroundHandler)
            registered = true
        }

        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, sensorDelay, backgroundHandler)
        }

        gravitySensor?.let {
            sensorManager.registerListener(this, it, sensorDelay, backgroundHandler)
        }

        linearAccelerationSensor?.let {
            sensorManager.registerListener(this, it, sensorDelay, backgroundHandler)
        }

        _isTracking.value = registered
        return registered
    }

    /**
     * Stop capturing sensor readings and release background thread resources.
     */
    fun stop() {
        if (!_isTracking.value) return

        sensorManager?.unregisterListener(this)
        _isTracking.value = false

        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null
    }

    /**
     * Set zero-offsets for stationary phone mounting.
     */
    fun setCalibrationOffsets(accelOffset: Vector3, gyroOffset: Vector3 = Vector3()) {
        this.calibAccelOffset = accelOffset
        this.calibGyroOffset = gyroOffset
        this.isCalibrated = true
    }

    /**
     * Calculates instant tractive force in Newtons: F = m * a
     * @param vehicleMassKg total vehicle mass including driver and fuel (kg)
     * @param longitudinalAccelMps2 forward acceleration in m/s²
     */
    fun calculateTractiveForceNewtons(vehicleMassKg: Double, longitudinalAccelMps2: Double): Double {
        return vehicleMassKg * longitudinalAccelMps2
    }

    /**
     * Calculates wheel power in Horsepower (hp) from tractive force and speed:
     * Power (Watts) = Force (N) * Speed (m/s)
     * 1 HP = 745.699872 Watts (metric CV ≈ 735.49875 W)
     */
    fun calculateInstantWheelPowerHp(
        vehicleMassKg: Double,
        longitudinalAccelMps2: Double,
        speedKmh: Double
    ): Double {
        val speedMps = speedKmh / 3.6
        val forceN = calculateTractiveForceNewtons(vehicleMassKg, longitudinalAccelMps2)
        val powerWatts = (forceN * speedMps).coerceAtLeast(0.0)
        return powerWatts / 745.699872
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()
        val x = event.values.getOrElse(0) { 0f }.toDouble()
        val y = event.values.getOrElse(1) { 0f }.toDouble()
        val z = event.values.getOrElse(2) { 0f }.toDouble()
        val rawVec = Vector3(x, y, z)

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                _accelerometerData.value = rawVec

                // If hardware gravity is unavailable, estimate using low-pass filtering
                if (gravitySensor == null) {
                    lowPassGravity = Vector3(
                        alpha * x + (1.0 - alpha) * lowPassGravity.x,
                        alpha * y + (1.0 - alpha) * lowPassGravity.y,
                        alpha * z + (1.0 - alpha) * lowPassGravity.z
                    )
                    _gravityVector.value = lowPassGravity
                }

                // If hardware linear acceleration is unavailable, subtract gravity vector
                if (linearAccelerationSensor == null) {
                    _linearAcceleration.value = rawVec - _gravityVector.value
                }

                updateVehicleMetrics(now)
            }

            Sensor.TYPE_GYROSCOPE -> {
                val compensatedGyro = rawVec - calibGyroOffset
                _gyroscopeData.value = compensatedGyro
                updateVehicleMetrics(now)
            }

            Sensor.TYPE_GRAVITY -> {
                _gravityVector.value = rawVec
                lowPassGravity = rawVec
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                _linearAcceleration.value = rawVec
                updateVehicleMetrics(now)
            }
        }
    }

    private fun updateVehicleMetrics(timestampMs: Long) {
        val linAccel = _linearAcceleration.value
        val grav = _gravityVector.value
        val gyro = _gyroscopeData.value

        val standardGravity = 9.80665

        // In standard landscape mount (front facing vehicle front):
        // X-axis or projection is longitudinal acceleration, Y is lateral, Z is vertical
        val forwardMps2 = linAccel.x
        val longG = forwardMps2 / standardGravity
        val latG = linAccel.y / standardGravity
        val vertG = (linAccel.z + grav.magnitude) / standardGravity

        val totalG = sqrt(longG * longG + latG * latG)

        // Pitch & Roll computation from gravity vector
        val pitchRad = atan2(grav.x, sqrt(grav.y * grav.y + grav.z * grav.z))
        val rollRad = atan2(grav.y, grav.z)

        val pitchDeg = pitchRad * (180.0 / PI)
        val rollDeg = rollRad * (180.0 / PI)
        val yawRateDegPerSec = gyro.z * (180.0 / PI)

        _performanceMetrics.value = VehicleSensorMetrics(
            timestampMs = timestampMs,
            longitudinalAccelMps2 = forwardMps2,
            longitudinalG = longG,
            lateralG = latG,
            verticalG = vertG,
            pitchDeg = pitchDeg,
            rollDeg = rollDeg,
            yawRateDegPerSec = yawRateDegPerSec,
            totalAccelerationG = totalG
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handled as needed
    }
}
