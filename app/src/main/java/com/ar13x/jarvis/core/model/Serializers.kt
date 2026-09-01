package com.ar13x.jarvis.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * All timestamps are ISO-8601 instants in UTC (plan §4.2) and parse to [Instant].
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}

/**
 * `due_date` / `scheduled_date` are **bare local calendar days** and parse to
 * [LocalDate]. They are *not* derived from the instants — see [Task.dueDate].
 */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}

/**
 * Routine boundaries and slot edges — `"08:45"` or `"08:45:00"`, a **wall-clock
 * time with no date and no zone**, which is exactly what a routine declares.
 *
 * [LocalTime.parse] accepts both the with-seconds and without-seconds forms, so
 * this does not care which the gateway emits.
 *
 * There is deliberately no zone. A routine day runs from waking to sleeping and
 * its boundaries are *declared*, not derived (v2 plan §4.2); attaching a zone to
 * one would be the same mistake as deriving a calendar day from a timestamp.
 */
object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalTime =
        LocalTime.parse(decoder.decodeString())
}
