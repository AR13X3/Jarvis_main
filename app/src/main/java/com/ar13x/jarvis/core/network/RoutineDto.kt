package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.model.CategoryClass
import com.ar13x.jarvis.core.model.InstantSerializer
import com.ar13x.jarvis.core.model.LocalDateSerializer
import com.ar13x.jarvis.core.model.LocalTimeSerializer
import com.ar13x.jarvis.core.model.Routine
import com.ar13x.jarvis.core.model.RoutineCategory
import com.ar13x.jarvis.core.model.RoutineDay
import com.ar13x.jarvis.core.model.RoutineSlot
import com.ar13x.jarvis.core.model.SlotKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The routine on the wire, kept separate from the domain model it becomes.
 *
 * Every other model in this app deserialises straight into the type the UI
 * uses, and that is the better default — one type, no mapper, nothing to drift.
 * This one is the exception for a reason that is not stylistic: **the weekday
 * mapping cannot be done field by field.** See [RoutineDto.toDomain].
 *
 * The second reason is `started_at`: the gateway stores an instant, the day view
 * compares wall-clock times, and the conversion needs a zone. A serializer is
 * the wrong place to decide a zone, so it happens at the repository boundary
 * where it can be named.
 */
@Serializable
data class RoutineDto(
    @SerialName("routine_id") val routineId: Long,
    val name: String,
    @SerialName("version_id") val versionId: Long,
    @Serializable(LocalDateSerializer::class)
    @SerialName("effective_from") val effectiveFrom: LocalDate,
    val notes: List<String> = emptyList(),
    val categories: List<RoutineCategoryDto> = emptyList(),
    val days: List<RoutineDayDto> = emptyList(),
    /**
     * Asked for in tracker 109 and **not yet served**. Present here so the day
     * it ships it needs no client change: `SlotRow.drifted` is already null
     * until a tolerance arrives, so the app degrades to "no verdict" rather
     * than to a number it invented.
     */
    @SerialName("drift_threshold_minutes") val driftThresholdMinutes: Int? = null,
) {

    /**
     * **The weekday encoding is not in the contract, so it is measured rather
     * than assumed.**
     *
     * `RoutineDay.weekday` is declared as a bare `integer` with no enum, no
     * range and no word about its base. Python has both conventions one letter
     * apart — `date.weekday()` is 0 = Monday, `date.isoweekday()` is 1 = Monday
     * — and picking the wrong one shifts the entire week by a day. That failure
     * is close to invisible: every slot is still there, every time is still
     * right, the routine is simply *wrong*, and it looks like bad data rather
     * than a client bug.
     *
     * So the base is derived from the payload, which can answer it: a complete
     * week is either exactly `{0..6}` or exactly `{1..7}`, and those sets do not
     * overlap. Anything else throws rather than being coerced — a partial or
     * unexpected set is precisely the case where a guess would be least
     * detectable and most damaging.
     *
     * This should be a one-line branch, not a function. Tracker 117 asks gw03 to
     * put the range in the contract; when it does, this collapses.
     */
    fun toDomain(): Routine {
        val numbers = days.map { it.weekday }.toSet()
        val base = when {
            numbers.isEmpty() -> 1
            numbers.all { it in 1..7 } -> 1
            numbers.all { it in 0..6 } -> 0
            else -> throw IllegalArgumentException(
                "Routine weekdays are neither 0-6 nor 1-7: " + numbers.sorted(),
            )
        }

        return Routine(
            routineId = routineId,
            versionId = versionId,
            name = name,
            effectiveFrom = effectiveFrom,
            categories = categories.map { it.toDomain() },
            days = days.map { it.toDomain(base) },
            notes = notes,
            driftToleranceMinutes = driftThresholdMinutes,
        )
    }
}

@Serializable
data class RoutineCategoryDto(
    val key: String,
    val label: String,
    val cls: String,
) {
    fun toDomain(): RoutineCategory = RoutineCategory(
        id = key,
        label = label,
        cls = when (cls) {
            "committed" -> CategoryClass.Committed
            "upkeep" -> CategoryClass.Upkeep
            else -> CategoryClass.Free
        },
    )
}

@Serializable
data class RoutineDayDto(
    val weekday: Int,
    @Serializable(LocalTimeSerializer::class)
    @SerialName("starts_at") val startsAt: LocalTime,
    @Serializable(LocalTimeSerializer::class)
    @SerialName("ends_at") val endsAt: LocalTime,
    val slots: List<RoutineSlotDto> = emptyList(),
    val note: String? = null,
) {
    /** [base] is 0 or 1 — whichever number the payload uses for Monday. */
    fun toDomain(base: Int): RoutineDay = RoutineDay(
        weekday = DayOfWeek.of(weekday - base + 1),
        startsAt = startsAt,
        endsAt = endsAt,
        // Ordered by `position` rather than by arrival. The day view splits on
        // list order, so a reordering in transit would silently rearrange the
        // day — and slots that cross midnight make "sort by start time" the
        // wrong fallback: Friday's 13:45–00:15 is followed by two later slots
        // whose times are numerically earlier.
        slots = slots.sortedBy { it.position }.map { it.toDomain() },
        note = note,
    )
}

@Serializable
data class RoutineSlotDto(
    val key: String,
    val label: String,
    @Serializable(LocalTimeSerializer::class)
    @SerialName("starts_at") val startsAt: LocalTime,
    @Serializable(LocalTimeSerializer::class)
    @SerialName("ends_at") val endsAt: LocalTime,
    @SerialName("category_key") val categoryKey: String,
    val kind: String,
    val position: Int = 0,
) {
    fun toDomain(): RoutineSlot = RoutineSlot(
        id = key,
        start = startsAt,
        end = endsAt,
        label = label,
        categoryId = categoryKey,
        kind = when (kind) {
            "tracked" -> SlotKind.Tracked
            "scaffold" -> SlotKind.Scaffold
            "buffer" -> SlotKind.Buffer
            // `free` and anything the gateway adds later. Falling back to Free
            // is the safe direction: an unknown kind that became Tracked would
            // start appearing in adherence numbers as a slot nobody can tick.
            else -> SlotKind.Free
        },
    )
}

/** `GET /routine/starts`. */
@Serializable
data class SlotStartsDto(val starts: List<SlotStartDto> = emptyList())

@Serializable
data class SlotStartDto(
    @SerialName("version_id") val versionId: Long,
    @SerialName("slot_key") val slotKey: String,
    @Serializable(LocalDateSerializer::class)
    val on: LocalDate,
    /**
     * An **instant**, not a wall-clock time. The day view compares it against
     * planned times, which are wall-clock, so somebody has to choose a zone —
     * and that happens in `RemoteRoutineRepository`, named, rather than hidden
     * in a serializer.
     */
    @Serializable(InstantSerializer::class)
    @SerialName("started_at") val startedAt: Instant,
    val clamped: Boolean = false,
) {
    /**
     * [zone] is the user's, and it is a parameter rather than
     * `ZoneId.systemDefault()` read in here — a conversion that reaches for an
     * ambient zone is one no test can pin, and this is the conversion most worth
     * pinning.
     *
     * Only the **time of day** comes off the instant. The day is [on] and
     * arrives from the server, which is what keeps this on the right side of
     * §3.2: a Speedway shift begun at 00:05 on Saturday still belongs to
     * Friday, and no arithmetic here could have worked that out.
     */
    fun toDomain(zone: ZoneId): com.ar13x.jarvis.core.data.SlotStart =
        com.ar13x.jarvis.core.data.SlotStart(
            versionId = versionId,
            on = on,
            slotId = slotKey,
            at = LocalDateTime.ofInstant(startedAt, zone),
            clamped = clamped,
        )
}

/** `POST /routine/starts`. */
@Serializable
data class RecordStartBody(
    @SerialName("version_id") val versionId: Long,
    @SerialName("slot_key") val slotKey: String,
    @Serializable(LocalDateSerializer::class)
    val on: LocalDate,
    @Serializable(InstantSerializer::class)
    @SerialName("started_at") val startedAt: Instant,
    val clamped: Boolean = false,
)

/**
 * `GET /routine/now` — the gateway's own answer to "what logical day is it".
 *
 * **A cross-check, not the source of truth** (§4.2). The app computes this
 * locally from the boundaries the routine declares, because the routine tab has
 * to render off the tailnet. This route exists so the two can be compared: if
 * they ever disagree, that is a bug worth finding, and it is only findable
 * because both exist.
 */
@Serializable
data class RoutineNowDto(
    @Serializable(InstantSerializer::class)
    val at: Instant,
    @SerialName("version_id") val versionId: Long,
    @Serializable(LocalDateSerializer::class)
    @SerialName("logical_day") val logicalDay: LocalDate? = null,
    val weekday: Int? = null,
    val clamped: Boolean = false,
    @SerialName("current_slot_key") val currentSlotKey: String? = null,
)
