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
}
