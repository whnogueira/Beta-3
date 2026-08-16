package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.DynoRepository
import com.example.engine.DynoPassEngine
import com.example.model.DynoPoint
import com.example.model.DynoResult
import com.example.model.PassQuality
import com.example.model.PassState
import com.example.model.TelemetrySample
import com.example.model.VehicleSpec
import com.example.sensor.CalibratedOrientation
import com.example.sensor.DynoSensorManager
import com.example.sensor.OrientationLiveCheck
import com.example.sensor.SensorHealthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScreenState {
    DASHBOARD,
    PREPARATION,
    ORIENTATION_CALIBRATION,
    LIVE_DYNO,
    RESULT,
    DIAGNOSTIC,
    HISTORY,
    SUPPORT
}

class DynoViewModel(
    private val repository: DynoRepository,
    val sensorManager: DynoSensorManager? = null
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(ScreenState.DASHBOARD)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    val allVehicles: StateFlow<List<VehicleSpec>> = repository.allVehicles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _vehicleSpec = MutableStateFlow(VehicleSpec.VECTRA_EXAMPLE)
    val vehicleSpec: StateFlow<VehicleSpec> = _vehicleSpec.asStateFlow()

    private val _selectedGearIndex = MutableStateFlow(2) // 3ª marcha default
    val selectedGearIndex: StateFlow<Int> = _selectedGearIndex.asStateFlow()

    private val _dynoResult = MutableStateFlow<DynoResult?>(null)
    val dynoResult: StateFlow<DynoResult?> = _dynoResult.asStateFlow()

    // Pass State Machine Management in ViewModel
    private var internalPassEngine: DynoPassEngine? = null
    private val _vmPassState = MutableStateFlow(PassState.AGUARDANDO)
    val vmPassState: StateFlow<PassState> = _vmPassState.asStateFlow()

    private val _vmValidPoints = MutableStateFlow<List<DynoPoint>>(emptyList())
    val vmValidPoints: StateFlow<List<DynoPoint>> = _vmValidPoints.asStateFlow()

    private val _vmPowerCv = MutableStateFlow(0.0)
    val vmPowerCv: StateFlow<Double> = _vmPowerCv.asStateFlow()

    private val _vmTorqueKgfm = MutableStateFlow(0.0)
    val vmTorqueKgfm: StateFlow<Double> = _vmTorqueKgfm.asStateFlow()

    private val _vmRpm = MutableStateFlow(0)
    val vmRpm: StateFlow<Int> = _vmRpm.asStateFlow()

    private val _showEditVehicleDialog = MutableStateFlow(false)
    val showEditVehicleDialog: StateFlow<Boolean> = _showEditVehicleDialog.asStateFlow()

    private val _showVehicleListDialog = MutableStateFlow(false)
    val showVehicleListDialog: StateFlow<Boolean> = _showVehicleListDialog.asStateFlow()

    private val _editingVehicle = MutableStateFlow<VehicleSpec>(VehicleSpec())
    val editingVehicle: StateFlow<VehicleSpec> = _editingVehicle.asStateFlow()

    // Expose reactive sensor and telemetry state flows directly
    val sensorHealthState: StateFlow<SensorHealthState>? = sensorManager?.healthState
    val isRecording: StateFlow<Boolean>? = sensorManager?.isRecording
    val passState: StateFlow<PassState>? = sensorManager?.passState
    val validDynoPoints: StateFlow<List<DynoPoint>>? = sensorManager?.validDynoPoints
    val currentPowerCv: StateFlow<Double>? = sensorManager?.currentPowerCv
    val currentTorqueKgfm: StateFlow<Double>? = sensorManager?.currentTorqueKgfm
    val currentRpm: StateFlow<Int>? = sensorManager?.currentRpm
    val currentSpeedKmh: StateFlow<Double>? = sensorManager?.currentSpeedKmh
    val currentAccelMps2: StateFlow<Double>? = sensorManager?.currentAccelMps2
    val currentRawAccelMps2: StateFlow<Double>? = sensorManager?.currentRawAccelMps2
    val currentLateralAccelMps2: StateFlow<Double>? = sensorManager?.currentLateralAccelMps2
    val currentVerticalAccelMps2: StateFlow<Double>? = sensorManager?.currentVerticalAccelMps2
    val elapsedTimeSec: StateFlow<Double>? = sensorManager?.elapsedTimeSec
    val isCalibrating: StateFlow<Boolean>? = sensorManager?.isCalibrating
    val calibrationProgress: StateFlow<Float>? = sensorManager?.calibrationProgress
    val calibratedOrientation: StateFlow<CalibratedOrientation?>? = sensorManager?.calibratedOrientation
    val calibrationError: StateFlow<String?>? = sensorManager?.calibrationError
    val liveOrientation: StateFlow<OrientationLiveCheck>? = sensorManager?.liveOrientation
    val excessiveMovementDetected: StateFlow<Boolean>? = sensorManager?.excessiveMovementDetected
    val movementInvalidReason: StateFlow<String>? = sensorManager?.movementInvalidReason
    val outlierRejectedCount: StateFlow<Int>? = sensorManager?.outlierRejectedCount
    val signalQuality: StateFlow<PassQuality>? = sensorManager?.signalQuality

    init {
        viewModelScope.launch {
            allVehicles.collect { list ->
                if (list.isNotEmpty()) {
                    val current = _vehicleSpec.value
                    val matching = list.find { it.id == current.id && it.id > 0L }
                        ?: list.find { it.name == current.name }
                        ?: list.first()
                    _vehicleSpec.value = matching
                }
            }
        }
    }

    /**
     * Initiates high-performance sensor capture (~100Hz+) for real-time dynamometer pull.
     */
    fun startDynoCapture(spec: VehicleSpec? = null, gearIndex: Int? = null) {
        val targetSpec = spec ?: _vehicleSpec.value
        val targetGear = gearIndex ?: _selectedGearIndex.value
        sensorManager?.startRecording(spec = targetSpec, gearIndex = targetGear)
    }

    /**
     * Stops sensor capture and retrieves all high-frequency telemetry samples.
     */
    fun stopDynoCapture(): List<TelemetrySample> {
        return sensorManager?.stopRecording() ?: emptyList()
    }

    /**
     * Starts continuous low-latency sensor monitoring for orientation alignment guidance.
     */
    fun startOrientationMonitoring() {
        sensorManager?.startOrientationMonitoring()
    }

    /**
     * Stops continuous sensor monitoring for orientation guidance.
     */
    fun stopOrientationMonitoring() {
        sensorManager?.stopOrientationMonitoring()
    }

    /**
     * Starts vehicle stationary 3-second 6-DOF sensor calibration.
     */
    fun startCalibration(
        displayRotation: Int = android.view.Surface.ROTATION_90,
        onComplete: (Boolean, String?) -> Unit
    ) {
        sensorManager?.startCalibration(displayRotation = displayRotation, onComplete = onComplete)
    }

    /**
     * Cancels an ongoing sensor calibration.
     */
    fun cancelCalibration() {
        sensorManager?.cancelCalibration()
    }

    /**
     * Refreshes the hardware sensor and GPS permission health status.
     */
    fun refreshSensorHealth() {
        sensorManager?.refreshHealthState()
    }

    /**
     * Initializes or resets the pass state machine for a new pull.
     */
    fun initPassStateMachine(spec: VehicleSpec? = null, gearIndex: Int? = null) {
        val targetSpec = spec ?: _vehicleSpec.value
        val targetGear = gearIndex ?: _selectedGearIndex.value
        internalPassEngine = DynoPassEngine(spec = targetSpec, gearIndex = targetGear)
        _vmPassState.value = PassState.AGUARDANDO
        _vmValidPoints.value = emptyList()
        _vmPowerCv.value = 0.0
        _vmTorqueKgfm.value = 0.0
        _vmRpm.value = 0
    }

    /**
     * Feeds an instantaneous telemetry sample into the ViewModel state machine.
     * Guarantees that only positive monotonic acceleration samples populate the dyno curve
     * and automatically detects end-of-pull / deceleration.
     */
    fun processPassSample(
        timestampMs: Long,
        speedKmh: Double,
        accelMps2: Double
    ): PassState {
        val engine = internalPassEngine ?: run {
            initPassStateMachine()
            internalPassEngine!!
        }

        val newState = engine.processSample(
            timestampMs = timestampMs,
            speedKmh = speedKmh,
            rawAccelMps2 = accelMps2
        )

        _vmPassState.value = newState
        if (newState == PassState.ACELERANDO || newState == PassState.CONCLUIDA) {
            _vmValidPoints.value = engine.validPullPoints.toList()
            _vmPowerCv.value = engine.peakPowerCv
            _vmTorqueKgfm.value = engine.peakTorqueKgfm
            _vmRpm.value = engine.frozenRpm
        }

        return newState
    }

    /**
     * Concludes the pull, freezing all metrics and discarding trailing deceleration data.
     */
    fun completePassManually() {
        internalPassEngine?.completePull()
        _vmPassState.value = PassState.CONCLUIDA
        sensorManager?.stopRecording()
    }

    fun markPermissionRequested() {
        sensorManager?.markPermissionRequested()
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister all high-frequency sensor and GPS listeners to preserve battery
        sensorManager?.release()
    }

    fun initActiveVehicle(context: Context) {
        viewModelScope.launch {
            repository.ensureExampleVehicleCreated(context)
            val savedId = repository.getActiveVehicleId(context)
            val vehicles = repository.getAllVehiclesList()
            if (vehicles.isNotEmpty()) {
                val active = vehicles.find { it.id == savedId } ?: vehicles.first()
                _vehicleSpec.value = active
            } else {
                _vehicleSpec.value = VehicleSpec.VECTRA_EXAMPLE
            }
        }
    }

    fun selectVehicle(spec: VehicleSpec, context: Context) {
        _vehicleSpec.value = spec
        repository.setActiveVehicleId(context, spec.id)
    }

    fun saveVehicle(spec: VehicleSpec, context: Context) {
        viewModelScope.launch {
            val savedId = repository.saveVehicle(spec)
            val updated = spec.copy(id = savedId)
            _vehicleSpec.value = updated
            repository.setActiveVehicleId(context, savedId)
            _showEditVehicleDialog.value = false
        }
    }

    fun deleteVehicle(spec: VehicleSpec, context: Context) {
        viewModelScope.launch {
            if (spec.id > 0L) {
                repository.deleteVehicle(spec.id)
            }
            val remaining = repository.getAllVehiclesList()
            if (remaining.isNotEmpty()) {
                val nextActive = remaining.first()
                _vehicleSpec.value = nextActive
                repository.setActiveVehicleId(context, nextActive.id)
            } else {
                _vehicleSpec.value = VehicleSpec()
                repository.setActiveVehicleId(context, 0L)
            }
            _showEditVehicleDialog.value = false
        }
    }

    fun openEditVehicle(spec: VehicleSpec? = null) {
        _editingVehicle.value = spec ?: _vehicleSpec.value
        _showEditVehicleDialog.value = true
    }

    fun openAddNewVehicle() {
        _editingVehicle.value = VehicleSpec()
        _showEditVehicleDialog.value = true
    }

    fun setShowEditVehicleDialog(show: Boolean) {
        _showEditVehicleDialog.value = show
    }

    fun setShowVehicleListDialog(show: Boolean) {
        _showVehicleListDialog.value = show
    }

    fun updateSelectedGear(gearIndex: Int) {
        _selectedGearIndex.value = gearIndex
    }

    fun onNovaPassadaClicked() {
        if (!_vehicleSpec.value.isConfigured) {
            openAddNewVehicle()
        } else {
            _currentScreen.value = ScreenState.PREPARATION
        }
    }

    fun onAdvanceToCalibration() {
        if (!_vehicleSpec.value.isConfigured) {
            openAddNewVehicle()
        } else {
            _currentScreen.value = ScreenState.ORIENTATION_CALIBRATION
        }
    }

    fun onCalibrationSuccessStartLiveDyno() {
        _currentScreen.value = ScreenState.LIVE_DYNO
    }

    fun onRecalibrateClicked() {
        _currentScreen.value = ScreenState.ORIENTATION_CALIBRATION
    }

    fun onStartTestClicked() {
        if (!_vehicleSpec.value.isConfigured) {
            openAddNewVehicle()
        } else {
            _currentScreen.value = ScreenState.ORIENTATION_CALIBRATION
        }
    }

    fun onTestFinished(result: DynoResult) {
        _dynoResult.value = result
        _currentScreen.value = ScreenState.RESULT
    }

    fun onViewDiagnostic(result: DynoResult) {
        _dynoResult.value = result
        _currentScreen.value = ScreenState.DIAGNOSTIC
    }

    fun onSelectHistoricalRun(runId: Long) {
        viewModelScope.launch {
            val result = repository.getRunAsDynoResult(runId, _vehicleSpec.value)
            if (result != null) {
                _dynoResult.value = result
                _currentScreen.value = ScreenState.RESULT
            }
        }
    }

    fun onOpenHistory() {
        _currentScreen.value = ScreenState.HISTORY
    }

    fun onOpenSupport() {
        _currentScreen.value = ScreenState.SUPPORT
    }

    fun onBackToDashboard() {
        _currentScreen.value = ScreenState.DASHBOARD
    }

    fun onBackToPreparation() {
        _currentScreen.value = ScreenState.PREPARATION
    }

    fun onBackFromDiagnostic() {
        if (_dynoResult.value != null) {
            _currentScreen.value = ScreenState.RESULT
        } else {
            _currentScreen.value = ScreenState.DASHBOARD
        }
    }
}
