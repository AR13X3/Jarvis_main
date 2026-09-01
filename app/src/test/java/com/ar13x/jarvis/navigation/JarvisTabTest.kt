package com.ar13x.jarvis.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom bar is icon-only as of §9.2, which moves a label from being
 * *helpful* to being **the only name a tab has**.
 *
 * `JarvisTab.label` is passed straight to the icon's `contentDescription`, so a
 * blank or duplicated one is not a cosmetic slip — it is a tab that TalkBack
 * either cannot announce or announces identically to another. Nothing else in
 * the app would fail if that happened, which is exactly why it is asserted here.
 */
class JarvisTabTest {

    @Test
    fun `there are five tabs, and the two new ones are among them`() {
        // Joy asked for the dashboard and to-dos as tabs by name (§9.2), and
        // neither is demoted back to a nested screen. Pinned so that "five tabs
        // is a lot" does not quietly become four again.
        assertEquals(5, JarvisTab.entries.size)
        assertTrue(JarvisTab.Todos in JarvisTab.entries)
        assertTrue(JarvisTab.Dashboard in JarvisTab.entries)
    }

    @Test
    fun `every tab has a name a screen reader can read`() {
        for (tab in JarvisTab.entries) {
            assertTrue(
                "tab " + tab.name + " has no label, so it has no accessible name",
                tab.label.isNotBlank(),
            )
        }
    }

    @Test
    fun `no two tabs share a name`() {
        val labels = JarvisTab.entries.map { it.label }

        assertEquals(
            "two tabs announcing the same word are indistinguishable without sight",
            labels.size,
            labels.toSet().size,
        )
    }

    @Test
    fun `the reminders tab is not called Tasks, because the to-do tab is the plan's Tasks`() {
        // v2 plan §9 names the four surfaces Reminders, Tasks, Routine and
        // Dashboard: its *Reminders* is this enum's `Tasks` and its *Tasks* is
        // `Todos`. With both on the bar, two labels containing "task" would be
        // the confusion this screen was split up to end.
        assertEquals("Reminders", JarvisTab.Tasks.label)
        assertEquals("To-dos", JarvisTab.Todos.label)
    }
}
