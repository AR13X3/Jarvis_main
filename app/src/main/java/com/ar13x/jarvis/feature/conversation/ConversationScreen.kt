package com.ar13x.jarvis.feature.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.data.message
import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.Message
import com.ar13x.jarvis.core.ui.LoadState
import com.ar13x.jarvis.designsystem.component.BrandBackdrop
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * The conversation, shared by the task session (§5.3) and the Chat tab (§5.4).
 *
 * Both are the same screen with different framing — same message list, same
 * composer, same confirmation cards — so they are one composable with slots
 * rather than two that drift apart.
 */
@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onEvent: (ConversationEvent) -> Unit,
    onOpenTask: (Long) -> Unit,
    placeholder: String,
    header: @Composable () -> Unit,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    val listState = rememberLazyListState()
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(state.transientFailure) {
        val failure = state.transientFailure ?: return@LaunchedEffect
        snackbars.showSnackbar(failure.message(), duration = SnackbarDuration.Short)
        onEvent(ConversationEvent.DismissFailure)
    }

    LoadOlderEffect(state, listState, onEvent)

    Box(modifier.fillMaxSize()) {
        BrandBackdrop {
            Column(Modifier.fillMaxSize().imePadding()) {
                header()

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val history = state.history) {
                        is LoadState.Loading -> CenteredSpinner()
                        is LoadState.Failed -> ConversationFailure(
                            message = history.reason.message(),
                            onRetry = { onEvent(ConversationEvent.Retry) },
                        )
                        is LoadState.Ready ->
                            if (state.isEmpty) {
                                empty()
                            } else {
                                MessageStream(
                                    state = state,
                                    listState = listState,
                                    onEvent = onEvent,
                                    onOpenTask = onOpenTask,
                                )
                            }
                    }
                }

                if (state.isReadOnly) {
                    ReadOnlyStrip(status = state.task!!.status)
                } else {
                    Composer(
                        text = state.composerText,
                        canSend = state.canSend,
                        placeholder = placeholder,
                        onTextChange = { onEvent(ConversationEvent.ComposerChanged(it)) },
                        onSend = { onEvent(ConversationEvent.Send) },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbars,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Space.Gutter, vertical = Space.x12),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = Corner.Sm,
                containerColor = colors.ink,
                contentColor = colors.ground,
                actionColor = colors.brandCore,
            )
        }
    }
}

@Composable
private fun MessageStream(
    state: ConversationUiState,
    listState: LazyListState,
    onEvent: (ConversationEvent) -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    LazyColumn(
        state = listState,
        // Newest at the bottom, and the list stays pinned there as messages
        // arrive without anyone having to scroll it (plan §5.3).
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x3, Alignment.Bottom),
    ) {
        if (state.thinking) {
            item(key = "thinking") {
                Arrival(key = "thinking", modifier = Modifier.animateItem()) { ThinkingBubble() }
            }
        }

        items(state.stream, key = { it.id }) { message ->
            MessageItem(
                message = message,
                state = state,
                onEvent = onEvent,
                onOpenTask = onOpenTask,
                modifier = Modifier.animateItem(),
            )
        }

        if (state.hasMoreHistory) {
            item(key = "older") {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingOlder) {
                        CircularProgressIndicator(
                            color = JarvisTheme.colors.brandCore,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: Message,
    state: ConversationUiState,
    onEvent: (ConversationEvent) -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        if (message.text.isNotBlank()) {
            Arrival(key = message.id) { MessageBubble(message) }
        }

        message.components.forEachIndexed { index, component ->
            val key = OptionsKey(message.id, index)
            when (component) {
                is AgentComponent.Confirm -> Arrival(key = component.proposalId) {
                    ConfirmCard(
                        component = component,
                        resolving = state.resolving == component.proposalId,
                        onConfirm = { onEvent(ConversationEvent.Confirm(component.proposalId)) },
                        onReject = { onEvent(ConversationEvent.Reject(component.proposalId)) },
                    )
                }

                is AgentComponent.TaskOptions -> {
                    val options = state.options[key]
                    TaskOptionsCard(
                        component = component,
                        extra = options?.extra.orEmpty(),
                        cursor = options?.cursor,
                        loadingMore = options?.loading == true,
                        onPick = onOpenTask,
                        onShowMore = { cursor ->
                            onEvent(ConversationEvent.ShowMoreOptions(key, cursor))
                        },
                    )
                }

                // Forward compatibility (plan §4.5): a component type this build
                // has never heard of renders as its text and nothing else,
                // rather than taking the message list down.
                is AgentComponent.Unknown -> component.text?.let { text ->
                    MessageBubble(message.copy(text = text, components = emptyList()))
                }
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = JarvisTheme.colors.brandCore,
            strokeWidth = 2.dp,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ConversationFailure(message: String, onRetry: () -> Unit) {
    val colors = JarvisTheme.colors
    Box(Modifier.fillMaxSize().padding(Space.Gutter), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = JarvisTheme.typography.bodyLarge,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) {
                Text("Try again", color = colors.brandCore)
            }
        }
    }
}

/** Pages backwards as the user scrolls up into history (plan §5.3). */
@Composable
private fun LoadOlderEffect(
    state: ConversationUiState,
    listState: LazyListState,
    onEvent: (ConversationEvent) -> Unit,
) {
    LaunchedEffect(listState, state.hasMoreHistory) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            // "Last" is the top of the screen here, because the list is reversed.
            last >= layout.totalItemsCount - 3
        }.collect { nearTop ->
            if (nearTop) onEvent(ConversationEvent.LoadOlder)
        }
    }
}
