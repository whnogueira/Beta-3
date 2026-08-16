package com.example.ui.screens

import android.util.Log
import android.view.Surface
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensor.DynoSensorManager
import com.example.sensor.OrientationState
import com.example.ui.components.StandardMountOrientationVisual
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceVariant
import com.example.ui.theme.DynoAmber
import com.example.ui.theme.DynoCyan
import com.example.ui.theme.DynoGreen
import com.example.ui.theme.DynoRed
import com.example.ui.theme.DynoYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * OrientationCalibrationScreen
 *
 * ÚNICA FONTE DE VERDADE: OrientationState (WAITING, READY, CALIBRATING, CALIBRATED)
 * Toda a interface depende SOMENTE desse estado.
 */
@Composable
fun OrientationCalibrationScreen(
    sensorManager: DynoSensorManager,
    onCalibrationSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val isCalibrating by sensorManager.isCalibrating.collectAsState()
    val progress by sensorManager.calibrationProgress.collectAsState()
    val calibratedOrientation by sensorManager.calibratedOrientation.collectAsState()
    val calibrationError by sensorManager.calibrationError.collectAsState()
    val liveCheck by sensorManager.liveOrientation.collectAsState()
    val managerOrientationState by sensorManager.orientationState.collectAsState()
    val healthState by sensorManager.healthState.collectAsState()
    val calibrationElapsed by sensorManager.calibrationElapsedSec.collectAsState()
    val sampleCount by sensorManager.calibrationSampleCount.collectAsState()

    val isCalibrated = calibratedOrientation != null && calibratedOrientation?.isCalibrated == true

    // Single source of truth evaluation: READY, CALIBRATING, CALIBRATED, FAILED, WAITING
    val orientationState: OrientationState = when {
        isCalibrating -> OrientationState.CALIBRATING
        managerOrientationState == OrientationState.FAILED -> OrientationState.FAILED
        isCalibrated -> OrientationState.CALIBRATED
        managerOrientationState == OrientationState.CALIBRATING -> OrientationState.CALIBRATING
        managerOrientationState == OrientationState.CALIBRATED -> OrientationState.CALIBRATED
        managerOrientationState == OrientationState.WAITING -> OrientationState.WAITING
        else -> OrientationState.READY
    }

    DisposableEffect(Unit) {
        sensorManager.startOrientationMonitoring()
        onDispose {
            sensorManager.stopOrientationMonitoring()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(14.dp)
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp).testTag("calib_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ORIENTAÇÃO E CALIBRAÇÃO",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Detecção de eixo longitudinal e compensação de inclinação",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // STANDARD MOUNT ORIENTATION VISUAL (↑ FRENTE DO CARRO)
            StandardMountOrientationVisual(
                orientationState = orientationState,
                liveCheck = liveCheck,
                isCalibrated = isCalibrated
            )

            // INSTRUCTIONS / ORIENTATION RULES CARD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonSurface)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "PADRÃO DE MONTAGEM DO SMARTPHONE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = DynoCyan,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Celular horizontal no suporte do veículo.\n" +
                        "• Tela voltada para o motorista.\n" +
                        "• Costas do smartphone voltadas para a frente do carro.\n" +
                        "• O smartphone deve permanecer estável no suporte durante o teste.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = TextPrimary
                )
            }

            // CALIBRATION STATUS DEPENDING ONLY ON OrientationState
            when (orientationState) {
                OrientationState.FAILED -> {
                    // FAILED: vermelho, "Falha na calibração", botão TENTAR NOVAMENTE
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonSurface)
                            .border(1.dp, DynoRed, RoundedCornerShape(6.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DynoRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "FALHA NA CALIBRAÇÃO",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = DynoRed
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = calibrationError ?: "Não foi possível obter dados suficientes do sensor.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                Log.d("DynoCalibration", "CALIBRATE_BUTTON_CLICKED")
                                Log.d("DynoCalibration", "CALIBRATION_STARTED")
                                sensorManager.startCalibration(
                                    displayRotation = Surface.ROTATION_90
                                ) { _, _ -> }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("tentar_novamente_calib_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TENTAR NOVAMENTE",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                OrientationState.WAITING -> {
                    // WAITING: vermelho, "Ajuste a posição do celular", botão desabilitado
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonSurface)
                            .border(1.dp, DynoRed, RoundedCornerShape(6.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DynoRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AGUARDANDO POSIÇÃO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = DynoRed
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ajuste a posição do celular no suporte.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { },
                            enabled = false,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DynoRed,
                                disabledContainerColor = CarbonSurfaceVariant
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("calib_aguardando_button")
                        ) {
                            Text(
                                text = "AJUSTE O CELULAR PARA CALIBRAR",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = TextMuted
                            )
                        }
                    }
                }

                OrientationState.READY -> {
                    // READY: verde/ciano, "ORIENTAÇÃO OK", botão CALIBRAR AGORA habilitado
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonSurface)
                            .border(1.dp, DynoCyan, RoundedCornerShape(6.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = DynoCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ORIENTAÇÃO OK",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = DynoCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Posição correta — pronto para calibrar.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                Log.d("DynoCalibration", "CALIBRATE_BUTTON_CLICKED")
                                Log.d("DynoCalibration", "CALIBRATION_STARTED")
                                sensorManager.startCalibration(
                                    displayRotation = Surface.ROTATION_90
                                ) { success, _ ->
                                    if (success) {
                                        onCalibrationSuccess()
                                    }
                                }
                            },
                            enabled = true,
                            colors = ButtonDefaults.buttonColors(containerColor = DynoCyan),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("calibrar_agora_button")
                        ) {
                            Icon(imageVector = Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CALIBRAR AGORA",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }

                OrientationState.CALIBRATING -> {
                    // CALIBRATING: mostrar progresso 0%, 25%, 50%, 75%, 100% (botão CALIBRAR AGORA oculto)
                    val percentInt = (progress * 100).toInt().coerceIn(0, 100)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonSurface)
                            .border(1.dp, DynoCyan, RoundedCornerShape(6.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = DynoCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "CALIBRANDO SENSORES...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = DynoCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mantenha o veículo e o smartphone parados.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = DynoYellow,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = DynoCyan,
                            trackColor = CarbonDark,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progresso:",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                            Text(
                                text = "$percentInt%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = DynoCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Acelerômetro • Gravidade • Aceleração Linear • Giroscópio",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                OrientationState.CALIBRATED -> {
                    // CALIBRATED: mostrar calibração concluída e botão INICIAR PASSADA
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonSurface)
                            .border(1.dp, DynoGreen, RoundedCornerShape(6.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = DynoGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CALIBRAÇÃO CONCLUÍDA",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = DynoGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Posição OK — Pronto para medir.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        CalibrationCheckItem(label = "✓ CALIBRAÇÃO CONCLUÍDA", value = "OK")
                        CalibrationCheckItem(label = "✓ OFFSET DOS SENSORES DEFINIDO", value = "OK")
                        CalibrationCheckItem(label = "✓ GRAVIDADE COMPENSADA", value = "OK")
                        calibratedOrientation?.let { cal ->
                            CalibrationCheckItem(label = "Inclinação do suporte:", value = "%.1f°".format(cal.pitchAngleDeg))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onCalibrationSuccess,
                            colors = ButtonDefaults.buttonColors(containerColor = DynoGreen),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("iniciar_passada_calibrada_button")
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "INICIAR PASSADA",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = {
                                Log.d("DynoCalibration", "CALIBRATE_BUTTON_CLICKED")
                                Log.d("DynoCalibration", "CALIBRATION_STARTED")
                                sensorManager.startCalibration(
                                    displayRotation = Surface.ROTATION_90
                                ) { _, _ -> }
                            },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("recalibrar_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RECALIBRAR ZERO",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // TECHNICAL DIAGNOSTIC / DEBUG INFORMATION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonSurfaceVariant)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "DIAGNÓSTICO TÉCNICO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                DiagnosticRow(
                    label = "Calibration state:",
                    value = orientationState.name,
                    valueColor = when (orientationState) {
                        OrientationState.CALIBRATED -> DynoGreen
                        OrientationState.READY -> DynoCyan
                        OrientationState.CALIBRATING -> DynoYellow
                        OrientationState.FAILED, OrientationState.WAITING -> DynoRed
                    }
                )

                DiagnosticRow(
                    label = "Accelerometer samples:",
                    value = "$sampleCount",
                    valueColor = if (sampleCount >= 5) DynoGreen else TextPrimary
                )

                DiagnosticRow(
                    label = "Gyroscope:",
                    value = if (healthState.isGyroscopeAvailable) "AVAILABLE" else "NOT AVAILABLE",
                    valueColor = if (healthState.isGyroscopeAvailable) DynoGreen else TextMuted
                )

                DiagnosticRow(
                    label = "Gravity sensor:",
                    value = if (healthState.isGravitySensorAvailable) "AVAILABLE" else "FALLBACK",
                    valueColor = if (healthState.isGravitySensorAvailable) DynoGreen else DynoAmber
                )

                DiagnosticRow(
                    label = "Linear acceleration:",
                    value = if (healthState.isLinearAccelAvailable) "AVAILABLE" else "FALLBACK",
                    valueColor = if (healthState.isLinearAccelAvailable) DynoGreen else DynoAmber
                )

                DiagnosticRow(
                    label = "Calibration elapsed:",
                    value = "%.1f s".format(calibrationElapsed),
                    valueColor = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary
        )
        Text(
            text = value,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun CalibrationCheckItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = DynoGreen
            )
        }
    }
}
