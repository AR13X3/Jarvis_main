package com.ar13x.jarvis.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe routes (plan §2). Arguments are checked at compile time and there is
 * no string route to parse — a task id cannot silently arrive as the literal
 * "{taskId}" because a placeholder was misspelled somewhere.
 */
sealed interface Route

/** Tab graphs. Each tab keeps its own back stack (plan §5.1). */
@Serializable data object TasksGraph : Route

@Serializable data object ChatGraph : Route

@Serializable data object TaskList : Route

/** Tapping a row opens *that task's* persistent session (plan §5.3). */
@Serializable data class TaskSession(val taskId: Long) : Route

/**
 * The `+` creates a new **unbound** session and navigates straight into it
 * (plan §5.2).
 *
 * It carries no id: the session is created by the screen's ViewModel on entry,
 * because inventing one here would mean the app minting identifiers the gateway
 * owns. It stays unbound until a create proposal is confirmed, at which point
 * the gateway binds it — one session per task, forever after.
 */
@Serializable data class NewSession(
    /**
     * Text to send the moment the session exists.
     *
     * Set when the general session hands a create request over (§5.4): the user
     * already said what they wanted, and making them retype it because the
     * server scopes tools per session is the app's problem to hide, not theirs.
     */
    val seed: String? = null,
) : Route

@Serializable data object Chat : Route
