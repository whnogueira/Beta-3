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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.db.DynoRepository
import com.example.db.DynoRunEntity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PassesHistoryScreen(
    repository: DynoRepository,
    onBack: () -> Unit,
    onSelectRun: (Long) -> Unit
) {
    val runs by repository.last10Runs.collectAsState(initial = emptyList())
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp).testTag("history_back_button")
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
                    text = "HISTÓRICO DE TELEMETRIA",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Registros de passadas locais (${runs.size}/10)",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }
        }

        if (runs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "NENHUMA PASSADA REGISTRADA",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Inicie uma nova passada para gravar gráficos e telemetria.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(runs, key = { it.id }) { run ->
                    PassHistoryRow(
                        run = run,
                        dateFormat = dateFormat,
                        onClick = { onSelectRun(run.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PassHistoryRow(
    run: DynoRunEntity,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val dateStr = dateFormat.format(Date(run.timestampMs))
    val gearLabels = listOf("1ª", "2ª", "3ª", "4ª", "5ª")
    val gearStr = gearLabels.getOrElse(run.selectedGear) { "3ª" }
    val isRealTest = run.dataSource == "REAL_TEST_DATA"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(CarbonSurface)
            .border(1.dp, if (isRealTest) DynoGreen.copy(alpha = 0.35f) else CarbonBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(10.dp)
            .testTag("history_item_${run.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = run.vehicleName.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• $gearStr MARCHA",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isRealTest) DynoGreen.copy(alpha = 0.2f) else DynoYellow.copy(alpha = 0.2f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (isRealTest) "REAL" else "DEMO",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isRealTest) DynoGreen else DynoYellow,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Dense Telemetry Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(CarbonSurfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("POTÊNCIA", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                Text("%.1f CV".format(run.peakPowerCv), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = DynoCyan)
            }

            Column {
                Text("TORQUE", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                Text("%.1f kgfm".format(run.peakTorqueKgfm), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = DynoAmber)
            }

            Column {
                Text("VEL. MÁX", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                Text("%.0f km/h".format(run.maxSpeedKmh), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = DynoYellow)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("REGISTRO", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                Text(dateStr, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
