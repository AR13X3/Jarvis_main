package com.ar13x.jarvis.core.voice

import com.ar13x.jarvis.core.model.AgentComponent
import com.ar13x.jarvis.core.model.AgentResponse
import com.ar13x.jarvis.core.model.ProposalAction
import com.ar13x.jarvis.core.ui.DueDateFormat

/**
 * Turns a reply into something worth hearing.
 *
 * The gateway sends structure, not prose (plan §4.5): `text` carries the
 * sentence and `components` carry the cards. Speaking only `text` would read
 * out "Just to confirm —" and then stop, leaving the part that matters on the
 * screen. So a confirmation is spoken too.
 *
 * **This is the case the whole feature has to get right.** Parent plan §2.5
 * measured the model emitting a malformed day set roughly 1 in 5 on the "last
 * Friday of every month" pattern, and a wrong recurrence is only ever caught by
 * a human checking it *before* confirming. Someone listening rather than
 * looking must therefore hear the recurrence, not a summary that skips it —
 * which is the spoken equivalent of §4.5's rule that `recurrence_text` is never
 * collapsed behind a details affordance.
 *
 * Markdown is stripped rather than rendered: `**Tuesday**` should be heard as
 * Tuesday, not as asterisks.
 */
fun AgentResponse.toUtterance(): String {
    val parts = mutableListOf<String>()

    text.stripMarkdown().takeIf { it.isNotBlank() }?.let(parts::add)

    components.filterIsInstance<AgentComponent.Confirm>()
        .firstOrNull()
        ?.let { parts += it.spoken() }

    // Options are deliberately not read out. Three titles and their dates make
    // a list nobody can hold in their head, and tapping one is the actual next
    // step — so the screen is the right place for them.
    return parts.joinToString(" ")
}

private fun AgentComponent.Confirm.spoken(): String {
    val s = summary
    val verb = when (s.action) {
        ProposalAction.Create -> "Create"
        ProposalAction.Update -> "Update"
        ProposalAction.Cancel -> "Cancel"
        ProposalAction.Complete -> "Complete"
    }

    val sentence = StringBuilder(verb).append(": ").append(s.title.stripMarkdown())

    // Recurrence before the one-off date: for a repeating task the rule *is*
    // the schedule, and hearing "every Tuesday" is what makes a wrong day set
    // catchable.
    s.recurrenceText?.takeIf { it.isNotBlank() }?.let { sentence.append(", ").append(it) }

    DueDateFormat.forProposal(s.dueDate, s.dueAt)?.let { sentence.append(", ").append(it) }

    if (s.isPriority) sentence.append(", marked priority")

    // Says out loud that nothing has happened yet. Someone who is listening
    // rather than looking has no card in front of them, and the single most
    // important property of this system is that nothing is written until it is
    // confirmed (§3, guardrail 4).
    sentence.append(". Tap confirm to go ahead.")
    return sentence.toString()
}

/**
 * Removes the inline markdown the agent's prose carries.
 *
 * Same four delimiters `InlineMarkdown` renders visually. Not shared with it
 * because that one produces an `AnnotatedString` for the screen and this needs
 * plain characters for a speech engine.
 */
private fun String.stripMarkdown(): String =
    replace(MARKDOWN_DELIMITERS, "").replace(WHITESPACE, " ").trim()

private val MARKDOWN_DELIMITERS = Regex("""\*\*|~~|[*_`]""")
private val WHITESPACE = Regex("""\s+""")
