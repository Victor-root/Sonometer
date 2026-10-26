package app.sonometer.vroot.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sonometer.vroot.R
import app.sonometer.vroot.ui.components.BottomControlBar
import app.sonometer.vroot.ui.components.CalibrationCard
import app.sonometer.vroot.ui.components.DbGauge
import app.sonometer.vroot.ui.components.DbHistoryChart
import app.sonometer.vroot.ui.components.DbLevelColorBar
import app.sonometer.vroot.ui.components.DbLevelDescriptor
import app.sonometer.vroot.ui.components.DbStatsRow
import app.sonometer.vroot.ui.components.DbValueDisplay
import app.sonometer.vroot.ui.components.DB_MIN
import app.sonometer.vroot.ui.components.MeterModeSelector
import app.sonometer.vroot.utils.FrequencyWeighting
import app.sonometer.vroot.utils.TimeWeighting
import app.sonometer.vroot.viewmodels.MainViewModel
import app.sonometer.vroot.viewmodels.MeterUiState

@Composable
fun SonometerScreen(
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val exportLabel = stringResource(R.string.action_export)

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) viewModel.startRecording()
    }

    // Auto-start: as soon as we know the permission is granted, start measuring.
    // If the permission isn't granted yet, request it once on first composition.
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            viewModel.startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (!permissionGranted) {
            PermissionPrompt(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(systemBarsPadding),
                onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
        } else {
            SonometerContent(
                state = state,
                topPadding = statusBarsPadding,
                bottomPadding = systemBarsPadding,
                onToggle = viewModel::toggleRecording,
                onReset = viewModel::resetMeasurement,
                onFrequencyWeighting = viewModel::setFrequencyWeighting,
                onTimeWeighting = viewModel::setTimeWeighting,
                onCalibrationAdjust = viewModel::adjustCalibration,
                onCalibrationReset = viewModel::resetCalibration,
                onCalibrationAlign = viewModel::alignCalibrationToReference,
                onExport = {
                    val pdf = viewModel.exportGraphToPdf(context)
                    val msg = if (pdf != null) "PDF: $pdf" else exportLabel
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun SonometerContent(
    state: MeterUiState,
    topPadding: PaddingValues,
    bottomPadding: PaddingValues,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onFrequencyWeighting: (FrequencyWeighting) -> Unit,
    onTimeWeighting: (TimeWeighting) -> Unit,
    onCalibrationAdjust: (Float) -> Unit,
    onCalibrationReset: () -> Unit,
    onCalibrationAlign: (Float) -> Unit,
    onExport: () -> Unit,
) {
    val level = state.levelDb?.takeUnless { state.signalLost }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding.calculateTopPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        DbGauge(
            decibel = level ?: DB_MIN,
            minDb = state.minDb,
            peakDb = state.maxDb,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        DbValueDisplay(
            decibel = level,
            weighting = state.frequencyWeighting,
            elapsedSeconds = state.elapsedSeconds,
            overload = state.overload,
            overloadLatched = state.overloadLatched,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(8.dp))

        MeterModeSelector(
            frequencyWeighting = state.frequencyWeighting,
            timeWeighting = state.timeWeighting,
            onFrequencyWeighting = onFrequencyWeighting,
            onTimeWeighting = onTimeWeighting,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(10.dp))

        DbStatsRow(
            leqDb = state.leqDb,
            minDb = state.minDb,
            maxDb = state.maxDb,
            peakDb = state.peakDb,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(10.dp))

        CalibrationCard(
            correctionDb = state.calibrationDb,
            onAdjust = onCalibrationAdjust,
            onReset = onCalibrationReset,
            onAlign = onCalibrationAlign,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(10.dp))

        when {
            state.micUnavailable -> StatusMessage(stringResource(R.string.status_mic_unavailable))
            state.signalLost -> StatusMessage(stringResource(R.string.status_signal_lost))
            level != null -> DbLevelDescriptor(decibel = level)
            else -> StatusMessage(stringResource(R.string.status_settling))
        }

        Spacer(Modifier.height(12.dp))

        DbLevelColorBar(
            decibel = level ?: DB_MIN,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        DbHistoryChart(
            readings = state.history,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        BottomControlBar(
            isRecording = state.isRecording,
            onExport = onExport,
            onToggleRecording = onToggle,
            onReset = onReset,
        )

        Spacer(
            Modifier.height(
                bottomPadding.calculateBottomPadding().coerceAtLeast(8.dp)
            )
        )
    }
}

@Composable
private fun StatusMessage(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun PermissionPrompt(
    modifier: Modifier = Modifier,
    onRequest: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.microphone_required),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.microphone_permission_needed),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}
