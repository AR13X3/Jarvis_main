package com.ar13x.jarvis.feature.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.StopCircle
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.motion.Motion
import com.ar13x.jarvis.designsystem.motion.motionFloat
import com.ar13x.jarvis.designsystem.motion.motionSize
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.rounded.ErrorOutline
import com.ar13x.jarvis.core.model.MessageRole
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onCreateTask: (String) -> Unit,
    placeholder: String,
    header: @Composable () -> Unit,
    /**
     * Tapping the microphone. Lives above this because starting dictation may
     * need the RECORD_AUDIO prompt, and only something Activity-scoped can ask.
     */
    onMic: () -> Unit = {},
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
            // No navigationBarsPadding here: the host already reserves the bar
            // plus that inset when the keyboard is down, and imePadding subsumes
            // it when the keyboard is up. Adding it again double-counted the
            // inset and left dead space under the composer.
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
                                    onCreateTask = onCreateTask,
                                )
                            }
                    }
                }

                if (state.isReadOnly) {
                    // The mic goes with the composer, so a terminal task cannot
                    // be dictated at either — §5.3 falls out of the layout
                    // rather than needing its own rule.
                    ReadOnlyStrip(status = state.task!!.status)
                } else {
                    VoiceErrorStrip(
                        message = state.voiceError,
                        onDismiss = { onEvent(ConversationEvent.DismissVoiceError) },
                    )
                    Composer(
                        text = state.composerText,
                        canSend = state.canSend,
                        placeholder = placeholder,
                        onTextChange = { onEvent(ConversationEvent.ComposerChanged(it)) },
                        onSend = { onEvent(ConversationEvent.Send) },
                        micAvailable = state.micAvailable,
                        listening = state.listening,
                        amplitude = state.amplitude,
                        onMic = onMic,
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
    onCreateTask: (String) -> Unit,
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
                onCreateTask = onCreateTask,
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
    onCreateTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        if (message.text.isNotBlank()) {
            Arrival(key = message.id) { MessageBubble(message) }
        } else if (message.role == MessageRole.Assistant && message.components.isEmpty()) {
            // An agent turn with no prose AND no cards used to render as
            // literally nothing: no bubble, no card, no error, no gap. The
            // request had succeeded, so there was no failure to report either —
            // the screen simply looked as though the message had never been
            // sent.
            //
            // Seen in the field: a still-unbound session with a pending
            // proposal was asked to change the not-yet-created task, and the
            // turn came back empty. §8.2's rule is that a failure names its
            // cause; silence names nothing, and is the one outcome the user
            // cannot tell apart from a bug in the app.
            Arrival(key = message.id) { EmptyTurn() }
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
                        statusFor = state.optionStatus::get,
                        onPick = onOpenTask,
                        onShowMore = { cursor ->
                            onEvent(ConversationEvent.ShowMoreOptions(key, cursor))
                        },
                    )
                }

                is AgentComponent.NewTask -> Arrival(key = "new-task-" + message.id) {
                    HandoffButton(
                        label = component.label,
                        onClick = { onCreateTask(component.seed) },
                    )
                }

                is AgentComponent.Overdue -> Arrival(key = "overdue-" + component.occurrenceId) {
                    OverdueCard(
                        component = component,
                        // Only the newest card for an occurrence is answerable;
                        // the loop asks three times and the earlier questions
                        // are about a deadline that has already moved.
                        superseded = state.liveOverdue[component.occurrenceId] != key,
                        busy = state.resolvingOverdue == component.occurrenceId,
                        onComplete = {
                            onEvent(ConversationEvent.CompleteOccurrence(component.occurrenceId))
                        },
                        onExtend = { minutes ->
                            onEvent(
                                ConversationEvent.ExtendOccurrence(component.occurrenceId, minutes),
                            )
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

/**
 * The handoff out of general chat into a new task session (plan §5.4).
 *
 * A general session has no create tool, so asked to make something the agent can
 * only decline. This turns that decline into a door: one tap opens the session
 * that *can* create, already carrying what the user said.
 */
@Composable
private fun HandoffButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors
    Row(
        modifier = modifier
            .clip(Corner.Pill)
            .background(colors.brandCore, Corner.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = colors.onBrand,
            modifier = Modifier.size(16.dp),
        )
        Text(text = label, style = JarvisTheme.typography.labelLarge, color = colors.onBrand)
    }
}

/**
 * Recognition failed, said quietly.
 *
 * A strip above the composer rather than a dialog or a snackbar: dictation
 * failing is a small, local, immediately retryable thing, and interrupting the
 * screen for it would make it feel far more serious than it is.
 */
@Composable
private fun VoiceErrorStrip(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(motionFloat(Motion.Standard)) + expandVertically(motionSize(Motion.StandardSize)),
        exit = fadeOut(motionFloat(Motion.Snappy)) + shrinkVertically(motionSize(Motion.StandardSize)),
    ) {
        val shown = message ?: return@AnimatedVisibility
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Space.Gutter)
                .clip(Corner.Sm)
                .background(colors.surfaceSunk, Corner.Sm)
                .clickable(onClick = onDismiss)
                .padding(horizontal = Space.x4, vertical = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shown,
                style = JarvisTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }
    }
}

/**
 * The spoken-replies toggle, for a conversation header.
 *
 * In the header rather than buried in a settings screen because it is the kind
 * of setting whose right value changes by situation — on in the car, off in an
 * office — and one that is three taps away is one nobody turns off in time.
 *
 * While speaking it becomes a stop button. There must always be a way to shut
 * it up that does not involve turning the feature off entirely.
 */
@Composable
fun SpeakToggle(
    enabled: Boolean,
    speaking: Boolean,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors

    CircleIconButton(
        onClick = if (speaking) onStop else onToggle,
        diameter = 40.dp,
        background = if (enabled) colors.brandTint else colors.surfaceSunk,
        modifier = modifier,
    ) {
        Icon(
            imageVector = when {
                speaking -> Icons.Rounded.StopCircle
                enabled -> Icons.AutoMirrored.Rounded.VolumeUp
                else -> Icons.AutoMirrored.Rounded.VolumeOff
            },
            contentDescription = when {
                speaking -> "Stop speaking"
                enabled -> "Spoken replies on"
                else -> "Spoken replies off"
            },
            tint = if (enabled) colors.brandCore else colors.inkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The microphone tap, permission and all.
 *
 * `RECORD_AUDIO` is asked **in context** — on the first tap of the mic, by
 * someone who has just said they want to dictate — rather than in a wall of
 * dialogs at launch (plan §5.5, the same rule the reminder permissions follow).
 * A permission prompt whose reason is on screen behind it is one people answer;
 * one at first launch is one they dismiss.
 *
 * A denial is not nagged at. The mic simply does nothing this time and can be
 * tapped again later, which is the honest behaviour — Android stops delivering
 * the dialog after two refusals anyway, so a third attempt would be an
 * invisible no-op dressed up as a retry.
 */
@Composable
fun rememberMicAction(
    listening: Boolean,
    onToggleMic: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val granted = remember(context) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }
    var hasPermission by rememberSaveable { mutableStateOf(granted) }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        hasPermission = allowed
        // Starts listening straight away on a grant. Making someone tap the
        // mic a second time after they just agreed to it is a small insult.
        if (allowed) onToggleMic()
    }

    return {
        when {
            hasPermission -> onToggleMic()
            listening -> onToggleMic()
            else -> request.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/**
 * The agent said nothing at all.
 *
 * Rendered in the agent's own position and styling so the stream still reads as
 * a conversation with a turn in it — because there *was* a turn, it just
 * carried nothing. Saying so is the whole job: an empty reply the user can see
 * is a thing they can act on, and an empty reply they cannot is indistinguishable
 * from the app having eaten their message.
 */
@Composable
private fun EmptyTurn(modifier: Modifier = Modifier) {
    val colors = JarvisTheme.colors
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = Space.Gutter)) {
        Row(
            modifier = Modifier
                .clip(Corner.Lg)
                .background(colors.surfaceSunk, Corner.Lg)
                .padding(horizontal = Space.x4, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = colors.status.incomplete,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Space.x2))
            Text(
                text = "Jarvis didn’t answer that. Try asking again.",
                style = JarvisTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
        }
    }
}
