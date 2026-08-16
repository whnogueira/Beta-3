package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DynoResult
import com.example.model.PassQuality
import com.example.model.TestDataSource
import com.example.sensor.DynoSensorManager
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

enum class DiagnosticViewMode {
    PROCESSED_CURVE,
    RAW_DATA
}

@Composable
fun CalculationDiagnosticScreen(
    result: DynoResult,
    sensorManager: DynoSensorManager? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gearNames = listOf("1ª", "2ª", "3ª", "4ª", "5ª")
    val gearLabel = gearNames.getOrElse(result.selectedGear) { "3ª" }

    var viewMode by remember { mutableStateOf(DiagnosticViewMode.PROCESSED_CURVE) }

    val rawPointsList = if (result.rawPoints.isNotEmpty()) result.rawPoints else result.points
    val processedPointsList = if (result.points.isNotEmpty()) result.points else result.rawPoints
    val displayPoints = if (viewMode == DiagnosticViewMode.PROCESSED_CURVE) processedPointsList else rawPointsList

    // Sensor live states for diagnostic readout
    val healthState by (sensorManager?.healthState ?: remember {
        kotlinx.coroutines.flow.MutableStateFlow(com.example.sensor.SensorHealthState())
    }).collectAsState()

    val calibratedOrientation by (sensorManager?.calibratedOrientation ?: remember {
        kotlinx.coroutines.flow.MutableStateFlow(null)
    }).collectAsState()

    val excessiveMovement by (sensorManager?.excessiveMovementDetected ?: remember {
        kotlinx.coroutines.flow.MutableStateFlow(false)
    }).collectAsState()

    val signalQuality by (sensorManager?.signalQuality ?: remember {
        kotlinx.coroutines.flow.MutableStateFlow(result.passQuality)
    }).collectAsState()

    val isOrientationCalibrated = calibratedOrientation?.isCalibrated == true || result.dataSource == TestDataSource.REAL_TEST_DATA
    val phoneStabilityText = if (excessiveMovement || !result.isValid && result.invalidReason.contains("Movimento")) "Movimento detectado" else "Boa"
    val signalQualityLabel = when {
        !result.isValid || signalQuality == PassQuality.INVALIDA -> "Ruim"
        result.passQuality == PassQuality.REGULAR -> "Regular"
        else -> "Boa"
    }

    val isQualityPoor = result.passQuality == PassQuality.INVALIDA || (result.passQuality == PassQuality.REGULAR && !result.isValid)

    fun buildDiagnosticReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== DYNO MOBILE - DIAGNÓSTICO TÉCNICO & TELEMETRIA ===")
        sb.appendLine("Data Source: ${if (result.dataSource == TestDataSource.REAL_TEST_DATA) "REAL SENSOR DATA" else "DEMO SIMULATION"}")
        sb.appendLine("Status da Validação: ${if (result.isValid) "PASSADA VÁLIDA" else "PASSADA INVÁLIDA"}")
        if (!result.isValid && result.invalidReason.isNotEmpty()) {
            sb.appendLine("Motivo de Invalidação: ${result.invalidReason}")
        }
        sb.appendLine("Qualidade da Passada: ${result.passQuality.label}")
        sb.appendLine("--------------------------------------------")
        sb.appendLine("DIAGNÓSTICO DE SENSORES E ORIENTAÇÃO:")
        sb.appendLine("Orientação: ${if (isOrientationCalibrated) "Calibrada" else "Não calibrada"}")
        sb.appendLine("Estabilidade do Celular: $phoneStabilityText")
        sb.appendLine("Aceleração Longitudinal: %.2f m/s²".format(result.maxAccelMps2))
        sb.appendLine("GPS Status: ${healthState.gpsStatus}")
        sb.appendLine("Amostras Rejeitadas (Outliers): ${result.rejectedSampleCount}")
        sb.appendLine("Qualidade do Sinal: $signalQualityLabel")
        sb.appendLine("--------------------------------------------")
        sb.appendLine("Veículo: ${result.vehicleSpec.name}")
        sb.appendLine("Massa Total: ${(result.vehicleSpec.weightKg + 100.0).toInt()} kg")
        sb.appendLine("Pneu: ${result.vehicleSpec.tireWidthMm}/${result.vehicleSpec.tireAspect} R${result.vehicleSpec.rimInches}")
        sb.appendLine("Marcha: $gearLabel (${result.vehicleSpec.gearRatios.getOrElse(result.selectedGear) { 1.32 }})")
        sb.appendLine("Diferencial: ${result.vehicleSpec.finalDrive}")
        sb.appendLine("--------------------------------------------")
        sb.appendLine("Velocidade Inicial -> Final: ${result.initialSpeedKmh} -> ${result.finalSpeedKmh} km/h")
        sb.appendLine("RPM Inicial -> Final: ${result.initialRpm} -> ${result.finalRpm} RPM")
        sb.appendLine("Aceleração Máx (Longitudinal Filtrada): ${result.maxAccelMps2} m/s²")
        sb.appendLine("Força Máxima: ${result.maxForceN} N")
        sb.appendLine("Torque Máximo (Processado): ${result.peakTorqueKgfm} kgfm @ ${result.peakTorqueRpm} RPM")
        sb.appendLine("Potência Máxima (Processada): ${result.peakPowerCv} CV @ ${result.peakPowerRpm} RPM")
        sb.appendLine("--------------------------------------------")
        sb.appendLine("Amostras Brutas Registradas: ${result.totalSampleCount}")
        sb.appendLine("Pontos da Curva Processada: ${result.points.size}")
        sb.appendLine("Frequência Média de Leitura: %.1f Hz".format(result.avgSensorFrequencyHz))
        sb.appendLine("--------------------------------------------")
        sb.appendLine("AMOSTRAS (Tempo, Speed, RPM, a_Long_Bruta, a_Long_Filt, Força, Torque, Potência):")
        for (p in displayPoints) {
            sb.appendLine("%.2fs | %.1fkm/h | %drpm | a_Long: %.2fm/s² | Filt: %.2fm/s² | %.0fN | %.1fkgfm | %.1fCV".format(
                p.timeSeconds, p.speedKmh, p.rpm, p.accelRawMps2, p.accelFilteredMps2, p.forceN, p.torqueKgfm, p.powerCv
            ))
        }
        return sb.toString()
    }

    fun copyToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("DynoDiagnostic", buildDiagnosticReport())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Relatório copiado!", Toast.LENGTH_SHORT).show()
    }

    fun shareReport() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, buildDiagnosticReport())
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Compartilhar Diagnóstico")
        context.startActivity(shareIntent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(12.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp).testTag("diag_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DIAGNÓSTICO E TELEMETRIA",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Curva processada vs dados brutos e rejeição de ruído",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }

            IconButton(onClick = { copyToClipboard() }, modifier = Modifier.size(32.dp).testTag("copy_diagnostic_button")) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar", tint = DynoCyan, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { shareReport() }, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Compartilhar", tint = DynoGreen, modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // TOGGLE SELECTOR: [ CURVA PROCESSADA ] | [ DADOS BRUTOS ]
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarbonSurface)
                        .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (viewMode == DiagnosticViewMode.PROCESSED_CURVE) DynoCyan.copy(alpha = 0.25f) else Color.Transparent)
                            .border(
                                width = if (viewMode == DiagnosticViewMode.PROCESSED_CURVE) 1.dp else 0.dp,
                                color = if (viewMode == DiagnosticViewMode.PROCESSED_CURVE) DynoCyan else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { viewMode = DiagnosticViewMode.PROCESSED_CURVE }
                            .padding(vertical = 8.dp)
                            .testTag("toggle_processed_curve"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CURVA PROCESSADA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (viewMode == DiagnosticViewMode.PROCESSED_CURVE) DynoCyan else TextMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (viewMode == DiagnosticViewMode.RAW_DATA) DynoYellow.copy(alpha = 0.25f) else Color.Transparent)
                            .border(
                                width = if (viewMode == DiagnosticViewMode.RAW_DATA) 1.dp else 0.dp,
                                color = if (viewMode == DiagnosticViewMode.RAW_DATA) DynoYellow else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { viewMode = DiagnosticViewMode.RAW_DATA }
                            .padding(vertical = 8.dp)
                            .testTag("toggle_raw_data"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DADOS BRUTOS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (viewMode == DiagnosticViewMode.RAW_DATA) DynoYellow else TextMuted
                        )
                    }
                }
            }

            // DIAGNOSTIC CHART CANVAS
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarbonSurface)
                        .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (viewMode == DiagnosticViewMode.PROCESSED_CURVE) {
                            "CURVA PROCESSADA (Faixas 100 RPM + Spline Monotônica sem Overshoot)"
                        } else {
                            "DADOS BRUTOS DOS SENSORES (${rawPointsList.size} amostras originais)"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (viewMode == DiagnosticViewMode.PROCESSED_CURVE) DynoCyan else DynoYellow,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val activeResultForChart = if (viewMode == DiagnosticViewMode.PROCESSED_CURVE) {
                        result
                    } else {
                        result.copy(points = rawPointsList)
                    }

                    DynoChartCanvas(
                        result = activeResultForChart,
                        mode = ChartMode.POWER_AND_TORQUE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }

            // Quality Warning Banner if Poor
            if (isQualityPoor || !result.isValid) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(CarbonSurface)
                            .border(1.dp, DynoRed, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = DynoRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "QUALIDADE DA PASSADA: RUIM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = DynoRed
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Resultado não confiável. Recomendamos repetir a passada.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = TextPrimary
                        )
                        if (result.invalidReason.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Motivo: ${result.invalidReason}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Quality & Status Summary Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(CarbonSurface)
                        .border(1.dp, CarbonBorder, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "QUALIDADE DA PASSADA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = result.passQuality.label.uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = when (result.passQuality) {
                                PassQuality.EXCELENTE -> DynoGreen
                                PassQuality.BOA -> DynoCyan
                                PassQuality.REGULAR -> DynoYellow
                                PassQuality.INVALIDA -> DynoRed
                            }
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "STATUS DO CÁLCULO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (result.isValid) "VÁLIDA" else "INVÁLIDA",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (result.isValid) DynoGreen else DynoRed
                        )
                    }
                }
            }

            // SENSORS & ORIENTATION DIAGNOSTIC CARD
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(CarbonSurface)
                        .border(1.dp, CarbonBorder, RoundedCornerShape(4.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "DIAGNÓSTICO DOS SENSORES E ORIENTAÇÃO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = DynoGreen,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DiagParamRow(
                        "ORIENTAÇÃO",
                        if (isOrientationCalibrated) "Calibrada" else "Não calibrada",
                        if (isOrientationCalibrated) DynoGreen else DynoYellow
                    )
                    DiagParamRow(
                        "ESTABILIDADE DO CELULAR",
                        phoneStabilityText,
                        if (phoneStabilityText == "Boa") DynoGreen else DynoRed
                    )
                    DiagParamRow(
                        "ACELERAÇÃO LONGITUDINAL",
                        "%.2f m/s²".format(result.maxAccelMps2),
                        DynoCyan
                    )
                    DiagParamRow(
                        "GPS",
                        healthState.gpsStatus,
                        if (healthState.gpsStatus == "OK") DynoGreen else DynoYellow
                    )
                    DiagParamRow(
                        "AMOSTRAS REJEITADAS",
                        "${result.rejectedSampleCount} pontos",
                        if (result.rejectedSampleCount <= 3) DynoGreen else DynoAmber
                    )
                    DiagParamRow(
                        "QUALIDADE DO SINAL",
                        signalQualityLabel,
                        when (signalQualityLabel) {
                            "Boa" -> DynoGreen
                            "Regular" -> DynoYellow
                            else -> DynoRed
                        }
                    )
                }
            }

            // Data Parameters Box
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(CarbonSurface)
                        .border(1.dp, CarbonBorder, RoundedCornerShape(4.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "PARÂMETROS DA PASSADA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = DynoCyan,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DiagParamRow("Massa do Veículo + Ocupante", "${(result.vehicleSpec.weightKg + 100.0).toInt()} kg")
                    DiagParamRow("Medida do Pneu", "${result.vehicleSpec.tireWidthMm}/${result.vehicleSpec.tireAspect} R${result.vehicleSpec.rimInches}")
                    DiagParamRow("Relação da Marcha ($gearLabel)", "%.2f".format(result.vehicleSpec.gearRatios.getOrElse(result.selectedGear) { 1.32 }))
                    DiagParamRow("Relação do Diferencial", "%.2f".format(result.vehicleSpec.finalDrive))
                    DiagParamRow("Velocidade (Inicial -> Final)", "%.1f -> %.1f km/h".format(result.initialSpeedKmh, result.finalSpeedKmh))
                    DiagParamRow("RPM (Inicial -> Final)", "${result.initialRpm} -> ${result.finalRpm} RPM")
                    DiagParamRow("Aceleração Máx (Filtrada)", "%.2f m/s²".format(result.maxAccelMps2))
                    DiagParamRow("Força Máxima", "%.0f N".format(result.maxForceN))
                    DiagParamRow("Torque Máximo (Processado)", if (result.isValid) "%.1f kgfm @ %d RPM".format(result.peakTorqueKgfm, result.peakTorqueRpm) else "---")
                    DiagParamRow("Potência Máxima (Processada)", if (result.isValid) "%.1f CV @ %d RPM".format(result.peakPowerCv, result.peakPowerRpm) else "---")
                    DiagParamRow("Amostras Registradas", "${result.totalSampleCount} pontos")
                    DiagParamRow("Frequência Média", "%.1f Hz".format(result.avgSensorFrequencyHz))
                }
            }

            // Formulas Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(CarbonSurface)
                        .border(1.dp, CarbonBorder, RoundedCornerShape(4.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "FÓRMULAS & PROJEÇÃO LONGITUDINAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = DynoAmber,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FormulaBox("Eixo Longitudinal", "a_longitudinal = (a_linear) · u_frente_veiculo")
                    FormulaBox("RPM", "RPM = (v_m/s * R_total * 60) / (2 * π * r_pneu)")
                    FormulaBox("Agrupamento Robusto", "Bins 100 RPM + Mediana + Rejeição MAD")
                    FormulaBox("Força Longitudinal (N)", "F = m * a_long + 0.5*ρ*Cd*A*v² + Crr*m*g")
                    FormulaBox("Potência Processada (CV)", "P_CV = MonotoneSpline(Bins(P_robust))")
                    FormulaBox("Torque Físico Estrito (kgfm)", "Torque_kgfm = (Potência_CV * 716.2) / RPM")
                }
            }

            // Registered Samples Table Header
            item {
                Text(
                    text = if (viewMode == DiagnosticViewMode.PROCESSED_CURVE) {
                        "TABELA DE AMOSTRAGEM PROCESSADA (${displayPoints.size} PONTOS)"
                    } else {
                        "TABELA DE AMOSTRAS BRUTAS DOS SENSORES (${displayPoints.size} PONTOS)"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Header row for samples table
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(CarbonSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SampleHeaderCell("Tempo", Modifier.weight(0.8f))
                    SampleHeaderCell("Speed", Modifier.weight(0.9f))
                    SampleHeaderCell("RPM", Modifier.weight(0.9f))
                    SampleHeaderCell("a_Long", Modifier.weight(0.9f))
                    SampleHeaderCell("a_Filt", Modifier.weight(0.9f))
                    SampleHeaderCell("Torque", Modifier.weight(1.0f))
                    SampleHeaderCell("Potência", Modifier.weight(1.0f))
                }
            }

            itemsIndexed(displayPoints) { idx, p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SampleDataCell("%.1fs".format(p.timeSeconds), Modifier.weight(0.8f), TextMuted)
                    SampleDataCell("%.0fkm/h".format(p.speedKmh), Modifier.weight(0.9f), DynoYellow)
                    SampleDataCell("%d".format(p.rpm), Modifier.weight(0.9f), TextPrimary)
                    SampleDataCell("%.2f".format(p.accelRawMps2), Modifier.weight(0.9f), TextMuted)
                    SampleDataCell("%.2f".format(p.accelFilteredMps2), Modifier.weight(0.9f), DynoGreen)
                    SampleDataCell("%.1f".format(p.torqueKgfm), Modifier.weight(1.0f), DynoAmber)
                    SampleDataCell("%.1fCV".format(p.powerCv), Modifier.weight(1.0f), DynoCyan)
                }
                if (idx < displayPoints.size - 1) {
                    HorizontalDivider(color = CarbonBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun DiagParamRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
        Text(text = value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun FormulaBox(title: String, formula: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(CarbonDark)
            .border(0.5.dp, CarbonBorder, RoundedCornerShape(3.dp))
            .padding(6.dp)
    ) {
        Text(text = title, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = DynoAmber)
        Text(text = formula, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
    }
}

@Composable
private fun SampleHeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = TextMuted,
        modifier = modifier
    )
}

@Composable
private fun SampleDataCell(text: String, modifier: Modifier, color: Color) {
    Text(
        text = text,
        fontSize = 9.5.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = modifier
    )
}
