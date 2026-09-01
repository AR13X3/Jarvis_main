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
 * questions.
 */
@Serializable data object RoutineGraph : Route

@Serializable data object RoutineDayView : Route

/** The week's shape and totals — "where the hours go" (v2 plan §4.1). */
@Serializable data object RoutineWeek : Route

/**
 * The dashboard — "how it is going" (v2 plan §6). **Its own tab as of §9.2.**
 *
 * It spent a fortnight parked inside the Routine graph behind a chart icon, on
 * the grounds that placement was a design decision v2 plan §9 left open and
 * which was not this file's to take. Joy has since taken it: the dashboard and
 * to-dos both become tabs and neither is demoted back to a nested screen.
 *
 * The comment that stood here predicted the move would cost "one entry here and
 * one in `JarvisBottomBar`". It cost three — a graph, a tab and the removal of
 * the chart icon that used to reach it — because two ways into one screen means
 * two copies of it on two back stacks.
 */
@Serializable data object DashboardGraph : Route

@Serializable data object DashboardRoute : Route

@Serializable data object TaskList : Route

/**
 * To-dos (v2 plan §5) — **their own tab as of §9.2**, not a screen in Tasks.
 *
 * The v2 plan's four surfaces are Reminders, Tasks, Routine and Dashboard, where
 * its *Reminders* is this app's `Tasks` tab and its *Tasks* is this screen. They
 * are siblings, which is what they now are on the bar.
 *
 * The cost of the move is real and worth naming: backing out of a to-do used to
 * return to the reminder list, because the to-do screens lived in the Tasks
 * graph. They now have a graph of their own, so a to-do backs out to the to-do
 * list — which is the correct behaviour for a tab and would have been wrong
 * while it was a nested screen.
 */
@Serializable data object TodosGraph : Route

@Serializable data object TodoList : Route

/** One to-do: its description, its status, and its deadline (§9.1). */
@Serializable data class TodoDetail(val todoId: Long) : Route

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
