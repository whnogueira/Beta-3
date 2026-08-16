package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DynoResult
import com.example.model.VehicleSpec
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

private const val SUPPORT_EMAIL = "suporte.dynomobile@gmail.com"
private const val APP_VERSION = "DynoMobile Beta v1.0.0"

@Composable
fun SupportContactScreen(
    vehicleSpec: VehicleSpec,
    lastResult: DynoResult? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var problemDescription by remember { mutableStateOf("") }
    var selectedProblemType by remember { mutableStateOf("Dúvida / Funcionamento Geral") }

    val problemTypes = listOf(
        "Dúvida / Funcionamento Geral",
        "Precisão do Dinamômetro",
        "Ruído / Picos nos Gráficos",
        "Problemas com Sensores / GPS",
        "Sugestão de Melhoria"
    )

    fun sendSupportEmail() {
        val vehicleInfo = if (vehicleSpec.isConfigured) {
            "${vehicleSpec.name} (${vehicleSpec.weightKg.toInt()} kg, Pneu ${vehicleSpec.tireWidthMm}/${vehicleSpec.tireAspect} R${vehicleSpec.rimInches})"
        } else {
            "Nenhum veículo cadastrado"
        }

        val telemetryDetails = if (lastResult != null && lastResult.isValid) {
            """
            - Marcha: ${lastResult.selectedGear + 1}ª
            - Potência Estimada: ${lastResult.peakPowerCv} CV @ ${lastResult.peakPowerRpm} RPM
            - Torque Estimado: ${lastResult.peakTorqueKgfm} kgfm @ ${lastResult.peakTorqueRpm} RPM
            - Velocidade Máx: ${lastResult.maxSpeedKmh} km/h
            - Duração da Passada: ${lastResult.points.lastOrNull()?.timeSeconds ?: 0.0} s
            - Qualidade da Passada: ${lastResult.passQuality.label}
            """.trimIndent()
        } else if (lastResult != null && !lastResult.isValid) {
            """
            - Marcha: ${lastResult.selectedGear + 1}ª
            - Status: Passada Inválida (${lastResult.invalidReason})
            - Amostras Registradas: ${lastResult.totalSampleCount} (Rejeitadas: ${lastResult.rejectedSampleCount})
            """.trimIndent()
        } else {
            "- Telemetria de última passada: Nenhuma passada recente"
        }

        val bodyText = """
Olá Suporte DynoMobile Beta,

[Descreva seu problema, dúvida ou feedback abaixo]:
${if (problemDescription.isNotBlank()) problemDescription else "(Digite sua mensagem aqui...)"}

----------------------------------------
INFORMAÇÕES DE DIAGNÓSTICO DO DISPOSITIVO
- Aplicativo: $APP_VERSION
- Fabricante: ${Build.MANUFACTURER}
- Modelo do Aparelho: ${Build.MODEL}
- Versão do Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
- Tipo do Problema: $selectedProblemType
- Veículo Cadastrado: $vehicleInfo
$telemetryDetails
----------------------------------------
*O usuário revisa e envia este e-mail manualmente. Nenhuma informação é transmitida automaticamente.*
        """.trimIndent()

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "Suporte DynoMobile Beta - $selectedProblemType")
            putExtra(Intent.EXTRA_TEXT, bodyText)
        }

        try {
            context.startActivity(Intent.createChooser(emailIntent, "Enviar e-mail de suporte"))
        } catch (e: Exception) {
            Toast.makeText(context, "Nenhum aplicativo de e-mail encontrado no dispositivo.", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(14.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp).testTag("support_back_button")
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
                    text = "AJUDA E SUPORTE",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Canal oficial DynoMobile Beta",
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Official Support Channel Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonSurface)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Help,
                        contentDescription = null,
                        tint = DynoCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SUPORTE DYNOMOBILE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = DynoCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "E-mail oficial de suporte:",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                Text(
                    text = SUPPORT_EMAIL,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { sendSupportEmail() },
                    colors = ButtonDefaults.buttonColors(containerColor = DynoCyan),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("enviar_email_suporte_button")
                ) {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ENVIAR E-MAIL PARA O SUPORTE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }

            // Report Issue / Feedback Form Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonSurface)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = null,
                        tint = DynoYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RELATAR PROBLEMA OU FEEDBACK",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = DynoYellow
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Tipo do problema:",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Problem Type Selector Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    problemTypes.forEach { type ->
                        val isSelected = selectedProblemType == type
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) CarbonSurfaceVariant else CarbonDark)
                                .border(1.dp, if (isSelected) DynoYellow else CarbonBorder, RoundedCornerShape(4.dp))
                                .clickable { selectedProblemType = type }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = type,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) DynoYellow else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Descrição detalhada (opcional):",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = problemDescription,
                    onValueChange = { problemDescription = it },
                    placeholder = { Text("Descreva o que ocorreu ou sua sugestão...", fontSize = 11.sp) },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DynoYellow,
                        unfocusedBorderColor = CarbonBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = DynoYellow
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().testTag("problem_description_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { sendSupportEmail() },
                    colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("relatar_problema_button")
                ) {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RELATAR PROBLEMA", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "O aplicativo abrirá seu cliente de e-mail com as informações de diagnóstico pré-preenchidas para sua revisão antes do envio.",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = TextMuted,
                    lineHeight = 13.sp
                )
            }

            // Beta Disclaimer & Offline Privacy Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonSurface)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = DynoGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AVISO DE VERSÃO BETA & PRIVACIDADE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = DynoGreen
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "O DynoMobile é um software em fase BETA destinado a medições estimadas através dos sensores inerciais e de posicionamento do smartphone. Os valores de potência (CV) e torque (kgfm) são aproximações matemáticas calculadas a partir da física de dinâmica veicular.",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Privacidade: Todo o processamento e histórico são mantidos 100% locais no dispositivo offline. Nenhuma informação é enviada pela internet automaticamente.",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = TextMuted,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
