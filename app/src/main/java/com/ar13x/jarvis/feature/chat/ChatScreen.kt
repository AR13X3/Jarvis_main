package com.ar13x.jarvis.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.model.SessionSummary
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.tabularNums
import com.ar13x.jarvis.feature.conversation.ConversationEvent
import com.ar13x.jarvis.feature.conversation.rememberMicAction
import com.ar13x.jarvis.feature.conversation.SpeakToggle
import com.ar13x.jarvis.feature.conversation.ConversationScreen
import com.ar13x.jarvis.feature.conversation.ConversationViewModel
import com.ar13x.jarvis.feature.conversation.SessionTarget
import com.ar13x.jarvis.feature.tasks.session.EmptyPrompt
import java.time.Duration
import java.time.Instant

/**
 * The Chat tab (plan §5.4): the general session, task lookup, and history.
 *
 * Unlike a task session — one thread bound to one task forever — this is a
 * series of conversations. It opens a **fresh** one every time rather than
 * resuming the most recent: arriving at the tab almost always means having a new
 * question, and landing in a finished conversation makes the tab feel like a
 * place to be dug out of. Going back is what the history button is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenTask: (Long) -> Unit,
    onCreateTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.start(SessionTarget.General()) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val onMic = rememberMicAction(
        listening = state.listening,
        onToggleMic = { viewModel.onEvent(ConversationEvent.ToggleMic) },
    )

    ConversationScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onMic = onMic,
        onOpenTask = onOpenTask,
        onCreateTask = onCreateTask,
        placeholder = "Ask anything…",
        modifier = modifier,
        header = {
            ChatHeader(
                speakReplies = state.speakReplies,
                speaking = state.speaking,
                onToggleSpeak = { viewModel.onEvent(ConversationEvent.ToggleSpeakReplies) },
                onStopSpeaking = { viewModel.onEvent(ConversationEvent.StopSpeaking) },
                hasConversation = !state.isEmpty,
                onHistory = {
                    showHistory = true
                    viewModel.onEvent(ConversationEvent.LoadConversations)
                },
                onNew = { viewModel.onEvent(ConversationEvent.NewConversation) },
            )
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

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = sheetState,
            containerColor = JarvisTheme.colors.surface,
        ) {
            ConversationHistory(
                conversations = state.conversations,
                currentId = state.sessionId,
                onPick = { id ->
                    showHistory = false
                    viewModel.onEvent(ConversationEvent.OpenConversation(id))
                },
            )
        }
    }
}

@Composable
private fun ChatHeader(
    /**
     * Whether anything has been said yet.
     *
     * Drives both the title and the `+`, because they answer the same question.
     * In an empty conversation the oversized headline is already the header, and
     * "new conversation" is a no-op — you are in one.
     */
    hasConversation: Boolean,
    onHistory: () -> Unit,
    onNew: () -> Unit,
    speakReplies: Boolean,
    speaking: Boolean,
    onToggleSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
) {
    val colors = JarvisTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.Gutter, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(onClick = onHistory, diameter = 40.dp) {
            Icon(
                Icons.Rounded.History,
                contentDescription = "Past conversations",
                tint = colors.ink,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.size(Space.x3))
        // The oversized headline is the header in the empty state, so a title
        // here would only compete with it.
        if (hasConversation) {
            Text("Jarvis", style = JarvisTheme.typography.titleLarge, color = colors.ink)
        }
        Spacer(Modifier.weight(1f))
        // Before the + rather than after: the + is the saturated one and stays
        // the last thing on the row, so the eye still lands there first (§6.7).
        SpeakToggle(
            enabled = speakReplies,
            speaking = speaking,
            onToggle = onToggleSpeak,
            onStop = onStopSpeaking,
        )
        // Hidden in an empty conversation, where it is a no-op that looks like
        // a reload — you are already in a new conversation, so it swaps one
        // blank screen for another identical one.
        //
        // It is not merely useless there: every tap calls `newGeneralSession()`
        // and creates a row server-side, which is the litter gw03 asked us to
        // filter out of the history list with `message_count = 0`. Not offering
        // the tap is better than filtering its result.
        //
        // Kept once something has been said, where it is the only way back to a
        // fresh chat from a conversation opened out of history — switching tabs
        // preserves state (§5.1), so it would otherwise be a dead end.
        if (hasConversation) {
            Spacer(Modifier.size(Space.x2))
            CircleIconButton(onClick = onNew, diameter = 40.dp, background = colors.brandCore) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "New conversation",
                    tint = colors.onBrand,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ConversationHistory(
    conversations: LoadState<List<SessionSummary>>?,
    currentId: String?,
    onPick: (String) -> Unit,
) {
    val colors = JarvisTheme.colors

    Column(Modifier.padding(bottom = Space.x8)) {
        Text(
            text = "PAST CONVERSATIONS",
            style = JarvisTheme.typography.labelSmall,
            color = colors.inkMuted,
            modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.x3),
        )

        when (conversations) {
            null, LoadState.Loading -> Box(
                Modifier.fillMaxWidth().height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = colors.brandCore,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }

            is LoadState.Failed -> Text(
                text = "Couldn't load your conversations.",
                style = JarvisTheme.typography.bodyMedium,
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.x4),
            )

            is LoadState.Ready ->
                if (conversations.data.isEmpty()) {
                    Text(
                        text = "Nothing yet. This is your first conversation.",
                        style = JarvisTheme.typography.bodyMedium,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.x4),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(conversations.data, key = { it.id }) { summary ->
                            ConversationRow(
                                summary = summary,
                                current = summary.id == currentId,
                                onClick = { onPick(summary.id) },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun ConversationRow(
    summary: SessionSummary,
    current: Boolean,
    onClick: () -> Unit,
) {
    val colors = JarvisTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (current) colors.brandTint else colors.surface)
            .padding(horizontal = Space.Gutter, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = summary.title ?: "Untitled conversation",
                style = JarvisTheme.typography.titleMedium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = relativeTime(summary.updatedAt) + " · " +
                    summary.messageCount + (if (summary.messageCount == 1) " message" else " messages"),
                style = JarvisTheme.typography.bodySmall.tabularNums(),
                color = colors.inkMuted,
            )
        }
        if (current) {
            Box(
                Modifier
                    .clip(Corner.Pill)
                    .background(colors.brandCore, Corner.Pill)
                    .padding(horizontal = Space.x2, vertical = 2.dp),
            ) {
                Text("Now", style = JarvisTheme.typography.labelSmall, color = colors.onBrand)
            }
        }
    }
}

/**
 * Coarse on purpose. The exact minute a conversation ended is never the thing
 * being looked for — "yesterday" is what people actually remember.
 */
private fun relativeTime(at: Instant): String {
    val elapsed = Duration.between(at, Instant.now())
    return when {
        elapsed.toMinutes() < 1 -> "Just now"
        elapsed.toHours() < 1 -> elapsed.toMinutes().toString() + " min ago"
        elapsed.toHours() < 24 -> elapsed.toHours().toString() + "h ago"
        elapsed.toDays() == 1L -> "Yesterday"
        elapsed.toDays() < 7 -> elapsed.toDays().toString() + " days ago"
        else -> (elapsed.toDays() / 7).toString() + "w ago"
    }
}

/**
 * Phrased to match what the general session's tools can do. `find_tasks` takes
 * a date range now (BUILD_NOTES §3.10), so these are answerable.
 */
private val SUGGESTIONS = listOf(
    "What's due today?",
    "What's due this week?",
    "What's due in the next 3 days?",
)
