package com.example.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.core.content.ContextCompat
import com.example.engine.DynoPassEngine
import com.example.model.DynoPoint
import com.example.model.PassQuality
import com.example.model.PassState
import com.example.model.TelemetrySample
import com.example.model.VehicleSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class SensorHealthState(
    val hasLocationPermission: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val isAccelerometerAvailable: Boolean = false,
    val isGyroscopeAvailable: Boolean = false,
    val isGravitySensorAvailable: Boolean = false,
    val isLinearAccelAvailable: Boolean = false,
    val permissionRequestedOnce: Boolean = false
) {
    val isSystemReady: Boolean
        get() = hasLocationPermission && isGpsEnabled && isAccelerometerAvailable

    // Formatted statuses strictly matching requirements
    val accelerometerStatus: String
        get() = if (isAccelerometerAvailable) "OK" else "indisponível"

    val gyroscopeStatus: String
        get() = if (isGyroscopeAvailable) "OK" else "indisponível"

    val gpsStatus: String
        get() = when {
            !hasLocationPermission -> "sem permissão"
            !isGpsEnabled -> "desligado"
            else -> "OK"
        }
}

class DynoSensorManager(private val context: Context) : SensorEventListener, LocationListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // Sensors
    private val linearAccelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rawAccelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

    companion object {
        /**
         * High-performance sensor sampling delay.
         * Using SENSOR_DELAY_FASTEST ensures sub-10ms sampling interval (~100Hz - 200Hz)
         * for high-precision real-time dynamometer calculation and vibration filtering.
         */
        const val SENSOR_RATE_HIGH_PERFORMANCE = SensorManager.SENSOR_DELAY_FASTEST
        const val SENSOR_RATE_CALIBRATION = SensorManager.SENSOR_DELAY_FASTEST
        const val SENSOR_RATE_UI_PREVIEW = SensorManager.SENSOR_DELAY_UI
    }

    private var permissionRequestedOnce: Boolean = false

    // Health state
    private val _healthState = MutableStateFlow(SensorHealthState())
    val healthState: StateFlow<SensorHealthState> = _healthState.asStateFlow()

    // Calibration
    private val calibrator = OrientationCalibrator()
    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _calibrationProgress = MutableStateFlow(0f)
    val calibrationProgress: StateFlow<Float> = _calibrationProgress.asStateFlow()

    private val _calibratedOrientation = MutableStateFlow<CalibratedOrientation?>(null)
    val calibratedOrientation: StateFlow<CalibratedOrientation?> = _calibratedOrientation.asStateFlow()

    private val _calibrationError = MutableStateFlow<String?>(null)
    val calibrationError: StateFlow<String?> = _calibrationError.asStateFlow()

    // Single source of truth for orientation state: WAITING, READY, CALIBRATING, CALIBRATED
    private val _orientationState = MutableStateFlow(OrientationState.READY)
    val orientationState: StateFlow<OrientationState> = _orientationState.asStateFlow()

    // Live Orientation Guidance
    private val _liveOrientation = MutableStateFlow(
        OrientationLiveCheck(
            state = OrientationState.READY,
            guideStatus = OrientationGuideStatus.POSITION_OK,
            instruction = "Posição correta — pronto para calibrar.",
            isOrientationCompatible = true,
            pitchAngleDeg = 0.0,
            rollAngleDeg = 0.0,
            isStable = true
        )
    )
    val liveOrientation: StateFlow<OrientationLiveCheck> = _liveOrientation.asStateFlow()

    private var isMonitoringOrientation = false

    // Real-time Recording & Telemetry
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _passState = MutableStateFlow(PassState.AGUARDANDO)
    val passState: StateFlow<PassState> = _passState.asStateFlow()

    private val _validDynoPoints = MutableStateFlow<List<DynoPoint>>(emptyList())
    val validDynoPoints: StateFlow<List<DynoPoint>> = _validDynoPoints.asStateFlow()

    private val _currentPowerCv = MutableStateFlow(0.0)
    val currentPowerCv: StateFlow<Double> = _currentPowerCv.asStateFlow()

    private val _currentTorqueKgfm = MutableStateFlow(0.0)
    val currentTorqueKgfm: StateFlow<Double> = _currentTorqueKgfm.asStateFlow()

    private val _currentRpm = MutableStateFlow(0)
    val currentRpm: StateFlow<Int> = _currentRpm.asStateFlow()

    private val _currentSpeedKmh = MutableStateFlow(0.0)
    val currentSpeedKmh: StateFlow<Double> = _currentSpeedKmh.asStateFlow()

    // Strictly isolated longitudinal acceleration (TRASEIRA -> FRENTE)
    private val _currentAccelMps2 = MutableStateFlow(0.0)
    val currentAccelMps2: StateFlow<Double> = _currentAccelMps2.asStateFlow()

    private val _currentRawAccelMps2 = MutableStateFlow(0.0)
    val currentRawAccelMps2: StateFlow<Double> = _currentRawAccelMps2.asStateFlow()

    private val _currentLateralAccelMps2 = MutableStateFlow(0.0)
    val currentLateralAccelMps2: StateFlow<Double> = _currentLateralAccelMps2.asStateFlow()

    private val _currentVerticalAccelMps2 = MutableStateFlow(0.0)
    val currentVerticalAccelMps2: StateFlow<Double> = _currentVerticalAccelMps2.asStateFlow()

    private val _elapsedTimeSec = MutableStateFlow(0.0)
    val elapsedTimeSec: StateFlow<Double> = _elapsedTimeSec.asStateFlow()

    // In-pass Mount Movement / Slip Detection
    private val _excessiveMovementDetected = MutableStateFlow(false)
    val excessiveMovementDetected: StateFlow<Boolean> = _excessiveMovementDetected.asStateFlow()

    private val _movementInvalidReason = MutableStateFlow("")
    val movementInvalidReason: StateFlow<String> = _movementInvalidReason.asStateFlow()

    private val _outlierRejectedCount = MutableStateFlow(0)
    val outlierRejectedCount: StateFlow<Int> = _outlierRejectedCount.asStateFlow()

    private val _signalQuality = MutableStateFlow(PassQuality.BOA)
    val signalQuality: StateFlow<PassQuality> = _signalQuality.asStateFlow()

    // Sensor Fusion & Attitude diagnostics
    private val _dynamicPitchDeg = MutableStateFlow(0.0)
    val dynamicPitchDeg: StateFlow<Double> = _dynamicPitchDeg.asStateFlow()

    private val _dynamicRollDeg = MutableStateFlow(0.0)
    val dynamicRollDeg: StateFlow<Double> = _dynamicRollDeg.asStateFlow()

    private val _estimatedGravityMag = MutableStateFlow(9.81)
    val estimatedGravityMag: StateFlow<Double> = _estimatedGravityMag.asStateFlow()

    // Internal processing engine & buffers
    private val sensorProcessor = LongitudinalSensorProcessor()
    private var activePassEngine: DynoPassEngine? = null
    private val recordedSamples = mutableListOf<TelemetrySample>()
    private var startTimeMs: Long = 0L
    private var calibrationStartTimeMs: Long = 0L
    private var activeDisplayRotation: Int = Surface.ROTATION_90

    // Speed vs Accel correlation tracking for outlier classification
    private var lastSpeedCheckMs: Long = 0L
    private var lastSpeedCheckValue: Double = 0.0

    private val mainHandler = Handler(Looper.getMainLooper())
    private var calibrationCompleteCallback: ((Boolean, String?) -> Unit)? = null

    init {
        _healthState.value = evaluateHealthState()
    }

    fun refreshHealthState(): SensorHealthState {
        val state = evaluateHealthState()
        _healthState.value = state
        return state
    }

    private fun evaluateHealthState(): SensorHealthState {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasPermission = fineGranted || coarseGranted

        val gpsActive = try {
            locationManager?.let { lm ->
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } ?: false
        } catch (e: Exception) {
            false
        }

        return SensorHealthState(
            hasLocationPermission = hasPermission,
            isGpsEnabled = gpsActive,
            isAccelerometerAvailable = (linearAccelSensor != null || rawAccelSensor != null),
            isGyroscopeAvailable = (gyroSensor != null),
            isGravitySensorAvailable = (gravitySensor != null),
            isLinearAccelAvailable = (linearAccelSensor != null),
            permissionRequestedOnce = permissionRequestedOnce
        )
    }

    fun markPermissionRequested() {
        permissionRequestedOnce = true
        _healthState.value = _healthState.value.copy(permissionRequestedOnce = true)
    }

    // ==========================================
    // ORIENTATION MONITORING (PREPARATION & PRE-CALIB)
    // ==========================================

    fun startOrientationMonitoring() {
        if (isMonitoringOrientation || _isCalibrating.value || _isRecording.value) return
        isMonitoringOrientation = true
        rawAccelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gravitySensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stopOrientationMonitoring() {
        if (!isMonitoringOrientation) return
        isMonitoringOrientation = false
        if (!_isCalibrating.value && !_isRecording.value) {
            sensorManager?.unregisterListener(this)
        }
    }

    // ==========================================
    // CALIBRATION (VEHICLE STATIONARY - 3 SECONDS)
    // ==========================================

    fun startCalibration(
        displayRotation: Int = Surface.ROTATION_90,
        onComplete: (Boolean, String?) -> Unit
    ) {
        refreshHealthState()
        activeDisplayRotation = displayRotation
        calibrationCompleteCallback = onComplete

        _calibrationError.value = null
        _calibrationProgress.value = 0f
        _isCalibrating.value = true
        _orientationState.value = OrientationState.CALIBRATING
        calibrator.reset()
        calibrationStartTimeMs = System.currentTimeMillis()

        // Register sensors with high sampling rate
        rawAccelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        linearAccelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        gravitySensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        gyroSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }

        val totalMs = OrientationCalibrator.CALIBRATION_DURATION_MS
        val updateIntervalMs = 50L

        val progressRunnable = object : Runnable {
            override fun run() {
                if (!_isCalibrating.value) return

                val elapsed = System.currentTimeMillis() - calibrationStartTimeMs
                val prog = (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                _calibrationProgress.value = prog

                if (elapsed >= totalMs) {
                    finishCalibration()
                } else {
                    mainHandler.postDelayed(this, updateIntervalMs)
                }
            }
        }
        mainHandler.postDelayed(progressRunnable, updateIntervalMs)
    }

    private fun finishCalibration() {
        _isCalibrating.value = false
        if (!_isRecording.value && !isMonitoringOrientation) {
            sensorManager?.unregisterListener(this)
        }

        val result = calibrator.computeCalibration(
            displayRotation = activeDisplayRotation
        )

        when (result) {
            is CalibrationResult.Success -> {
                _calibratedOrientation.value = result.orientation
                sensorProcessor.setCalibration(result.orientation)
                _calibrationError.value = null
                _excessiveMovementDetected.value = false
                _movementInvalidReason.value = ""
                _orientationState.value = OrientationState.CALIBRATED
                _liveOrientation.value = _liveOrientation.value.copy(
                    state = OrientationState.CALIBRATED,
                    guideStatus = OrientationGuideStatus.POSITION_OK,
                    instruction = "Posição OK — Pronto para medir.",
                    isOrientationCompatible = true
                )
                calibrationCompleteCallback?.invoke(true, null)
            }
            is CalibrationResult.Failure -> {
                _calibratedOrientation.value = null
                _calibrationError.value = result.reason
                _orientationState.value = OrientationState.READY
                _liveOrientation.value = _liveOrientation.value.copy(
                    state = OrientationState.READY,
                    guideStatus = result.guideStatus,
                    instruction = result.reason,
                    isOrientationCompatible = true
                )
                calibrationCompleteCallback?.invoke(false, result.reason)
            }
        }
    }

    fun cancelCalibration() {
        _isCalibrating.value = false
        _calibrationProgress.value = 0f
        _orientationState.value = if (_calibratedOrientation.value != null) OrientationState.CALIBRATED else OrientationState.READY
        if (!_isRecording.value && !isMonitoringOrientation) {
            sensorManager?.unregisterListener(this)
        }
    }

    // ==========================================
    // REAL-TIME RECORDING (PULL / PASSADA)
    // ==========================================

    fun startRecording(spec: VehicleSpec = VehicleSpec.VECTRA_EXAMPLE, gearIndex: Int = 2) {
        refreshHealthState()
        recordedSamples.clear()
        startTimeMs = System.currentTimeMillis()
        _isRecording.value = true
        _elapsedTimeSec.value = 0.0
        _excessiveMovementDetected.value = false
        _movementInvalidReason.value = ""
        _outlierRejectedCount.value = 0
        _signalQuality.value = PassQuality.BOA

        _passState.value = PassState.AGUARDANDO
        _validDynoPoints.value = emptyList()
        _currentPowerCv.value = 0.0
        _currentTorqueKgfm.value = 0.0
        _currentRpm.value = 0

        activePassEngine = DynoPassEngine(spec = spec, gearIndex = gearIndex)
        _calibratedOrientation.value?.let { sensorProcessor.setCalibration(it) } ?: sensorProcessor.reset()

        lastSpeedCheckMs = startTimeMs
        lastSpeedCheckValue = _currentSpeedKmh.value

        // Register sensors for in-flight acquisition with high-performance sampling (~100Hz+)
        rawAccelSensor?.let { sensorManager?.registerListener(this, it, SENSOR_RATE_HIGH_PERFORMANCE) }
        linearAccelSensor?.let { sensorManager?.registerListener(this, it, SENSOR_RATE_HIGH_PERFORMANCE) }
        gravitySensor?.let { sensorManager?.registerListener(this, it, SENSOR_RATE_HIGH_PERFORMANCE) }
        gyroSensor?.let { sensorManager?.registerListener(this, it, SENSOR_RATE_HIGH_PERFORMANCE) }

        // Register GPS location updates
        try {
            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100L, 0f, this)
                } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 100L, 0f, this)
                }
            }
        } catch (e: SecurityException) {
            // Permission missing fallback
        }
    }

    fun stopRecording(): List<TelemetrySample> {
        _isRecording.value = false
        activePassEngine?.completePull()
        _passState.value = PassState.CONCLUIDA
        if (!isMonitoringOrientation) {
            sensorManager?.unregisterListener(this)
        }
        try {
            locationManager?.removeUpdates(this)
        } catch (e: Exception) {
            // ignore
        }
        val samples = activePassEngine?.rawSessionSamples ?: recordedSamples
        return samples.toList().ifEmpty { recordedSamples.toList() }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val x = event.values.getOrNull(0)?.toDouble() ?: 0.0
        val y = event.values.getOrNull(1)?.toDouble() ?: 0.0
        val z = event.values.getOrNull(2)?.toDouble() ?: 0.0
        val sensorVec = Vector3(x, y, z)

        // 1. Handling Calibration State
        if (_isCalibrating.value) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> calibrator.addCalibrationSample(accel = sensorVec)
                Sensor.TYPE_GYROSCOPE -> calibrator.addCalibrationSample(accel = sensorVec, gyro = sensorVec)
                Sensor.TYPE_GRAVITY -> calibrator.addCalibrationSample(accel = sensorVec, gravity = sensorVec)
            }
            return
        }

        // 2. Handling Live Orientation Preview Monitoring
        if (isMonitoringOrientation && !_isRecording.value) {
            if (event.sensor.type == Sensor.TYPE_GRAVITY || event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val live = calibrator.evaluateInstantaneousOrientation(sensorVec)
                _liveOrientation.value = live
                if (_calibratedOrientation.value == null && !_isCalibrating.value) {
                    _orientationState.value = live.state
                }
            }
            return
        }

        // 3. Handling Active Dyno Recording State
        if (!_isRecording.value) return

        val now = System.currentTimeMillis()
        val elapsedSec = (now - startTimeMs) / 1000.0
        _elapsedTimeSec.value = elapsedSec

        // Check Gyroscope for mount movement / phone slip
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            handleGyroscopeInFlight(sensorVec, event.timestamp)
            return
        }

        // Check Linear Accel or Accelerometer
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION || event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerationInFlight(sensorVec, event.sensor.type, now)
        }
    }

    private fun handleGyroscopeInFlight(gyroVec: Vector3, timestampNs: Long) {
        sensorProcessor.processGyroscope(gyroVec, timestampNs)

        val cal = _calibratedOrientation.value
        val gyroCalibrated = if (cal != null) gyroVec - cal.gyroBias else gyroVec
        val angularSpeed = gyroCalibrated.magnitude

        // Check for sharp sudden rotation / phone drop
        if (angularSpeed > OrientationCalibrator.MAX_IN_PASS_ANGULAR_VELOCITY) {
            triggerExcessiveMovement()
        }
    }

    private fun triggerExcessiveMovement() {
        if (!_excessiveMovementDetected.value) {
            _excessiveMovementDetected.value = true
            _movementInvalidReason.value = "Movimento excessivo do smartphone detectado. Verifique o suporte, reposicione o aparelho e calibre novamente."
            _signalQuality.value = PassQuality.INVALIDA
        }
    }

    private fun handleAccelerationInFlight(sensorVec: Vector3, sensorType: Int, nowMs: Long) {
        val isLinear = (sensorType == Sensor.TYPE_LINEAR_ACCELERATION)
        val processed = sensorProcessor.processAccelerometer(
            accelRaw = sensorVec,
            timestampMs = nowMs,
            isKnownLinear = isLinear,
            vehicleSpeedKmh = _currentSpeedKmh.value
        )

        _currentRawAccelMps2.value = processed.rawLongitudinalAccel
        _currentLateralAccelMps2.value = processed.lateralAccel
        _currentVerticalAccelMps2.value = processed.verticalAccel
        _dynamicPitchDeg.value = processed.dynamicPitchDeg
        _dynamicRollDeg.value = processed.dynamicRollDeg
        _estimatedGravityMag.value = processed.estimatedGravityMag

        if (processed.isExcessiveMovement) {
            triggerExcessiveMovement()
        }

        if (processed.isOutlier) {
            _outlierRejectedCount.value = _outlierRejectedCount.value + 1
        }

        // Accel vs Speed sanity cross-check over 1-second intervals
        if (nowMs - lastSpeedCheckMs >= 1000L) {
            val speedDelta = _currentSpeedKmh.value - lastSpeedCheckValue
            if (processed.filteredLongitudinalAccel > 4.0 && speedDelta < -2.0) {
                // Large accel but car actually braking -> suspect sample
                _outlierRejectedCount.value = _outlierRejectedCount.value + 1
            }
            lastSpeedCheckMs = nowMs
            lastSpeedCheckValue = _currentSpeedKmh.value
        }

        val cleanedAccel = max(0.0, processed.filteredLongitudinalAccel)
        _currentAccelMps2.value = cleanedAccel

        // Process through real-time state machine
        val engine = activePassEngine
        if (engine != null) {
            val newState = engine.processSample(
                timestampMs = nowMs,
                speedKmh = _currentSpeedKmh.value,
                rawAccelMps2 = cleanedAccel
            )
            _passState.value = newState

            if (newState == PassState.ACELERANDO || newState == PassState.CONCLUIDA) {
                _validDynoPoints.value = engine.validPullPoints.toList()
                _currentPowerCv.value = engine.peakPowerCv
                _currentTorqueKgfm.value = engine.peakTorqueKgfm
                _currentRpm.value = engine.frozenRpm
            }
        }

        // Record sample for dyno session diagnostics
        recordedSamples.add(
            TelemetrySample(
                timestampMs = nowMs,
                speedKmh = _currentSpeedKmh.value,
                accelMps2 = cleanedAccel,
                state = _passState.value
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            val speedKmh = location.speed * 3.6 // m/s to km/h
            _currentSpeedKmh.value = speedKmh.toDouble()
        }
    }

    @Deprecated("Deprecated in API level 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {
        refreshHealthState()
    }
    override fun onProviderDisabled(provider: String) {
        refreshHealthState()
    }

    /**
     * Unregisters all sensor and location listeners when ViewModel or Activity is destroyed.
     */
    fun release() {
        _isRecording.value = false
        _isCalibrating.value = false
        isMonitoringOrientation = false
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            // ignore
        }
        try {
            locationManager?.removeUpdates(this)
        } catch (e: Exception) {
            // ignore
        }
    }
}
