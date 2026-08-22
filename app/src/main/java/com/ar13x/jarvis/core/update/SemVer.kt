package com.ar13x.jarvis.core.update

/**
 * A real version comparison (plan §10.2, step 3).
 *
 * The plan calls this out explicitly because the obvious implementation is
 * wrong: compared as strings, **`1.10.0` sorts below `1.9.0`**, so the app would
 * stop offering updates at the tenth patch of any minor and never say why.
 * Comparing the numbers is the entire point of this class existing.
 *
 * Deliberately narrow — three integers, no pre-release ordering. The release
 * channel (§10.1) only ever produces `major.minor.patch`, and implementing the
 * full spec's pre-release precedence rules would be code with no caller.
 */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int = when {
        major != other.major -> major.compareTo(other.major)
        minor != other.minor -> minor.compareTo(other.minor)
        else -> patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Parses `1.4.0`, the git tag form `v1.4.0`, and the debug build's
         * `0.1.0-debug` — the suffix `versionNameSuffix` adds is not part of the
         * version and must not stop it parsing, or the update check would be
         * dead in exactly the build used to test it.
         *
         * Returns null rather than throwing on anything else. A malformed tag on
         * a release someone hand-edited must degrade to "no update known", never
         * to a crash on launch (§10.2: fail silently).
         */
        fun parseOrNull(raw: String?): SemVer? {
            val trimmed = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
            // Drop pre-release and build metadata: 1.4.0-debug, 1.4.0+sha.
            val core = trimmed.substringBefore('-').substringBefore('+')
            val parts = core.split('.')
            if (parts.isEmpty() || parts.size > 3) return null
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            if (numbers.any { it < 0 }) return null
            return SemVer(
                major = numbers[0],
                minor = numbers.getOrElse(1) { 0 },
                patch = numbers.getOrElse(2) { 0 },
            )
        }
    }
}
