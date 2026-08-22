package com.ar13x.jarvis.feature.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.update.UpdateStatus
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.motion.motionSize

/**
 * The banner, wherever it is dropped.
 *
 * Reaches for its own ViewModel rather than being handed state, because the
 * repository behind it is a singleton — so this can sit in a lazy list item
 * without every screen above it having to know that updates exist.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val viewModel: UpdateViewModel = hiltViewModel()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val available = (status as? UpdateStatus.Available)?.update

    // Feature code goes through the reduced-motion helpers rather than touching
    // Motion directly, so ANIMATOR_DURATION_SCALE is honoured here too (§6.5).
    val fade = motionFloat(Motion.Standard)
    val fadeOutSpec = motionFloat(Motion.Snappy)
    val size = motionSize(Motion.StandardSize)

    AnimatedVisibility(
        visible = available != null,
        // Dismissal should look like the card leaving, not the list blinking:
        // the rows below need to visibly close the gap (§6.5, list mutation).
        enter = fadeIn(fade) + expandVertically(size),
        exit = fadeOut(fadeOutSpec) + shrinkVertically(size),
    ) {
        // Held across the exit animation so the card does not blank out
        // mid-collapse when the state clears.
        val shown = available ?: return@AnimatedVisibility
        UpdateCard(
            update = shown,
            onDismiss = { viewModel.dismiss(shown.version) },
            modifier = modifier,
        )
    }
}
