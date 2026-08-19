package com.ar13x.jarvis.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ar13x.jarvis.R

/**
 * Bundled font files, not downloadable fonts (plan §2 / §6.6): downloadable
 * fonts need Play Services at runtime and can silently fall back, and a brand
 * face that sometimes doesn't load is worse than no brand face.
 *
 * Both files are variable fonts, so one file covers every weight. minSdk 31 is
 * comfortably above the API 26 floor for font variation settings.
 */
private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Display — headlines and task titles. Carries the personality. */
val Display = FontFamily(
    variable(R.font.outfit_variable, 400),
    variable(R.font.outfit_variable, 500),
    variable(R.font.outfit_variable, 600),
    variable(R.font.outfit_variable, 700),
)

/** UI/body — a highly legible neutral sans for everything else. */
val Body = FontFamily(
    variable(R.font.inter_variable, 400),
    variable(R.font.inter_variable, 500),
    variable(R.font.inter_variable, 600),
    variable(R.font.inter_variable, 700),
)

/**
 * Tabular numerals, for anywhere dates, times or counts align in a column.
 * Proportional digits make a list of times visibly ragged (plan §6.6).
 */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = "tnum")

private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun display(size: Int, weight: FontWeight, leading: Float, tracking: Float) = TextStyle(
    fontFamily = Display,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * leading).sp,
    letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = TrimBoth,
)

private fun body(size: Int, weight: FontWeight, leading: Float, tracking: Float = 0f) = TextStyle(
    fontFamily = Body,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * leading).sp,
    letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = TrimBoth,
)

/**
 * The oversized headline with ~1.05 leading is the reference's loudest move and
 * it costs nothing (plan §6.4), so the display sizes are large and their leading
 * is tight enough to read as one block rather than separate lines.
 */
internal val JarvisTypography = Typography(
    displayLarge = display(46, FontWeight.SemiBold, 1.05f, -1.2f),
    displayMedium = display(38, FontWeight.SemiBold, 1.06f, -0.9f),
    displaySmall = display(30, FontWeight.SemiBold, 1.10f, -0.6f),

    headlineLarge = display(28, FontWeight.SemiBold, 1.15f, -0.4f),
    headlineMedium = display(24, FontWeight.SemiBold, 1.18f, -0.3f),
    headlineSmall = display(20, FontWeight.SemiBold, 1.20f, -0.2f),

    titleLarge = display(19, FontWeight.Medium, 1.25f, -0.1f),
    titleMedium = display(16, FontWeight.Medium, 1.30f, 0f),
    titleSmall = body(14, FontWeight.SemiBold, 1.35f),

    bodyLarge = body(16, FontWeight.Normal, 1.50f),
    bodyMedium = body(14, FontWeight.Normal, 1.50f),
    bodySmall = body(12, FontWeight.Normal, 1.45f),

    labelLarge = body(14, FontWeight.Medium, 1.30f),
    labelMedium = body(12, FontWeight.Medium, 1.30f),
    // Section headers: small, uppercase, letter-spaced (plan §6.7).
    labelSmall = body(11, FontWeight.SemiBold, 1.30f, tracking = 0.9f),
)

/** Em-based helper for the rare place a style needs relative leading inline. */
internal val TightLeading = 1.05f.em
