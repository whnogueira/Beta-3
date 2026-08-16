package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensor.OrientationLiveCheck
import com.example.sensor.OrientationState
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DynoCyan
import com.example.ui.theme.DynoGreen
import com.example.ui.theme.DynoRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

/**
 * StandardMountOrientationVisual
 *
 * Padrão físico único de montagem:
 * - Celular no suporte horizontal fixado no painel / para-brisa.
 * - Tela voltada para o motorista.
 * - Costas voltadas para a frente do veículo.
 * - Seta clara apontando para CIMA: "↑ FRENTE DO CARRO".
 * - Visual depende exclusivamente do estado: WAITING, READY, CALIBRATING, CALIBRATED.
 */
@Composable
fun StandardMountOrientationVisual(
    orientationState: OrientationState = OrientationState.READY,
    liveCheck: OrientationLiveCheck? = null,
    isCalibrated: Boolean = false,
    modifier: Modifier = Modifier
) {
    val state = when {
        isCalibrated -> OrientationState.CALIBRATED
        orientationState == OrientationState.CALIBRATED -> OrientationState.CALIBRATED
        orientationState == OrientationState.CALIBRATING -> OrientationState.CALIBRATING
        orientationState == OrientationState.WAITING -> OrientationState.WAITING
        else -> OrientationState.READY
    }

    val infiniteTransition = rememberInfiniteTransition(label = "upward_arrow_pulse")
    val arrowPulseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowPulseOffset"
    )

    val statusColor = when (state) {
        OrientationState.CALIBRATED -> DynoGreen
        OrientationState.READY -> DynoCyan
        OrientationState.CALIBRATING -> DynoCyan
        OrientationState.FAILED, OrientationState.WAITING -> DynoRed
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CarbonSurface)
            .border(1.dp, statusColor, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visual Diagram Canvas showing: Car Windshield, Dashboard, Horizontal Smartphone, UPWARD Vector
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CarbonDark),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw subtle technical grid
                val gridStep = 20.dp.toPx()
                var gx = 0f
                while (gx < w) {
                    drawLine(
                        color = Color(0x0EFFFFFF),
                        start = Offset(gx, 0f),
                        end = Offset(gx, h),
                        strokeWidth = 1f
                    )
                    gx += gridStep
                }
                var gy = 0f
                while (gy < h) {
                    drawLine(
                        color = Color(0x0EFFFFFF),
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 1f
                    )
                    gy += gridStep
                }

                // 1. Draw Dashboard / Windshield horizon base at bottom
                val dashY = h - 24.dp.toPx()
                val dashPath = Path().apply {
                    moveTo(0f, dashY + 14.dp.toPx())
                    lineTo(w * 0.2f, dashY + 6.dp.toPx())
                    lineTo(w * 0.8f, dashY + 6.dp.toPx())
                    lineTo(w, dashY + 14.dp.toPx())
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(dashPath, color = Color(0xFF161A22))
                drawLine(
                    color = Color(0xFF2B3340),
                    start = Offset(0f, dashY + 8.dp.toPx()),
                    end = Offset(w, dashY + 8.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                // 2. Draw Mount base attached to Dashboard
                val mountCenterX = w * 0.5f
                val mountBaseY = dashY + 6.dp.toPx()

                // Suction cup base
                drawArc(
                    color = Color(0xFF2F3746),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(mountCenterX - 24.dp.toPx(), mountBaseY - 10.dp.toPx()),
                    size = Size(48.dp.toPx(), 20.dp.toPx())
                )

                // Mount Arm (upward holding structure)
                val mountArmPath = Path().apply {
                    moveTo(mountCenterX - 8.dp.toPx(), mountBaseY - 4.dp.toPx())
                    lineTo(mountCenterX - 4.dp.toPx(), h * 0.65f)
                    lineTo(mountCenterX + 4.dp.toPx(), h * 0.65f)
                    lineTo(mountCenterX + 8.dp.toPx(), mountBaseY - 4.dp.toPx())
                    close()
                }
                drawPath(mountArmPath, color = Color(0xFF3F4B5E))

                // Ball Joint / Clamp bracket
                drawCircle(
                    color = Color(0xFF53627A),
                    radius = 7.dp.toPx(),
                    center = Offset(mountCenterX, h * 0.65f)
                )

                // 3. Draw Smartphone in Landscape / Horizontal position
                val phoneCenterX = mountCenterX
                val phoneCenterY = h * 0.62f

                val phoneW = 146.dp.toPx()
                val phoneH = 64.dp.toPx()
                val phoneLeft = phoneCenterX - phoneW / 2f
                val phoneTop = phoneCenterY - phoneH / 2f

                // Claws holding phone
                drawRoundRect(
                    color = Color(0xFF4B5568),
                    topLeft = Offset(phoneCenterX - 14.dp.toPx(), phoneTop - 5.dp.toPx()),
                    size = Size(28.dp.toPx(), 8.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF4B5568),
                    topLeft = Offset(phoneCenterX - 14.dp.toPx(), phoneTop + phoneH - 3.dp.toPx()),
                    size = Size(28.dp.toPx(), 8.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Smartphone outer body
                drawRoundRect(
                    color = Color(0xFF1B2028),
                    topLeft = Offset(phoneLeft, phoneTop),
                    size = Size(phoneW, phoneH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                // Smartphone highlight border
                drawRoundRect(
                    color = statusColor,
                    topLeft = Offset(phoneLeft, phoneTop),
                    size = Size(phoneW, phoneH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Screen Bezel (facing driver)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0D1117), Color(0xFF161B22))
                    ),
                    topLeft = Offset(phoneLeft + 6.dp.toPx(), phoneTop + 5.dp.toPx()),
                    size = Size(phoneW - 12.dp.toPx(), phoneH - 10.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Screen UI simulation
                val hudLeft = phoneLeft + 12.dp.toPx()
                val hudTop = phoneTop + 10.dp.toPx()
                drawCircle(
                    color = statusColor.copy(alpha = 0.4f),
                    radius = 12.dp.toPx(),
                    center = Offset(hudLeft + 16.dp.toPx(), phoneCenterY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawLine(
                    color = statusColor.copy(alpha = 0.8f),
                    start = Offset(hudLeft + 38.dp.toPx(), hudTop + 6.dp.toPx()),
                    end = Offset(hudLeft + 90.dp.toPx(), hudTop + 6.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = Color(0x66FFFFFF),
                    start = Offset(hudLeft + 38.dp.toPx(), hudTop + 15.dp.toPx()),
                    end = Offset(hudLeft + 80.dp.toPx(), hudTop + 15.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                // 4. Large UPWARD ARROW for vehicle forward motion (Traseira -> Frente)
                val arrowTipY = 14.dp.toPx() - arrowPulseOffset
                val arrowBaseY = phoneTop - 8.dp.toPx()
                val arrowX = mountCenterX

                val arrowHeadPath = Path().apply {
                    moveTo(arrowX, arrowTipY)
                    lineTo(arrowX - 9.dp.toPx(), arrowTipY + 12.dp.toPx())
                    lineTo(arrowX - 3.dp.toPx(), arrowTipY + 12.dp.toPx())
                    lineTo(arrowX - 3.dp.toPx(), arrowBaseY)
                    lineTo(arrowX + 3.dp.toPx(), arrowBaseY)
                    lineTo(arrowX + 3.dp.toPx(), arrowTipY + 12.dp.toPx())
                    lineTo(arrowX + 9.dp.toPx(), arrowTipY + 12.dp.toPx())
                    close()
                }
                drawPath(arrowHeadPath, color = statusColor.copy(alpha = 0.9f))
            }

            // Foreground Overlays
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Centered Vector Indicator: ↑ FRENTE DO CARRO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.2f))
                            .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↑ FRENTE DO CARRO",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            color = statusColor
                        )
                    }
                }

                // Bottom Labels: Screen to driver + Back to vehicle front
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xCC0B0E14))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TELA P/ MOTORISTA",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC9D1D9)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xCC0B0E14))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COSTAS DO CELULAR P/ FRENTE",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // DYNAMIC INSTRUCTION BANNER BASED ONLY ON OrientationState
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(statusColor.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (state) {
                    OrientationState.CALIBRATED -> Icons.Default.CheckCircle
                    OrientationState.READY -> Icons.Default.CheckCircle
                    OrientationState.CALIBRATING -> Icons.Default.Sensors
                    OrientationState.FAILED, OrientationState.WAITING -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = when (state) {
                        OrientationState.CALIBRATED -> "Posição OK — Pronto para medir."
                        OrientationState.READY -> "Posição correta — pronto para calibrar."
                        OrientationState.CALIBRATING -> "Calibrando zero & inclinação..."
                        OrientationState.FAILED -> "Falha na calibração. Tente novamente."
                        OrientationState.WAITING -> "Ajuste a posição do celular."
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = statusColor
                )
                if (state == OrientationState.CALIBRATED) {
                    Text(
                        text = "Vetor de aceleração calibrado e inclinação do suporte compensada.",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
