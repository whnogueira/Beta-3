package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.SplineSmoothing
import com.example.model.DynoPoint
import com.example.model.DynoResult
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurfaceVariant
import com.example.ui.theme.ChartBackground
import com.example.ui.theme.ChartGridLine
import com.example.ui.theme.ChartSubGridLine
import com.example.ui.theme.DynoAmber
import com.example.ui.theme.DynoCyan
import com.example.ui.theme.DynoYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.max

enum class ChartMode {
    POWER_AND_TORQUE,
    POWER_ONLY,
    TORQUE_ONLY,
    SPEED_TIME
}

@Composable
fun DynoChartCanvas(
    result: DynoResult,
    mode: ChartMode = ChartMode.POWER_AND_TORQUE,
    modifier: Modifier = Modifier,
    liveCursorRpm: Int? = null
) {
    val points = result.points
    if (points.isEmpty()) return

    var selectedPoint by remember(result, mode) { mutableStateOf<DynoPoint?>(null) }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    val cyanStyle = remember {
        androidx.compose.ui.text.TextStyle(
            color = DynoCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
    val amberStyle = remember {
        androidx.compose.ui.text.TextStyle(
            color = DynoAmber,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
    val axisLabelStyle = remember {
        androidx.compose.ui.text.TextStyle(
            color = TextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    Column(modifier = modifier) {
        // Telemetry Key Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mode == ChartMode.POWER_AND_TORQUE || mode == ChartMode.POWER_ONLY) {
                LegendIndicator(color = DynoCyan, label = "POTÊNCIA (CV)", dashed = false)
                Spacer(modifier = Modifier.width(16.dp))
            }
            if (mode == ChartMode.POWER_AND_TORQUE || mode == ChartMode.TORQUE_ONLY) {
                LegendIndicator(color = DynoAmber, label = "TORQUE (kgfm)", dashed = false)
                Spacer(modifier = Modifier.width(16.dp))
            }
            if (mode == ChartMode.SPEED_TIME) {
                LegendIndicator(color = DynoYellow, label = "VELOCIDADE (km/h)", dashed = false)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (selectedPoint != null) {
                val p = selectedPoint!!
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CarbonSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (mode == ChartMode.SPEED_TIME) "%.2fs".format(p.timeSeconds) else "${p.rpm} RPM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (mode == ChartMode.POWER_AND_TORQUE || mode == ChartMode.POWER_ONLY) {
                        Text(
                            text = "%.1f CV".format(p.powerCv),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DynoCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (mode == ChartMode.POWER_AND_TORQUE || mode == ChartMode.TORQUE_ONLY) {
                        Text(
                            text = "%.1f kgfm".format(p.torqueKgfm),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DynoAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "%.0f km/h".format(p.speedKmh),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = DynoYellow
                    )
                }
            } else {
                Text(
                    text = if (mode == ChartMode.SPEED_TIME) "EIXO X: TEMPO (s)" else "EIXO X: RPM",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }
        }

        // Dyno Canvas Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(ChartBackground)
                .border(1.dp, CarbonBorder, RoundedCornerShape(4.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(result, mode) {
                        detectTapGestures { tapOffset ->
                            updateSelectedPoint(tapOffset.x, size.width.toFloat(), points, mode) { selectedPoint = it }
                        }
                    }
                    .pointerInput(result, mode) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            updateSelectedPoint(change.position.x, size.width.toFloat(), points, mode) { selectedPoint = it }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height

                val paddingLeft = 44.dp.toPx()
                val paddingRight = 44.dp.toPx()
                val paddingTop = 18.dp.toPx()
                val paddingBottom = 26.dp.toPx()

                val chartWidth = width - paddingLeft - paddingRight
                val chartHeight = height - paddingTop - paddingBottom

                if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

                // Range Calculations
                val minRpm = 1000.0
                val maxRpm = max(7000.0, ((points.maxOfOrNull { it.rpm } ?: 6000) / 1000 + 1) * 1000.0)

                val minX = if (mode == ChartMode.SPEED_TIME) points.first().timeSeconds else minRpm
                val maxX = if (mode == ChartMode.SPEED_TIME) max(5.0, points.last().timeSeconds * 1.1) else maxRpm

                val maxCv = max(50.0, (points.maxOfOrNull { it.powerCv } ?: 100.0) * 1.15)
                val maxKgfm = max(10.0, (points.maxOfOrNull { it.torqueKgfm } ?: 20.0) * 1.15)
                val maxSpeed = max(60.0, (points.maxOfOrNull { it.speedKmh } ?: 100.0) * 1.15)

                // Draw Horizontal Gridlines & Y-Axis Labels
                val gridRows = 5
                val gridEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

                for (i in 0..gridRows) {
                    val y = paddingTop + (chartHeight * i / gridRows)
                    val ratio = 1f - (i.toFloat() / gridRows)

                    // Grid line
                    drawLine(
                        color = if (i == gridRows) CarbonBorder else ChartGridLine,
                        start = Offset(paddingLeft, y),
                        end = Offset(width - paddingRight, y),
                        pathEffect = if (i == gridRows) null else gridEffect,
                        strokeWidth = if (i == gridRows) 1.5f else 1f
                    )

                    // Left Y-Axis Label (Power CV)
                    val leftLabel = when (mode) {
                        ChartMode.POWER_AND_TORQUE, ChartMode.POWER_ONLY -> "${(maxCv * ratio).toInt()}"
                        ChartMode.TORQUE_ONLY -> "%.1f".format(maxKgfm * ratio)
                        ChartMode.SPEED_TIME -> "${(maxSpeed * ratio).toInt()}"
                    }

                    val leftStyle = if (mode == ChartMode.TORQUE_ONLY) amberStyle else cyanStyle
                    val leftLayout = textMeasurer.measure(leftLabel, style = leftStyle)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = leftLabel,
                        style = leftStyle,
                        topLeft = Offset(paddingLeft - leftLayout.size.width - 6.dp.toPx(), y - leftLayout.size.height / 2f)
                    )

                    // Right Y-Axis Label (Torque kgfm) when dual-axis
                    if (mode == ChartMode.POWER_AND_TORQUE) {
                        val rightLabel = "%.1f".format(maxKgfm * ratio)
                        val rightLayout = textMeasurer.measure(rightLabel, style = amberStyle)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = rightLabel,
                            style = amberStyle,
                            topLeft = Offset(width - paddingRight + 6.dp.toPx(), y - rightLayout.size.height / 2f)
                        )
                    }
                }

                // Draw Vertical Gridlines & X-Axis Labels (RPM or Time)
                val gridCols = 6
                for (i in 0..gridCols) {
                    val x = paddingLeft + (chartWidth * i / gridCols)

                    drawLine(
                        color = ChartGridLine,
                        start = Offset(x, paddingTop),
                        end = Offset(x, height - paddingBottom),
                        pathEffect = gridEffect,
                        strokeWidth = 1f
                    )

                    val xVal = minX + (maxX - minX) * (i.toDouble() / gridCols)
                    val labelX = if (mode == ChartMode.SPEED_TIME) "%.1fs".format(xVal) else "${(xVal / 1000).toInt()}k"
                    val xLayout = textMeasurer.measure(labelX, style = axisLabelStyle)

                    drawText(
                        textMeasurer = textMeasurer,
                        text = labelX,
                        style = axisLabelStyle,
                        topLeft = Offset(x - xLayout.size.width / 2f, height - paddingBottom + 4.dp.toPx())
                    )
                }

                // Coordinate Mapping
                fun getX(p: DynoPoint): Float {
                    val v = if (mode == ChartMode.SPEED_TIME) p.timeSeconds else p.rpm.toDouble()
                    val ratio = if (maxX > minX) (v - minX) / (maxX - minX) else 0.0
                    return (paddingLeft + chartWidth * ratio.coerceIn(0.0, 1.0)).toFloat()
                }

                fun getY(valDouble: Double, maxValDouble: Double): Float {
                    val ratio = (valDouble / maxValDouble).coerceIn(0.0, 1.0)
                    return (height - paddingBottom - chartHeight * ratio).toFloat()
                }

                // Draw Power Curve (Cyan)
                if (mode == ChartMode.POWER_AND_TORQUE || mode == ChartMode.POWER_ONLY) {
                    val powerOffsets = points.map { p ->
                        Offset(getX(p), getY(p.powerCv, maxCv))
                    }

                    val powerPath = Path()
                    val fillPath = Path()

                    SplineSmoothing.buildSmoothSplinePath(
                        offsets = powerOffsets,
                        strokePath = powerPath,
                        fillPath = fillPath,
                        baselineY = height - paddingBottom
                    )

                    if (powerOffsets.isNotEmpty()) {
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(DynoCyan.copy(alpha = 0.18f), Color.Transparent),
                                startY = paddingTop,
                                endY = height - paddingBottom
                            )
                        )
                    }

                    drawPath(
                        path = powerPath,
                        color = DynoCyan,
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Peak Power Indicator
                    val peakPowerPoint = points.maxByOrNull { it.powerCv }
                    peakPowerPoint?.let { p: DynoPoint ->
                        val px = getX(p)
                        val py = getY(p.powerCv, maxCv)
                        drawCircle(color = DynoCyan, radius = 4.dp.toPx(), center = Offset(px, py))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(px, py))

                        // Peak Tag
                        val peakCvText = "PEAK %.1f CV".format(p.powerCv)
                        val peakCvLayout = textMeasurer.measure(peakCvText, style = cyanStyle)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = peakCvText,
                            style = cyanStyle,
                            topLeft = Offset(px - peakCvLayout.size.width / 2f, py - peakCvLayout.size.height - 4.dp.toPx())
                        )
                    }
                }

                // Draw Torque Curve (Amber)
                if (mode == ChartMode.POWER_AND_TORQUE || mode == ChartMode.TORQUE_ONLY) {
                    val torqueOffsets = points.map { p ->
                        Offset(getX(p), getY(p.torqueKgfm, maxKgfm))
                    }

                    val torquePath = Path()
                    val fillPath = Path()

                    SplineSmoothing.buildSmoothSplinePath(
                        offsets = torqueOffsets,
                        strokePath = torquePath,
                        fillPath = fillPath,
                        baselineY = height - paddingBottom
                    )

                    if (torqueOffsets.isNotEmpty() && mode == ChartMode.TORQUE_ONLY) {
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(DynoAmber.copy(alpha = 0.18f), Color.Transparent),
                                startY = paddingTop,
                                endY = height - paddingBottom
                            )
                        )
                    }

                    drawPath(
                        path = torquePath,
                        color = DynoAmber,
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Peak Torque Indicator
                    val peakTorquePoint = points.maxByOrNull { it.torqueKgfm }
                    peakTorquePoint?.let { p: DynoPoint ->
                        val px = getX(p)
                        val py = getY(p.torqueKgfm, maxKgfm)
                        drawCircle(color = DynoAmber, radius = 4.dp.toPx(), center = Offset(px, py))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(px, py))

                        // Peak Tag
                        val peakTorqueText = "PEAK %.1f kgfm".format(p.torqueKgfm)
                        val peakTorqueLayout = textMeasurer.measure(peakTorqueText, style = amberStyle)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = peakTorqueText,
                            style = amberStyle,
                            topLeft = Offset(px - peakTorqueLayout.size.width / 2f, py - peakTorqueLayout.size.height - 4.dp.toPx())
                        )
                    }
                }

                // Draw Speed vs Time (Yellow)
                if (mode == ChartMode.SPEED_TIME) {
                    val speedOffsets = points.map { p ->
                        Offset(getX(p), getY(p.speedKmh, maxSpeed))
                    }

                    val speedPath = Path()
                    val fillPath = Path()

                    SplineSmoothing.buildSmoothSplinePath(
                        offsets = speedOffsets,
                        strokePath = speedPath,
                        fillPath = fillPath,
                        baselineY = height - paddingBottom
                    )

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(DynoYellow.copy(alpha = 0.18f), Color.Transparent),
                            startY = paddingTop,
                            endY = height - paddingBottom
                        )
                    )

                    drawPath(
                        path = speedPath,
                        color = DynoYellow,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                // Draw Live Cursor or Touch Cursor
                val activeCursorPoint = selectedPoint ?: if (liveCursorRpm != null && points.isNotEmpty()) {
                    points.minByOrNull { kotlin.math.abs(it.rpm - liveCursorRpm) }
                } else null

                activeCursorPoint?.let { p ->
                    val px = getX(p)
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(px, paddingTop),
                        end = Offset(px, height - paddingBottom),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                }
            }
        }
    }
}

private fun updateSelectedPoint(
    touchX: Float,
    totalWidth: Float,
    points: List<DynoPoint>,
    mode: ChartMode,
    onSelect: (DynoPoint?) -> Unit
) {
    if (points.isEmpty()) return
    val paddingLeft = 44f
    val paddingRight = 44f
    val chartWidth = totalWidth - paddingLeft - paddingRight
    val clampedX = (touchX - paddingLeft).coerceIn(0f, chartWidth)
    val ratio = clampedX / chartWidth
    val index = (ratio * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
    onSelect(points[index])
}

@Composable
private fun LegendIndicator(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}
