package com.example

import com.example.sensor.CalibrationResult
import com.example.sensor.OrientationCalibrator
import com.example.sensor.OrientationGuideStatus
import com.example.sensor.OrientationState
import com.example.sensor.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrientationCalibratorTest {

    @Test
    fun testLiveCheck_carMount_isReady() {
        val calibrator = OrientationCalibrator()
        // Standard Car Mount: Gravity acts predominantly along X or Y (phone horizontal) + small Z tilt
        val mountSample = Vector3(9.5, 0.2, -2.2)

        val check = calibrator.evaluateInstantaneousOrientation(mountSample)
        assertTrue("Orientação no suporte deve ser compatível", check.isOrientationCompatible)
        assertEquals(OrientationState.READY, check.state)
        assertEquals(OrientationGuideStatus.POSITION_OK, check.guideStatus)
    }

    @Test
    fun testLiveCheck_flatOnTable_detectsNeedsTiltUp() {
        val calibrator = OrientationCalibrator()
        // Flat on table facing ceiling: Z ≈ 9.8, X ≈ 0, Y ≈ 0
        val flatSample = Vector3(0.1, 0.1, 9.8)

        val check = calibrator.evaluateInstantaneousOrientation(flatSample)
        assertFalse("Celular deitado não deve ser compatível", check.isOrientationCompatible)
        assertEquals(OrientationState.WAITING, check.state)
        assertEquals(OrientationGuideStatus.NEEDS_MOUNT_TILT_UP, check.guideStatus)
    }

    @Test
    fun testCalibration_standardPosition_compensatesTiltAndAlignsLongitudinal() {
        val calibrator = OrientationCalibrator()
        // Stationary samples in landscape mount with 20° tilt
        for (i in 0..40) {
            calibrator.addCalibrationSample(
                accel = Vector3(9.2, 0.1, -3.3),
                gyro = Vector3(0.001, 0.002, 0.001),
                gravity = Vector3(9.2, 0.1, -3.3)
            )
        }

        val result = calibrator.computeCalibration()
        assertTrue("Calibração deve ter sucesso", result is CalibrationResult.Success)

        val cal = (result as CalibrationResult.Success).orientation
        assertTrue(cal.isCalibrated)
        assertEquals(cal.longitudinalAxis, cal.VEHICLE_FORWARD_VECTOR)
        assertEquals(cal.longitudinalAxis, cal.vehicleForwardVector)

        // Verify that VEHICLE_FORWARD_VECTOR is orthogonal to gravity (removes gravity effect)
        val gravityDotForward = cal.gravityBaseline.normalized().dot(cal.VEHICLE_FORWARD_VECTOR)
        assertTrue("VEHICLE_FORWARD_VECTOR deve ser ortogonal ao vetor de gravidade (dot ≈ 0)", abs(gravityDotForward) < 1e-4)

        // Linear forward acceleration (vehicle pulling forward -> car moves forward, inertial force in phone)
        // With back of phone pointing to vehicle front (-Z), forward acceleration projects positive
        val testForwardLinearAccel = Vector3(0.0, 0.0, -3.5)
        val proj = cal.projectLongitudinal(testForwardLinearAccel)
        assertTrue("Projeção longitudinal para a frente deve ser positiva", proj > 0.0)
    }

    @Test
    fun testCalibration_separateSensorCallbacks_succeeds() {
        val calibrator = OrientationCalibrator()
        // Simulate separate onSensorChanged events arriving for accel, gyro, gravity
        for (i in 0..30) {
            calibrator.addAccelSample(Vector3(9.8, 0.05, 0.0))
            calibrator.addGyroSample(Vector3(0.001, 0.001, 0.002))
            calibrator.addGravitySample(Vector3(9.8, 0.0, 0.0))
        }

        val result = calibrator.computeCalibration()
        assertTrue("Calibração com callbacks separados de sensores deve ter sucesso", result is CalibrationResult.Success)
    }
}
