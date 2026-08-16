package com.example.model

/**
 * State machine for the dyno pass lifecycle:
 * - AGUARDANDO: Waiting for the vehicle to start consistent acceleration.
 * - ACELERANDO: Actively recording strictly monotonic acceleration data for the dyno curve.
 * - FINALIZANDO: Detected sustained deceleration (throttle lift-off or braking for ~300-500ms).
 * - CONCLUIDA: Frozen immediately; no further points or filter tails can modify curve or peaks.
 */
enum class PassState(val label: String, val description: String) {
    AGUARDANDO("AGUARDANDO", "Aguardando início da aceleração..."),
    ACELERANDO("ACELERANDO", "Acelerando..."),
    FINALIZANDO("FINALIZANDO", "Finalizando puxada..."),
    CONCLUIDA("CONCLUÍDA", "Passada Concluída")
}

/**
 * Raw telemetry recorded during real test runs using device sensors.
 */
data class TelemetrySample(
    val timestampMs: Long,
    val speedKmh: Double,
    val accelMps2: Double,
    val state: PassState = PassState.AGUARDANDO,
    val isValidPullPoint: Boolean = false,
    val rpm: Int = 0
) {
    val speedMs: Double get() = speedKmh / 3.6
}

/**
 * Calculated curve point for dyno visualization.
 */
data class DynoPoint(
    val rpm: Int,
    val powerCv: Double,
    val torqueKgfm: Double,
    val speedKmh: Double,
    val timeSeconds: Double,
    val forceN: Double = 0.0,
    val accelMps2: Double = 0.0,
    val accelRawMps2: Double = accelMps2,
    val accelFilteredMps2: Double = accelMps2,
    val timestampMs: Long = 0L
)

/**
 * Architectural data source classification for separating demo simulation from real telemetry runs.
 */
enum class TestDataSource {
    DEMO_DATA,
    REAL_TEST_DATA
}

/**
 * Quality rating of the test pass based on sensor stability and noise level.
 */
enum class PassQuality(val label: String) {
    EXCELENTE("Excelente"),
    BOA("Boa"),
    REGULAR("Regular"),
    INVALIDA("Inválida")
}

/**
 * Container structure for raw real test telemetry data.
 */
data class RealTestData(
    val samples: List<TelemetrySample>,
    val gearIndex: Int,
    val capturedTimestampMs: Long = System.currentTimeMillis()
)

/**
 * Container structure for pre-calculated demonstration data.
 */
data class DemoData(
    val spec: VehicleSpec,
    val gearIndex: Int
)

data class DynoResult(
    val peakPowerCv: Double,
    val peakPowerRpm: Int,
    val peakTorqueKgfm: Double,
    val peakTorqueRpm: Int,
    val maxSpeedKmh: Double,
    val selectedGear: Int, // 0 = 1ª, 1 = 2ª, 2 = 3ª, 3 = 4ª, 4 = 5ª
    val vehicleSpec: VehicleSpec,
    val points: List<DynoPoint>, // Valid monotonic 100-RPM binned curve points
    val dataSource: TestDataSource = TestDataSource.DEMO_DATA,
    val realTestData: RealTestData? = null,
    val demoData: DemoData? = null,
    val initialSpeedKmh: Double = 0.0,
    val finalSpeedKmh: Double = 0.0,
    val initialRpm: Int = 0,
    val finalRpm: Int = 0,
    val maxAccelMps2: Double = 0.0,
    val maxForceN: Double = 0.0,
    val totalSampleCount: Int = 0,
    val avgSensorFrequencyHz: Double = 0.0,
    val isValid: Boolean = true,
    val invalidReason: String = "",
    val rejectedSampleCount: Int = 0,
    val passQuality: PassQuality = PassQuality.BOA,
    val rawPoints: List<DynoPoint> = emptyList(), // Valid unbinned pull points
    val rawSessionSamples: List<TelemetrySample> = emptyList() // All session telemetry including deceleration/braking
)

