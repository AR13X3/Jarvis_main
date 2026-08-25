package com.ar13x.jarvis.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the About screen shows under "What's new".
 *
 * The reason this is a list and not just the newest release: someone on 0.1.3
 * when 0.1.7 lands has missed three releases, and the latest one describes only
 * the last of them. "What changed" has to mean everything since the build in
 * your hand, or the screen quietly hides two thirds of the answer.
 */
class WhatsNewTest {

    private fun release(tag: String, body: String = "notes for $tag", draft: Boolean = false, pre: Boolean = false) =
        GithubRelease(tagName = tag, body = body, draft = draft, prerelease = pre, publishedAt = "2026-08-23T06:52:11Z")

    private val channel = listOf(
        release("v0.1.6"), release("v0.1.5"), release("v0.1.3"), release("v0.1.2"), release("v0.1.1"),
    )

    @Test
    fun `a build three releases behind sees all three, plus its own`() {
        val notes = channel.toReleaseNotes(SemVer(0, 1, 3))

        assertEquals(listOf("0.1.6", "0.1.5", "0.1.3"), notes.map { it.version.toString() })
    }

    @Test
    fun `the installed build is marked and the others are not`() {
        val notes = channel.toReleaseNotes(SemVer(0, 1, 3))

        assertTrue(notes.single { it.version == SemVer(0, 1, 3) }.current)
        assertFalse(notes.single { it.version == SemVer(0, 1, 6) }.current)
    }

    /** Newest first — the thing you would install is the thing you read first. */
    @Test
    fun `order is newest first, not the order github returned`() {
        val shuffled = listOf(release("v0.1.1"), release("v0.1.6"), release("v0.1.3"))

        assertEquals(
            listOf("0.1.6", "0.1.3", "0.1.1"),
            shuffled.toReleaseNotes(SemVer(0, 1, 1)).map { it.version.toString() },
        )
    }

    @Test
    fun `a current build sees only itself`() {
        val notes = channel.toReleaseNotes(SemVer(0, 1, 6))

        assertEquals(1, notes.size)
        assertTrue("and it should say so", notes.single().current)
    }

    /** The channel ships finished releases; Obtainium would not install the rest. */
    @Test
    fun `drafts and pre-releases are excluded`() {
        val mixed = listOf(
            release("v0.2.0", draft = true),
            release("v0.1.9", pre = true),
            release("v0.1.6"),
        )

        assertEquals(listOf("0.1.6"), mixed.toReleaseNotes(SemVer(0, 1, 6)).map { it.version.toString() })
    }

    /** A hand-edited tag must not take the section down with it. */
    @Test
    fun `an unparseable tag is skipped, not fatal`() {
        val notes = listOf(release("latest"), release("v0.1.6")).toReleaseNotes(SemVer(0, 1, 6))

        assertEquals(listOf("0.1.6"), notes.map { it.version.toString() })
    }

    /**
     * A build newer than anything published — a local build, or one pulled from
     * the channel. Showing nothing is honest; showing older releases as "new"
     * would not be.
     */
    @Test
    fun `a build ahead of the channel sees nothing`() {
        assertTrue(channel.toReleaseNotes(SemVer(9, 0, 0)).isEmpty())
    }
}
