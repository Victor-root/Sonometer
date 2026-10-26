package app.sonometer.vroot.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.sonometer.vroot.R
import app.sonometer.vroot.ui.theme.SonoBlue
import app.sonometer.vroot.ui.theme.SonoGreen
import app.sonometer.vroot.ui.theme.SonoOrange
import app.sonometer.vroot.ui.theme.SonoRed
import app.sonometer.vroot.ui.theme.SonoRedDim
import app.sonometer.vroot.ui.theme.SonoYellow

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
const val DB_MAX = 130f

/** Threshold above which the gauge / chart turn red-orange. */
const val DB_HOT_THRESHOLD = 90f
