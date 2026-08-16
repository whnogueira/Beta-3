package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun VehicleSelectionDialog(
    vehicles: List<VehicleSpec>,
    activeVehicle: VehicleSpec,
    onSelectVehicle: (VehicleSpec) -> Unit,
    onEditVehicle: (VehicleSpec) -> Unit,
    onAddNewVehicle: () -> Unit,
    onDeleteVehicle: (VehicleSpec) -> Unit,
    onDismiss: () -> Unit
) {
    var vehicleToDelete by remember { mutableStateOf<VehicleSpec?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CarbonSurface)
                .border(1.dp, CarbonBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GARAGEM DE VEÍCULOS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Selecione ou gerencie seus veículos cadastrados",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (vehicles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarbonDark)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Nenhum veículo cadastrado",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vehicles, key = { it.id }) { vehicle ->
                        val isActive = (vehicle.id == activeVehicle.id && vehicle.id > 0) ||
                            (vehicle.name == activeVehicle.name && activeVehicle.id == 0L)

                        VehicleListItemCard(
                            vehicle = vehicle,
                            isActive = isActive,
                            onSelect = {
                                onSelectVehicle(vehicle)
                                onDismiss()
                            },
                            onEdit = {
                                onEditVehicle(vehicle)
                            },
                            onDelete = {
                                vehicleToDelete = vehicle
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, CarbonBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("FECHAR", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                }

                Button(
                    onClick = onAddNewVehicle,
                    colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(36.dp).testTag("dialog_add_new_vehicle_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("NOVO VEÍCULO", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (vehicleToDelete != null) {
        val target = vehicleToDelete!!
        AlertDialog(
            onDismissRequest = { vehicleToDelete = null },
            title = {
                Text(
                    text = "Excluir Veículo?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Deseja remover '${target.name}' da garagem?\nEsta ação não poderá ser desfeita.",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVehicle(target)
                        vehicleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("EXCLUIR", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vehicleToDelete = null }) {
                    Text("CANCELAR", fontFamily = FontFamily.Monospace, color = TextSecondary)
                }
            },
            containerColor = CarbonSurface,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun VehicleListItemCard(
    vehicle: VehicleSpec,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) CarbonSurfaceVariant else CarbonDark)
            .border(
                1.dp,
                if (isActive) DynoCyan.copy(alpha = 0.8f) else CarbonBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable { onSelect() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = if (isActive) DynoCyan else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = vehicle.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = TextPrimary
                        )

                        if (vehicle.isExampleVehicle) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(DynoYellow.copy(alpha = 0.2f))
                                    .border(0.5.dp, DynoYellow.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
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

                        if (isActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(DynoGreen.copy(alpha = 0.2f))
                                    .border(0.5.dp, DynoGreen.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "ATIVO",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = DynoGreen
                                )
                            }
                        }
                    }

                    if (vehicle.engine.isNotBlank() || vehicle.transmissionName.isNotBlank()) {
                        Text(
                            text = listOfNotNull(
                                vehicle.engine.takeIf { it.isNotBlank() },
                                vehicle.transmissionName.takeIf { it.isNotBlank() }
                            ).joinToString(" • "),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Quick Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp).testTag("edit_vehicle_item_${vehicle.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp).testTag("delete_vehicle_item_${vehicle.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = DynoRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Technical Specs Mini Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(CarbonDark.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Massa: ${vehicle.weightKg.toInt()} kg (Teste: ${vehicle.effectiveTestMassKg.toInt()} kg)",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
            Text(
                text = "Pneu: ${vehicle.tireWidthMm}/${vehicle.tireAspect} R${vehicle.rimInches}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
            Text(
                text = "Dif: %.2f".format(vehicle.finalDrive),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = DynoAmber
            )
        }
    }
}
