package com.example.djmeter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.djmeter.R
import com.example.djmeter.ui.theme.SonoRed

@Composable
fun BottomControlBar(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    onChart: () -> Unit,
    onExport: () -> Unit,
    onToggleRecording: () -> Unit,
    onReset: () -> Unit,
    onWeighting: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularControlButton(
            icon = Icons.Outlined.ShowChart,
            contentDescription = stringResource(R.string.cd_button_chart),
            onClick = onChart,
        )
        CircularControlButton(
            icon = Icons.Outlined.FileDownload,
            contentDescription = stringResource(R.string.cd_button_export),
            onClick = onExport,
        )
        CircularControlButton(
            icon = if (isRecording) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.cd_main_button),
            onClick = onToggleRecording,
            backgroundColor = SonoRed,
            iconColor = Color.White,
            size = 72.dp,
            iconSize = 32.dp,
        )
        CircularControlButton(
            icon = Icons.Outlined.Refresh,
            contentDescription = stringResource(R.string.cd_button_reset),
            onClick = onReset,
        )
        WeightingButton(onClick = onWeighting)
    }
}

@Composable
fun CircularControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    iconColor: Color = MaterialTheme.colorScheme.onBackground,
    size: Dp = 52.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun WeightingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cd = stringResource(R.string.cd_button_weighting)
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "A",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}
