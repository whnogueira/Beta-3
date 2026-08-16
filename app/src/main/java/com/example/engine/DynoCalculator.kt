package com.example.engine

import com.example.model.DynoPoint
import com.example.model.DynoResult
import com.example.model.PassQuality
import com.example.model.RealTestData
import com.example.model.TelemetrySample
import com.example.model.TestDataSource
import com.example.model.VehicleSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Automotive Inertial Dynamometer Calculation Engine.
 *
 * Implements strict physics calculation:
 * - Separation of RAW DATA and DYNO CURVE DATA.
 * - Grouping by ~100 RPM discrete bins with MAD outlier rejection and median/trimmed-mean metrics.
 * - Monotone Cubic Spline (Fritsch-Carlson) smooth curve construction with ZERO mathematical overshoot.
 * - Strict torque derivation: Torque_kgfm = (Power_CV * 716.2) / RPM.
 * - Peak power and torque derived solely from the processed curve.
 * - Offline operation with no fixed artificial curves and no fake power invention.
 */
object DynoCalculator {

    const val WATTS_TO_CV = 735.49875
    const val TORQUE_CONSTANT = 716.2 // CV = kgfm * RPM / 716.2 => kgfm = CV * 716.2 / RPM

    /**
     * Calculates dyno metrics and curves from raw telemetry samples.
     */
    fun calculate(
        samples: List<TelemetrySample>,
        spec: VehicleSpec,
        gearIndex: Int
    ): DynoResult {
        val totalRawCount = samples.size
        if (totalRawCount < 5) {
            return buildInvalidResult(
                spec = spec,
                gearIndex = gearIndex,
                totalCount = totalRawCount,
                rejectedCount = totalRawCount,
                reason = "Amostras insuficientes gravadas. Refaça a passagem acelerando em marcha única."
            )
        }

        val totalMassKg = spec.weightKg + 100.0 // Car mass + driver/fluids
        val radiusM = spec.tireRadiusMeters
        val totalRatio = spec.totalRatio(gearIndex)

        val cd = 0.32
        val frontalAreaM2 = 2.05
        val airDensity = 1.225
        val crr = 0.015
        val g = 9.80665

        // Step 1: Run DynoPassEngine over session samples to isolate positive acceleration window
        val passEngine = DynoPassEngine(spec = spec, gearIndex = gearIndex)
        for (s in samples) {
            passEngine.processSample(
                timestampMs = s.timestampMs,
                speedKmh = s.speedKmh,
                rawAccelMps2 = s.accelMps2
            )
        }

        val validPullPoints = passEngine.validPullPoints
        val allSessionSamples = passEngine.rawSessionSamples.ifEmpty { samples }

        // Step 2: Validate acceleration segment duration, RPM span, and speed delta
        val duration = if (validPullPoints.isNotEmpty()) validPullPoints.last().timeSeconds - validPullPoints.first().timeSeconds else 0.0
        val minRpm = validPullPoints.minOfOrNull { it.rpm } ?: 0
        val maxRpm = validPullPoints.maxOfOrNull { it.rpm } ?: 0
        val minSpeed = validPullPoints.minOfOrNull { it.speedKmh } ?: 0.0
        val maxSpeed = validPullPoints.maxOfOrNull { it.speedKmh } ?: 0.0
        val rpmDelta = maxRpm - minRpm
        val speedDelta = maxSpeed - minSpeed

        val isValidPass = validPullPoints.size >= 6 &&
            duration >= 1.0 &&
            rpmDelta >= 700 &&
            speedDelta >= 7.0

        if (!isValidPass) {
            return buildInvalidResult(
                spec = spec,
                gearIndex = gearIndex,
                totalCount = totalRawCount,
                rejectedCount = totalRawCount - validPullPoints.size,
                rawPoints = validPullPoints,
                rawSessionSamples = allSessionSamples,
                reason = "Dados insuficientes ou aceleração curta. Pise fundo em marcha única até próximo ao limitador."
            )
        }

        // Step 3: Group samples into 100 RPM bands with robust outlier rejection
        val sortedRawPoints = validPullPoints.sortedBy { it.rpm }
        val processedCurvePoints = buildRobustProcessedDynoCurve(
            rawPoints = sortedRawPoints,
            spec = spec,
            gearIndex = gearIndex,
            totalRatio = totalRatio,
            radiusM = radiusM,
            totalMassKg = totalMassKg
        )

        if (processedCurvePoints.size < 4) {
            return buildInvalidResult(
                spec = spec,
                gearIndex = gearIndex,
                totalCount = totalRawCount,
                rejectedCount = totalRawCount - validPullPoints.size,
                rawPoints = sortedRawPoints,
                rawSessionSamples = allSessionSamples,
                reason = "Vibração excessiva ou inconsistência nos sensores. Repita a passada."
            )
        }

        // Step 4: Extract Peak Power and Peak Torque strictly from the PROCESSED CURVE
        val peakCvPoint = processedCurvePoints.maxByOrNull { it.powerCv } ?: processedCurvePoints.first()
        val peakKgfmPoint = processedCurvePoints.maxByOrNull { it.torqueKgfm } ?: processedCurvePoints.first()
        val maxRecordedSpeed = processedCurvePoints.maxOfOrNull { it.speedKmh } ?: maxSpeed
        val maxAccel = sortedRawPoints.maxOfOrNull { it.accelFilteredMps2 } ?: 0.0
        val maxForce = sortedRawPoints.maxOfOrNull { it.forceN } ?: 0.0

        // Determine Quality rating
        val rejectedRatio = (totalRawCount - validPullPoints.size).toDouble() / totalRawCount
        val quality = when {
            rejectedRatio <= 0.35 && rpmDelta >= 2000 && duration >= 2.2 -> PassQuality.EXCELENTE
            rpmDelta >= 1200 -> PassQuality.BOA
            else -> PassQuality.REGULAR
        }

        return DynoResult(
            peakPowerCv = (peakCvPoint.powerCv * 10).toInt() / 10.0,
            peakPowerRpm = peakCvPoint.rpm,
            peakTorqueKgfm = (peakKgfmPoint.torqueKgfm * 10).toInt() / 10.0,
            peakTorqueRpm = peakKgfmPoint.rpm,
            maxSpeedKmh = (maxRecordedSpeed * 10).toInt() / 10.0,
            selectedGear = gearIndex,
            vehicleSpec = spec,
            points = processedCurvePoints, // Clean processed curve
            rawPoints = sortedRawPoints, // Raw unbinned pull points
            rawSessionSamples = allSessionSamples, // Complete session stream
            dataSource = TestDataSource.REAL_TEST_DATA,
            realTestData = RealTestData(allSessionSamples, gearIndex),
            initialSpeedKmh = (processedCurvePoints.first().speedKmh * 10).toInt() / 10.0,
            finalSpeedKmh = (processedCurvePoints.last().speedKmh * 10).toInt() / 10.0,
            initialRpm = processedCurvePoints.first().rpm,
            finalRpm = processedCurvePoints.last().rpm,
            maxAccelMps2 = (maxAccel * 100).toInt() / 100.0,
            maxForceN = (maxForce * 10).toInt() / 10.0,
            totalSampleCount = totalRawCount,
            rejectedSampleCount = totalRawCount - validPullPoints.size,
            avgSensorFrequencyHz = if (duration > 0) totalRawCount / duration else 10.0,
            isValid = true,
            invalidReason = "",
            passQuality = quality
        )
    }

    /**
     * Builds a smooth, professional, and physically rigorous processed curve:
     * 1. 100 RPM Bins (e.g. 2000-2099 RPM, 2100-2199 RPM).
     * 2. Robust MAD outlier rejection within each bin (discards isolated 280 CV vibration spikes in 55 CV regions).
     * 3. Robust median/trimmed-mean aggregation for acceleration, speed, power, and torque.
     * 4. Cross-bin consistency check to prevent single-bin contamination.
     * 5. Monotone Cubic Spline (Fritsch-Carlson) interpolation with ZERO mathematical overshoot.
     * 6. Strict Torque derivation: kgfm = (CV * 716.2) / RPM.
     */
    fun buildRobustProcessedDynoCurve(
        rawPoints: List<DynoPoint>,
        spec: VehicleSpec,
        gearIndex: Int,
        totalRatio: Double,
        radiusM: Double,
        totalMassKg: Double
    ): List<DynoPoint> {
        if (rawPoints.isEmpty()) return emptyList()

        val minRawRpm = rawPoints.first().rpm
        val maxRawRpm = rawPoints.last().rpm

        val startBinRpm = ((minRawRpm / 100) * 100).coerceAtLeast(1000)
        val endBinRpm = (((maxRawRpm + 99) / 100) * 100).coerceAtMost(8000)

        val cd = 0.32
        val frontalAreaM2 = 2.05
        val airDensity = 1.225
        val crr = 0.015
        val g = 9.80665

        val rawBinnedPoints = mutableListOf<DynoPoint>()

        // 1. Group into 100 RPM discrete bins
        for (binRpm in startBinRpm..endBinRpm step 100) {
            val binEnd = binRpm + 99
            val samplesInBin = rawPoints.filter { it.rpm in binRpm..binEnd }

            if (samplesInBin.isNotEmpty()) {
                val rawPowerList = samplesInBin.map { it.powerCv }
                val rawAccelList = samplesInBin.map { it.accelFilteredMps2 }
                val rawSpeedList = samplesInBin.map { it.speedKmh }
                val rawTimeList = samplesInBin.map { it.timeSeconds }

                // Outlier rejection inside bin using Median Absolute Deviation (MAD)
                val cleanPowerList = SplineSmoothing.rejectOutliersMad(rawPowerList)
                val cleanAccelList = SplineSmoothing.rejectOutliersMad(rawAccelList)

                // Robust central values (median or trimmed mean)
                val robustPowerCv = SplineSmoothing.computeMedian(cleanPowerList)
                val robustAccelMps2 = SplineSmoothing.computeMedian(cleanAccelList)
                val robustSpeedKmh = SplineSmoothing.computeMedian(rawSpeedList)
                val robustTimeSec = SplineSmoothing.computeMedian(rawTimeList)

                val speedMs = max(0.1, robustSpeedKmh / 3.6)
                val fAccel = totalMassKg * robustAccelMps2
                val fAero = 0.5 * airDensity * cd * frontalAreaM2 * speedMs.pow(2)
                val fRoll = crr * totalMassKg * g
                val fTotal = max(0.0, fAccel + fAero + fRoll)

                val binCenterRpm = binRpm + 50
                // Strict torque formula: Torque_kgfm = (Power_CV * 716.2) / RPM
                val derivedTorqueKgfm = if (binCenterRpm >= 500) {
                    (robustPowerCv * TORQUE_CONSTANT) / binCenterRpm
                } else 0.0

                rawBinnedPoints.add(
                    DynoPoint(
                        rpm = binCenterRpm,
                        powerCv = (robustPowerCv * 10).toInt() / 10.0,
                        torqueKgfm = (derivedTorqueKgfm * 10).toInt() / 10.0,
                        speedKmh = (robustSpeedKmh * 10).toInt() / 10.0,
                        timeSeconds = (robustTimeSec * 10).toInt() / 10.0,
                        forceN = (fTotal * 10).toInt() / 10.0,
                        accelMps2 = (robustAccelMps2 * 100).toInt() / 100.0,
                        accelRawMps2 = (robustAccelMps2 * 100).toInt() / 100.0,
                        accelFilteredMps2 = (robustAccelMps2 * 100).toInt() / 100.0
                    )
                )
            }
        }

        if (rawBinnedPoints.size < 3) return rawBinnedPoints

        // 2. Cross-Bin Outlier Rejection:
        // A single anomalous bin cannot jump unrealistically compared to its neighbors.
        val cleanedBins = mutableListOf<DynoPoint>()
        for (i in rawBinnedPoints.indices) {
            val current = rawBinnedPoints[i]
            val prev = rawBinnedPoints.getOrNull(i - 1)
            val next = rawBinnedPoints.getOrNull(i + 1)

            var adjustedPower = current.powerCv
            if (prev != null && next != null) {
                val neighborAvg = (prev.powerCv + next.powerCv) / 2.0
                // If power is an isolated spike > 45% above neighbor average
                if (current.powerCv > neighborAvg * 1.45 && current.powerCv > 40.0) {
                    adjustedPower = neighborAvg * 1.15
                }
            }

            val derivedTorque = if (current.rpm >= 500) (adjustedPower * TORQUE_CONSTANT) / current.rpm else 0.0
            cleanedBins.add(
                current.copy(
                    powerCv = (adjustedPower * 10).toInt() / 10.0,
                    torqueKgfm = (derivedTorque * 10).toInt() / 10.0
                )
            )
        }

        // 3. Interpolate small 1-bin gaps (e.g. 200 RPM difference) without extrapolating outside data
        val gapFilledBins = mutableListOf<DynoPoint>()
        for (i in cleanedBins.indices) {
            gapFilledBins.add(cleanedBins[i])
            if (i < cleanedBins.size - 1) {
                val cur = cleanedBins[i]
                val nxt = cleanedBins[i + 1]
                val gap = nxt.rpm - cur.rpm
                if (gap in 150..250) {
                    // Fill single intermediate 100 RPM bin
                    val midRpm = (cur.rpm + nxt.rpm) / 2
                    val midPower = (cur.powerCv + nxt.powerCv) / 2.0
                    val midTorque = (midPower * TORQUE_CONSTANT) / midRpm
                    val midSpeed = (cur.speedKmh + nxt.speedKmh) / 2.0
                    val midTime = (cur.timeSeconds + nxt.timeSeconds) / 2.0
                    val midForce = (cur.forceN + nxt.forceN) / 2.0
                    val midAccel = (cur.accelMps2 + nxt.accelMps2) / 2.0

                    gapFilledBins.add(
                        DynoPoint(
                            rpm = midRpm,
                            powerCv = (midPower * 10).toInt() / 10.0,
                            torqueKgfm = (midTorque * 10).toInt() / 10.0,
                            speedKmh = (midSpeed * 10).toInt() / 10.0,
                            timeSeconds = (midTime * 10).toInt() / 10.0,
                            forceN = midForce,
                            accelMps2 = midAccel,
                            accelRawMps2 = midAccel,
                            accelFilteredMps2 = midAccel
                        )
                    )
                }
            }
        }

        // 4. Construct Monotone Cubic Spline (Fritsch-Carlson) curve points across the RPM span
        if (gapFilledBins.size < 3) return gapFilledBins

        val xArr = DoubleArray(gapFilledBins.size) { gapFilledBins[it].rpm.toDouble() }
        val yArrPower = DoubleArray(gapFilledBins.size) { gapFilledBins[it].powerCv }
        val yArrSpeed = DoubleArray(gapFilledBins.size) { gapFilledBins[it].speedKmh }
        val yArrTime = DoubleArray(gapFilledBins.size) { gapFilledBins[it].timeSeconds }
        val yArrAccel = DoubleArray(gapFilledBins.size) { gapFilledBins[it].accelFilteredMps2 }

        val splinePower = SplineSmoothing.interpolateMonotoneSpline(xArr, yArrPower)
        val splineSpeed = SplineSmoothing.interpolateMonotoneSpline(xArr, yArrSpeed)
        val splineTime = SplineSmoothing.interpolateMonotoneSpline(xArr, yArrTime)
        val splineAccel = SplineSmoothing.interpolateMonotoneSpline(xArr, yArrAccel)

        val finalProcessedCurve = mutableListOf<DynoPoint>()
        val startRpm = gapFilledBins.first().rpm
        val endRpm = gapFilledBins.last().rpm

        // Sample smooth curve every 50 RPM
        for (rpm in startRpm..endRpm step 50) {
            val r = rpm.toDouble()
            val powerCv = max(0.0, splinePower(r))
            // Strict physical formula: Torque_kgfm = (Power_CV * 716.2) / RPM
            val torqueKgfm = if (rpm >= 500) (powerCv * TORQUE_CONSTANT) / rpm else 0.0
            val speedKmh = splineSpeed(r)
            val timeSec = splineTime(r)
            val accelMps2 = splineAccel(r)

            val speedMs = max(0.1, speedKmh / 3.6)
            val fTotal = (powerCv * WATTS_TO_CV) / speedMs

            finalProcessedCurve.add(
                DynoPoint(
                    rpm = rpm,
                    powerCv = (powerCv * 10).toInt() / 10.0,
                    torqueKgfm = (torqueKgfm * 10).toInt() / 10.0,
                    speedKmh = (speedKmh * 10).toInt() / 10.0,
                    timeSeconds = (timeSec * 10).toInt() / 10.0,
                    forceN = (fTotal * 10).toInt() / 10.0,
                    accelMps2 = (accelMps2 * 100).toInt() / 100.0,
                    accelRawMps2 = (accelMps2 * 100).toInt() / 100.0,
                    accelFilteredMps2 = (accelMps2 * 100).toInt() / 100.0
                )
            )
        }

        return if (finalProcessedCurve.isNotEmpty()) finalProcessedCurve else gapFilledBins
    }

    private fun buildInvalidResult(
        spec: VehicleSpec,
        gearIndex: Int,
        totalCount: Int,
        rejectedCount: Int,
        rawPoints: List<DynoPoint> = emptyList(),
        rawSessionSamples: List<TelemetrySample> = emptyList(),
        reason: String
    ): DynoResult {
        return DynoResult(
            peakPowerCv = 0.0,
            peakPowerRpm = 0,
            peakTorqueKgfm = 0.0,
            peakTorqueRpm = 0,
            maxSpeedKmh = 0.0,
            selectedGear = gearIndex,
            vehicleSpec = spec,
            points = emptyList(),
            rawPoints = rawPoints,
            rawSessionSamples = rawSessionSamples,
            dataSource = TestDataSource.REAL_TEST_DATA,
            totalSampleCount = totalCount,
            rejectedSampleCount = rejectedCount,
            isValid = false,
            invalidReason = reason,
            passQuality = PassQuality.INVALIDA
        )
    }

    /**
     * Generates a realistic simulated Dyno pull curve for demo and preview mode.
     */
    fun generateSimulatedRun(spec: VehicleSpec, gearIndex: Int = 2): DynoResult {
        val points = mutableListOf<DynoPoint>()
        val startRpm = 1800
        val endRpm = 6200
        val stepRpm = 100

        val totalRatio = spec.totalRatio(gearIndex)
        val radiusM = spec.tireRadiusMeters

        val targetMaxTorqueKgfm = 19.2
        val peakTorqueRpm = 2800.0

        var timeSec = 0.0

        for (rpm in startRpm..endRpm step stepRpm) {
            val r = rpm.toDouble()

            val torqueFactor = when {
                r < peakTorqueRpm -> 0.70 + 0.30 * ((r - startRpm) / (peakTorqueRpm - startRpm)).pow(0.8)
                r <= 4200.0 -> 1.0 - 0.05 * ((r - peakTorqueRpm) / (4200.0 - peakTorqueRpm))
                else -> 0.95 - 0.35 * ((r - 4200.0) / (endRpm - 4200.0)).pow(1.2)
            }

            val torqueKgfm = targetMaxTorqueKgfm * torqueFactor
            // Strict physical formula: Power_CV = (Torque_kgfm * RPM) / 716.2
            val powerCv = (torqueKgfm * r) / TORQUE_CONSTANT

            val speedMs = (r / 60.0) * 2.0 * PI * radiusM / totalRatio
            val speedKmh = speedMs * 3.6

            timeSec += 0.055

            val totalMassKg = spec.weightKg + 100.0
            val fTotal = (powerCv * WATTS_TO_CV) / speedMs
            val accelMps2 = fTotal / totalMassKg

            points.add(
                DynoPoint(
                    rpm = rpm,
                    powerCv = (powerCv * 10).toInt() / 10.0,
                    torqueKgfm = (torqueKgfm * 10).toInt() / 10.0,
                    speedKmh = (speedKmh * 10).toInt() / 10.0,
                    timeSeconds = (timeSec * 10).toInt() / 10.0,
                    forceN = (fTotal * 10).toInt() / 10.0,
                    accelMps2 = (accelMps2 * 100).toInt() / 100.0,
                    accelRawMps2 = (accelMps2 * 100).toInt() / 100.0,
                    accelFilteredMps2 = (accelMps2 * 100).toInt() / 100.0
                )
            )
        }

        val peakCvPoint = points.maxByOrNull { it.powerCv } ?: points.first()
        val peakKgfmPoint = points.maxByOrNull { it.torqueKgfm } ?: points.first()
        val maxSpeed = points.maxOfOrNull { it.speedKmh } ?: 0.0

        return DynoResult(
            peakPowerCv = (peakCvPoint.powerCv * 10).toInt() / 10.0,
            peakPowerRpm = peakCvPoint.rpm,
            peakTorqueKgfm = (peakKgfmPoint.torqueKgfm * 10).toInt() / 10.0,
            peakTorqueRpm = peakKgfmPoint.rpm,
            maxSpeedKmh = (maxSpeed * 10).toInt() / 10.0,
            selectedGear = gearIndex,
            vehicleSpec = spec,
            points = points,
            rawPoints = points,
            dataSource = TestDataSource.DEMO_DATA,
            initialSpeedKmh = points.first().speedKmh,
            finalSpeedKmh = points.last().speedKmh,
            initialRpm = points.first().rpm,
            finalRpm = points.last().rpm,
            maxAccelMps2 = points.maxOfOrNull { it.accelMps2 } ?: 0.0,
            maxForceN = points.maxOfOrNull { it.forceN } ?: 0.0,
            totalSampleCount = points.size,
            rejectedSampleCount = 0,
            avgSensorFrequencyHz = if (timeSec > 0) points.size / timeSec else 10.0,
            isValid = true,
            invalidReason = "",
            passQuality = PassQuality.EXCELENTE
        )
    }
}
