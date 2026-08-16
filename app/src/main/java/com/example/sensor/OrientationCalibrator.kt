package com.example.sensor

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 3D Vector for sensor physics math.
 */
data class Vector3(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0
) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Double) = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Double) = if (scalar != 0.0) Vector3(x / scalar, y / scalar, z / scalar) else Vector3(0.0, 0.0, 0.0)

    fun dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    val magnitude: Double get() = sqrt(x * x + y * y + z * z)

    fun normalized(): Vector3 {
        val mag = magnitude
        return if (mag > 1e-6) this / mag else Vector3(0.0, 0.0, 0.0)
    }
}

data class CalibratedOrientation(
    val longitudinalAxis: Vector3,
    val lateralAxis: Vector3,
    val verticalAxis: Vector3,
    val gravityBaseline: Vector3,
    val gyroBias: Vector3 = Vector3(),
    val pitchAngleDeg: Double = 0.0,
    val rollAngleDeg: Double = 0.0,
    val isCalibrated: Boolean = true
) {
    val vehicleForwardVector: Vector3 get() = longitudinalAxis
    val VEHICLE_FORWARD_VECTOR: Vector3 get() = longitudinalAxis

    fun projectLongitudinal(linearAccel: Vector3): Double {
        return linearAccel.dot(longitudinalAxis)
    }

    fun projectLateral(linearAccel: Vector3): Double {
        return linearAccel.dot(lateralAxis)
    }

    fun projectVertical(linearAccel: Vector3): Double {
        return linearAccel.dot(verticalAxis)
    }
}

/**
 * Single source of truth for orientation and calibration flow.
 */
enum class OrientationState {
    READY,
    CALIBRATING,
    CALIBRATED,
    FAILED,
    WAITING
}

enum class OrientationGuideStatus {
    POSITION_OK,
    NEEDS_MOUNT_TILT_UP
}

data class OrientationLiveCheck(
    val state: OrientationState = OrientationState.READY,
    val guideStatus: OrientationGuideStatus = OrientationGuideStatus.POSITION_OK,
    val instruction: String = "Posição correta — pronto para calibrar.",
    val isOrientationCompatible: Boolean = true,
    val pitchAngleDeg: Double = 0.0,
    val rollAngleDeg: Double = 0.0,
    val isStable: Boolean = true
)

sealed class CalibrationResult {
    data class Success(val orientation: CalibratedOrientation) : CalibrationResult()
    data class Failure(
        val reason: String,
        val guideStatus: OrientationGuideStatus = OrientationGuideStatus.POSITION_OK
    ) : CalibrationResult()
}

/**
 * OrientationCalibrator
 *
 * Realiza calibração estática do smartphone fixado no suporte do painel do veículo.
 * - Desacoplado de qualquer dependência de rotação do display do Android.
 * - Elimina validações antigas conflitantes (portrait/landscape UI lock).
 * - Isola o vetor de frente do veículo (VEHICLE_FORWARD_VECTOR) de forma ortogonal à gravidade.
 */
class OrientationCalibrator {

    companion object {
        const val CALIBRATION_DURATION_MS = 2500L
        const val CALIBRATION_TIMEOUT_MS = 5000L
        const val MIN_SAMPLES_REQUIRED = 5 // Realistic minimum for 2.5s on any device/emulator
        const val MAX_ALLOWED_ACCEL_VARIANCE = 1.50 // m/s² max variance during stationary rest
        const val MAX_ALLOWED_GYRO_SPEED = 0.60 // rad/s max rotation rate during stationary rest
        const val MAX_IN_PASS_ANGULAR_DRIFT_DEG = 14.0 // Max phone rotation in mount before invalidation
        const val MAX_IN_PASS_ANGULAR_VELOCITY = 1.50 // rad/s max in-pass jerk
    }

    private val accelSamples = mutableListOf<Vector3>()
    private val gyroSamples = mutableListOf<Vector3>()
    private val gravitySamples = mutableListOf<Vector3>()

    val sampleCount: Int get() = accelSamples.size.coerceAtLeast(gravitySamples.size)

    fun reset() {
        accelSamples.clear()
        gyroSamples.clear()
        gravitySamples.clear()
    }

    fun addAccelSample(accel: Vector3) {
        accelSamples.add(accel)
    }

    fun addGyroSample(gyro: Vector3) {
        gyroSamples.add(gyro)
    }

    fun addGravitySample(gravity: Vector3) {
        gravitySamples.add(gravity)
    }

    fun addCalibrationSample(
        accel: Vector3? = null,
        gyro: Vector3? = null,
        gravity: Vector3? = null
    ) {
        if (accel != null) accelSamples.add(accel)
        if (gyro != null) gyroSamples.add(gyro)
        if (gravity != null) gravitySamples.add(gravity)
    }

    /**
     * Live check of instantaneous sensor reading to provide guidance before/during calibration.
     * Note: Does NOT rely on Android display rotation or portrait/landscape UI lock.
     */
    fun evaluateInstantaneousOrientation(gravityOrAccel: Vector3, gyro: Vector3? = null): OrientationLiveCheck {
        val gNorm = gravityOrAccel.normalized()
        val gMag = gravityOrAccel.magnitude

        val isStable = if (gyro != null) {
            gyro.magnitude < MAX_ALLOWED_GYRO_SPEED && (gMag in 6.0..14.0 || gMag < 0.1)
        } else {
            gMag in 6.0..14.0 || gMag < 0.1
        }

        val zFraction = gNorm.z

        // Only flag WAITING if phone is lying completely flat facing directly up/down with zero tilt
        val isLyingCompletelyFlat = abs(zFraction) > 0.96 && gMag > 5.0

        val state = if (isLyingCompletelyFlat) OrientationState.WAITING else OrientationState.READY
        val guideStatus = if (isLyingCompletelyFlat) OrientationGuideStatus.NEEDS_MOUNT_TILT_UP else OrientationGuideStatus.POSITION_OK
        val instruction = if (isLyingCompletelyFlat) "Ajuste a posição do celular." else "Posição correta — pronto para calibrar."
        val isCompatible = !isLyingCompletelyFlat

        val pitchDeg = Math.toDegrees(asin(gNorm.z.coerceIn(-1.0, 1.0)))
        val rollDeg = Math.toDegrees(atan2(gNorm.x, gNorm.y))

        return OrientationLiveCheck(
            state = state,
            guideStatus = guideStatus,
            instruction = instruction,
            isOrientationCompatible = isCompatible,
            pitchAngleDeg = pitchDeg,
            rollAngleDeg = rollDeg,
            isStable = isStable
        )
    }

    /**
     * Analyzes accumulated stationary samples and determines vehicle coordinate basis.
     * Extracts longitudinal forward axis orthogonal to gravity baseline.
     */
    fun computeCalibration(
        displayRotation: Int = 1
    ): CalibrationResult {
        val totalCount = accelSamples.size.coerceAtLeast(gravitySamples.size)
        if (totalCount < MIN_SAMPLES_REQUIRED) {
            return CalibrationResult.Failure("Amostras insuficientes coletadas durante a calibração.")
        }

        // 1. Compute Mean Gravity Vector
        val gravitySource = if (gravitySamples.size >= MIN_SAMPLES_REQUIRED) gravitySamples else accelSamples
        if (gravitySource.isEmpty()) {
            return CalibrationResult.Failure("Não foi possível obter dados do acelerômetro.")
        }

        var sumGrav = Vector3()
        for (g in gravitySource) {
            sumGrav += g
        }
        val meanGravity = sumGrav / gravitySource.size.toDouble()
        val gravityMag = meanGravity.magnitude

        if (gravityMag < 4.0 || gravityMag > 16.0) {
            return CalibrationResult.Failure("Mantenha o smartphone e o veículo parados.")
        }

        // 2. Check Accelerometer Stability (Variance / Noise)
        if (accelSamples.isNotEmpty()) {
            var accelVarianceSum = 0.0
            for (a in accelSamples) {
                val diff = (a - meanGravity).magnitude
                accelVarianceSum += diff * diff
            }
            val accelStdDev = sqrt(accelVarianceSum / accelSamples.size)
            if (accelStdDev > MAX_ALLOWED_ACCEL_VARIANCE) {
                return CalibrationResult.Failure("Mantenha o smartphone e o veículo parados.")
            }
        }

        // 3. Check Gyroscope Stability
        var gyroBias = Vector3()
        if (gyroSamples.isNotEmpty()) {
            var sumGyro = Vector3()
            var maxGyroSpeed = 0.0
            for (w in gyroSamples) {
                sumGyro += w
                val speed = w.magnitude
                if (speed > maxGyroSpeed) maxGyroSpeed = speed
            }
            gyroBias = sumGyro / gyroSamples.size.toDouble()
            if (maxGyroSpeed > MAX_ALLOWED_GYRO_SPEED) {
                return CalibrationResult.Failure("Mantenha o smartphone e o veículo parados.")
            }
        }

        // 4. Build Orthogonal Coordinate Basis:
        val vDown = meanGravity.normalized()
        val backOfPhoneVector = Vector3(0.0, 0.0, -1.0)

        // Project backOfPhoneVector onto horizontal plane (orthogonal to gravity)
        val forwardVerticalComponent = backOfPhoneVector.dot(vDown)
        var forwardHorizontal = backOfPhoneVector - (vDown * forwardVerticalComponent)

        // If phone is mounted facing strictly down/up, fallback to orthogonal reference
        if (forwardHorizontal.magnitude < 0.05) {
            val altVector = if (abs(vDown.y) < 0.9) Vector3(0.0, 1.0, 0.0) else Vector3(1.0, 0.0, 0.0)
            forwardHorizontal = altVector - (vDown * altVector.dot(vDown))
        }

        // Normalized longitudinal forward axis (Traseira -> Frente do carro)
        val uLongitudinal = forwardHorizontal.normalized()

        // Vertical axis (pointing downwards along gravity)
        val uVertical = vDown

        // Lateral axis (pointing towards vehicle right side): uLateral = uVertical x uLongitudinal
        val uLateral = uVertical.cross(uLongitudinal).normalized()

        val pitchRad = forwardVerticalComponent.coerceIn(-1.0, 1.0).let { asin(it) }
        val pitchDeg = Math.toDegrees(pitchRad)

        val cal = CalibratedOrientation(
            longitudinalAxis = uLongitudinal,
            lateralAxis = uLateral,
            verticalAxis = uVertical,
            gravityBaseline = meanGravity,
            gyroBias = gyroBias,
            pitchAngleDeg = pitchDeg,
            isCalibrated = true
        )

        return CalibrationResult.Success(cal)
    }
}
