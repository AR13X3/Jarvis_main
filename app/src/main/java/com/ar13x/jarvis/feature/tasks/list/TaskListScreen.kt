package com.ar13x.jarvis.feature.tasks.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ar13x.jarvis.core.model.TaskStatus
import com.ar13x.jarvis.designsystem.component.BrandBackdrop
import com.ar13x.jarvis.designsystem.component.CircleIconButton
import com.ar13x.jarvis.designsystem.component.JarvisCard
import com.ar13x.jarvis.designsystem.component.ScreenHeader
import com.ar13x.jarvis.designsystem.component.SectionHeader
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import com.ar13x.jarvis.designsystem.theme.statusStyle

/**
 * Phase A scaffold for the Tasks tab.
 *
 * The header, the `+`, and the section header are the real components phase B
 * builds on. The specimen strip below them is not — it exists so acceptance item
 * 6 ("`cancelled` and `incomplete` are visibly distinct, in both themes") can be
 * checked on the device now rather than after the list is wired, and it is
 * deleted when the real sections land.
 */
@Composable
fun TaskListScreen(
    onOpenTask: (Long) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JarvisTheme.colors

    BrandBackdrop(modifier = modifier.fillMaxSize(), washHeight = 300.dp) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.x12),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Nothing overdue",
                    headline = "What needs\ndoing today?",
                    centred = true,
                    leading = {
                        // The + is a brand-filled circle, and it is the only
                        // saturated element in the list — so the eye lands on it
                        // immediately (plan §6.7).
                        CircleIconButton(
                            onClick = onNewSession,
                            diameter = 44.dp,
                            background = colors.brandCore,
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "New task",
                                tint = colors.onBrand,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(Space.x8))
            }

            item { SectionHeader("Status specimen") }

            items(TaskStatus.entries, key = { it.name }) { status ->
                StatusSpecimenRow(
                    status = status,
                    onClick = { onOpenTask(1L) },
                    modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.x1),
                )
            }
        }
    }
}

@Composable
private fun StatusSpecimenRow(
    status: TaskStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = statusStyle(status)
    val colors = JarvisTheme.colors

    JarvisCard(modifier = modifier.fillMaxWidth().alpha(style.rowAlpha), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(
                color = style.accent,
                outlined = style.indicatorOutlined,
                fill = style.rowFill,
            )
            Spacer(Modifier.size(Space.x3))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Send the insurance paperwork",
                    style = JarvisTheme.typography.titleMedium,
                    color = style.titleColor,
                    textDecoration = style.titleDecoration,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = style.label,
                    style = JarvisTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

/**
 * Form carries the distinction as well as colour: `incomplete` is an outlined
 * ring, `cancelled` a filled grey dot behind a struck-through title. That is
 * what makes the two survive colour-blindness and a dark theme (plan §6.2).
 */
@Composable
private fun StatusDot(
    color: Color,
    outlined: Boolean,
    fill: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(12.dp)
            .clip(CircleShape)
            .then(
                if (outlined) {
                    Modifier.border(2.dp, color, CircleShape)
                } else {
                    Modifier.background(if (fill != Color.Transparent) fill else color, CircleShape)
                },
            ),
    )
}
