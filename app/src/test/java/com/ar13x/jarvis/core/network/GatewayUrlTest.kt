package com.ar13x.jarvis.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sending requests to the gateway the user actually paired with.
 *
 * The bug this fixes was silent for three phases: the pairing screen saved a
 * URL nobody read, so a changed gateway was accepted and then ignored. What
 * these pin is the part that makes the fix non-obvious — the `/api` mount.
 */
class GatewayUrlTest {

    /** The base Retrofit is constructed with, and so the prefix already in every path. */
    private val base = "https://gw03.tail9662e3.ts.net/api".toHttpUrl()

    private fun rebase(request: String, target: String) =
        request.toHttpUrl().rebased(from = base, to = target.toHttpUrl()).toString()

    /**
     * The one that matters. `tailscale serve` mounts the gateway at `/api` and
     * strips it, so the stored URL carries that segment and the Retrofit paths
     * do not. Swapping only the host drops it and every call 404s.
     */
    @Test
    fun `the stored path prefix is preserved`() {
        assertEquals(
            "https://other.example.net/api/tasks/sections",
            rebase("https://gw03.tail9662e3.ts.net/api/tasks/sections", "https://other.example.net/api"),
        )
    }

    @Test
    fun `a target with no prefix produces no prefix`() {
        assertEquals(
            "https://plain.example.net/tasks/sections",
            rebase("https://gw03.tail9662e3.ts.net/api/tasks/sections", "https://plain.example.net"),
        )
    }

    @Test
    fun `a trailing slash on the stored url does not double up`() {
        assertEquals(
            "https://other.example.net/api/tasks/sections",
            rebase("https://gw03.tail9662e3.ts.net/api/tasks/sections", "https://other.example.net/api/"),
        )
    }

    @Test
    fun `scheme and port move too`() {
        assertEquals(
            "http://192.168.0.93:8080/api/health",
            rebase("https://gw03.tail9662e3.ts.net/api/health", "http://192.168.0.93:8080/api"),
        )
    }

    /** Paging and filters live in the query, and must survive the move. */
    @Test
    fun `the query is untouched`() {
        assertEquals(
            "https://other.example.net/api/tasks?status=active&page=2",
            rebase(
                "https://gw03.tail9662e3.ts.net/api/tasks?status=active&page=2",
                "https://other.example.net/api",
            ),
        )
    }

    /** Rebasing onto where it already points must be a no-op, not a mangling. */
    @Test
    fun `rebasing onto the same base changes nothing`() {
        val url = "https://gw03.tail9662e3.ts.net/api/tasks/sections"
        assertEquals(url, rebase(url, "https://gw03.tail9662e3.ts.net/api"))
    }

    @Test
    fun `a deep prefix is preserved whole`() {
        assertEquals(
            "https://host.example.net/a/b/c/tasks",
            rebase("https://gw03.tail9662e3.ts.net/api/tasks", "https://host.example.net/a/b/c"),
        )
    }
}
