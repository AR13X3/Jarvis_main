package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.data.SummaryRepository
import com.ar13x.jarvis.core.model.Summary
import com.ar13x.jarvis.core.model.SummaryPeriod
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Summaries against the gateway. A translation layer and nothing more — no
 * cache, no local aggregation, no write queue (plan §3.5).
 *
 * There is no fake alongside this one, unlike every other repository here, and
 * that is deliberate. A summary is *facts the gateway counted* plus *prose a
 * model wrote from them*; a fixture would have to invent both, and invented
 * prose about invented arithmetic on a screen whose entire purpose is telling
 * Joy what actually happened is the one fiction this app must not contain.
 * Off the tailnet the screen says it could not reach the gateway.
 */
@Singleton
class RemoteSummaryRepository @Inject constructor(
    private val api: JarvisApi,
) : SummaryRepository {

    override suspend fun summaries(
        period: SummaryPeriod?,
        tag: String?,
        limit: Int,
    ): List<Summary> = gatewayCall {
        api.summaries(period = period?.wireName(), tag = tag, limit = limit).summaries
    }

    override suspend fun generate(
        period: SummaryPeriod,
        day: LocalDate?,
        tag: String?,
        factsOnly: Boolean,
        replace: Boolean,
    ): Summary = gatewayCall(mutating = true) {
        api.generateSummary(
            GenerateSummaryBody(
                period = period.wireName(),
                day = day,
                tag = tag,
                factsOnly = factsOnly,
                replace = replace,
            ),
        )
    }
}

/**
 * The wire spelling, written out rather than `name.lowercase()`.
 *
 * An exhaustive `when` fails to compile when a value is added; `lowercase()`
 * would keep compiling and start sending a string the gateway may not know. And
 * an enum the app spells differently fails **silently** here — `JarvisJson` sets
 * `coerceInputValues`, so a bad value read back becomes the default rather than
 * throwing. That is the `OverdueResolution` bug, and it cost real behaviour.
 */
fun SummaryPeriod.wireName(): String = when (this) {
    SummaryPeriod.Daily -> "daily"
    SummaryPeriod.Weekly -> "weekly"
}
