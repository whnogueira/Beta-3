package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sensor.DynoSensorManager
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceVariant
import com.example.ui.theme.DynoCyan
import com.example.ui.theme.DynoGreen
import com.example.ui.theme.DynoRed
import com.example.ui.theme.DynoYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PreTestSensorStatusCard(
    sensorManager: DynoSensorManager,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val healthState by sensorManager.healthState.collectAsState()

    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sensorManager.refreshHealthState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        sensorManager.markPermissionRequested()
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        sensorManager.refreshHealthState()

        if (!fineGranted && !coarseGranted) {
            permissionDeniedMessage = "Permissão de localização necessária para telemetria."
        } else {
            permissionDeniedMessage = null
        }
    }

    val isReady = healthState.isSystemReady

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CarbonSurface)
            .border(1.dp, if (isReady) DynoGreen.copy(alpha = 0.4f) else CarbonBorder, RoundedCornerShape(6.dp))
            .padding(12.dp)
            .testTag("sensor_health_card")
    ) {
        // Status Bar Top
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isReady) DynoGreen else DynoYellow)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isReady) "SISTEMA DE TELEMETRIA PRONTO" else "TELEMETRIA DE SENSORES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp,
                    color = if (isReady) DynoGreen else DynoYellow
                )
            }

            Text(
                text = if (isReady) "READY" else "CHECK",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isReady) DynoGreen else DynoYellow
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3 Sensor Status Pills / Columns
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SensorChip(
                name = "GPS",
                status = healthState.gpsStatus,
                isOk = healthState.hasLocationPermission && healthState.isGpsEnabled,
                modifier = Modifier.weight(1f)
            )
            SensorChip(
                name = "ACCEL",
                status = healthState.accelerometerStatus,
                isOk = healthState.isAccelerometerAvailable,
                modifier = Modifier.weight(1f)
            )
            SensorChip(
                name = "GYRO",
                status = healthState.gyroscopeStatus,
                isOk = healthState.isGyroscopeAvailable,
                modifier = Modifier.weight(1f)
            )
        }

        // Action needed if permission / GPS missing
        if (!healthState.hasLocationPermission) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DynoRed),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("permitir_localizacao_button")
                ) {
                    Text(
                        text = "AUTORIZAR GPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }

                if (permissionDeniedMessage != null || healthState.permissionRequestedOnce) {
                    OutlinedButton(
                        onClick = { openAppSettings(context) },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("abrir_configuracoes_button")
                    ) {
                        Text(
                            text = "CONFIGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = DynoYellow
                        )
                    }
                }
            }
        } else if (!healthState.isGpsEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { openLocationSettings(context) },
                colors = ButtonDefaults.buttonColors(containerColor = DynoYellow),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("ativar_localizacao_button")
            ) {
                Text(
                    text = "LIGAR LOCALIZAÇÃO (GPS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun SensorChip(
    name: String,
    status: String,
    isOk: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor = if (isOk) DynoGreen else DynoYellow

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(CarbonSurfaceVariant)
            .border(1.dp, if (isOk) DynoGreen.copy(alpha = 0.25f) else CarbonBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }

        Text(
            text = if (isOk) "OK" else status.uppercase(),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
    }
}

fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // ignore
    }
}

fun openLocationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // ignore
    }
}
