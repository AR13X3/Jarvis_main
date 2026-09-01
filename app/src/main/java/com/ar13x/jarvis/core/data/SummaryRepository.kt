package com.ar13x.jarvis.core.data

import com.ar13x.jarvis.core.model.Summary
import com.ar13x.jarvis.core.model.SummaryPeriod
import java.time.LocalDate

/**
 * Summaries (v2 plan §7). The same §4 seam as every other repository.
 *
 * **Reading and writing are separate on purpose.** [summaries] is a cheap list;
 * [generate] costs a model call and real money, so it is never a side effect of
 * looking at the screen. gw03 made the same split on the wire — a `GET` and a
 * `POST` with a body — and it is worth preserving here rather than hiding
 * generation behind a refresh.
 */
interface SummaryRepository {

    suspend fun summaries(
        period: SummaryPeriod? = null,
        tag: String? = null,
        limit: Int = 30,
    ): List<Summary>

    /**
     * Writes one, and returns it.
     *
     * [factsOnly] stores the arithmetic without asking a model for prose. It is
     * the honest option when the sentences are not wanted, and a summary with
     * facts and no text is a legitimate result rather than a failed one.
     *
     * [replace] overwrites an existing summary for the same period and tag. The
     * default is not to, so asking twice by accident does not spend twice.
     */
    suspend fun generate(
        period: SummaryPeriod,
        day: LocalDate? = null,
        tag: String? = null,
        factsOnly: Boolean = false,
        replace: Boolean = false,
    ): Summary
}
