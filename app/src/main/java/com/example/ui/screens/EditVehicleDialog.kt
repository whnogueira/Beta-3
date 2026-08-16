package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import kotlin.math.PI

@Composable
fun EditVehicleDialog(
    spec: VehicleSpec,
    onDismiss: () -> Unit,
    onSave: (VehicleSpec) -> Unit,
    onDelete: ((VehicleSpec) -> Unit)? = null
) {
    var name by remember { mutableStateOf(spec.name) }
    var brand by remember { mutableStateOf(spec.brand) }
    var model by remember { mutableStateOf(spec.model) }
    var yearText by remember { mutableStateOf(if (spec.year > 0) spec.year.toString() else "") }
    var engine by remember { mutableStateOf(spec.engine) }
    var drive by remember { mutableStateOf(spec.drive) }
    var transmissionName by remember { mutableStateOf(spec.transmissionName) }

    var weightText by remember { mutableStateOf(if (spec.weightKg > 0.0) spec.weightKg.toInt().toString() else "") }
    var testWeightText by remember { mutableStateOf(if (spec.testWeightKg > 0.0) spec.testWeightKg.toInt().toString() else "") }

    var widthText by remember { mutableStateOf(spec.tireWidthMm.toString()) }
    var aspectText by remember { mutableStateOf(spec.tireAspect.toString()) }
    var rimText by remember { mutableStateOf(spec.rimInches.toString()) }
    var finalDriveText by remember { mutableStateOf(spec.finalDrive.toString()) }

    var gear1 by remember { mutableStateOf(spec.gearRatios.getOrElse(0) { 3.55 }.toString()) }
    var gear2 by remember { mutableStateOf(spec.gearRatios.getOrElse(1) { 1.95 }.toString()) }
    var gear3 by remember { mutableStateOf(spec.gearRatios.getOrElse(2) { 1.28 }.toString()) }
    var gear4 by remember { mutableStateOf(spec.gearRatios.getOrElse(3) { 0.89 }.toString()) }
    var gear5 by remember { mutableStateOf(spec.gearRatios.getOrElse(4) { 0.71 }.toString()) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isNewVehicle = spec.id == 0L && spec.name.isBlank()

    // Real-time tire calculations preview
    val pWidth = widthText.toIntOrNull() ?: 185
    val pAspect = aspectText.toIntOrNull() ?: 70
    val pRim = rimText.toIntOrNull() ?: 14
    val previewDiameterMm = (pRim * 25.4) + (2.0 * pWidth * (pAspect / 100.0))
    val previewCircumferenceM = (previewDiameterMm / 1000.0) * PI

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(CarbonSurface)
                .border(1.dp, CarbonBorder, RoundedCornerShape(6.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isNewVehicle) "ADICIONAR VEÍCULO" else "CONFIGURAÇÃO DO VEÍCULO",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Parâmetros físicos, pneus e relações de câmbio",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }

                if (spec.isExampleVehicle) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(DynoYellow.copy(alpha = 0.2f))
                            .border(0.5.dp, DynoYellow.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "EXEMPLO",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = DynoYellow
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Nome principal
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = { Text("Nome / Modelo do Veículo *") },
                placeholder = { Text("Ex: Vectra 2.2 8V 1999 — Exemplo") },
                colors = outlinedColors(),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().testTag("edit_vehicle_name_field")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Marca e Modelo
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marca") },
                    placeholder = { Text("Chevrolet") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Modelo") },
                    placeholder = { Text("Vectra") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ano, Motor, Tração
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it },
                    label = { Text("Ano") },
                    placeholder = { Text("1999") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(0.7f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = engine,
                    onValueChange = { engine = it },
                    label = { Text("Motor") },
                    placeholder = { Text("2.2 8V") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1.3f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Câmbio & Tração
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = transmissionName,
                    onValueChange = { transmissionName = it },
                    label = { Text("Câmbio") },
                    placeholder = { Text("F17 CCW") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = drive,
                    onValueChange = { drive = it },
                    label = { Text("Tração") },
                    placeholder = { Text("Dianteira (FWD)") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "PESO DO VEÍCULO E TESTE",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = DynoGreen,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it
                        errorMessage = null
                    },
                    label = { Text("Peso Veículo (kg) *") },
                    placeholder = { Text("1359") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).testTag("edit_vehicle_weight_field")
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = testWeightText,
                    onValueChange = { testWeightText = it },
                    label = { Text("Peso Padrão Teste (kg)") },
                    placeholder = { Text("1380") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).testTag("edit_vehicle_test_weight_field")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "DIMENSÕES DO PNEU",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = DynoCyan,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it },
                    label = { Text("Largura (mm)") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = aspectText,
                    onValueChange = { aspectText = it },
                    label = { Text("Perfil (%)") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = rimText,
                    onValueChange = { rimText = it },
                    label = { Text("Aro (pol)") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            // Calculation banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(CarbonSurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Diâmetro: %.1f mm".format(previewDiameterMm),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DynoCyan
                )
                Text(
                    text = "Circunferência: %.3f m".format(previewCircumferenceM),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DynoYellow
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "RELAÇÕES DE TRANSMISSÃO",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = DynoAmber,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = gear1,
                    onValueChange = { gear1 = it },
                    label = { Text("1ª") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = gear2,
                    onValueChange = { gear2 = it },
                    label = { Text("2ª") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = gear3,
                    onValueChange = { gear3 = it },
                    label = { Text("3ª") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = gear4,
                    onValueChange = { gear4 = it },
                    label = { Text("4ª") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = gear5,
                    onValueChange = { gear5 = it },
                    label = { Text("5ª") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = finalDriveText,
                    onValueChange = { finalDriveText = it },
                    label = { Text("Diferencial") },
                    colors = outlinedColors(),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = DynoRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CarbonBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("CANCELAR", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onDelete != null && spec.id > 0L) {
                        OutlinedButton(
                            onClick = { onDelete(spec) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, DynoRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(36.dp).testTag("dialog_delete_vehicle_button")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = DynoRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EXCLUIR", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DynoRed, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            val parsedWeight = weightText.toDoubleOrNull()
                            val parsedTestWeight = testWeightText.toDoubleOrNull() ?: 0.0

                            if (trimmedName.isBlank()) {
                                errorMessage = "Informe o nome/modelo do veículo."
                                return@Button
                            }
                            if (parsedWeight == null || parsedWeight <= 0.0) {
                                errorMessage = "Informe uma massa (peso em kg) válida."
                                return@Button
                            }

                            val updated = spec.copy(
                                name = trimmedName,
                                brand = brand.trim(),
                                model = model.trim(),
                                year = yearText.toIntOrNull() ?: 0,
                                engine = engine.trim(),
                                drive = drive.trim().ifBlank { "Dianteira (FWD)" },
                                transmissionName = transmissionName.trim(),
                                weightKg = parsedWeight,
                                testWeightKg = parsedTestWeight,
                                tireWidthMm = widthText.toIntOrNull() ?: 185,
                                tireAspect = aspectText.toIntOrNull() ?: 70,
                                rimInches = rimText.toIntOrNull() ?: 14,
                                finalDrive = finalDriveText.toDoubleOrNull() ?: 3.74,
                                gearRatios = listOf(
                                    gear1.toDoubleOrNull() ?: 3.55,
                                    gear2.toDoubleOrNull() ?: 1.95,
                                    gear3.toDoubleOrNull() ?: 1.28,
                                    gear4.toDoubleOrNull() ?: 0.89,
                                    gear5.toDoubleOrNull() ?: 0.71
                                )
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(36.dp).testTag("save_vehicle_button")
                    ) {
                        Text("SALVAR", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DynoRed,
    unfocusedBorderColor = CarbonBorder,
    focusedLabelColor = DynoRed,
    unfocusedLabelColor = TextMuted,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = DynoRed
)
