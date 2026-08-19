package com.ar13x.jarvis.feature.tasks.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.motion.sharedTaskTitle
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.statusStyle
import com.ar13x.jarvis.designsystem.theme.tabularNums
import com.ar13x.jarvis.feature.conversation.ConversationEvent
import com.ar13x.jarvis.feature.conversation.ConversationScreen
import com.ar13x.jarvis.feature.conversation.ConversationViewModel
import com.ar13x.jarvis.feature.conversation.SessionTarget

/**
 * A task's persistent session (plan §5.3), and the unbound session the `+` opens
 * (§5.2).
 *
 * Both are the same screen: an unbound one simply has no task yet, and gains one
 * the moment a create proposal is confirmed — at which point the gateway binds
 * the session and it can never create a second task (parent plan §2.2).
 */
@Composable
fun SessionScreen(
    taskId: Long?,
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val target = remember(taskId) {
        if (taskId == null) SessionTarget.NewTask else SessionTarget.Bound(taskId)
    }
    LaunchedEffect(target) { viewModel.start(target) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val task = state.task

    ConversationScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTask = onOpenTask,
        placeholder = if (task == null) "What should I remind you about?" else "Ask or change something…",
        modifier = modifier,
        header = {
            SessionHeader(
                title = task?.title ?: "New task",
                subtitle = task?.let { DueDateFormat.forRow(it) },
                statusLabel = task?.let { statusStyle(it.status).label },
                sharedTaskId = task?.id,
                onBack = onBack,
            )
        },
        empty = {
            EmptyPrompt(
                headline = if (task == null) "What should\nI set up?" else "Nothing said yet",
                detail = if (task == null) {
                    "Describe it in your own words. I will ask for anything I need " +
                        "and show you a card to confirm before writing it down."
                } else {
                    "Ask about this task, or tell me what changed."
                },
            )
        },
    )
}

@Composable
private fun SessionHeader(
    title: String,
    subtitle: String?,
    statusLabel: String?,
    sharedTaskId: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.Gutter, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(onClick = onBack, diameter = 40.dp) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = colors.ink,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(Space.x3))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = JarvisTheme.typography.titleLarge,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // The title morphs from the row that opened it — the signature
                // move, and the one the architecture is built around, since
                // there is exactly one session per task to morph into (§6.5).
                modifier = if (sharedTaskId != null) {
                    Modifier.sharedTaskTitle(sharedTaskId)
                } else {
                    Modifier
                },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = JarvisTheme.typography.bodySmall.tabularNums(),
                    color = colors.inkMuted,
                    maxLines = 1,
                )
            }
        }

        if (statusLabel != null) {
            Spacer(Modifier.width(Space.x2))
            Box(
                Modifier
                    .clip(Corner.Pill)
                    .background(colors.surfaceSunk, Corner.Pill)
                    .padding(horizontal = Space.x3, vertical = 4.dp),
            ) {
                Text(statusLabel, style = JarvisTheme.typography.labelSmall, color = colors.inkMuted)
            }
        }
    }
}

@Composable
internal fun EmptyPrompt(
    headline: String,
    detail: String,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
) {
    val colors = JarvisTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = headline,
            style = JarvisTheme.typography.displaySmall,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.x3))
        Text(
            text = detail,
            style = JarvisTheme.typography.bodyMedium,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(Space.x6))
            suggestions.forEach { suggestion ->
                SuggestionPill(text = suggestion, onClick = { onSuggestion(suggestion) })
                Spacer(Modifier.height(Space.x2))
            }
        }
    }
}

@Composable
private fun SuggestionPill(
    text: String,
    onClick: () -> Unit,
) {
    val colors = JarvisTheme.colors
    Box(
        Modifier
            .clip(Corner.Pill)
            .background(colors.surface, Corner.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x4, vertical = Space.x3),
    ) {
        Text(text, style = JarvisTheme.typography.bodyMedium, color = colors.ink)
    }
}
