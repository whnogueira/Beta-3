package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.db.DynoDaoMock
import com.example.db.DynoRepository
import com.example.db.VehicleDaoMock
import com.example.engine.DynoCalculator
import com.example.model.DynoPoint
import com.example.model.DynoResult
import com.example.model.TelemetrySample
import com.example.model.VehicleSpec
import com.example.sensor.CalibrationResult
import com.example.sensor.OrientationCalibrator
import com.example.sensor.Vector3
import com.example.ui.viewmodel.DynoViewModel
import com.example.ui.viewmodel.ScreenState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DynoMobile", appName)
    }

    @Test
    fun `test Vectra example vehicle spec parameters and calculated tire metrics`() {
        val vectra = VehicleSpec.VECTRA_EXAMPLE
        assertEquals("Vectra 2.2 8V 1999 — Exemplo", vectra.name)
        assertEquals("Chevrolet", vectra.brand)
        assertEquals("Vectra", vectra.model)
        assertEquals(1999, vectra.year)
        assertEquals("2.2 8V", vectra.engine)
        assertTrue(vectra.drive.contains("Dianteira") || vectra.drive.contains("FWD"))
        assertEquals(1359.0, vectra.weightKg, 0.001)
        assertEquals(1380.0, vectra.testWeightKg, 0.001)
        assertEquals(1380.0, vectra.effectiveTestMassKg, 0.001)

        assertEquals(185, vectra.tireWidthMm)
        assertEquals(70, vectra.tireAspect)
        assertEquals(14, vectra.rimInches)

        // Diameter: 14 * 25.4 + 2 * (185 * 0.70) = 355.6 + 259.0 = 614.6 mm
        assertEquals(614.6, vectra.tireDiameterMm, 0.1)

        // Circumference: (614.6 / 1000) * PI ≈ 1.9308... m ≈ 1.931 m
        assertEquals(1.931, vectra.tireCircumferenceMeters, 0.005)

        assertEquals("F17 CCW", vectra.transmissionName)
        assertEquals(listOf(3.55, 1.95, 1.28, 0.89, 0.71), vectra.gearRatios)
        assertEquals(3.74, vectra.finalDrive, 0.001)
        assertTrue("Must be identified as example vehicle", vectra.isExampleVehicle)
        assertTrue(vectra.isConfigured)
    }

    @Test
    fun `test example vehicle lifecycle, seed once and respect user deletion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vehicleDao = VehicleDaoMock()
        val runDao = DynoDaoMock()
        val repository = DynoRepository(runDao, vehicleDao)

        // Clean prefs
        context.getSharedPreferences("dyno_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        // 1. First installation seed
        repository.ensureExampleVehicleCreated(context)
        val vehiclesAfterFirstSeed = repository.getAllVehiclesList()
        assertEquals(1, vehiclesAfterFirstSeed.size)
        assertEquals("Vectra 2.2 8V 1999 — Exemplo", vehiclesAfterFirstSeed.first().name)
        assertTrue(vehiclesAfterFirstSeed.first().isExampleVehicle)

        // 2. Subsequent launch -> Should NOT create duplicates
        repository.ensureExampleVehicleCreated(context)
        val vehiclesAfterSecondLaunch = repository.getAllVehiclesList()
        assertEquals(1, vehiclesAfterSecondLaunch.size)

        // 3. User deletes example vehicle
        val vectra = vehiclesAfterSecondLaunch.first()
        repository.deleteVehicle(vectra.id)
        assertEquals(0, repository.getAllVehiclesList().size)

        // 4. App relaunches -> Should NOT re-add deleted example vehicle
        repository.ensureExampleVehicleCreated(context)
        assertEquals(0, repository.getAllVehiclesList().size)

        // 5. User adds multiple custom vehicles
        val custom1 = VehicleSpec(name = "Civic VTI", weightKg = 1100.0)
        val custom2 = VehicleSpec(name = "M3 E36", weightKg = 1450.0)
        val id1 = repository.saveVehicle(custom1)
        val id2 = repository.saveVehicle(custom2)
        assertEquals(2, repository.getAllVehiclesList().size)

        // 6. Seed check doesn't wipe user vehicles
        repository.ensureExampleVehicleCreated(context)
        val list = repository.getAllVehiclesList()
        assertEquals(2, list.size)
        assertTrue(list.any { it.name == "Civic VTI" })
        assertTrue(list.any { it.name == "M3 E36" })
    }

    @Test
    fun `test OrientationCalibrator successfully establishes longitudinal axis and compensates gravity`() {
        val calibrator = OrientationCalibrator()

        for (i in 0..50) {
            calibrator.addCalibrationSample(
                accel = Vector3(8.50 + 0.01 * (i % 2), 0.02 * (i % 3 - 1), -4.90 + 0.01 * (i % 2)),
                gyro = Vector3(0.005, -0.005, 0.002)
            )
        }

        val result = calibrator.computeCalibration(displayRotation = 1)
        assertTrue("Calibration should succeed with stable stationary samples", result is CalibrationResult.Success)

        val cal = (result as CalibrationResult.Success).orientation
        assertTrue(cal.isCalibrated)
        assertEquals(1.0, cal.longitudinalAxis.magnitude, 0.01)
        assertEquals(1.0, cal.lateralAxis.magnitude, 0.01)
        assertEquals(1.0, cal.verticalAxis.magnitude, 0.01)

        val fwdAccel = cal.longitudinalAxis * 3.5
        val projected = cal.projectLongitudinal(fwdAccel)
        assertEquals(3.5, projected, 0.05)

        val latAccel = cal.lateralAxis * 4.0
        val latProjected = cal.projectLongitudinal(latAccel)
        assertEquals(0.0, latProjected, 0.001)

        val vertAccel = cal.verticalAxis * 6.0
        val vertProjected = cal.projectLongitudinal(vertAccel)
        assertEquals(0.0, vertProjected, 0.001)
    }

    @Test
    fun `test OrientationCalibrator rejects calibration if phone or vehicle was moving`() {
        val calibrator = OrientationCalibrator()

        for (i in 0..50) {
            calibrator.addCalibrationSample(
                accel = Vector3(2.5 * (i % 5), 8.0 + 3.0 * (i % 3), 4.0),
                gyro = Vector3(0.80 * (i % 2), 0.50, 0.0)
            )
        }

        val result = calibrator.computeCalibration(displayRotation = 1)
        assertTrue("Calibration should fail if phone is unstable", result is CalibrationResult.Failure)
        val failReason = (result as CalibrationResult.Failure).reason
        assertTrue(failReason.contains("parados") || failReason.contains("Mantenha"))
    }

    @Test
    fun `test DynoCalculator strict torque derivation and 100 RPM binning`() {
        val spec = VehicleSpec.VECTRA_EXAMPLE
        val sim = DynoCalculator.generateSimulatedRun(spec, gearIndex = 2)

        assertTrue(sim.isValid)
        assertTrue(sim.points.isNotEmpty())

        for (i in 0 until sim.points.size - 1) {
            val p = sim.points[i]
            val nextP = sim.points[i + 1]
            assertEquals(100, nextP.rpm - p.rpm)

            val expectedTorque = (p.powerCv * 716.2) / p.rpm
            assertTrue(
                "Torque ${p.torqueKgfm} should match derived torque $expectedTorque",
                abs(p.torqueKgfm - expectedTorque) < 0.2
            )
        }
    }

    @Test
    fun `test DynoCalculator outlier rejection and median filtering on noisy samples`() {
        val spec = VehicleSpec.VECTRA_EXAMPLE
        val samples = mutableListOf<TelemetrySample>()
        val baseTime = 1700000000000L

        for (i in 0..40) {
            val tMs = baseTime + (i * 100L)
            val speedKmh = 40.0 + (i * 2.0)
            val accel = if (i == 15) 25.0 else if (i == 25) -5.0 else 2.2 + (i * 0.02)
            samples.add(TelemetrySample(timestampMs = tMs, speedKmh = speedKmh, accelMps2 = accel))
        }

        val result = DynoCalculator.calculate(samples, spec, gearIndex = 2)

        assertTrue("Calculation should be valid after rejecting isolated outliers", result.isValid)
        assertTrue("Rejected sample count should track spikes", result.rejectedSampleCount >= 2)
        assertTrue("Points should be grouped in 100 RPM bins", result.points.isNotEmpty())
        assertTrue("Peak power should be realistic and filtered", result.peakPowerCv < 300.0)

        for (p in result.points) {
            val expectedTorque = (p.powerCv * 716.2) / p.rpm
            assertTrue(
                "Torque ${p.torqueKgfm} must strictly derive from power ${p.powerCv} at ${p.rpm} RPM (expected $expectedTorque)",
                abs(p.torqueKgfm - expectedTorque) < 0.2
            )
        }
    }

    @Test
    fun `test DynoCalculator invalidates inconsistent pull`() {
        val spec = VehicleSpec(name = "Test Vehicle", weightKg = 1200.0)
        val samples = listOf(
            TelemetrySample(1000L, 10.0, 0.1),
            TelemetrySample(1100L, 10.2, 0.1),
            TelemetrySample(1200L, 10.1, 0.0)
        )

        val result = DynoCalculator.calculate(samples, spec, gearIndex = 2)
        assertFalse("Should be marked as invalid pass", result.isValid)
        assertEquals(0.0, result.peakPowerCv, 0.01)
        assertEquals(0.0, result.peakTorqueKgfm, 0.01)
        assertTrue(result.invalidReason.isNotEmpty())
    }

    @Test
    fun `test complete flow with Vectra example ready to pull immediately`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runDao = DynoDaoMock()
        val vehicleDao = VehicleDaoMock()
        val repository = DynoRepository(runDao, vehicleDao)
        val viewModel = DynoViewModel(repository)

        viewModel.initActiveVehicle(context)

        // 1. Initial State: Vectra is active and configured
        assertEquals(ScreenState.DASHBOARD, viewModel.currentScreen.value)
        assertTrue(viewModel.vehicleSpec.value.isConfigured)
        assertEquals("Vectra 2.2 8V 1999 — Exemplo", viewModel.vehicleSpec.value.name)

        // 2. Click Nova Passada with Vectra -> Opens Preparation Screen immediately
        viewModel.onNovaPassadaClicked()
        assertEquals(ScreenState.PREPARATION, viewModel.currentScreen.value)

        // 3. Select Gear in Preparation
        viewModel.updateSelectedGear(2) // 3ª marcha
        assertEquals(2, viewModel.selectedGearIndex.value)

        // 4. Click Start Test -> Opens Orientation Calibration Screen
        viewModel.onStartTestClicked()
        assertEquals(ScreenState.ORIENTATION_CALIBRATION, viewModel.currentScreen.value)

        // 5. Complete Calibration -> Advances to Live Dyno HUD
        viewModel.onCalibrationSuccessStartLiveDyno()
        assertEquals(ScreenState.LIVE_DYNO, viewModel.currentScreen.value)

        // 6. Finish Test -> Opens Result Screen
        val mockResult = DynoResult(
            peakPowerCv = 123.0,
            peakPowerRpm = 5200,
            peakTorqueKgfm = 19.2,
            peakTorqueRpm = 2800,
            maxSpeedKmh = 142.0,
            selectedGear = 2,
            vehicleSpec = viewModel.vehicleSpec.value,
            points = listOf(
                DynoPoint(rpm = 2800, powerCv = 75.0, torqueKgfm = 19.2, speedKmh = 65.0, timeSeconds = 1.0),
                DynoPoint(rpm = 5200, powerCv = 123.0, torqueKgfm = 16.9, speedKmh = 120.0, timeSeconds = 3.5)
            )
        )
        viewModel.onTestFinished(mockResult)
        assertEquals(ScreenState.RESULT, viewModel.currentScreen.value)
        assertNotNull(viewModel.dynoResult.value)
        assertEquals(123.0, viewModel.dynoResult.value?.peakPowerCv)
    }

    @Test
    fun `test DynoViewModel sensor integration and high-performance capture control`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runDao = DynoDaoMock()
        val vehicleDao = VehicleDaoMock()
        val repository = DynoRepository(runDao, vehicleDao)
        val sensorManager = com.example.sensor.DynoSensorManager(context)
        val viewModel = DynoViewModel(repository, sensorManager)

        assertNotNull(viewModel.sensorManager)
        assertNotNull(viewModel.sensorHealthState)
        assertNotNull(viewModel.isRecording)
        assertEquals(false, viewModel.isRecording?.value)

        // Start capture for pull
        viewModel.startDynoCapture()
        assertEquals(true, viewModel.isRecording?.value)

        // Stop capture
        val samples = viewModel.stopDynoCapture()
        assertEquals(false, viewModel.isRecording?.value)
        assertNotNull(samples)

        // Refresh health check
        viewModel.refreshSensorHealth()
        assertNotNull(viewModel.sensorHealthState?.value)
    }

    @Test
    fun `test DynoViewModel pass state machine lifecycle and positive acceleration filtering`() {
        val runDao = DynoDaoMock()
        val vehicleDao = VehicleDaoMock()
        val repository = DynoRepository(runDao, vehicleDao)
        val viewModel = DynoViewModel(repository)

        viewModel.initPassStateMachine(VehicleSpec.VECTRA_EXAMPLE, gearIndex = 2)
        assertEquals(com.example.model.PassState.AGUARDANDO, viewModel.vmPassState.value)
        assertEquals(0, viewModel.vmValidPoints.value.size)

        var tMs = 1000000L

        // 1. In AGUARDANDO: low idle speed or no acceleration does NOT populate graph points
        for (i in 0..5) {
            tMs += 50L
            val state = viewModel.processPassSample(tMs, speedKmh = 15.0, accelMps2 = 0.05)
            assertEquals(com.example.model.PassState.AGUARDANDO, state)
            assertEquals(0, viewModel.vmValidPoints.value.size)
        }

        // 2. Transition to ACELERANDO with sustained positive acceleration
        for (i in 0..3) {
            tMs += 50L
            viewModel.processPassSample(tMs, speedKmh = 35.0 + i * 2.0, accelMps2 = 2.4)
        }
        assertEquals(com.example.model.PassState.ACELERANDO, viewModel.vmPassState.value)

        // 3. Accelerating: monotonic positive acceleration populates graph
        for (i in 1..20) {
            tMs += 50L
            val spd = 45.0 + (i * 2.5)
            viewModel.processPassSample(tMs, speedKmh = spd, accelMps2 = 2.2)
        }
        val pointsDuringAcceleration = viewModel.vmValidPoints.value.size
        assertTrue("Valid points must be generated during acceleration", pointsDuringAcceleration > 5)
        assertTrue("Power must be positive", viewModel.vmPowerCv.value > 0.0)
        assertTrue("Torque must be positive", viewModel.vmTorqueKgfm.value > 0.0)

        // 4. Vehicle lifts off / brakes: sustained deceleration for 400ms
        for (i in 1..9) { // 9 * 50ms = 450ms > 350ms threshold
            tMs += 50L
            viewModel.processPassSample(tMs, speedKmh = 95.0 - (i * 1.5), accelMps2 = -1.2)
        }

        // Must reach CONCLUIDA / FINALIZANDO and freeze points
        assertTrue(
            "State must transition to CONCLUIDA/FINALIZANDO after deceleration",
            viewModel.vmPassState.value == com.example.model.PassState.CONCLUIDA ||
            viewModel.vmPassState.value == com.example.model.PassState.FINALIZANDO
        )

        // Points count must NOT increase during deceleration/braking
        val pointsAfterDecel = viewModel.vmValidPoints.value.size
        assertEquals("Deceleration samples must NOT be added to dyno curve", pointsDuringAcceleration, pointsAfterDecel)
    }
}
