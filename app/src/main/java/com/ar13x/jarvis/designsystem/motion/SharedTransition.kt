package com.ar13x.jarvis.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Plumbing for the task row → session shared element (plan §6.5, move 1).
 *
 * This is here in phase A rather than phase C on purpose. The plan is explicit
 * that the theme and this transition are the two things painful to retrofit,
 * because the scope has to be established *above* the NavHost and threaded
 * through every destination — doing that later means touching navigation, every
 * screen signature, and every row at once.
 *
 * With the locals in place, phase C only has to attach [sharedTaskTitle] at both
 * ends and the morph works.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The title morphing from row to session header — the signature move, and the
 * one the architecture is built around, since there is exactly one session per
 * task to morph into.
 *
 * A no-op when either scope is missing (previews, tests, a screen outside the
 * NavHost) rather than a crash: a missing transition should degrade to a plain
 * navigation, never take the screen down.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedTaskTitle(taskId: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    val reduced = LocalReducedMotion.current
    with(shared) {
        return this@sharedTaskTitle.sharedBounds(
            sharedContentState = rememberSharedContentState(key = TaskTitleKey(taskId)),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> if (reduced) Motion.ReducedRect else Motion.StandardRect },
        )
    }
}

/** Typed key, so two unrelated elements cannot collide on a stringly-typed one. */
private data class TaskTitleKey(val taskId: Long)
