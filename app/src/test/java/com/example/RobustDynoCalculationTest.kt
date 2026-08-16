package com.example

import com.example.engine.DynoCalculator
import com.example.engine.SplineSmoothing
import com.example.model.TelemetrySample
import com.example.model.VehicleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RobustDynoCalculationTest {

    @Test
    fun testMadOutlierRejectionRemovesVibrationSpikes() {
        // Test sequence with single isolated spike: 40, 55, 280 (pothole/vibration), 62, 58, 70
        val noisyValues = listOf(40.0, 55.0, 280.0, 62.0, 58.0, 70.0)
        val cleaned = SplineSmoothing.rejectOutliersMad(noisyValues)

        assertFalse("Vibration spike of 280 should be removed", cleaned.contains(280.0))
        val median = SplineSmoothing.computeMedian(cleaned)
        assertTrue("Median should represent real trend (~58-62)", median in 55.0..65.0)
    }

    @Test
    fun testMonotoneSplineHasNoOvershoot() {
        val x = doubleArrayOf(2000.0, 3000.0, 4000.0, 5000.0, 6000.0)
        val y = doubleArrayOf(50.0, 80.0, 100.0, 95.0, 85.0)

        val spline = SplineSmoothing.interpolateMonotoneSpline(x, y)

        val maxY = y.maxOrNull() ?: 100.0
        val minY = y.minOrNull() ?: 50.0

        for (testRpm in 2000..6000 step 50) {
            val eval = spline(testRpm.toDouble())
            assertTrue(
                "Evaluated spline point ($eval) at $testRpm RPM must not overshoot maxY ($maxY)",
                eval <= maxY + 0.1
            )
            assertTrue(
                "Evaluated spline point ($eval) at $testRpm RPM must not undershoot minY ($minY)",
                eval >= minY - 0.1
            )
        }
    }

    @Test
    fun testStrictTorquePhysicsConsistency() {
        val spec = VehicleSpec(
            name = "Test Car",
            weightKg = 1100.0,
            tireWidthMm = 205,
            tireAspect = 55,
            rimInches = 16,
            finalDrive = 3.94,
            gearRatios = listOf(3.45, 1.95, 1.32, 0.97, 0.76)
        )

        val simulated = DynoCalculator.generateSimulatedRun(spec, gearIndex = 2)
        assertTrue("Simulated run must have points", simulated.points.isNotEmpty())

        for (p in simulated.points) {
            val expectedTorque = (p.powerCv * 716.2) / p.rpm
            val diff = abs(p.torqueKgfm - expectedTorque)
            assertTrue(
                "Torque at ${p.rpm} RPM (${p.torqueKgfm}) must match (CV * 716.2)/RPM ($expectedTorque), diff: $diff",
                diff < 0.2
            )
        }
    }

    @Test
    fun testEndPullOnSustainedDeceleration() {
        val spec = VehicleSpec(
            name = "Test Car",
            weightKg = 1100.0,
            tireWidthMm = 205,
            tireAspect = 55,
            rimInches = 16,
            finalDrive = 3.94,
            gearRatios = listOf(3.45, 1.95, 1.32, 0.97, 0.76)
        )

        // Build telemetry samples: 1. idle, 2. accelerating from 2000 to 5500 RPM, 3. sustained braking/deceleration
        val samples = mutableListOf<TelemetrySample>()
        var timestamp = 1000L
        var speed = 20.0

        // Acceleration phase
        for (i in 0 until 40) {
            timestamp += 60L
            speed += 1.2
            samples.add(
                TelemetrySample(
                    timestampMs = timestamp,
                    speedKmh = speed,
                    accelMps2 = 2.2
                )
            )
        }

        // Deceleration / braking phase
        for (i in 0 until 20) {
            timestamp += 60L
            speed -= 2.0
            samples.add(
                TelemetrySample(
                    timestampMs = timestamp,
                    speedKmh = speed.coerceAtLeast(0.0),
                    accelMps2 = -3.5
                )
            )
        }

        val result = DynoCalculator.calculate(samples, spec, gearIndex = 2)
        assertTrue("Pass must be valid", result.isValid)
        assertTrue("Points must be extracted from positive acceleration only", result.points.isNotEmpty())
        assertTrue("Peak power must be calculated on processed curve", result.peakPowerCv > 0)
        assertTrue("Deceleration samples must be preserved in rawSessionSamples", result.rawSessionSamples.size >= 50)
    }
}
