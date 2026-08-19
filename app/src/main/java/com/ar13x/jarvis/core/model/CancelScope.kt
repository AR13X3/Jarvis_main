package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What "cancel" means for a **recurring** task.
 *
 * A recurring task is not one thing: the parent row is a rule, and each firing
 * is an occurrence with its own status (parent plan §2.5 — that is the whole
 * reason `tasks.occurrences` exists, so that marking Monday's gym session done
 * does not close the weekly reminder). Cancel has the same shape: skipping this
 * week and calling the whole thing off are different intentions, and a single
 * confirm button cannot express both.
 *
 * One-shot tasks have exactly one occurrence, so the distinction collapses and
 * the dialog never offers it.
 *
 * **Contract addition** — see BUILD_NOTES §3.4. §4.4 defines only
 * `POST /tasks/{id}/cancel {confirm:true}`, which has no way to say which of
 * these was meant.
 */
@Serializable
enum class CancelScope {
    /** Skip this firing. The rule survives and fires again next time. */
    @SerialName("occurrence") Occurrence,

    /** Call the whole thing off. The task becomes `cancelled`. */
    @SerialName("series") Series,
}
