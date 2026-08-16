package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VehicleSpec
import com.example.sensor.DynoSensorManager
import com.example.ui.components.PreTestSensorStatusCard
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

@Composable
fun TestPreparationScreen(
    vehicleSpec: VehicleSpec,
    selectedGearIndex: Int,
    sensorManager: DynoSensorManager,
    onSelectGear: (Int) -> Unit,
    onEditVehicle: () -> Unit,
    onStartTest: () -> Unit,
    onBack: () -> Unit
) {
    val gearLabels = listOf("1ª Marcha", "2ª Marcha", "3ª Marcha", "4ª Marcha", "5ª Marcha")
    val liveCheck by sensorManager.liveOrientation.collectAsState()
    val calibratedOrientation by sensorManager.calibratedOrientation.collectAsState()
    val isCalibrated = calibratedOrientation != null && calibratedOrientation?.isCalibrated == true

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
                .padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp).testTag("prep_back_button")
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
                    text = "PREPARAÇÃO DA PASSADA",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Configuração do veículo e marcha de teste",
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // VEHICLE SUMMARY CARD
            if (vehicleSpec.isConfigured) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarbonSurface)
                        .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = DynoYellow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VEÍCULO ATIVO", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = DynoYellow)
                        }
                        IconButton(onClick = onEditVehicle, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = vehicleSpec.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(CarbonSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("MASSA TOTAL", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                            Text("${(vehicleSpec.weightKg + 100.0).toInt()} kg", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column {
                            Text("PNEU", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                            Text("${vehicleSpec.tireWidthMm}/${vehicleSpec.tireAspect} R${vehicleSpec.rimInches}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column {
                            Text("DIFERENCIAL", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                            Text("%.2f".format(vehicleSpec.finalDrive), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            } else {
                // No vehicle configured warning in prep screen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarbonSurface)
                        .border(1.dp, DynoYellow.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = DynoYellow, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Nenhum veículo cadastrado", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Você precisa cadastrar um veículo antes de iniciar a medição.", fontSize = 11.sp, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onEditVehicle,
                        colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(34.dp).testTag("prep_adicionar_veiculo_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ADICIONAR VEÍCULO", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // STANDARD MOUNT ORIENTATION VISUAL
            StandardMountOrientationVisual(
                liveCheck = liveCheck,
                isCalibrated = isCalibrated
            )

            // GEAR SELECTOR
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonSurface)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "SELECIONE A MARCHA DA PUXADA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = DynoCyan,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recomendado: 2ª ou 3ª marcha para melhor resolução e tração sem patinamento.",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    gearLabels.forEachIndexed { index, label ->
                        val isSelected = selectedGearIndex == index
                        val ratio = vehicleSpec.gearRatios.getOrElse(index) { 1.0 }
                        val total = ratio * vehicleSpec.finalDrive

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) CarbonSurfaceVariant else CarbonDark)
                                .border(1.dp, if (isSelected) DynoRed else CarbonBorder, RoundedCornerShape(4.dp))
                                .clickable { onSelectGear(index) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isSelected) DynoRed else CarbonBorder),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            }

                            Text(
                                text = "Relação: %.2f (Total: %.2f)".format(ratio, total),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) DynoAmber else TextMuted
                            )
                        }
                    }
                }
            }

            // SENSOR STATUS
            PreTestSensorStatusCard(sensorManager = sensorManager)

            // CALIBRATION / START BUTTON
            Button(
                onClick = onStartTest,
                colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("iniciar_passada_direta_button")
            ) {
                Icon(imageVector = Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CALIBRAR ORIENTAÇÃO E INICIAR",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
