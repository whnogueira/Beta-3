package com.example.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.example.model.DynoPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Robust signal processing, outlier rejection, and Monotone Cubic Spline (Fritsch-Carlson)
 * interpolation for automotive dynamometer telemetry.
 *
 * Guaranteed properties:
 * - NO mathematical overshoot (never invents peaks that did not exist in valid data).
 * - Preservation of physical monotonicity in acceleration intervals.
 * - Complete rejection of single-sample vibration/sensor spikes.
 */
object SplineSmoothing {

    /**
     * Rejects single spikes and outliers from a series using Median Absolute Deviation (MAD).
     * Returns a list containing only statistically representative samples.
     */
    fun rejectOutliersMad(values: List<Double>, maxZScore: Double = 2.5): List<Double> {
        if (values.size < 3) return values

        val median = computeMedian(values)
        val deviations = values.map { abs(it - median) }
        val mad = computeMedian(deviations)

        // If data is virtually constant, return as is
        if (mad < 1e-4) return values

        // Standard consistency factor for normal distribution: 1.4826
        val robustSigma = 1.4826 * mad
        val maxAllowedDiff = max(2.5, maxZScore * robustSigma)

        val filtered = values.filter { abs(it - median) <= maxAllowedDiff }
        return if (filtered.isNotEmpty()) filtered else values
    }

    /**
     * Computes the exact median of a numeric list.
     */
    fun computeMedian(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        }
    }

    /**
     * Computes a 15% trimmed mean of values for robust estimation against boundary noise.
     */
    fun computeTrimmedMean(values: List<Double>, trimRatio: Double = 0.15): Double {
        if (values.isEmpty()) return 0.0
        if (values.size <= 4) return computeMedian(values)

        val sorted = values.sorted()
        val trimCount = (sorted.size * trimRatio).toInt().coerceAtMost((sorted.size - 1) / 2)
        val trimmed = sorted.subList(trimCount, sorted.size - trimCount)
        return trimmed.average()
    }

    /**
     * Monotone Cubic Hermite Interpolation (Fritsch-Carlson algorithm).
     * Produces a smooth C1 interpolant without Runge's phenomenon or artificial overshoots.
     */
    fun interpolateMonotoneSpline(
        x: DoubleArray,
        y: DoubleArray
    ): (Double) -> Double {
        val n = x.size
        if (n == 0) return { 0.0 }
        if (n == 1) return { y[0] }
        if (n == 2) {
            val slope = (y[1] - y[0]) / (x[1] - x[0])
            return { targetX -> y[0] + slope * (targetX - x[0]) }
        }

        // 1. Calculate secants (slopes of segments)
        val delta = DoubleArray(n - 1)
        val h = DoubleArray(n - 1)
        for (i in 0 until n - 1) {
            h[i] = x[i + 1] - x[i]
            if (h[i] <= 0.0) h[i] = 1.0 // Guard against duplicate X
            delta[i] = (y[i + 1] - y[i]) / h[i]
        }

        // 2. Initialize tangents (derivatives at points)
        val d = DoubleArray(n)
        d[0] = delta[0]
        d[n - 1] = delta[n - 2]
        for (i in 1 until n - 1) {
            if (delta[i - 1] * delta[i] <= 0.0) {
                d[i] = 0.0 // Local extremum: zero derivative to preserve monotonicity
            } else {
                // Harmonic mean of slopes ensures shape preservation
                d[i] = (2.0 * delta[i - 1] * delta[i]) / (delta[i - 1] + delta[i])
            }
        }

        // 3. Fritsch-Carlson monotonicity condition
        for (i in 0 until n - 1) {
            if (abs(delta[i]) < 1e-9) {
                d[i] = 0.0
                d[i + 1] = 0.0
            } else {
                val alpha = d[i] / delta[i]
                val beta = d[i + 1] / delta[i]
                val dist = alpha * alpha + beta * beta
                if (dist > 9.0) {
                    val tau = 3.0 / sqrt(dist)
                    d[i] = tau * alpha * delta[i]
                    d[i + 1] = tau * beta * delta[i]
                }
            }
        }

        // 4. Return evaluator function for any X
        return { targetX ->
            when {
                targetX <= x[0] -> y[0]
                targetX >= x[n - 1] -> y[n - 1]
                else -> {
                    var idx = n - 2
                    for (i in 0 until n - 1) {
                        if (targetX >= x[i] && targetX <= x[i + 1]) {
                            idx = i
                            break
                        }
                    }
                    val segH = h[idx]
                    val t = (targetX - x[idx]) / segH
                    val t2 = t * t
                    val t3 = t2 * t

                    // Hermite basis functions
                    val h00 = 2.0 * t3 - 3.0 * t2 + 1.0
                    val h10 = t3 - 2.0 * t2 + t
                    val h01 = -2.0 * t3 + 3.0 * t2
                    val h11 = t3 - t2

                    h00 * y[idx] + h10 * segH * d[idx] + h01 * y[idx + 1] + h11 * segH * d[idx + 1]
                }
            }
        }
    }

    /**
     * Constructs a smooth Monotone Cubic Spline across pixel coordinates on Canvas.
     * Prevents overshoots and sharp corner artifacts, mapping directly into Bézier cubic segments.
     */
    fun buildSmoothSplinePath(
        offsets: List<Offset>,
        strokePath: Path,
        fillPath: Path? = null,
        baselineY: Float = 0f
    ) {
        if (offsets.isEmpty()) return

        if (offsets.size == 1) {
            strokePath.moveTo(offsets[0].x, offsets[0].y)
            fillPath?.moveTo(offsets[0].x, baselineY)
            fillPath?.lineTo(offsets[0].x, offsets[0].y)
            return
        }

        if (offsets.size == 2) {
            strokePath.moveTo(offsets[0].x, offsets[0].y)
            strokePath.lineTo(offsets[1].x, offsets[1].y)
            if (fillPath != null) {
                fillPath.moveTo(offsets[0].x, baselineY)
                fillPath.lineTo(offsets[0].x, offsets[0].y)
                fillPath.lineTo(offsets[1].x, offsets[1].y)
                fillPath.lineTo(offsets[1].x, baselineY)
                fillPath.close()
            }
            return
        }

        strokePath.moveTo(offsets[0].x, offsets[0].y)
        fillPath?.moveTo(offsets[0].x, baselineY)
        fillPath?.lineTo(offsets[0].x, offsets[0].y)

        val n = offsets.size
        val x = DoubleArray(n) { offsets[it].x.toDouble() }
        val y = DoubleArray(n) { offsets[it].y.toDouble() }

        // Fritsch-Carlson derivatives
        val delta = DoubleArray(n - 1)
        val h = DoubleArray(n - 1)
        for (i in 0 until n - 1) {
            h[i] = max(1.0, x[i + 1] - x[i])
            delta[i] = (y[i + 1] - y[i]) / h[i]
        }

        val d = DoubleArray(n)
        d[0] = delta[0]
        d[n - 1] = delta[n - 2]
        for (i in 1 until n - 1) {
            if (delta[i - 1] * delta[i] <= 0.0) {
                d[i] = 0.0
            } else {
                d[i] = (2.0 * delta[i - 1] * delta[i]) / (delta[i - 1] + delta[i])
            }
        }

        for (i in 0 until n - 1) {
            if (abs(delta[i]) < 1e-9) {
                d[i] = 0.0
                d[i + 1] = 0.0
            } else {
                val alpha = d[i] / delta[i]
                val beta = d[i + 1] / delta[i]
                val dist = alpha * alpha + beta * beta
                if (dist > 9.0) {
                    val tau = 3.0 / sqrt(dist)
                    d[i] = tau * alpha * delta[i]
                    d[i + 1] = tau * beta * delta[i]
                }
            }
        }

        // Convert Hermite derivatives into Cubic Bézier control points
        for (i in 0 until n - 1) {
            val segH = h[i]
            val cp1x = (x[i] + segH / 3.0).toFloat()
            val cp1y = (y[i] + (segH * d[i]) / 3.0).toFloat()

            val cp2x = (x[i + 1] - segH / 3.0).toFloat()
            val cp2y = (y[i + 1] - (segH * d[i + 1]) / 3.0).toFloat()

            val endX = offsets[i + 1].x
            val endY = offsets[i + 1].y

            strokePath.cubicTo(cp1x, cp1y, cp2x, cp2y, endX, endY)
            fillPath?.cubicTo(cp1x, cp1y, cp2x, cp2y, endX, endY)
        }

        if (fillPath != null && offsets.isNotEmpty()) {
            fillPath.lineTo(offsets.last().x, baselineY)
            fillPath.close()
        }
    }
}
