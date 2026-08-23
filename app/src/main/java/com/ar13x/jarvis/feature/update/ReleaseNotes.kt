package com.ar13x.jarvis.feature.update

/**
 * A GitHub release body, made safe to show on a phone.
 *
 * **The release body is an app surface**, which is easy to forget because it is
 * authored on a web page. `UpdateCard` renders it through `toInlineMarkdown`,
 * which understands exactly four inline delimiters — `` ` ``, `**`, `~~`, `*`
 * and `_` — and nothing block-level. So a note written the way release notes are
 * normally written arrives as literal characters: `- ` in front of every bullet,
 * a row of dashes where a rule was, `##` in front of a heading.
 *
 * The process answer is to write notes inline-only and put the summary first.
 * That is correct and it is written down (BUILD_NOTES §8.9), but it is a rule
 * someone has to remember at the exact moment they are thinking about something
 * else. This is the other half: notes written for a web page still read as prose
 * on the phone.
 *
 * Deliberately conservative — it removes block *markers*, never content. A line
 * that was a bullet becomes a line. Nothing is reordered, nothing is summarised,
 * and the inline emphasis `toInlineMarkdown` does understand is left untouched.
 */
fun String.asBannerNotes(): String {
    val cleaned = lineSequence()
        .map { it.trim() }
        // A horizontal rule is pure decoration on a web page and pure noise in
        // six lines. The convention of putting one between a summary and the
        // detail makes it the *most* likely thing to land mid-banner.
        .filterNot { it.isHorizontalRule() }
        .map { it.stripBlockMarkers() }
        .toList()

    // Blank lines survive as paragraph breaks, but a run of them wastes lines
    // the banner does not have.
    val collapsed = mutableListOf<String>()
    for (line in cleaned) {
        if (line.isEmpty() && collapsed.lastOrNull()?.isEmpty() != false) continue
        collapsed += line
    }
    return collapsed.joinToString("\n").trim()
}

private fun String.isHorizontalRule(): Boolean =
    HORIZONTAL_RULE.matches(this)

private fun String.stripBlockMarkers(): String = this
    .replace(HEADING, "")
    .replace(BLOCKQUOTE, "")
    .replace(BULLET, "")
    .replace(NUMBERED, "")

/** `---`, `***`, `___`, with or without spaces between. */
private val HORIZONTAL_RULE = Regex("""^([-*_])(\s*\1){2,}$""")

private val HEADING = Regex("""^#{1,6}\s+""")
private val BLOCKQUOTE = Regex("""^>\s?""")

/**
 * The trailing space is load-bearing. `*emphasis*` starts with an asterisk and
 * must survive; a bullet is an asterisk *followed by whitespace*.
 */
private val BULLET = Regex("""^[-*+]\s+""")

private val NUMBERED = Regex("""^\d+[.)]\s+""")
