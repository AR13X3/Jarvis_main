package com.ar13x.jarvis.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison the plan names as a trap (§10.2, step 3).
 *
 * Compared as strings — the obvious implementation — `"1.10.0" > "1.9.0"` is
 * **false**, because '1' sorts before '9'. The app would quietly stop offering
 * updates after the ninth minor of any major and give no sign it had.
 */
class SemVerTest {

    @Test
    fun `1_10_0 is newer than 1_9_0`() {
        val ten = SemVer.parseOrNull("1.10.0")!!
        val nine = SemVer.parseOrNull("1.9.0")!!

        assertTrue(ten > nine)
        // The failure this guards against, stated directly.
        assertTrue("string comparison would disagree", "1.10.0" < "1.9.0")
    }

    @Test
    fun `patch releases order within a minor`() {
        assertTrue(SemVer.parseOrNull("1.4.10")!! > SemVer.parseOrNull("1.4.9")!!)
        assertTrue(SemVer.parseOrNull("2.0.0")!! > SemVer.parseOrNull("1.99.99")!!)
    }

    @Test
    fun `equal versions are not an update`() {
        assertEquals(SemVer.parseOrNull("0.1.0"), SemVer.parseOrNull("0.1.0"))
        assertTrue(SemVer.parseOrNull("0.1.0")!! <= SemVer.parseOrNull("0.1.0")!!)
    }

    /** Git tags carry the `v` (§10.1) and the release check reads them raw. */
    @Test
    fun `the git tag form parses`() {
        assertEquals(SemVer(1, 4, 0), SemVer.parseOrNull("v1.4.0"))
    }

    /**
     * The debug build's `versionNameSuffix` is `-debug`. If that stopped the
     * version parsing, the update check would be dead in exactly the build used
     * to develop it — and would look like it worked.
     */
    @Test
    fun `the debug suffix does not stop it parsing`() {
        assertEquals(SemVer(0, 1, 0), SemVer.parseOrNull("0.1.0-debug"))
    }

    @Test
    fun `missing components default to zero`() {
        assertEquals(SemVer(1, 0, 0), SemVer.parseOrNull("1"))
        assertEquals(SemVer(1, 4, 0), SemVer.parseOrNull("1.4"))
    }

    /** Anything unparseable degrades to "no update known", never to a crash. */
    @Test
    fun `garbage is null, not an exception`() {
        assertNull(SemVer.parseOrNull(null))
        assertNull(SemVer.parseOrNull(""))
        assertNull(SemVer.parseOrNull("latest"))
        assertNull(SemVer.parseOrNull("1.x.0"))
        assertNull(SemVer.parseOrNull("1.2.3.4"))
    }
}
