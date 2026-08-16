package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sensor.VehiclePerformanceSensorHelper
import com.example.sensor.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class VehiclePerformanceSensorHelperTest {

    private lateinit var context: Context
    private lateinit var helper: VehiclePerformanceSensorHelper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        helper = VehiclePerformanceSensorHelper(context)
    }

    @Test
    fun testTractiveForceCalculation() {
        val vehicleMassKg = 1500.0 // 1500 kg vehicle
        val accelMps2 = 4.0 // 4.0 m/s² (approx 0.41 G)

        val forceN = helper.calculateTractiveForceNewtons(vehicleMassKg, accelMps2)
        assertEquals(6000.0, forceN, 0.001)
    }

    @Test
    fun testInstantWheelPowerHpCalculation() {
        val vehicleMassKg = 1500.0
        val accelMps2 = 3.5 // m/s²
        val speedKmh = 100.0 // 100 km/h = 27.7778 m/s

        val powerHp = helper.calculateInstantWheelPowerHp(
            vehicleMassKg = vehicleMassKg,
            longitudinalAccelMps2 = accelMps2,
            speedKmh = speedKmh
        )

        // Force = 1500 * 3.5 = 5250 N
        // Power = 5250 * (100 / 3.6) = 145833.33 Watts
        // HP = 145833.33 / 745.699872 ≈ 195.56 HP
        assertTrue("HP should be approximately 195.56 HP", abs(powerHp - 195.56) < 1.0)
    }
}
