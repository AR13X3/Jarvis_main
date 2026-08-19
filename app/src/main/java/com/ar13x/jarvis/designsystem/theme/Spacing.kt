package com.ar13x.jarvis.designsystem.theme

import androidx.compose.ui.unit.dp

/** Spacing scale (plan §6.4). Not a CompositionLocal — it never varies by theme. */
object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x12 = 48.dp

    /** Screen gutter. Every screen's horizontal padding starts here. */
    val Gutter = 20.dp
}
