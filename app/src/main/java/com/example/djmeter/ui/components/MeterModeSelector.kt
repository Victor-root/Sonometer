package com.example.djmeter.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.djmeter.R
import com.example.djmeter.ui.theme.SonoRed
import com.example.djmeter.utils.FrequencyWeighting
import com.example.djmeter.utils.TimeWeighting

/** The two switches of a sound level meter: frequency (A/C/Z) and time (F/S/I) weighting. */
@Composable
fun MeterModeSelector(
    frequencyWeighting: FrequencyWeighting,
    timeWeighting: TimeWeighting,
    onFrequencyWeighting: (FrequencyWeighting) -> Unit,
    onTimeWeighting: (TimeWeighting) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Segmented(
            options = FrequencyWeighting.entries,
            selected = frequencyWeighting,
            label = { it.name },
            groupDescription = stringResource(R.string.cd_frequency_weighting),
            onSelect = onFrequencyWeighting,
        )
        Segmented(
            options = TimeWeighting.entries,
            selected = timeWeighting,
            label = { stringResource(it.labelRes) },
            groupDescription = stringResource(R.string.cd_time_weighting),
            onSelect = onTimeWeighting,
        )
    }
}

@get:StringRes
private val TimeWeighting.labelRes: Int
    get() = when (this) {
        TimeWeighting.FAST -> R.string.weighting_fast
        TimeWeighting.SLOW -> R.string.weighting_slow
        TimeWeighting.IMPULSE -> R.string.weighting_impulse
    }

@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    groupDescription: String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = groupDescription }
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                text = label(option),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) SonoRed else MaterialTheme.colorScheme.surfaceVariant)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}
