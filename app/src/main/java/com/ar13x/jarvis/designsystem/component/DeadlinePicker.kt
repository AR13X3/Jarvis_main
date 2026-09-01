package com.ar13x.jarvis.designsystem.component

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ar13x.jarvis.core.ui.Deadline
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Picking a deadline: a day, and an hour only if one is wanted.
 *
 * **The first date picker in this codebase**, and written as a shared component
 * for that reason rather than because two screens happened to need it — the
 * to-do detail screen and the capture sheet both open this one, and a sub-task's
 * deadline will open it too.
 *
 * **Most things want a day, not an hour.** So the date is the whole of the first
 * step and the time is a deliberate second one behind "Add a time". A single
 * combined sheet would make every capture answer a question — *what hour?* —
 * that almost nothing actually has an answer to, and the answer it would take by
 * default is a made-up precision that then shows on the row forever.
 *
 * The dialog hands back the [Instant] to send, **and the day that was tapped**.
 * The instant is what the gateway is sent, because `due_date` is the server's to
 * compute (§3.2) and the conversion belongs in exactly one place, which is
 * [Deadline.instantOf]. The day comes with it so the caller can check the
 * server's answer against the question without re-deriving a calendar day from
 * the instant — which is the very thing §3.2 forbids, and which would make the
 * check agree with itself by construction and catch nothing.
 *
 * @param initialDay the day to open on — pass the **server's** `due_date`, never
 *   a day derived from the stored instant. `null` opens on today.
 * @param initialTime the hour to seed the time picker with, from
 *   [Deadline.timeOf]. `null`, or [Deadline.EndOfDay], means this deadline names
 *   no hour and the time step starts closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinePickerDialog(
    onDismiss: () -> Unit,
    onPick: (dueAt: Instant, day: LocalDate) -> Unit,
    initialDay: LocalDate? = null,
    initialTime: LocalTime? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val today = remember(zone) { LocalDate.now(zone) }
    val opensOn = initialDay ?: today

    val dateState = rememberDatePickerState(
        // `selectedDateMillis` is defined as UTC-midnight of the chosen day —
        // the picker's own encoding of a bare calendar date, not a moment in
        // time. So UTC is used on the way in and on the way back out, and that
        // is the inverse of how the value was encoded rather than the §3.2
        // mistake it resembles. The zone only enters when the day becomes an
        // instant, which happens once, in `Deadline.instantOf`.
        initialSelectedDateMillis = opensOn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    // Held here rather than read back from the time picker, so that stepping
    // into the time stage and cancelling out of it leaves the deadline as it
    // was instead of silently committing whatever the dial happened to show.
    var time by remember { mutableStateOf(initialTime?.takeIf { it != Deadline.EndOfDay }) }
    var pickingTime by remember { mutableStateOf(false) }

    val chosenDay: LocalDate? = dateState.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
    }

    fun commit() {
        val day = chosenDay ?: return
        onPick(Deadline.instantOf(day, time ?: Deadline.EndOfDay, zone), day)
    }

    if (pickingTime) {
        val context = LocalContext.current
        val seed = time ?: LocalTime.of(9, 0)
        val timeState = rememberTimePickerState(
            initialHour = seed.hour,
            initialMinute = seed.minute,
            // The device's own 12/24 setting. Guessing either way makes the
            // dial disagree with every other clock on the phone.
            is24Hour = DateFormat.is24HourFormat(context),
        )

        AlertDialog(
            onDismissRequest = { pickingTime = false },
            containerColor = JarvisTheme.colors.surface,
            title = { Text("At what time?", style = JarvisTheme.typography.titleLarge) },
            text = { TimePicker(state = timeState) },
            dismissButton = {
                // Goes back to the day rather than closing the whole dialog:
                // the day is already chosen and throwing it away to correct an
                // hour would be a punishment for changing your mind.
                TextButton(onClick = { pickingTime = false }) { Text("Back") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        time = LocalTime.of(timeState.hour, timeState.minute)
                        pickingTime = false
                        commit()
                    },
                ) { Text("Set deadline") }
            },
        )
        return
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                TextButton(
                    enabled = chosenDay != null,
                    onClick = { pickingTime = true },
                ) {
                    // Says what it adds, and reads as optional. "Time" alone
                    // would look like a required second field.
                    Text(if (time == null) "Add a time" else "Change time")
                }
                TextButton(enabled = chosenDay != null, onClick = ::commit) {
                    Text("Set deadline")
                }
            }
        },
    ) {
        DatePicker(
            state = dateState,
            // A deadline in the past is legal and is not rare — recording
            // something that was due last Tuesday is an ordinary thing to do,
            // and a picker that refused it would be an opinion the domain does
            // not hold.
            title = null,
        )
    }
}
