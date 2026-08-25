package com.ar13x.jarvis.core.ui

/**
 * Checklists, living inside a task's description.
 *
 * **Why the description and not a table.** Descriptions are already being used
 * as lists — "apply for the warehouse job again, and other Christmas casual
 * jobs, apply at David Jones Warehouse" arrived one clause at a time, each
 * costing a full conversational turn. This formalises what was already
 * happening rather than betting on a habit that might not form, and it needs one
 * field on `PATCH /tasks/{id}` instead of a schema, endpoints and agent tools.
 *
 * If checklists become load-bearing, `tasks.checklist_items` is the right home —
 * and this migrates into it, because parsing `- [ ]` lines into rows is a
 * migration rather than a rewrite.
 *
 * **The rule that matters: nothing that is not a checkbox line is touched.** The
 * agent writes descriptions too, and a toggle that reformatted prose, changed
 * indentation, or normalised markers would be destroying the user's text to
 * tick a box. Every function here preserves the original line except the single
 * checkbox being flipped, and [ChecklistTest] pins that.
 */
data class ChecklistItem(
    /** Index of the line in the original description. The toggle key. */
    val line: Int,
    val text: String,
    val done: Boolean,
)

/** The checkbox lines in a description, in order. Empty when there are none. */
fun String.checklistItems(): List<ChecklistItem> =
    lineSequence().mapIndexedNotNull { index, line ->
        CHECKBOX.matchEntire(line)?.let { match ->
            ChecklistItem(
                line = index,
                text = match.groupValues[4].trim(),
                done = match.groupValues[3].lowercase() == "x",
            )
        }
    }.toList()

/**
 * Flips the checkbox on [line] and returns the whole description.
 *
 * Rewrites **only the marker character**, so indentation, the bullet style, the
 * spacing and the text all survive byte for byte. A line that is not a checkbox,
 * or an index out of range, returns the description unchanged rather than
 * throwing — a stale index from a description the agent edited underneath us is
 * a race, not a bug worth crashing on.
 */
fun String.toggleChecklistItem(line: Int): String {
    val lines = lines()
    val original = lines.getOrNull(line) ?: return this
    val match = CHECKBOX.matchEntire(original) ?: return this

    val (indent, marker, state) = Triple(
        match.groupValues[1],
        match.groupValues[2],
        match.groupValues[3],
    )
    val flipped = if (state.lowercase() == "x") " " else "x"
    val rebuilt = indent + marker + " [" + flipped + "]" + original.substringAfter("]", "")

    return lines.toMutableList().also { it[line] = rebuilt }.joinToString("\n")
}

/**
 * True when there is anything to tick.
 *
 * Split into lines like [checklistItems] rather than searched with
 * `containsMatchIn`, and deliberately: [CHECKBOX] is anchored, and without
 * `MULTILINE` those anchors bind to the whole description rather than to each
 * line. Searching it therefore answered true only when the description *was* a
 * single bare checkbox line, and false for the ordinary case of a list with a
 * sentence above it. Sharing the line-splitting is what stops the two functions
 * from ever disagreeing again; the test pins that as one property rather than
 * as two separate ones.
 */
fun String.hasChecklist(): Boolean = lineSequence().any(CHECKBOX::matches)

/**
 * `- [ ] thing`, `* [x] thing`, `  + [X] thing`.
 *
 * Groups: indent, bullet marker, state character, text. Anchored with
 * `matchEntire` at the call sites so a sentence merely *containing* `[ ]`
 * somewhere is prose and stays prose.
 */
private val CHECKBOX = Regex("""^(\s*)([-*+])\s+\[([ xX])]\s*(.*)$""")
