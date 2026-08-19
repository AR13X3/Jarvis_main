package com.ar13x.jarvis.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw brand values (plan §6.2). These are *never* referenced from feature code —
 * only [JarvisColors] reads them. Swap the hue here and the whole app follows.
 *
 * Dark is deliberately not an inversion: a bright crimson wash vibrates on a
 * dark ground, so the gradient goes deeper and less saturated while [BrandCore]
 * brightens to hold contrast.
 */

// --- Light --------------------------------------------------------------------
internal val BrandCore   = Color(0xFFD8203C)  // primary actions, active states
internal val BrandDeep   = Color(0xFF9E0F26)  // gradient far stop, pressed
internal val BrandTint   = Color(0xFFFDE7EA)  // selected chip fill, subtle wash
internal val Ground      = Color(0xFFFAF7F8)  // page background — warm-biased
internal val Surface     = Color(0xFFFFFFFF)  // cards, composer, sheets
internal val SurfaceSunk = Color(0xFFF1EDEF)  // input wells, inactive chips
internal val Ink         = Color(0xFF15100F)  // headlines
internal val InkMuted    = Color(0xFF6B6164)  // secondary copy
internal val Hairline    = Color(0xFFE7E0E2)

// --- Dark ---------------------------------------------------------------------
internal val BrandCoreDark   = Color(0xFFF04156)
internal val BrandDeepDark   = Color(0xFF7E0A1D)
internal val BrandTintDark   = Color(0xFF3A1A21)
internal val GroundDark      = Color(0xFF141011)  // warm-biased near-black
internal val SurfaceDark     = Color(0xFF1E1819)
internal val SurfaceSunkDark = Color(0xFF272021)
internal val InkDark         = Color(0xFFF6F1F2)
internal val InkMutedDark    = Color(0xFFA79DA0)
internal val HairlineDark    = Color(0xFF332B2D)

/**
 * Status lives on a separate scale that contains **no red** (plan §6.2).
 *
 * If the brand hue also meant "bad", every CTA would read as a warning and every
 * error as a CTA. `cancelled` (a decision) and `incomplete` (a lapse) must also
 * stay distinguishable at a glance — so they differ in *form* (strike vs
 * outline) as well as colour, which survives colour-blindness and dark mode.
 */
internal val StatusGreen      = Color(0xFF1E8E5A)
internal val StatusGreenDark  = Color(0xFF4ECB8C)
internal val StatusAmber      = Color(0xFFB26A00)
internal val StatusAmberDark  = Color(0xFFE3A63F)
internal val StatusGrey       = Color(0xFF938A8D)
internal val StatusGreyDark   = Color(0xFF7C7276)
