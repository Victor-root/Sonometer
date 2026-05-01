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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.djmeter.R
import com.example.djmeter.ui.theme.SonoRed

/**
 * Bottom row: Export — Play/Pause (large red) — Reset.
 * Buttons that have no real backend (chart screen, weighting) have been
 * removed to keep the bar tight and avoid dead controls.
 */
@Composable
fun BottomControlBar(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    onExport: () -> Unit,
    onToggleRecording: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp,
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
