package com.ar13x.jarvis.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ar13x.jarvis.designsystem.motion.LocalNavAnimatedVisibilityScope
import com.ar13x.jarvis.designsystem.motion.LocalReducedMotion
import com.ar13x.jarvis.designsystem.motion.LocalSharedTransitionScope
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.feature.onboarding.AppGateViewModel
import com.ar13x.jarvis.feature.onboarding.TokenScreen
import com.ar13x.jarvis.feature.chat.ChatScreen
import com.ar13x.jarvis.feature.tasks.list.TaskListScreen
import com.ar13x.jarvis.feature.tasks.session.SessionScreen

/**
 * The app gate: onboarding until a token exists, the tabs afterwards.
 *
 * `null` means the token store has not answered yet, and renders nothing —
 * defaulting to "no token" would flash the pairing screen at an already-paired
 * user on every cold start.
 */
@Composable
fun JarvisApp(gate: AppGateViewModel = hiltViewModel()) {
    val hasToken by gate.hasToken.collectAsStateWithLifecycle()

    when (hasToken) {
        null -> Box(Modifier.fillMaxSize().background(JarvisTheme.colors.ground))
        false -> TokenScreen(onConnected = { /* the flow re-emits and swaps this out */ })
        true -> JarvisTabs()
    }
}

@Composable
private fun JarvisTabs() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val currentTab = when {
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(ChatGraph::class) } == true -> JarvisTab.Chat
        else -> JarvisTab.Tasks
    }

    Box(Modifier.fillMaxSize()) {
        JarvisNavHost(
            navController = navController,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = JarvisBottomBarHeight),
        )
        JarvisBottomBar(
            current = currentTab,
            onSelect = navController::switchTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Switching tabs preserves scroll position and any open session (plan §5.1).
 * That is what `saveState`/`restoreState` buy, and it is the reason each tab is
 * its own nested graph rather than one flat back stack.
 */
private fun NavHostController.switchTab(tab: JarvisTab) {
    val target: Route = when (tab) {
        JarvisTab.Tasks -> TasksGraph
        JarvisTab.Chat -> ChatGraph
    }
    navigate(target) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun JarvisNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current

    // The scope has to sit above the NavHost for a row in one destination and a
    // header in the next to be the same element (plan §6.5). Establishing it in
    // phase A is the point — retrofitting it later means touching navigation,
    // every screen signature and every row at once.
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = TasksGraph,
                // Reduced motion collapses to a cross-fade rather than being
                // ignored (plan §6.5).
                enterTransition = {
                    if (reduced) {
                        fadeIn(tween(Motion.ReducedMillis))
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            Motion.StandardOffset,
                        ) + fadeIn(Motion.Standard)
                    }
                },
                exitTransition = {
                    if (reduced) {
                        fadeOut(tween(Motion.ReducedMillis))
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            Motion.StandardOffset,
                        ) + fadeOut(Motion.Standard)
                    }
                },
                popEnterTransition = {
                    if (reduced) {
                        fadeIn(tween(Motion.ReducedMillis))
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            Motion.StandardOffset,
                        ) + fadeIn(Motion.Standard)
                    }
                },
                popExitTransition = {
                    if (reduced) {
                        fadeOut(tween(Motion.ReducedMillis))
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            Motion.StandardOffset,
                        ) + fadeOut(Motion.Standard)
                    }
                },
            ) {
                navigation<TasksGraph>(startDestination = TaskList) {
                    composable<TaskList> {
                        WithNavScope {
                            TaskListScreen(
                                onOpenTask = { taskId -> navController.navigate(TaskSession(taskId)) },
                                onNewSession = { navController.navigate(NewSession) },
                            )
                        }
                    }
                    composable<TaskSession> { entry ->
                        WithNavScope {
                            SessionScreen(
                                taskId = entry.toRoute<TaskSession>().taskId,
                                onBack = navController::popBackStack,
                                onOpenTask = { id -> navController.navigate(TaskSession(id)) },
                            )
                        }
                    }
                    composable<NewSession> {
                        WithNavScope {
                            SessionScreen(
                                taskId = null,
                                onBack = navController::popBackStack,
                                onOpenTask = { id -> navController.navigate(TaskSession(id)) },
                            )
                        }
                    }
                }

                navigation<ChatGraph>(startDestination = Chat) {
                    composable<Chat> {
                        WithNavScope {
                            ChatScreen(
                                // Tapping a disambiguation button navigates into
                                // that task's session — cross-tab, into the Tasks
                                // stack (plan §5.4).
                                onOpenTask = { taskId -> navController.navigate(TaskSession(taskId)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Publishes the destination's own [AnimatedVisibilityScope] so a shared element
 * can find it.
 *
 * Half of a shared transition lives in the layout above the NavHost and half
 * inside each destination. This is the second half, wired once here so no screen
 * has to remember to do it.
 */
@Composable
private fun AnimatedVisibilityScope.WithNavScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this, content = content)
}
