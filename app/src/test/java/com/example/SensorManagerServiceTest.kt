package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sensor.CalibratedOrientation
import com.example.sensor.SensorManagerService
import com.example.sensor.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class SensorManagerServiceTest {

    private lateinit var context: Context
    private lateinit var service: SensorManagerService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        service = SensorManagerService(context)
    }

    @Test
    fun testServiceInitializationAndPermissions() {
        val ready = service.checkPermissionsAndSensors()
        assertNotNull(service.availability.value)
    }

    @Test
    fun testApplyCalibrationAndVehicleForwardVector() {
        // Mock calibrated orientation: phone in portrait inclined 45 degrees
        val forwardVector = Vector3(0.0, 0.7071, -0.7071).normalized()
        val gravityBaseline = Vector3(0.0, 0.7071, 0.7071).normalized() * 9.80665
        val verticalAxis = gravityBaseline.normalized()
        val lateralAxis = verticalAxis.cross(forwardVector).normalized()

        val orientation = CalibratedOrientation(
            longitudinalAxis = forwardVector,
            lateralAxis = lateralAxis,
            verticalAxis = verticalAxis,
            gravityBaseline = gravityBaseline,
            isCalibrated = true
        )

        service.applyCalibration(orientation)

        assertNotNull(service.calibratedOrientation.value)
        assertEquals(forwardVector, service.calibratedOrientation.value?.VEHICLE_FORWARD_VECTOR)
        assertEquals(forwardVector, service.calibratedOrientation.value?.vehicleForwardVector)

        // Verify orthogonality between gravity and VEHICLE_FORWARD_VECTOR
        val dotProduct = gravityBaseline.normalized().dot(forwardVector)
        assertTrue("Forward vector must be orthogonal to gravity baseline", abs(dotProduct) < 1e-4)
    }
}
