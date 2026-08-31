package com.ar13x.jarvis.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colours for routine categories (v2 plan §4.5).
 *
 * The palette elsewhere in this app is deliberately one brand hue and a
 * near-neutral ground — no dynamic colour, one saturated thing on screen at a
 * time (plan §6.1). A routine breaks that on purpose and it is worth saying why:
 * the day bar and the slot ticks are the one place where colour is *carrying
 * data* rather than decorating, because seven categories have to be told apart
 * at a glance in a stack nine pixels tall.
 *
 * Hues are taken from Joy's own `the-week.html`, which is where the association
 * between a colour and a part of her week already lives. Only the lightness
 * moves: the source is tuned for a near-black ground, and the same values on a
 * warm near-white read as pastel and lose their separation.
 *
 * Life and free stay deliberately grey. They are the parts of the day that are
 * not a commitment, and a chart where the buffer is as loud as Speedway invites
 * exactly the wrong reading.
 */
@Composable
@ReadOnlyComposable
fun routineCategoryColor(categoryId: String): Color {
    val dark = JarvisTheme.colors.isDark
    return when (categoryId) {
        "speedway" -> if (dark) Color(0xFF4A7EA8) else Color(0xFF2F6690)
        "reskill" -> if (dark) Color(0xFFC26A3E) else Color(0xFFA85327)
        "uni" -> if (dark) Color(0xFF3F8F74) else Color(0xFF2E7A60)
        "webdev" -> if (dark) Color(0xFFB08A34) else Color(0xFF8E6C1B)
        "gym" -> if (dark) Color(0xFFA35D78) else Color(0xFF8C4761)
        "life" -> if (dark) Color(0xFF5C636E) else Color(0xFF8B9099)
        "free" -> if (dark) Color(0xFF343C48) else Color(0xFFC5C9CF)
        else -> JarvisTheme.colors.inkMuted
    }
}
