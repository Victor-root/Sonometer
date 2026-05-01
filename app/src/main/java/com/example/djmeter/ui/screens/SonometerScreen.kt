package com.example.djmeter.ui.screens

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
import com.example.djmeter.R
import com.example.djmeter.ui.components.BottomControlBar
import com.example.djmeter.ui.components.DbGauge
import com.example.djmeter.ui.components.DbHistoryChart
import com.example.djmeter.ui.components.DbLevelColorBar
import com.example.djmeter.ui.components.DbLevelDescriptor
import com.example.djmeter.ui.components.DbStatsRow
import com.example.djmeter.ui.components.DbValueDisplay
import com.example.djmeter.viewmodels.MainViewModel

@Composable
fun SonometerScreen(
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val decibel by viewModel.decibelLevel.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val readings by viewModel.decibelReadings.collectAsState()
    val recordingTime by viewModel.recordingTime.collectAsState()
    val minDb by viewModel.minDb.collectAsState()
    val avgDb by viewModel.avgDb.collectAsState()
    val maxDb by viewModel.maxDb.collectAsState()

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
            if (!viewModel.isRecording.value) viewModel.startRecording()
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
                decibel = decibel,
                isRecording = isRecording,
                readings = readings,
                recordingTime = recordingTime,
                minDb = minDb,
                avgDb = avgDb,
                maxDb = maxDb,
                topPadding = statusBarsPadding,
                bottomPadding = systemBarsPadding,
                onToggle = { viewModel.toggleRecording() },
                onReset = { viewModel.resetMeasurement() },
                onExport = {
                    val pdf = viewModel.exportGraphToPdf(context)
                    val msg = if (pdf != null) "PDF: $pdf"
                    else context.getString(R.string.action_export)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun SonometerContent(
    decibel: Float,
    isRecording: Boolean,
    readings: List<Float>,
    recordingTime: Long,
    minDb: Float?,
    avgDb: Float?,
    maxDb: Float?,
    topPadding: PaddingValues,
    bottomPadding: PaddingValues,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding.calculateTopPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        DbGauge(
            decibel = decibel,
            minDb = minDb,
            peakDb = maxDb,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        DbValueDisplay(
            decibel = decibel,
            elapsedSeconds = recordingTime,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(8.dp))

        DbStatsRow(
            minDb = minDb,
            avgDb = avgDb,
            maxDb = maxDb,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(Modifier.height(12.dp))

        DbLevelDescriptor(decibel = decibel)

        Spacer(Modifier.height(12.dp))

        DbLevelColorBar(
            decibel = decibel,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        DbHistoryChart(
            readings = readings,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        BottomControlBar(
            isRecording = isRecording,
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
