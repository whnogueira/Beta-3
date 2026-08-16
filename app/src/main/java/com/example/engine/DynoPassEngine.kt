package com.example.engine

import com.example.model.DynoPoint
import com.example.model.PassState
import com.example.model.TelemetrySample
import com.example.model.VehicleSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Real-time dyno pass state machine and monotonic acceleration engine.
 *
 * Implements strict positive-acceleration window isolation:
 * - AGUARDANDO: Waits for consistent positive acceleration onset (WOT in test gear).
 * - ACELERANDO: Strictly captures positive acceleration telemetry while RPM and speed are increasing.
 * - FINALIZANDO: Detects sustained deceleration (300-500ms) or throttle lift-off / braking.
 * - CONCLUÍDA: Immediately freezes curve, RPM, power, and torque.
 */
class DynoPassEngine(
    private val spec: VehicleSpec,
    private val gearIndex: Int
) {
    companion object {
        const val WATTS_TO_CV = 735.49875
        const val TORQUE_CONSTANT = 716.2

        // Acceleration onset thresholds
        const val ONSET_ACCEL_THRESHOLD = 0.35 // m/s²
        const val ONSET_MIN_SPEED_KMH = 8.0
        const val ONSET_CONSECUTIVE_SAMPLES = 3

        // Deceleration / end-of-pull thresholds
        const val DECEL_SUSTAINED_TIME_MS = 350L // 350ms (in the 300..500ms range)
        const val DECEL_ACCEL_THRESHOLD = 0.05 // m/s²
        const val DECEL_RPM_DROP_THRESHOLD = 150 // RPM drop from peak
        const val DECEL_SPEED_DROP_KMH = 1.5 // km/h drop from peak
    }

    private val totalMassKg = spec.weightKg + 100.0
    private val radiusM = spec.tireRadiusMeters
    private val totalRatio = spec.totalRatio(gearIndex)

    private val cd = 0.32
    private val frontalAreaM2 = 2.05
    private val airDensity = 1.225
    private val crr = 0.015
    private val g = 9.80665

    var currentState: PassState = PassState.AGUARDANDO
        private set

    val validPullPoints = mutableListOf<DynoPoint>()
    val rawSessionSamples = mutableListOf<TelemetrySample>()

    private var onsetCount = 0
    private var pullStartTimestampMs = 0L
    private var decelStartTimestampMs = 0L

    private var highestRpmSoFar = 0
    private var highestSpeedKmhSoFar = 0.0

    // Low latency IIR filter
    private var filteredAccel = 0.0
    private var isFilterInitialized = false
    private val filterAlpha = 0.28

    // Peak tracking
    var peakPowerCv: Double = 0.0
        private set
    var peakPowerRpm: Int = 0
        private set
    var peakTorqueKgfm: Double = 0.0
        private set
    var peakTorqueRpm: Int = 0
        private set
    var frozenSpeedKmh: Double = 0.0
        private set
    var frozenRpm: Int = 0
        private set

    fun reset() {
        currentState = PassState.AGUARDANDO
        validPullPoints.clear()
        rawSessionSamples.clear()
        onsetCount = 0
        pullStartTimestampMs = 0L
        decelStartTimestampMs = 0L
        highestRpmSoFar = 0
        highestSpeedKmhSoFar = 0.0
        filteredAccel = 0.0
        isFilterInitialized = false
        peakPowerCv = 0.0
        peakPowerRpm = 0
        peakTorqueKgfm = 0.0
        peakTorqueRpm = 0
        frozenSpeedKmh = 0.0
        frozenRpm = 0
    }

    /**
     * Process an instantaneous synchronized sample (same timestamp for speed and accel).
     */
    fun processSample(timestampMs: Long, speedKmh: Double, rawAccelMps2: Double): PassState {
        val speedMs = max(0.0, speedKmh / 3.6)
        val calculatedRpm = if (radiusM > 0 && totalRatio > 0) {
            ((speedMs * totalRatio * 60.0) / (2.0 * PI * radiusM)).toInt()
        } else 0

        // If already completed, record raw diagnostics only and keep frozen state
        if (currentState == PassState.CONCLUIDA) {
            rawSessionSamples.add(
                TelemetrySample(
                    timestampMs = timestampMs,
                    speedKmh = speedKmh,
                    accelMps2 = rawAccelMps2,
                    state = PassState.CONCLUIDA,
                    isValidPullPoint = false,
                    rpm = calculatedRpm
                )
            )
            return currentState
        }

        // Apply low-latency filter
        if (!isFilterInitialized) {
            filteredAccel = rawAccelMps2
            isFilterInitialized = true
        } else {
            filteredAccel = filterAlpha * rawAccelMps2 + (1.0 - filterAlpha) * filteredAccel
        }

        // =====================================================================
        // 1. STATE: AGUARDANDO
        // =====================================================================
        if (currentState == PassState.AGUARDANDO) {
            rawSessionSamples.add(
                TelemetrySample(
                    timestampMs = timestampMs,
                    speedKmh = speedKmh,
                    accelMps2 = rawAccelMps2,
                    state = PassState.AGUARDANDO,
                    isValidPullPoint = false,
                    rpm = calculatedRpm
                )
            )

            // Check for consistent positive acceleration onset
            if (rawAccelMps2 >= ONSET_ACCEL_THRESHOLD && speedKmh >= ONSET_MIN_SPEED_KMH && calculatedRpm >= 1000) {
                onsetCount++
                if (onsetCount >= ONSET_CONSECUTIVE_SAMPLES) {
                    // Transition to ACELERANDO
                    currentState = PassState.ACELERANDO
                    pullStartTimestampMs = timestampMs
                    highestRpmSoFar = calculatedRpm
                    highestSpeedKmhSoFar = speedKmh
                    filteredAccel = rawAccelMps2
                    decelStartTimestampMs = 0L
                }
            } else {
                onsetCount = 0
            }
            return currentState
        }

        // =====================================================================
        // 2. STATE: ACELERANDO
        // =====================================================================
        if (currentState == PassState.ACELERANDO) {
            // Check for sustained deceleration / throttle lift-off / braking
            val isDecelerating = filteredAccel <= DECEL_ACCEL_THRESHOLD ||
                calculatedRpm < (highestRpmSoFar - DECEL_RPM_DROP_THRESHOLD) ||
                speedKmh < (highestSpeedKmhSoFar - DECEL_SPEED_DROP_KMH)

            if (isDecelerating) {
                if (decelStartTimestampMs == 0L) {
                    decelStartTimestampMs = timestampMs
                }
                val decelDuration = timestampMs - decelStartTimestampMs
                if (decelDuration >= DECEL_SUSTAINED_TIME_MS) {
                    // Sustained deceleration detected -> Transition to FINALIZANDO / CONCLUIDA
                    currentState = PassState.FINALIZANDO
                    completePull()
                    rawSessionSamples.add(
                        TelemetrySample(
                            timestampMs = timestampMs,
                            speedKmh = speedKmh,
                            accelMps2 = rawAccelMps2,
                            state = PassState.CONCLUIDA,
                            isValidPullPoint = false,
                            rpm = calculatedRpm
                        )
                    )
                    return currentState
                }
            } else {
                // Actively accelerating
                decelStartTimestampMs = 0L
            }

            // Monotonicity and validity check:
            // 1. Filtered positive acceleration > 0.08 m/s²
            // 2. RPM increasing or forward progress
            // 3. Speed progressing forward
            // 4. Realistic engine operating band
            val isValidPullPoint = filteredAccel > 0.08 &&
                calculatedRpm >= (highestRpmSoFar - 50) &&
                speedKmh >= (highestSpeedKmhSoFar - 0.2) &&
                calculatedRpm in 1000..8500

            rawSessionSamples.add(
                TelemetrySample(
                    timestampMs = timestampMs,
                    speedKmh = speedKmh,
                    accelMps2 = rawAccelMps2,
                    state = PassState.ACELERANDO,
                    isValidPullPoint = isValidPullPoint,
                    rpm = calculatedRpm
                )
            )

            if (isValidPullPoint) {
                val fAccel = totalMassKg * filteredAccel
                val fAero = 0.5 * airDensity * cd * frontalAreaM2 * speedMs.pow(2)
                val fRoll = crr * totalMassKg * g
                val fTotal = max(0.0, fAccel + fAero + fRoll)

                val powerWatts = fTotal * speedMs
                val powerCv = max(0.0, powerWatts / WATTS_TO_CV)
                val torqueKgfm = if (calculatedRpm >= 500) (powerCv * TORQUE_CONSTANT) / calculatedRpm else 0.0

                // Reject impossible single-sample accelerometer spike (> 3.0x jump in < 60ms)
                var acceptPoint = true
                if (validPullPoints.size >= 2) {
                    val lastPoint = validPullPoints.last()
                    val dt = (timestampMs - lastPoint.timestampMs) / 1000.0
                    if (dt < 0.06 && powerCv > lastPoint.powerCv * 3.0 && powerCv > 120.0) {
                        acceptPoint = false
                    }
                }

                if (acceptPoint) {
                    val timeSec = (timestampMs - pullStartTimestampMs) / 1000.0
                    val dynoPoint = DynoPoint(
                        rpm = calculatedRpm,
                        powerCv = (powerCv * 10).toInt() / 10.0,
                        torqueKgfm = (torqueKgfm * 10).toInt() / 10.0,
                        speedKmh = (speedKmh * 10).toInt() / 10.0,
                        timeSeconds = (timeSec * 10).toInt() / 10.0,
                        forceN = (fTotal * 10).toInt() / 10.0,
                        accelMps2 = (filteredAccel * 100).toInt() / 100.0,
                        accelRawMps2 = (rawAccelMps2 * 100).toInt() / 100.0,
                        accelFilteredMps2 = (filteredAccel * 100).toInt() / 100.0,
                        timestampMs = timestampMs
                    )

                    validPullPoints.add(dynoPoint)
                    if (calculatedRpm > highestRpmSoFar) highestRpmSoFar = calculatedRpm
                    if (speedKmh > highestSpeedKmhSoFar) highestSpeedKmhSoFar = speedKmh

                    // Update live estimates
                    if (powerCv > peakPowerCv) {
                        peakPowerCv = powerCv
                        peakPowerRpm = calculatedRpm
                    }
                    if (torqueKgfm > peakTorqueKgfm) {
                        peakTorqueKgfm = torqueKgfm
                        peakTorqueRpm = calculatedRpm
                    }
                    frozenRpm = calculatedRpm
                    frozenSpeedKmh = speedKmh
                }
            }
        }

        return currentState
    }

    fun completePull() {
        currentState = PassState.CONCLUIDA
        if (validPullPoints.isNotEmpty()) {
            val lastPoint = validPullPoints.last()
            frozenRpm = lastPoint.rpm
            frozenSpeedKmh = lastPoint.speedKmh
        }
    }
}
