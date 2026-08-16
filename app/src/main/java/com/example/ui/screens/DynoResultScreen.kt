package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DynoResult
import com.example.model.PassQuality
import com.example.model.TestDataSource
import com.example.ui.components.ChartMode
import com.example.ui.components.DynoChartCanvas
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
fun DynoResultScreen(
    result: DynoResult,
    onNovoTeste: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var chartMode by remember { mutableStateOf(ChartMode.POWER_AND_TORQUE) }
    val gearLabels = listOf("1ª", "2ª", "3ª", "4ª", "5ª")
    val gearStr = gearLabels.getOrElse(result.selectedGear) { "3ª" }

    fun shareResult() {
        if (!result.isValid) {
            Toast.makeText(context, "Passada inválida para compartilhamento.", Toast.LENGTH_SHORT).show()
            return
        }
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                """
                🏁 DYNO MOBILE - RESULTADO DO TESTE
                Veículo: ${result.vehicleSpec.name}
                Marcha: $gearStr
                ⚡ Potência: %.1f CV @ %d RPM
                🔧 Torque: %.1f kgfm @ %d RPM
                🚀 Velocidade Máx: %.0f km/h
                📊 Qualidade: ${result.passQuality.label}
                """.trimIndent()
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Compartilhar Resultado Dyno")
        context.startActivity(shareIntent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // TOP TELEMETRY REPORT HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(CarbonSurface)
                .border(1.dp, CarbonBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(30.dp).testTag("result_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (result.isValid) "RELATÓRIO DE PASSADA" else "PASSADA INVÁLIDA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (result.isValid) TextPrimary else DynoRed
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        !result.isValid -> DynoRed.copy(alpha = 0.2f)
                                        result.dataSource == TestDataSource.REAL_TEST_DATA -> DynoGreen.copy(alpha = 0.2f)
                                        else -> DynoYellow.copy(alpha = 0.2f)
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = when {
                                    !result.isValid -> "INVÁLIDA"
                                    result.dataSource == TestDataSource.REAL_TEST_DATA -> "REAL"
                                    else -> "DEMO"
                                },
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    !result.isValid -> DynoRed
                                    result.dataSource == TestDataSource.REAL_TEST_DATA -> DynoGreen
                                    else -> DynoYellow
                                }
                            )
                        }

                        if (result.isValid) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when (result.passQuality) {
                                            PassQuality.EXCELENTE -> DynoGreen.copy(alpha = 0.2f)
                                            PassQuality.BOA -> DynoCyan.copy(alpha = 0.2f)
                                            PassQuality.REGULAR -> DynoYellow.copy(alpha = 0.2f)
                                            PassQuality.INVALIDA -> DynoRed.copy(alpha = 0.2f)
                                        }
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = result.passQuality.label.uppercase(),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = when (result.passQuality) {
                                        PassQuality.EXCELENTE -> DynoGreen
                                        PassQuality.BOA -> DynoCyan
                                        PassQuality.REGULAR -> DynoYellow
                                        PassQuality.INVALIDA -> DynoRed
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "${result.vehicleSpec.name} • $gearStr Marcha • ${result.vehicleSpec.weightKg.toInt()} kg",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }

            // Quick Metrics Summary in Header
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("PICO POTÊNCIA", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                    Text(
                        text = if (result.isValid) "%.1f CV".format(result.peakPowerCv) else "---",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = if (result.isValid) DynoCyan else TextMuted
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("PICO TORQUE", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                    Text(
                        text = if (result.isValid) "%.1f kgfm".format(result.peakTorqueKgfm) else "---",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = if (result.isValid) DynoAmber else TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // MAIN REPORT AREA: DYNO GRAPH (Valid) OR INVALID NOTICE CARD (Invalid)
        if (result.isValid && result.points.isNotEmpty()) {
            DynoChartCanvas(
                result = result,
                mode = chartMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            // PASSADA INVÁLIDA Technical Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CarbonSurface)
                    .border(1.dp, DynoRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = DynoRed,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "PASSADA INVÁLIDA",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp,
                        color = DynoRed
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Motivo:\n\"${if (result.invalidReason.isNotBlank()) result.invalidReason else "Dados inconsistentes dos sensores. Refaça a passagem."}\"",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CarbonDark)
                            .border(1.dp, CarbonBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "DIRETRIZES PARA UMA PASSADA PRECISA:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = DynoYellow
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Mantenha aceleração plena (WOT) contínua em marcha única (sem troca de marcha).\n• Fixe o celular firmemente no suporte do veículo para evitar vibração excessiva.\n• Garanta sinal aberto de GPS com boa visibilidade do céu.\n• Realize a puxada por pelo menos 2 a 4 segundos contínuos.",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // BOTTOM CONTROLS & FILTER CHIPS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chart Mode Selector Chips
            if (result.isValid) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModeChip("CV + TORQUE", chartMode == ChartMode.POWER_AND_TORQUE) { chartMode = ChartMode.POWER_AND_TORQUE }
                    ModeChip("CV", chartMode == ChartMode.POWER_ONLY) { chartMode = ChartMode.POWER_ONLY }
                    ModeChip("TORQUE", chartMode == ChartMode.TORQUE_ONLY) { chartMode = ChartMode.TORQUE_ONLY }
                    ModeChip("VELOCIDADE", chartMode == ChartMode.SPEED_TIME) { chartMode = ChartMode.SPEED_TIME }
                }
            } else {
                Text(
                    text = "Amostras: ${result.totalSampleCount} | Rejeitadas: ${result.rejectedSampleCount}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result.isValid) {
                    OutlinedButton(
                        onClick = { shareResult() },
                        border = BorderStroke(1.dp, CarbonBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(30.dp).testTag("share_result_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COMPARTILHAR", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    }
                }

                OutlinedButton(
                    onClick = onOpenDiagnostic,
                    border = BorderStroke(1.dp, CarbonBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(30.dp).testTag("view_diagnostic_button")
                ) {
                    Icon(imageVector = Icons.Default.Calculate, contentDescription = null, tint = DynoYellow, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DIAGNÓSTICO", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DynoYellow)
                }

                Button(
                    onClick = onNovoTeste,
                    colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(30.dp).testTag("novo_teste_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("NOVA PASSADA", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) CarbonSurfaceVariant else CarbonDark)
            .border(1.dp, if (selected) DynoCyan else CarbonBorder, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) DynoCyan else TextSecondary
        )
    }
}
