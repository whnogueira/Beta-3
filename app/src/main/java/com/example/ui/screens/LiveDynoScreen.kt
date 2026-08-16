package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.db.DynoRepository
import com.example.engine.DynoCalculator
import com.example.engine.SplineSmoothing
import com.example.model.DynoPoint
import com.example.model.DynoResult
import com.example.model.PassQuality
import com.example.model.PassState
import com.example.model.TestDataSource
import com.example.model.VehicleSpec
import com.example.sensor.DynoSensorManager
import com.example.ui.components.PreTestSensorStatusCard
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceVariant
import com.example.ui.theme.ChartBackground
import com.example.ui.theme.ChartGridLine
import com.example.ui.theme.ChartSubGridLine
import com.example.ui.theme.DynoAmber
import com.example.ui.theme.DynoCyan
import com.example.ui.theme.DynoGreen
import com.example.ui.theme.DynoRed
import com.example.ui.theme.DynoYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

@Composable
fun LiveDynoScreen(
    vehicleSpec: VehicleSpec,
    initialGearIndex: Int = 2,
    sensorManager: DynoSensorManager,
    repository: DynoRepository,
    onBack: () -> Unit,
    onRecalibrate: () -> Unit,
    onViewFullResult: (DynoResult) -> Unit,
    onViewDiagnostic: (DynoResult) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val isSensorRecording by sensorManager.isRecording.collectAsState()
    val passState by sensorManager.passState.collectAsState()
    val validSensorPoints by sensorManager.validDynoPoints.collectAsState()
    val sensorPowerCv by sensorManager.currentPowerCv.collectAsState()
    val sensorTorqueKgfm by sensorManager.currentTorqueKgfm.collectAsState()
    val sensorRpm by sensorManager.currentRpm.collectAsState()
    val currentSpeedKmh by sensorManager.currentSpeedKmh.collectAsState()
    val currentAccelMps2 by sensorManager.currentAccelMps2.collectAsState()
    val elapsedSec by sensorManager.elapsedTimeSec.collectAsState()
    val healthState by sensorManager.healthState.collectAsState()
    val excessiveMovement by sensorManager.excessiveMovementDetected.collectAsState()
    val movementInvalidReason by sensorManager.movementInvalidReason.collectAsState()

    var showPreTestDialog by remember { mutableStateOf(false) }
    var selectedGearIndex by remember { mutableIntStateOf(initialGearIndex) }
    val gearLabels = listOf("1ª", "2ª", "3ª", "4ª", "5ª")

    var isSimulating by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }

    val livePoints = remember { mutableStateListOf<DynoPoint>() }
    var finalResult by remember { mutableStateOf<DynoResult?>(null) }
    var userInspectingPoint by remember { mutableStateOf<DynoPoint?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )

    fun finishTestPass(result: DynoResult) {
        finalResult = result
        isFinished = true
        isSimulating = false

        if (result.isValid) {
            coroutineScope.launch {
                repository.saveRun(result)
            }
        }
    }

    // Handle automated completion when PassState becomes CONCLUIDA
    LaunchedEffect(passState, isSensorRecording) {
        if (isSensorRecording && passState == PassState.CONCLUIDA && !isFinished) {
            val samples = sensorManager.stopRecording()
            val calculated = DynoCalculator.calculate(samples, vehicleSpec, selectedGearIndex)
            finishTestPass(calculated)
        }
    }

    // If excessive phone movement occurs during pull, automatically stop and invalidate
    LaunchedEffect(excessiveMovement) {
        if (excessiveMovement && isSensorRecording) {
            val samples = sensorManager.stopRecording()
            val invalidRes = DynoCalculator.calculate(samples, vehicleSpec, selectedGearIndex).copy(
                isValid = false,
                invalidReason = "Movimento excessivo do smartphone detectado. Verifique o suporte, reposicione o aparelho e calibre novamente.",
                passQuality = PassQuality.INVALIDA
            )
            finalResult = invalidRes
            isFinished = true
        }
    }

    fun startSimulatedPass() {
        livePoints.clear()
        isFinished = false
        finalResult = null
        isSimulating = true
        userInspectingPoint = null

        coroutineScope.launch {
            val fullSim = DynoCalculator.generateSimulatedRun(vehicleSpec, selectedGearIndex)
            val simPoints = fullSim.points

            for (p in simPoints) {
                if (!isSimulating) break
                livePoints.add(p)
                delay(75)
            }
            if (isSimulating && livePoints.isNotEmpty()) {
                finishTestPass(fullSim)
            }
        }
    }

    val displayPoints = if (isSimulating) livePoints else if (validSensorPoints.isNotEmpty()) validSensorPoints else livePoints
    val lastPoint = displayPoints.lastOrNull()

    val displayRpm = if (isSensorRecording && sensorRpm > 0) sensorRpm else lastPoint?.rpm ?: 0
    val displaySpeed = if (isSensorRecording) currentSpeedKmh else lastPoint?.speedKmh ?: 0.0
    val displayPower = if (isSensorRecording && sensorPowerCv > 0) sensorPowerCv else lastPoint?.powerCv ?: 0.0
    val displayTorque = if (isSensorRecording && sensorTorqueKgfm > 0) sensorTorqueKgfm else lastPoint?.torqueKgfm ?: 0.0

    // Peak tracking
    val peakPowerSoFar = displayPoints.maxOfOrNull { it.powerCv } ?: displayPower
    val peakPowerRpmSoFar = displayPoints.maxByOrNull { it.powerCv }?.rpm ?: displayRpm
    val peakTorqueSoFar = displayPoints.maxOfOrNull { it.torqueKgfm } ?: displayTorque
    val peakTorqueRpmSoFar = displayPoints.maxByOrNull { it.torqueKgfm }?.rpm ?: displayRpm

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        // 1. TOP TELEMETRY HUD BAR (RPM TACHOMETER, CV, KGFM, KM/H, A_LONG & PASS STATE)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(CarbonSurface)
                .border(1.dp, CarbonBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back button + Vehicle Spec Tag
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CarbonSurfaceVariant)
                        .testTag("live_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column {
                    Text(
                        text = vehicleSpec.name.ifBlank { "VEÍCULO CONFIGURADO" }.take(18),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(DynoRed)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "MARCHA ${selectedGearIndex + 1}",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${vehicleSpec.testWeightKg.toInt()} kg",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }
                }
            }

            // Central Telemetry Digital Gauges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // RPM
                HudMetricBlock(
                    label = "RPM",
                    value = if (displayRpm > 0) "%d".format(displayRpm) else "0",
                    unit = "",
                    color = when {
                        displayRpm >= 6200 -> DynoRed
                        displayRpm >= 5000 -> DynoAmber
                        else -> TextPrimary
                    },
                    subtext = if (displayPoints.isNotEmpty()) "PICO: $peakPowerRpmSoFar" else ""
                )

                // POTÊNCIA (CV)
                HudMetricBlock(
                    label = "POTÊNCIA",
                    value = "%.1f".format(displayPower),
                    unit = "CV",
                    color = DynoCyan,
                    subtext = if (peakPowerSoFar > 0.0) "MAX: %.1f".format(peakPowerSoFar) else ""
                )

                // TORQUE (kgf.m)
                HudMetricBlock(
                    label = "TORQUE",
                    value = "%.1f".format(displayTorque),
                    unit = "kgfm",
                    color = DynoAmber,
                    subtext = if (peakTorqueSoFar > 0.0) "MAX: %.1f".format(peakTorqueSoFar) else ""
                )

                // VELOCIDADE (km/h)
                HudMetricBlock(
                    label = "VELOCIDADE",
                    value = "%.0f".format(displaySpeed),
                    unit = "km/h",
                    color = DynoYellow,
                    subtext = if (displayPoints.isNotEmpty()) "%.0f km/h".format(displayPoints.maxOf { it.speedKmh }) else ""
                )

                // ACELERAÇÃO LONGITUDINAL (m/s²)
                HudMetricBlock(
                    label = "a_LONG",
                    value = "%.2f".format(currentAccelMps2),
                    unit = "m/s²",
                    color = if (currentAccelMps2 > 0.3) DynoGreen else DynoAmber,
                    subtext = "PURIFIED"
                )
            }

            // PASS STATE BADGE
            val (stateBg, stateTextColor, stateIcon) = when {
                !isSensorRecording && !isSimulating && !isFinished -> Triple(CarbonSurfaceVariant, TextMuted, Icons.Default.HourglassTop)
                isSimulating -> Triple(DynoCyan.copy(alpha = 0.2f), DynoCyan, Icons.AutoMirrored.Filled.TrendingUp)
                else -> when (passState) {
                    PassState.AGUARDANDO -> Triple(CarbonSurfaceVariant, DynoYellow, Icons.Default.HourglassTop)
                    PassState.ACELERANDO -> Triple(DynoGreen.copy(alpha = 0.25f), DynoGreen, Icons.AutoMirrored.Filled.TrendingUp)
                    PassState.FINALIZANDO -> Triple(DynoAmber.copy(alpha = 0.25f), DynoAmber, Icons.Default.HourglassTop)
                    PassState.CONCLUIDA -> Triple(DynoGreen.copy(alpha = 0.35f), DynoGreen, Icons.Default.CheckCircle)
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(stateBg)
                    .border(1.dp, stateTextColor.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = stateIcon, contentDescription = null, tint = stateTextColor, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSimulating) "SIMULAÇÃO" else passState.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = stateTextColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // 2. RPM SHIFT LIGHT STRIP (RACING TELEMETRY LEDS)
        RpmShiftLightBar(
            currentRpm = displayRpm,
            maxRpm = 7000,
            redlineRpm = 6200,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CarbonSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(3.dp))

        // 3. MAIN FULL-SCREEN REALTIME DYNO GRAPH CANVAS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(ChartBackground)
                .border(1.dp, CarbonBorder, RoundedCornerShape(4.dp))
        ) {
            FullScreenDynoCanvas(
                points = displayPoints,
                pulseRadius = pulseRadius,
                inspectedPoint = userInspectingPoint,
                onPointSelected = { pt -> userInspectingPoint = pt },
                modifier = Modifier.fillMaxSize()
            )

            // Top-left Legend Overlay inside Canvas
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, top = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CarbonSurface.copy(alpha = 0.85f))
                    .border(1.dp, CarbonBorder, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LegendChip(color = DynoCyan, label = "POTÊNCIA (CV)")
                LegendChip(color = DynoAmber, label = "TORQUE (kgfm)")
                LegendChip(color = DynoYellow, label = "VELOCIDADE (km/h)", isDotted = true)

                if (userInspectingPoint != null) {
                    val ip = userInspectingPoint!!
                    Text(
                        text = "• ${ip.rpm} RPM | %.1f CV | %.1f kgfm | %.0f km/h".format(ip.powerCv, ip.torqueKgfm, ip.speedKmh),
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Finished Run Overlay Report Card
            if (isFinished && finalResult != null) {
                val res = finalResult!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonSurface)
                            .border(1.dp, if (res.isValid) DynoCyan else DynoRed, RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (res.isValid) "🏁 PASSADA FINALIZADA COM SUCESSO" else "⚠️ PASSADA INVÁLIDA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (res.isValid) DynoYellow else DynoRed,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (res.isValid) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CarbonSurfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                FinishedMetricItem("POTÊNCIA MÁXIMA", "%.1f CV".format(res.peakPowerCv), "@ ${res.peakPowerRpm} RPM", DynoCyan)
                                FinishedMetricItem("TORQUE MÁXIMO", "%.1f kgfm".format(res.peakTorqueKgfm), "@ ${res.peakTorqueRpm} RPM", DynoAmber)
                                FinishedMetricItem("VELOCIDADE MÁX", "%.0f km/h".format(res.maxSpeedKmh), "${vehicleSpec.name}", DynoYellow)
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = DynoRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (res.invalidReason.isNotBlank()) res.invalidReason else "Movimento excessivo do smartphone detectado. Verifique o suporte e calibre novamente.",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!res.isValid) {
                                Button(
                                    onClick = onRecalibrate,
                                    colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(32.dp).testTag("recalibrar_apos_invalido_button")
                                ) {
                                    Icon(imageVector = Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("RECALIBRAR ORIENTAÇÃO", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        livePoints.clear()
                                        isFinished = false
                                        finalResult = null
                                        userInspectingPoint = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(32.dp).testTag("novo_teste_live_button")
                                ) {
                                    Text("NOVA PASSADA", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onViewFullResult(res) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DynoCyan),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("RELATÓRIO COMPLETO", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            OutlinedButton(
                                onClick = { onViewDiagnostic(res) },
                                border = BorderStroke(1.dp, CarbonBorder),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("DIAGNÓSTICO", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // 4. BOTTOM TELEMETRY ACTION BAR (GEAR SELECTOR & OPERATION BUTTONS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gear Selector Mini Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "MARCHA:",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                gearLabels.forEachIndexed { index, label ->
                    val selected = selectedGearIndex == index
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) DynoRed else CarbonSurfaceVariant)
                            .border(1.dp, if (selected) DynoRed else CarbonBorder, RoundedCornerShape(3.dp))
                            .clickable { selectedGearIndex = index }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                            color = if (selected) Color.White else TextSecondary
                        )
                    }
                }
            }

            // Action Trigger Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isSensorRecording && !isSimulating && !isFinished) {
                    Button(
                        onClick = {
                            val currentHealth = sensorManager.refreshHealthState()
                            if (!currentHealth.isSystemReady) {
                                showPreTestDialog = true
                            } else {
                                livePoints.clear()
                                isFinished = false
                                userInspectingPoint = null
                                sensorManager.startRecording(vehicleSpec, selectedGearIndex)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("iniciar_passada_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("INICIAR PASSADA", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                    }

                    OutlinedButton(
                        onClick = { startSimulatedPass() },
                        border = BorderStroke(1.dp, CarbonBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("simular_passada_button")
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = DynoCyan, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SIMULAR", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = DynoCyan)
                    }
                } else if (isSensorRecording) {
                    Button(
                        onClick = {
                            val samples = sensorManager.stopRecording()
                            val calculated = DynoCalculator.calculate(samples, vehicleSpec, selectedGearIndex)
                            finishTestPass(calculated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DynoCyan),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("parar_passada_button")
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PARAR PASSADA", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                } else if (isSimulating) {
                    Button(
                        onClick = {
                            isSimulating = false
                            val sim = DynoCalculator.generateSimulatedRun(vehicleSpec, selectedGearIndex)
                            finishTestPass(sim)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DynoYellow),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PARAR", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        }

        if (showPreTestDialog) {
            Dialog(onDismissRequest = { showPreTestDialog = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarbonSurface)
                        .padding(12.dp)
                ) {
                    PreTestSensorStatusCard(sensorManager = sensorManager, compact = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            onClick = { showPreTestDialog = false },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("FECHAR", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RpmShiftLightBar(
    currentRpm: Int,
    maxRpm: Int,
    redlineRpm: Int,
    modifier: Modifier = Modifier
) {
    val numSegments = 28
    val activeRatio = (currentRpm.toFloat() / maxRpm.toFloat()).coerceIn(0f, 1f)
    val activeSegments = (activeRatio * numSegments).toInt()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        for (i in 0 until numSegments) {
            val segRatio = i.toFloat() / numSegments.toFloat()
            val segRpm = (segRatio * maxRpm).toInt()
            val isActive = i < activeSegments

            val segColor = when {
                segRpm >= redlineRpm -> DynoRed
                segRpm >= 5000 -> DynoAmber
                segRpm >= 3500 -> DynoYellow
                else -> DynoGreen
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isActive) segColor else CarbonSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String, isDotted: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = if (isDotted) 2.dp else 3.dp)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun HudMetricBlock(
    label: String,
    value: String,
    unit: String,
    color: Color,
    subtext: String = ""
) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextMuted
            )
            if (subtext.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = subtext,
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = color
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun FinishedMetricItem(label: String, value: String, subtext: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextMuted)
        Text(text = value, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = color)
        if (subtext.isNotEmpty()) {
            Text(text = subtext, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
        }
    }
}

@Composable
private fun FullScreenDynoCanvas(
    points: List<DynoPoint>,
    pulseRadius: Float,
    inspectedPoint: DynoPoint?,
    onPointSelected: (DynoPoint?) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    val cyanStyle = remember {
        androidx.compose.ui.text.TextStyle(
            color = DynoCyan,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
    val amberStyle = remember {
        androidx.compose.ui.text.TextStyle(
            color = DynoAmber,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
    val axisLabelStyle = remember {
        androidx.compose.ui.text.TextStyle(
            color = TextSecondary,
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    Canvas(
        modifier = modifier
            .pointerInput(points) {
                detectTapGestures(
                    onTap = { offset ->
                        if (points.isEmpty()) return@detectTapGestures
                        val minRpm = 1000.0
                        val maxRpm = 7000.0
                        val paddingLeft = 38.dp.toPx()
                        val paddingRight = 38.dp.toPx()
                        val chartWidth = size.width - paddingLeft - paddingRight
                        val ratio = ((offset.x - paddingLeft) / chartWidth).coerceIn(0f, 1f)
                        val targetRpm = minRpm + (maxRpm - minRpm) * ratio
                        val nearest = points.minByOrNull { kotlin.math.abs(it.rpm - targetRpm) }
                        onPointSelected(nearest)
                    }
                )
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        if (points.isEmpty()) return@detectDragGestures
                        val minRpm = 1000.0
                        val maxRpm = 7000.0
                        val paddingLeft = 38.dp.toPx()
                        val paddingRight = 38.dp.toPx()
                        val chartWidth = size.width - paddingLeft - paddingRight
                        val ratio = ((change.position.x - paddingLeft) / chartWidth).coerceIn(0f, 1f)
                        val targetRpm = minRpm + (maxRpm - minRpm) * ratio
                        val nearest = points.minByOrNull { kotlin.math.abs(it.rpm - targetRpm) }
                        onPointSelected(nearest)
                    },
                    onDragEnd = {}
                )
            }
    ) {
        val width = size.width
        val height = size.height

        val paddingLeft = 38.dp.toPx()
        val paddingRight = 38.dp.toPx()
        val paddingTop = 12.dp.toPx()
        val paddingBottom = 18.dp.toPx()

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

        val minRpm = 1000.0
        val maxRpm = 7000.0
        val maxCv = max(60.0, ((points.maxOfOrNull { it.powerCv } ?: 100.0) * 1.15))
        val maxKgfm = max(12.0, ((points.maxOfOrNull { it.torqueKgfm } ?: 20.0) * 1.15))
        val maxSpeed = max(80.0, ((points.maxOfOrNull { it.speedKmh } ?: 120.0) * 1.15))

        val gridEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        val subGridEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f)

        // 1. Horizontal Gridlines & Dual Y Axis (Left: CV, Right: kgfm)
        val gridRows = 5
        for (i in 0..gridRows) {
            val y = paddingTop + (chartHeight * i / gridRows)
            val ratio = 1f - (i.toFloat() / gridRows)

            drawLine(
                color = if (i == gridRows) CarbonBorder else ChartGridLine,
                start = Offset(paddingLeft, y),
                end = Offset(width - paddingRight, y),
                pathEffect = if (i == gridRows) null else gridEffect,
                strokeWidth = 1f
            )

            // Left Y-Axis: Power (CV)
            val cvText = "${(maxCv * ratio).toInt()}"
            val cvLayout = textMeasurer.measure(cvText, style = cyanStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = cvText,
                style = cyanStyle,
                topLeft = Offset(paddingLeft - cvLayout.size.width - 4.dp.toPx(), y - cvLayout.size.height / 2f)
            )

            // Right Y-Axis: Torque (kgf.m)
            val torqueText = "%.1f".format(maxKgfm * ratio)
            val torqueLayout = textMeasurer.measure(torqueText, style = amberStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = torqueText,
                style = amberStyle,
                topLeft = Offset(width - paddingRight + 4.dp.toPx(), y - torqueLayout.size.height / 2f)
            )
        }

        // 2. Vertical RPM Gridlines & Minor Subgrid Ticks
        val gridCols = 6
        for (i in 0..gridCols) {
            val x = paddingLeft + (chartWidth * i / gridCols)
            val rpmVal = minRpm + (maxRpm - minRpm) * (i.toDouble() / gridCols)

            drawLine(
                color = ChartGridLine,
                start = Offset(x, paddingTop),
                end = Offset(x, height - paddingBottom),
                pathEffect = gridEffect,
                strokeWidth = 1f
            )

            // X-Axis RPM Labels (1k, 2k, ..., 7k)
            val rpmText = "${(rpmVal / 1000).toInt()}k"
            val rpmLayout = textMeasurer.measure(rpmText, style = axisLabelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = rpmText,
                style = axisLabelStyle,
                topLeft = Offset(x - rpmLayout.size.width / 2f, height - paddingBottom + 2.dp.toPx())
            )
        }

        if (points.isEmpty()) return@Canvas

        fun getX(rpm: Int): Float {
            val ratio = (rpm - minRpm) / (maxRpm - minRpm)
            return (paddingLeft + chartWidth * ratio.coerceIn(0.0, 1.0)).toFloat()
        }

        fun getY(valDouble: Double, maxValDouble: Double): Float {
            val ratio = (valDouble / maxValDouble).coerceIn(0.0, 1.0)
            return (height - paddingBottom - chartHeight * ratio).toFloat()
        }

        // 3. Draw Speed Curve (Subtle Dotted Yellow)
        val speedOffsets = points.map { Offset(getX(it.rpm), getY(it.speedKmh, maxSpeed)) }
        val speedPath = Path()
        if (speedOffsets.size >= 2) {
            speedPath.moveTo(speedOffsets.first().x, speedOffsets.first().y)
            for (i in 1 until speedOffsets.size) {
                speedPath.lineTo(speedOffsets[i].x, speedOffsets[i].y)
            }
            drawPath(
                path = speedPath,
                color = DynoYellow.copy(alpha = 0.5f),
                style = Stroke(width = 1.2.dp.toPx(), pathEffect = subGridEffect)
            )
        }

        // 4. Draw Power Curve (Cyan with Gradient Fill)
        val powerOffsets = points.map { Offset(getX(it.rpm), getY(it.powerCv, maxCv)) }
        val powerPath = Path()
        val powerFill = Path()
        SplineSmoothing.buildSmoothSplinePath(powerOffsets, powerPath, powerFill, height - paddingBottom)

        drawPath(
            path = powerFill,
            brush = Brush.verticalGradient(
                colors = listOf(DynoCyan.copy(alpha = 0.18f), Color.Transparent),
                startY = paddingTop,
                endY = height - paddingBottom
            )
        )
        drawPath(path = powerPath, color = DynoCyan, style = Stroke(width = 2.5.dp.toPx()))

        // 5. Draw Torque Curve (Amber with Gradient Fill)
        val torqueOffsets = points.map { Offset(getX(it.rpm), getY(it.torqueKgfm, maxKgfm)) }
        val torquePath = Path()
        val torqueFill = Path()
        SplineSmoothing.buildSmoothSplinePath(torqueOffsets, torquePath, torqueFill, height - paddingBottom)

        drawPath(
            path = torqueFill,
            brush = Brush.verticalGradient(
                colors = listOf(DynoAmber.copy(alpha = 0.12f), Color.Transparent),
                startY = paddingTop,
                endY = height - paddingBottom
            )
        )
        drawPath(path = torquePath, color = DynoAmber, style = Stroke(width = 2.5.dp.toPx()))

        // 6. Draw Peak Markers (Diamond / Dot)
        val peakPowerPoint = points.maxByOrNull { it.powerCv }
        if (peakPowerPoint != null) {
            val px = getX(peakPowerPoint.rpm)
            val py = getY(peakPowerPoint.powerCv, maxCv)
            drawCircle(color = DynoCyan, radius = 3.5.dp.toPx(), center = Offset(px, py))
            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(px, py))
        }

        val peakTorquePoint = points.maxByOrNull { it.torqueKgfm }
        if (peakTorquePoint != null) {
            val tx = getX(peakTorquePoint.rpm)
            val ty = getY(peakTorquePoint.torqueKgfm, maxKgfm)
            drawCircle(color = DynoAmber, radius = 3.5.dp.toPx(), center = Offset(tx, ty))
            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(tx, ty))
        }

        // 7. Active Head Tracker / Inspected Cursor
        val targetPoint = inspectedPoint ?: points.last()
        val curX = getX(targetPoint.rpm)
        val curPowerY = getY(targetPoint.powerCv, maxCv)
        val curTorqueY = getY(targetPoint.torqueKgfm, maxKgfm)

        // Vertical Crosshair
        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = Offset(curX, paddingTop),
            end = Offset(curX, height - paddingBottom),
            strokeWidth = 1.dp.toPx(),
            pathEffect = gridEffect
        )

        // Pulsing cursor on power point
        drawCircle(color = DynoCyan.copy(alpha = 0.35f), radius = pulseRadius.dp.toPx(), center = Offset(curX, curPowerY))
        drawCircle(color = DynoCyan, radius = 3.5.dp.toPx(), center = Offset(curX, curPowerY))
        drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(curX, curPowerY))

        // Torque head dot
        drawCircle(color = DynoAmber, radius = 3.5.dp.toPx(), center = Offset(curX, curTorqueY))
        drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(curX, curTorqueY))
    }
}
