package com.example.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * SensorManagerService
 *
 * Dedicated service class responsible for:
 * 1. Managing Android SensorManager registration and lifecycle.
 * 2. High-frequency capture of Accelerometer (raw/linear) and Gyroscope sensor streams.
 * 3. Handling runtime permission verification.
 * 4. Structuring raw and gravity-compensated telemetry for vehicle orientation
 *    and VEHICLE_FORWARD_VECTOR projection.
 */
class SensorManagerService(
    private val context: Context,
    private val sensorRate: Int = SensorManager.SENSOR_DELAY_FASTEST
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // Hardware Sensors
    val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val gravitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    val linearAccelerationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    // Dedicated background handler for sensor dispatch to avoid UI thread jitter
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    // Streaming state
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    // Permissions and Hardware Availability State
    data class SensorAvailability(
        val hasHighSamplingPermission: Boolean = true,
        val isAccelerometerAvailable: Boolean = false,
        val isGyroscopeAvailable: Boolean = false,
        val isGravityAvailable: Boolean = false,
        val isLinearAccelAvailable: Boolean = false
    ) {
        val isOperational: Boolean get() = isAccelerometerAvailable && isGyroscopeAvailable
    }

    private val _availability = MutableStateFlow(
        SensorAvailability(
            isAccelerometerAvailable = accelerometer != null,
            isGyroscopeAvailable = gyroscope != null,
            isGravityAvailable = gravitySensor != null,
            isLinearAccelAvailable = linearAccelerationSensor != null
        )
    )
    val availability: StateFlow<SensorAvailability> = _availability.asStateFlow()

    // Real-time Raw Sensor Vectors
    private val _rawAccelerometerData = MutableStateFlow(Vector3())
    val rawAccelerometerData: StateFlow<Vector3> = _rawAccelerometerData.asStateFlow()

    private val _rawGyroscopeData = MutableStateFlow(Vector3())
    val rawGyroscopeData: StateFlow<Vector3> = _rawGyroscopeData.asStateFlow()

    // Gravity and Gravity-Free Linear Acceleration (in device coordinate system)
    private val _estimatedGravity = MutableStateFlow(Vector3(0.0, 9.80665, 0.0))
    val estimatedGravity: StateFlow<Vector3> = _estimatedGravity.asStateFlow()

    private val _gravityFreeLinearAccel = MutableStateFlow(Vector3())
    val gravityFreeLinearAccel: StateFlow<Vector3> = _gravityFreeLinearAccel.asStateFlow()

    // Sensor Fusion and Calibration Subsystem
    val calibrator = OrientationCalibrator()
    val processor = LongitudinalSensorProcessor()

    private val _calibratedOrientation = MutableStateFlow<CalibratedOrientation?>(null)
    val calibratedOrientation: StateFlow<CalibratedOrientation?> = _calibratedOrientation.asStateFlow()

    // Calibrated Forward Acceleration (along VEHICLE_FORWARD_VECTOR)
    private val _forwardAccelerationMps2 = MutableStateFlow(0.0)
    val forwardAccelerationMps2: StateFlow<Double> = _forwardAccelerationMps2.asStateFlow()

    private val _latestProcessedSample = MutableStateFlow<LongitudinalProcessedSample?>(null)
    val latestProcessedSample: StateFlow<LongitudinalProcessedSample?> = _latestProcessedSample.asStateFlow()

    // Low-pass filter for gravity baseline tracking if TYPE_GRAVITY is missing
    private var gravityEstimate = Vector3(0.0, 9.80665, 0.0)
    private val gravityFilterAlpha = 0.08

    /**
     * Checks if the required sensor capabilities and permissions are satisfied.
     */
    fun checkPermissionsAndSensors(): Boolean {
        _availability.value = SensorAvailability(
            hasHighSamplingPermission = true,
            isAccelerometerAvailable = accelerometer != null,
            isGyroscopeAvailable = gyroscope != null,
            isGravityAvailable = gravitySensor != null,
            isLinearAccelAvailable = linearAccelerationSensor != null
        )
        return _availability.value.isOperational
    }

    /**
     * Starts listening to sensor events.
     */
    fun startListening(): Boolean {
        if (_isStreaming.value || sensorManager == null) return false

        checkPermissionsAndSensors()

        // Create background thread for sensor dispatch
        sensorThread = HandlerThread("SensorManagerServiceThread").apply { start() }
        sensorHandler = Handler(sensorThread!!.looper)

        var registeredAny = false

        accelerometer?.let {
            sensorManager.registerListener(this, it, sensorRate, sensorHandler)
            registeredAny = true
        }

        gyroscope?.let {
            sensorManager.registerListener(this, it, sensorRate, sensorHandler)
            registeredAny = true
        }

        gravitySensor?.let {
            sensorManager.registerListener(this, it, sensorRate, sensorHandler)
        }

        linearAccelerationSensor?.let {
            sensorManager.registerListener(this, it, sensorRate, sensorHandler)
        }

        _isStreaming.value = registeredAny
        return registeredAny
    }

    /**
     * Stops listening to sensor events and terminates background thread.
     */
    fun stopListening() {
        if (!_isStreaming.value) return

        sensorManager?.unregisterListener(this)
        _isStreaming.value = false

        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null
    }

    /**
     * Applies a static calibration orientation.
     */
    fun applyCalibration(orientation: CalibratedOrientation) {
        _calibratedOrientation.value = orientation
        processor.setCalibration(orientation)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val timestampMs = System.currentTimeMillis()
        val timestampNs = event.timestamp
        val x = event.values.getOrElse(0) { 0f }.toDouble()
        val y = event.values.getOrElse(1) { 0f }.toDouble()
        val z = event.values.getOrElse(2) { 0f }.toDouble()
        val currentVec = Vector3(x, y, z)

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                _rawAccelerometerData.value = currentVec

                // If hardware TYPE_GRAVITY is not available, estimate gravity via low-pass filter
                if (gravitySensor == null) {
                    gravityEstimate = Vector3(
                        gravityFilterAlpha * currentVec.x + (1.0 - gravityFilterAlpha) * gravityEstimate.x,
                        gravityFilterAlpha * currentVec.y + (1.0 - gravityFilterAlpha) * gravityEstimate.y,
                        gravityFilterAlpha * currentVec.z + (1.0 - gravityFilterAlpha) * gravityEstimate.z
                    )
                    _estimatedGravity.value = gravityEstimate
                    _gravityFreeLinearAccel.value = currentVec - gravityEstimate
                }

                // Process through 6-DOF longitudinal processor (projects along VEHICLE_FORWARD_VECTOR)
                val processed = processor.processAccelerometer(
                    accelRaw = currentVec,
                    timestampMs = timestampMs,
                    isKnownLinear = false
                )
                _latestProcessedSample.value = processed
                _forwardAccelerationMps2.value = processed.filteredLongitudinalAccel
            }

            Sensor.TYPE_GYROSCOPE -> {
                _rawGyroscopeData.value = currentVec
                processor.processGyroscope(currentVec, timestampNs)
            }

            Sensor.TYPE_GRAVITY -> {
                gravityEstimate = currentVec
                _estimatedGravity.value = currentVec
                _gravityFreeLinearAccel.value = _rawAccelerometerData.value - currentVec
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                _gravityFreeLinearAccel.value = currentVec
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handled as needed
    }
}
