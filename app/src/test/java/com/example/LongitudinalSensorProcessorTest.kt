package com.example

import com.example.sensor.CalibratedOrientation
import com.example.sensor.LongitudinalSensorProcessor
import com.example.sensor.OrientationCalibrator
import com.example.sensor.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LongitudinalSensorProcessorTest {

    @Test
    fun testStationaryGravityCompensation_yieldsZeroLongitudinalAcceleration() {
        val calibrator = OrientationCalibrator()
        val mountGravity = Vector3(9.2, 0.1, -3.3) // Landscape mount with ~20° tilt

        for (i in 0..35) {
            calibrator.addCalibrationSample(
                accel = mountGravity,
                gyro = Vector3(0.001, 0.001, 0.001),
                gravity = mountGravity
            )
        }

        val calResult = calibrator.computeCalibration()
        assertTrue("Calibração estática deve ser bem-sucedida", calResult is com.example.sensor.CalibrationResult.Success)
        val orientation = (calResult as com.example.sensor.CalibrationResult.Success).orientation

        val processor = LongitudinalSensorProcessor(orientation)

        // Stationary state: raw accelerometer reads gravity vector
        var lastSample = processor.processAccelerometer(
            accelRaw = mountGravity,
            timestampMs = 1000L,
            isKnownLinear = false,
            vehicleSpeedKmh = 0.0
        )

        for (t in 1..20) {
            lastSample = processor.processAccelerometer(
                accelRaw = mountGravity,
                timestampMs = 1000L + t * 20L,
                isKnownLinear = false,
                vehicleSpeedKmh = 0.0
            )
        }

        // Longitudinal acceleration while stopped must be ~0.0 m/s² (gravity compensated)
        assertTrue(
            "Aceleração longitudinal parado deve ser ~0 m/s² mas foi ${lastSample.filteredLongitudinalAccel}",
            abs(lastSample.filteredLongitudinalAccel) < 0.15
        )
    }

    @Test
    fun testVehicleAccelerationPull_isolatesForwardAcceleration() {
        val calibrator = OrientationCalibrator()
        val mountGravity = Vector3(9.2, 0.1, -3.3)

        for (i in 0..35) {
            calibrator.addCalibrationSample(accel = mountGravity, gravity = mountGravity)
        }
        val orientation = (calibrator.computeCalibration() as com.example.sensor.CalibrationResult.Success).orientation
        val processor = LongitudinalSensorProcessor(orientation)

        // Simulate stationary baseline for 5 samples
        for (t in 1..5) {
            processor.processAccelerometer(mountGravity, 1000L + t * 20L, false, 0.0)
        }

        // Vehicle performs hard acceleration pull: +3.0 m/s² forward along vehicle motion
        // (with back of phone pointing forward -Z, forward linear acceleration adds -Z component)
        val pullAccel = mountGravity + Vector3(0.0, 0.0, -3.0)

        var pullSample = processor.processAccelerometer(pullAccel, 1200L, false, 40.0)
        for (t in 1..10) {
            pullSample = processor.processAccelerometer(pullAccel, 1200L + t * 20L, false, 45.0 + t * 1.5)
        }

        assertTrue(
            "Aceleração longitudinal durante puxada deve ser positiva (> 2.0 m/s²), obtido: ${pullSample.filteredLongitudinalAccel}",
            pullSample.filteredLongitudinalAccel > 2.0
        )
    }

    @Test
    fun testVibrationAndSpikeRejection() {
        val processor = LongitudinalSensorProcessor()
        val baseAccel = Vector3(0.0, 9.81, 0.0)

        // Simulate smooth ramp with an isolated physical shock spike
        for (t in 1..10) {
            processor.processAccelerometer(baseAccel + Vector3(0.0, 0.0, -1.5), 1000L + t * 20L, false, 30.0)
        }

        // Insert sudden high-frequency spike (+15 m/s²)
        val spikeSample = processor.processAccelerometer(baseAccel + Vector3(0.0, 0.0, -15.0), 1220L, false, 35.0)

        // The spike should be flagged as outlier and filtered by median/IIR
        assertTrue("Spike de vibração súbito deve ser filtrado ou marcado como outlier", spikeSample.isOutlier || spikeSample.filteredLongitudinalAccel < 8.0)
    }

    @Test
    fun testMountDislodgement_excessiveGyroRotationDetected() {
        val processor = LongitudinalSensorProcessor()

        // Gyro captures severe rotation (e.g. phone fell or rotated 30° in mount)
        processor.processGyroscope(Vector3(1.8, 0.2, 0.1), 1000000000L) // 1.8 rad/s > 1.25 rad/s threshold

        val sample = processor.processAccelerometer(Vector3(0.0, 9.81, 0.0), 1000L, false, 0.0)
        assertTrue("Movimento excessivo / queda do celular deve ser detectado", sample.isExcessiveMovement)
    }
}
