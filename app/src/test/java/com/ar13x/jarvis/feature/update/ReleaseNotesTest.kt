package com.ar13x.jarvis.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release body is an app surface, and it is authored somewhere that gives
 * no hint of that. These pin the failure mode: markdown a web page renders
 * arrives on the phone as literal characters, inside six lines.
 */
class ReleaseNotesTest {

    @Test
    fun `bullets become lines`() {
        val notes = """
            - Overdue section
            * Past-time warning
            + Another
            1. Numbered
            2) Also numbered
        """.trimIndent().asBannerNotes()

        assertEquals(
            "Overdue section\nPast-time warning\nAnother\nNumbered\nAlso numbered",
            notes,
        )
    }

    /** The convention of a rule between summary and detail puts it mid-banner. */
    @Test
    fun `horizontal rules are dropped`() {
        val notes = "Summary line\n\n---\n\nDetail line".asBannerNotes()
        assertFalse(notes, notes.contains("---"))
        assertEquals("Summary line\n\nDetail line", notes)
    }

    @Test
    fun `headings lose their hashes but keep their words`() {
        assertEquals("Fixed\nSomething", "## Fixed\n### Something".asBannerNotes())
    }

    @Test
    fun `blockquotes lose their marker`() {
        assertEquals("A quoted note", "> A quoted note".asBannerNotes())
    }

    /**
     * The one that would break if the bullet rule were written carelessly:
     * `*emphasis*` and `**bold**` start with an asterisk too, and
     * toInlineMarkdown still has to see them.
     */
    @Test
    fun `inline emphasis survives`() {
        val notes = "**Talk to Jarvis**, and let it *listen*. Use `Check now`.".asBannerNotes()
        assertEquals("**Talk to Jarvis**, and let it *listen*. Use `Check now`.", notes)
    }

    @Test
    fun `a bullet holding bold text keeps the bold`() {
        assertEquals("**Voice.** Dictate.", "- **Voice.** Dictate.".asBannerNotes())
    }

    /** Six lines is the whole budget, so a run of blanks is expensive. */
    @Test
    fun `runs of blank lines collapse to one`() {
        assertEquals("First\n\nSecond", "First\n\n\n\nSecond".asBannerNotes())
    }

    @Test
    fun `prose written for the app is left exactly alone`() {
        val written = "**Overdue tasks now sit at the top of the list.**\n\n" +
            "A task you set for 6:40 used to look like everything else at 6:45."
        assertEquals(written, written.asBannerNotes())
    }

    @Test
    fun `empty and blank are safe`() {
        assertEquals("", "".asBannerNotes())
        assertEquals("", "\n\n   \n".asBannerNotes())
    }

    /** Nothing is summarised or reordered — markers go, content stays. */
    @Test
    fun `no words are lost`() {
        val raw = "## Fixed\n- The **first** thing\n- The second thing\n---\nTrailer."
        val out = raw.asBannerNotes()
        listOf("Fixed", "first", "thing", "second", "Trailer.").forEach {
            assertTrue("lost: $it", out.contains(it))
        }
    }

    // --- tables and fences ---------------------------------------------------

    /**
     * The case worth caring about: a version-comparison table reads well on a
     * web page, so someone will put one in the first block, where the six-line
     * cap cannot save it.
     */
    @Test
    fun `a table becomes readable lines`() {
        val notes = TABLE.asBannerNotes()
        assertEquals("Version \u00b7 Change\n0.1.6 \u00b7 Chips from the server", notes)
    }

    @Test
    fun `alignment markers in the delimiter row are still recognised`() {
        assertEquals("A \u00b7 B", "| A | B |\n|:--|--:|".asBannerNotes())
    }

    @Test
    fun `code fences are dropped and their contents kept`() {
        assertEquals(
            "Run this:\ngh release create v1.0.0",
            "Run this:\n```bash\ngh release create v1.0.0\n```".asBannerNotes(),
        )
    }

    /**
     * A pipe in a sentence is prose, not a table, and prose is not ours to
     * touch. The discriminator is the leading pipe.
     */
    @Test
    fun `a sentence containing a pipe is left alone`() {
        val prose = "Piped through grep | head, as usual."
        assertEquals(prose, prose.asBannerNotes())
    }

    /** A rule has no pipe, so the two checks must not eat each other. */
    @Test
    fun `rules and table delimiters do not collide`() {
        assertEquals("Before\nAfter", "Before\n---\nAfter".asBannerNotes())
        assertEquals("Before\nAfter", "Before\n|---|---|\nAfter".asBannerNotes())
    }

    /** No dash means it is not a delimiter row, so it survives as content. */
    @Test
    fun `a row with no dashes survives as content`() {
        assertEquals("a \u00b7 b", "| a | b |".asBannerNotes())
    }

    private companion object {
        val TABLE = listOf(
            "| Version | Change |",
            "|---------|--------|",
            "| 0.1.6   | Chips from the server |",
        ).joinToString("\n")
    }
}
