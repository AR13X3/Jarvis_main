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

/**
 * The routine (v2 plan §4).
 *
 * Its own tab rather than a screen inside Tasks: a routine is not a list of
 * things to do, it is where the hours already go, and the two answer different
 * questions. Placement is called out as open in v2 plan §9 -- three tabs plus
 * chat is a design-system decision, not a routing one.
 */
@Serializable data object RoutineGraph : Route

@Serializable data object RoutineDayView : Route

/** The week's shape and totals — "where the hours go" (v2 plan §4.1). */
@Serializable data object RoutineWeek : Route

/**
 * The dashboard — "how it is going" (v2 plan §6).
 *
 * **Placed here provisionally, and the placement is not mine to settle.** §9
 * lists navigation as open: four surfaces plus Chat is a design-system decision
 * and `jarvis-app-plan.md` §6 is the authority. So this does *not* add a fourth
 * bottom-bar tab — it sits in the Routine graph beside [RoutineWeek], reached
 * the same way, because "how it is going" is the same question as "where the
 * hours go" asked over a longer window.
 *
 * That is a placement, not a claim. Moving it to its own tab is one entry here
 * and one in `JarvisBottomBar`; the screen itself does not care.
 */
@Serializable data object DashboardRoute : Route

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
    /**
     * An unfinished draft to resume, rather than a new session.
     *
     * A task conversation that was never confirmed leaves no task, so nothing
     * in the list pointed at it and backing out lost it. This is the way back.
     */
    val sessionId: String? = null,
) : Route

@Serializable data object Chat : Route

/**
 * Build identity (plan §10.1), and later the update banner's home (§10.2).
 *
 * It lives in the Tasks graph rather than at the top level so backing out of it
 * returns to the list with its scroll intact, the same as any other detail
 * screen.
 */
@Serializable data object About : Route
