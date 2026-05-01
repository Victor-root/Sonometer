package com.example.djmeter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.djmeter.R

/**
 * Compact, collapsible card with manual calibration controls:
 *  - ±0.5 dB / ±1 dB nudges
 *  - reset to 0 dB
 *  - "align" mode: type the value of a real sound meter and apply the delta.
 *
 * Collapsed by default so the existing layout is preserved.
 */
@Composable
fun CalibrationCard(
    correctionDb: Float,
    onAdjust: (Float) -> Unit,
    onReset: () -> Unit,
    onAlign: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var referenceText by remember { mutableStateOf("") }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.calibration_title),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${stringResource(R.string.calibration_correction)} : " +
                            stringResource(R.string.calibration_value_format, correctionDb),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AdjustPill("-1", onClick = { onAdjust(-1f) }, modifier = Modifier.weight(1f))
                    AdjustPill("-0.5", onClick = { onAdjust(-0.5f) }, modifier = Modifier.weight(1f))
                    AdjustPill("+0.5", onClick = { onAdjust(0.5f) }, modifier = Modifier.weight(1f))
                    AdjustPill("+1", onClick = { onAdjust(1f) }, modifier = Modifier.weight(1f))
                    AdjustPill(
                        stringResource(R.string.calibration_reset),
                        onClick = onReset,
                        modifier = Modifier.weight(1.2f),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.calibration_align_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = referenceText,
                        onValueChange = { input ->
                            referenceText = input.filter { it.isDigit() || it == '.' || it == ',' }
                                .replace(',', '.')
                        },
                        label = { Text(stringResource(R.string.calibration_reference_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            referenceText.toFloatOrNull()?.let { value ->
                                onAlign(value)
                                referenceText = ""
                            }
                        },
                    ) {
                        Text(stringResource(R.string.calibration_align_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
