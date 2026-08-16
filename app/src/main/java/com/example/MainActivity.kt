package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.db.DynoDatabase
import com.example.db.DynoRepository
import com.example.sensor.DynoSensorManager
import com.example.ui.screens.CalculationDiagnosticScreen
import com.example.ui.screens.DynoResultScreen
import com.example.ui.screens.DynoTestScreen
import com.example.ui.screens.EditVehicleDialog
import com.example.ui.screens.LiveDynoScreen
import com.example.ui.screens.OrientationCalibrationScreen
import com.example.ui.screens.PassesHistoryScreen
import com.example.ui.screens.SupportContactScreen
import com.example.ui.screens.TestPreparationScreen
import com.example.ui.screens.VehicleSelectionDialog
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceVariant
import com.example.ui.theme.DynoCyan
import com.example.ui.theme.DynoGreen
import com.example.ui.theme.DynoMobileTheme
import com.example.ui.theme.DynoRed
import com.example.ui.theme.DynoYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DynoViewModel
import com.example.ui.viewmodel.ScreenState

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: DynoSensorManager
    private lateinit var repository: DynoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = DynoDatabase.getDatabase(applicationContext)
        sensorManager = DynoSensorManager(applicationContext)
        repository = DynoRepository(
            runDao = db.dynoRunDao(),
            vehicleDao = db.vehicleDao()
        )

        setContent {
            DynoMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CarbonDark
                ) {
                    val viewModel: DynoViewModel = viewModel {
                        DynoViewModel(
                            repository = repository,
                            sensorManager = sensorManager
                        )
                    }

                    LaunchedEffect(Unit) {
                        viewModel.initActiveVehicle(applicationContext)
                    }

                    DynoMobileApp(
                        viewModel = viewModel,
                        sensorManager = sensorManager,
                        repository = repository
                    )
                }
            }
        }
    }
}

private fun checkLocationPermissionGranted(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fine || coarse
}

@Composable
fun DynoMobileApp(
    viewModel: DynoViewModel,
    sensorManager: DynoSensorManager,
    repository: DynoRepository
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val vehicleSpec by viewModel.vehicleSpec.collectAsState()
    val allVehicles by viewModel.allVehicles.collectAsState()
    val editingVehicle by viewModel.editingVehicle.collectAsState()
    val selectedGearIndex by viewModel.selectedGearIndex.collectAsState()
    val dynoResult by viewModel.dynoResult.collectAsState()
    val showEditVehicleDialog by viewModel.showEditVehicleDialog.collectAsState()
    val showVehicleListDialog by viewModel.showVehicleListDialog.collectAsState()

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var pendingTestInitiationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Runtime Permission Launcher for Location Permissions (ACCESS_FINE_LOCATION & ACCESS_COARSE_LOCATION)
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        sensorManager.markPermissionRequested()
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        sensorManager.refreshHealthState()

        if (fineGranted || coarseGranted) {
            showPermissionRationaleDialog = false
            pendingTestInitiationAction?.invoke()
            pendingTestInitiationAction = null
        } else {
            showPermissionRationaleDialog = true
        }
    }

    val requestPermissionAndExecute = { onGranted: () -> Unit ->
        if (checkLocationPermissionGranted(context)) {
            onGranted()
        } else {
            pendingTestInitiationAction = onGranted
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Manage screen orientation declaratively based on user flow
    LaunchedEffect(currentScreen) {
        val activity = context as? Activity ?: return@LaunchedEffect
        when (currentScreen) {
            ScreenState.ORIENTATION_CALIBRATION, ScreenState.LIVE_DYNO, ScreenState.RESULT, ScreenState.DIAGNOSTIC -> {
                if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
            ScreenState.DASHBOARD, ScreenState.PREPARATION, ScreenState.HISTORY, ScreenState.SUPPORT -> {
                if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }
    }

    when (currentScreen) {
        ScreenState.DASHBOARD -> {
            DynoTestScreen(
                vehicleSpec = vehicleSpec,
                sensorManager = sensorManager,
                onNovaPassada = {
                    requestPermissionAndExecute {
                        viewModel.onNovaPassadaClicked()
                    }
                },
                onOpenHistory = {
                    viewModel.onOpenHistory()
                },
                onEditVehicle = {
                    viewModel.openEditVehicle(vehicleSpec)
                },
                onOpenGarage = {
                    viewModel.setShowVehicleListDialog(true)
                },
                onOpenSupport = {
                    viewModel.onOpenSupport()
                }
            )
        }

        ScreenState.PREPARATION -> {
            TestPreparationScreen(
                vehicleSpec = vehicleSpec,
                selectedGearIndex = selectedGearIndex,
                sensorManager = sensorManager,
                onSelectGear = { gear ->
                    viewModel.updateSelectedGear(gear)
                },
                onEditVehicle = {
                    viewModel.openEditVehicle(vehicleSpec)
                },
                onStartTest = {
                    requestPermissionAndExecute {
                        viewModel.onStartTestClicked()
                    }
                },
                onBack = {
                    viewModel.onBackToDashboard()
                }
            )
        }

        ScreenState.ORIENTATION_CALIBRATION -> {
            OrientationCalibrationScreen(
                sensorManager = sensorManager,
                onCalibrationSuccess = {
                    viewModel.onCalibrationSuccessStartLiveDyno()
                },
                onBack = {
                    viewModel.onBackToPreparation()
                }
            )
        }

        ScreenState.LIVE_DYNO -> {
            LiveDynoScreen(
                vehicleSpec = vehicleSpec,
                initialGearIndex = selectedGearIndex,
                sensorManager = sensorManager,
                repository = repository,
                onBack = {
                    viewModel.onBackToPreparation()
                },
                onRecalibrate = {
                    viewModel.onRecalibrateClicked()
                },
                onViewFullResult = { result ->
                    viewModel.onTestFinished(result)
                },
                onViewDiagnostic = { result ->
                    viewModel.onViewDiagnostic(result)
                }
            )
        }

        ScreenState.RESULT -> {
            dynoResult?.let { result ->
                DynoResultScreen(
                    result = result,
                    onBack = {
                        viewModel.onBackToDashboard()
                    },
                    onNovoTeste = {
                        viewModel.onBackToPreparation()
                    },
                    onOpenDiagnostic = {
                        viewModel.onViewDiagnostic(result)
                    }
                )
            }
        }

        ScreenState.DIAGNOSTIC -> {
            dynoResult?.let { result ->
                CalculationDiagnosticScreen(
                    result = result,
                    sensorManager = sensorManager,
                    onBack = {
                        viewModel.onBackFromDiagnostic()
                    }
                )
            }
        }

        ScreenState.HISTORY -> {
            PassesHistoryScreen(
                repository = repository,
                onBack = {
                    viewModel.onBackToDashboard()
                },
                onSelectRun = { runId ->
                    viewModel.onSelectHistoricalRun(runId)
                }
            )
        }

        ScreenState.SUPPORT -> {
            SupportContactScreen(
                vehicleSpec = vehicleSpec,
                lastResult = dynoResult,
                onBack = {
                    viewModel.onBackToDashboard()
                }
            )
        }
    }

    // LOCATION PERMISSION RATIONALE / REQUEST DIALOG
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = DynoRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PERMISSÃO DE LOCALIZAÇÃO",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "O Dyno Mobile precisa de acesso à localização (GPS de alta precisão) para:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(CarbonSurfaceVariant)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "• Medir a velocidade real do veículo em km/h.",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DynoCyan
                        )
                        Text(
                            text = "• Correlacionar a aceleração inercial com o deslocamento.",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DynoCyan
                        )
                        Text(
                            text = "• Filtrar ruídos de vibração mecânica para curvas precisas de CV e Torque.",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DynoCyan
                        )
                    }
                    Text(
                        text = "Por favor, autorize a permissão para iniciar a passada de teste.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
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
                    modifier = Modifier.testTag("dialog_grant_permission_button")
                ) {
                    Text(
                        text = "AUTORIZAR GPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("dialog_settings_button")
                    ) {
                        Text(
                            text = "CONFIGURAÇÕES",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DynoYellow
                        )
                    }
                    TextButton(
                        onClick = { showPermissionRationaleDialog = false },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "CANCELAR",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }
            },
            containerColor = CarbonSurface,
            shape = RoundedCornerShape(8.dp)
        )
    }

    if (showVehicleListDialog) {
        VehicleSelectionDialog(
            vehicles = allVehicles,
            activeVehicle = vehicleSpec,
            onSelectVehicle = { spec ->
                viewModel.selectVehicle(spec, context)
            },
            onEditVehicle = { spec ->
                viewModel.openEditVehicle(spec)
                viewModel.setShowVehicleListDialog(false)
            },
            onAddNewVehicle = {
                viewModel.openAddNewVehicle()
                viewModel.setShowVehicleListDialog(false)
            },
            onDeleteVehicle = { spec ->
                viewModel.deleteVehicle(spec, context)
            },
            onDismiss = {
                viewModel.setShowVehicleListDialog(false)
            }
        )
    }

    if (showEditVehicleDialog) {
        EditVehicleDialog(
            spec = editingVehicle,
            onDismiss = { viewModel.setShowEditVehicleDialog(false) },
            onSave = { updatedSpec ->
                viewModel.saveVehicle(updatedSpec, context)
            },
            onDelete = { specToDelete ->
                viewModel.deleteVehicle(specToDelete, context)
            }
        )
    }
}

