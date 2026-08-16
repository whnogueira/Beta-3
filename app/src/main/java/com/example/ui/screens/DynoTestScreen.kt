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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun DynoTestScreen(
    vehicleSpec: VehicleSpec,
    sensorManager: DynoSensorManager,
    onNovaPassada: () -> Unit,
    onOpenHistory: () -> Unit,
    onEditVehicle: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenSupport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(14.dp)
    ) {
        // TOP LOGO & APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DynoRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DYNO MOBILE",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(DynoYellow.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = "BETA",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = DynoYellow
                            )
                        }
                    }
                    Text(
                        text = "Dinamômetro Inercial Automotivo",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onOpenGarage,
                    modifier = Modifier.size(36.dp).testTag("open_garage_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Garagem de Veículos",
                        tint = DynoYellow,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.size(36.dp).testTag("open_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Histórico de Passadas",
                        tint = DynoCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSupport,
                    modifier = Modifier.size(36.dp).testTag("open_support_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Help,
                        contentDescription = "Ajuda e Suporte",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // VEHICLE STATUS CARD (Configured vs No Vehicle)
            if (vehicleSpec.isConfigured) {
                // Configured Vehicle Card
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
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = DynoYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VEÍCULO ATIVO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = DynoYellow,
                                letterSpacing = 1.sp
                            )

                            if (vehicleSpec.isExampleVehicle) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(DynoYellow.copy(alpha = 0.2f))
                                        .border(0.5.dp, DynoYellow.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                ) {
                                    Text(
                                        text = "EXEMPLO",
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = DynoYellow
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onOpenGarage,
                                modifier = Modifier.size(28.dp).testTag("swap_vehicle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Trocar Veículo",
                                    tint = DynoCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onEditVehicle,
                                modifier = Modifier.size(28.dp).testTag("edit_vehicle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar Veículo",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = vehicleSpec.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = TextPrimary
                    )

                    val subDetails = listOfNotNull(
                        vehicleSpec.brand.takeIf { it.isNotBlank() },
                        vehicleSpec.engine.takeIf { it.isNotBlank() },
                        vehicleSpec.transmissionName.takeIf { it.isNotBlank() },
                        vehicleSpec.drive.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")

                    if (subDetails.isNotBlank()) {
                        Text(
                            text = subDetails,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Vehicle Parameters Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(CarbonSurfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("MASSA TOTAL", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                            Text(
                                "${vehicleSpec.effectiveTestMassKg.toInt()} kg",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Column {
                            Text("PNEU (Ø / CIRC)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                            Text(
                                "${vehicleSpec.tireWidthMm}/${vehicleSpec.tireAspect} R${vehicleSpec.rimInches} (%.1fmm)".format(vehicleSpec.tireDiameterMm),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Column {
                            Text("DIFERENCIAL", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                            Text(
                                "%.2f".format(vehicleSpec.finalDrive),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = DynoAmber
                            )
                        }
                    }
                }
            } else {
                // No Vehicle Registered Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarbonSurface)
                        .border(1.dp, DynoYellow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = DynoYellow,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Nenhum veículo cadastrado",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cadastre os dados do seu veículo (peso, pneu e relações) para realizar a medição de dinamômetro.",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onEditVehicle,
                        colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(36.dp).testTag("adicionar_veiculo_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ADICIONAR VEÍCULO", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // SENSORS HEALTH DIAGNOSTIC CARD
            PreTestSensorStatusCard(sensorManager = sensorManager)

            // START DYNO PULL / PASSADA BUTTON
            Button(
                onClick = onNovaPassada,
                colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("iniciar_teste_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "NOVA PASSADA DE DINAMÔMETRO",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (vehicleSpec.isConfigured) "Puxada em marcha contínua (WOT)" else "Configure o veículo para iniciar",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // Instructions & Best Practices Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonSurface)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "PROCEDIMENTO PARA PUXADA:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = DynoCyan,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Fixe o celular firmemente no suporte do painel.\n2. Engate a marcha de teste (ex: 2ª ou 3ª marcha).\n3. Em via plana e segura, inicie de baixa rotação (~1500 RPM).\n4. Pise fundo no acelerador (100% WOT) até próximo do limitador sem trocar de marcha.\n5. O DynoMobile calcula potência e torque a partir da aceleração filtrada.",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }

            // Beta Disclaimer Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(CarbonDark)
                    .border(0.5.dp, CarbonBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "Versão Beta - Medições estimadas via sensores inerciais e GPS do dispositivo. Operação 100% offline.",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
