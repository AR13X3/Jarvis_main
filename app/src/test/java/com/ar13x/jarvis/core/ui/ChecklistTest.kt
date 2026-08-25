package com.ar13x.jarvis.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklists inside a description.
 *
 * The stake is not the parsing, it is the *rewriting*. The agent writes
 * descriptions too, and this feature edits them in place — so a toggle that
 * reformatted prose, normalised a bullet, or lost indentation would be
 * destroying the user's text in order to tick a box. Most of these tests exist
 * to prove that nothing else moves.
 */
class ChecklistTest {

    private val description = """
        Apply for the warehouse job again and other Christmas casual jobs.

        - [ ] David Jones Warehouse
        - [x] Update the CV
        - [ ] Ask Sam about the referral

        Deadline is the end of the month.
    """.trimIndent()

    @Test
    fun `items are found in order, with their state`() {
        val items = description.checklistItems()

        assertEquals(3, items.size)
        assertEquals(listOf("David Jones Warehouse", "Update the CV", "Ask Sam about the referral"), items.map { it.text })
        assertEquals(listOf(false, true, false), items.map { it.done })
    }

    @Test
    fun `toggling flips only that item`() {
        val items = description.checklistItems()
        val after = description.toggleChecklistItem(items[0].line).checklistItems()

        assertEquals(listOf(true, true, false), after.map { it.done })
    }

    @Test
    fun `toggling a done item unticks it`() {
        val items = description.checklistItems()
        val after = description.toggleChecklistItem(items[1].line).checklistItems()

        assertEquals(listOf(false, false, false), after.map { it.done })
    }

    /** The one that matters. Prose is the user's, and the agent's. */
    @Test
    fun `every line that is not the toggled checkbox survives byte for byte`() {
        val items = description.checklistItems()
        val before = description.lines()
        val after = description.toggleChecklistItem(items[0].line).lines()

        assertEquals("line count must not change", before.size, after.size)
        before.indices.filter { it != items[0].line }.forEach { i ->
            assertEquals("line $i was altered", before[i], after[i])
        }
    }

    @Test
    fun `indentation and bullet style are preserved`() {
        val odd = "  * [ ] indented with a star\n+ [X] plus and capital X"
        val toggled = odd.toggleChecklistItem(0)

        assertEquals("  * [x] indented with a star", toggled.lines()[0])
        assertEquals("the other line is untouched", "+ [X] plus and capital X", toggled.lines()[1])
    }

    /** A sentence mentioning brackets is prose and must stay prose. */
    @Test
    fun `text merely containing brackets is not a checkbox`() {
        val prose = "Check the box [ ] on the form before sending it."

        assertFalse(prose.hasChecklist())
        assertTrue(prose.checklistItems().isEmpty())
        assertEquals(prose, prose.toggleChecklistItem(0))
    }

    @Test
    fun `a description with no checklist reports none`() {
        assertFalse("Just some prose.".hasChecklist())
        assertTrue("Just some prose.".checklistItems().isEmpty())
    }

    /**
     * The real invariant, and the one nobody wrote down: `hasChecklist` is only
     * ever used to decide whether to draw what `checklistItems` returns, so the
     * two disagreeing is the bug, whichever way round it happens.
     *
     * Every `hasChecklist` assertion before this one was a negative, and a
     * function that always returned false passed all of them.
     */
    @Test
    fun `hasChecklist agrees with checklistItems`() {
        val cases = listOf(
            description,
            "- [ ] milk",
            "Shopping:\n- [ ] milk",
            "- [x] done\nand a trailing note",
            "  + [X] indented last line",
            "Just some prose.",
            "Check the box [ ] on the form.",
            "",
        )

        for (case in cases) {
            assertEquals(
                "hasChecklist disagreed with checklistItems for: " + case,
                case.checklistItems().isNotEmpty(),
                case.hasChecklist(),
            )
        }
    }

    /**
     * The agent can rewrite a description between the list being drawn and a
     * box being tapped. A stale index is a race, not a crash.
     */
    @Test
    fun `an out of range or non-checkbox line changes nothing`() {
        assertEquals(description, description.toggleChecklistItem(99))
        assertEquals(description, description.toggleChecklistItem(-1))
        assertEquals("line 0 is prose", description, description.toggleChecklistItem(0))
    }

    @Test
    fun `an empty item is still an item`() {
        val items = "- [ ] ".checklistItems()

        assertEquals(1, items.size)
        assertEquals("", items.single().text)
    }

    @Test
    fun `toggling twice returns the original exactly`() {
        val items = description.checklistItems()
        val there = description.toggleChecklistItem(items[0].line)
        val back = there.toggleChecklistItem(items[0].line)

        assertEquals(description, back)
    }
}
