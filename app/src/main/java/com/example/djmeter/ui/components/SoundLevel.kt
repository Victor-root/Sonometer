package com.example.djmeter.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.djmeter.R
import com.example.djmeter.ui.theme.SonoBlue
import com.example.djmeter.ui.theme.SonoGreen
import com.example.djmeter.ui.theme.SonoOrange
import com.example.djmeter.ui.theme.SonoRed
import com.example.djmeter.ui.theme.SonoRedDim
import com.example.djmeter.ui.theme.SonoYellow

/** Maps a dB value to a level bucket (label + color). */
enum class SoundLevel(
    val maxDb: Int,
    @StringRes val labelRes: Int,
    val color: Color,
) {
    Library(30, R.string.level_library, SonoBlue),
    Quiet(50, R.string.level_quiet, SonoGreen),
    Conversation(70, R.string.level_conversation, SonoYellow),
    Loud(85, R.string.level_loud, SonoOrange),
    VeryLoud(100, R.string.level_very_loud, SonoRed),
    Dangerous(120, R.string.level_dangerous, SonoRedDim);

    companion object {
        fun fromDb(db: Float): SoundLevel =
            entries.firstOrNull { db <= it.maxDb } ?: Dangerous
    }
}

/** dB range used by the gauge and chart. */
const val DB_MIN = 20f
const val DB_MAX = 120f

/** Threshold above which the gauge / chart turn red-orange. */
const val DB_HOT_THRESHOLD = 90f
