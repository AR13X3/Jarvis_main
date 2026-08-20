package com.ar13x.jarvis.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.feature.conversation.ConversationEvent
import com.ar13x.jarvis.feature.conversation.ConversationScreen
import com.ar13x.jarvis.feature.conversation.ConversationViewModel
import com.ar13x.jarvis.feature.conversation.SessionTarget
import com.ar13x.jarvis.feature.tasks.session.EmptyPrompt

/**
 * The Chat tab (plan §5.4): the general session, plus task lookup.
 *
 * Ask about a task in natural language; if it is ambiguous the agent asks for a
 * date, and the candidates come back as buttons three at a time. Tapping one
 * navigates into that task's session — cross-tab, into the Tasks stack.
 */
@Composable
fun ChatScreen(
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.start(SessionTarget.General) }

    val state by viewModel.state.collectAsStateWithLifecycle()

    ConversationScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTask = onOpenTask,
        placeholder = "Ask anything…",
        modifier = modifier,
        header = {
            // Nothing but breathing room while the greeting is on screen: the
            // oversized headline *is* the header in the empty state, and
            // repeating a title above it would only compete with it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Space.Gutter, vertical = Space.x3),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (!state.isEmpty) {
                    Text(
                        text = "Jarvis",
                        style = JarvisTheme.typography.titleLarge,
                        color = JarvisTheme.colors.ink,
                    )
                } else {
                    Box(Modifier.padding(top = 20.dp))
                }
            }
        },
        empty = {
            EmptyPrompt(
                headline = "What would you like\nto sort out today?",
                detail = "Ask about a task in plain language. If more than one matches, " +
                    "I will ask which date you meant.",
                suggestions = SUGGESTIONS,
                onSuggestion = { viewModel.onEvent(ConversationEvent.ComposerChanged(it)) },
            )
        },
    )
}

/**
 * Phrased to match what the general session's tools can actually do.
 *
 * `find_tasks` searches **titles by keyword**; there is no list-everything or
 * filter-by-date tool (parent plan §2.3). "What's due today?" reads naturally
 * and the agent has to refuse it — asked live, it replied that it could not list
 * or filter by due date and offered to search keywords instead. A suggestion
 * chip that reliably produces an apology is worse than no chip: it teaches the
 * user the assistant is broken when it is doing exactly what it was scoped to.
 *
 * The Tasks tab already answers "what's due today" better than any chat turn
 * could, which is the other half of why this does not belong here.
 */
private val SUGGESTIONS = listOf(
    "Find my dentist appointment",
    "Look up the gym reminder",
    "What is the rates notice set to?",
)
