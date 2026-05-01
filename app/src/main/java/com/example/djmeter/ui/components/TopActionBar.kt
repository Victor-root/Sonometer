package com.example.djmeter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.djmeter.R

@Composable
fun TopActionBar(
    modifier: Modifier = Modifier,
    onReset: () -> Unit,
    onCalibrate: () -> Unit,
    onWeighting: () -> Unit,
    onMenu: () -> Unit,
    canReset: Boolean = true,
) {
    val tint = MaterialTheme.colorScheme.onBackground
    val mutedTint = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onReset, enabled = canReset) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.action_reset),
                tint = if (canReset) tint else mutedTint,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onCalibrate) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.action_calibration),
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onWeighting) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.action_weighting_a),
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.action_settings),
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
