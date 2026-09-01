package com.ar13x.jarvis.feature.todos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.ui.Deadline
import com.ar13x.jarvis.core.ui.DueDateFormat
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.DeadlinePickerDialog
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Capturing a to-do.
 *
 * **Direct, with no proposal, and that is §5.4's rule rather than an exception
 * to it.** A task is proposed-and-confirmed because the model parses "next
 * Thursday" and can misread it; a title typed into a field cannot be misparsed.
 * Nothing here goes near the agent.
 *
 * **The deadline here is optional and secondary, and that is a correction to
 * what this comment used to say.** It read "no deadline field, deliberately",
 * on the argument that §5.2 makes an undated to-do the ordinary case and that a
 * date is one tap away on the detail screen. The first half of that is still
 * true and is why the field is a quiet row rather than a third box you tab
 * through. The second half was wrong: **there was no way to set a deadline on
 * the detail screen either** — only to clear one — so "one tap away" pointed at
 * a tap that did not exist, and the two halves of the reasoning were each
 * relying on the other. Capture stays one field for the case that matters most;
 * the deadline is there for the note that arrives already knowing when it is due.
 */
@Composable
fun NewTodoSheet(
    onCreate: (title: String, description: String, tags: List<String>, dueAt: Instant?) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = JarvisTheme.colors
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tagText by remember { mutableStateOf("") }

    // Both halves are kept: the instant is what gets sent, and the day is what
    // was tapped. The day is only ever used to draw this row — the server has
    // not been asked yet, so there is no `due_date` to prefer over it, and this
    // is the one moment where the phone's own reading of the day is the
    // authoritative one because it is the question rather than the answer.
    var dueAt by remember { mutableStateOf<Instant?>(null) }
    var dueDay by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    val tags = remember(tagText) {
        tagText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    if (picking) {
        DeadlinePickerDialog(
            onDismiss = { picking = false },
            onPick = { instant, day ->
                dueAt = instant
                dueDay = day
                picking = false
            },
            initialDay = dueDay,
            initialTime = dueAt?.let { Deadline.timeOf(it, zone) },
            zone = zone,
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.Gutter, vertical = Space.x2),
    ) {
        Text("New to-do", style = JarvisTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(Space.x4))

        Field(
            value = title,
            onValueChange = { title = it },
            placeholder = "What is it?",
            minHeight = 44.dp,
        )
        Spacer(Modifier.height(Space.x2))
        Field(
            value = description,
            onValueChange = { description = it },
            placeholder = "Any detail worth keeping (optional)",
            minHeight = 88.dp,
            maxHeight = 160.dp,
        )
        Spacer(Modifier.height(Space.x2))
        Field(
            value = tagText,
            onValueChange = { tagText = it },
            // Comma-separated rather than a picker: tags are the summary
            // subscription's taxonomy (§7) and the set is small and known —
            // Reskill, CBAI, Uni. A picker would need a management surface for
            // three values.
            placeholder = "Tags, comma separated (optional)",
            minHeight = 44.dp,
            capitalization = KeyboardCapitalization.Words,
        )

        Spacer(Modifier.height(Space.x2))

        // A row rather than a field: it does not take focus, it does not join
        // the tab order, and skipping it costs nothing. That is the whole of
        // "optional and secondary" — the undated to-do stays a one-field capture.
        val deadline = DueDateFormat.forTodo(dueDay, dueAt, zone)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Corner.Sm)
                .clickable(
                    onClickLabel = if (dueAt == null) "Add a deadline" else "Change the deadline",
                    onClick = { picking = true },
                )
                .padding(horizontal = Space.x3, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            Icon(
                Icons.Rounded.EditCalendar,
                contentDescription = null,
                tint = if (dueAt == null) colors.inkMuted else colors.brandCore,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = deadline ?: "Add a deadline (optional)",
                style = JarvisTheme.typography.bodyMedium,
                color = if (dueAt == null) colors.inkMuted else colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (dueAt != null) {
                CircleIconButton(
                    onClick = { dueAt = null; dueDay = null },
                    diameter = 28.dp,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove the deadline",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.x4))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.height(Space.x2))
            TextButton(
                // A blank title is the only thing that can be wrong here, and
                // the button being dead says so without an error message
                // appearing after the fact.
                enabled = title.isNotBlank(),
                onClick = { onCreate(title.trim(), description.trim(), tags, dueAt) },
            ) {
                Text("Add")
            }
        }
        Spacer(Modifier.height(Space.x2))
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp = minHeight,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val colors = JarvisTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Corner.Sm)
            .background(colors.surfaceSunk, Corner.Sm)
            .then(
                if (colors.isDark) Modifier.border(1.dp, colors.hairline, Corner.Sm) else Modifier,
            )
            .padding(horizontal = Space.x3, vertical = Space.x2),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.merge(
                JarvisTheme.typography.bodyLarge.copy(color = colors.ink),
            ),
            cursorBrush = SolidColor(colors.brandCore),
            // Enter inserts a line; it does not submit. Same rule as the chat
            // composer, and for the same reason — the description is meant to
            // hold more than one line.
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default,
                capitalization = capitalization,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = JarvisTheme.typography.bodyLarge,
                            color = colors.inkMuted,
                        )
                    }
                    field()
                }
            },
        )
    }
}
