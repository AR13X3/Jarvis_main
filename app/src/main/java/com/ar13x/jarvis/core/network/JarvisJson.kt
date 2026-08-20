package com.ar13x.jarvis.core.network

import kotlinx.serialization.json.Json

/**
 * The one [Json] instance the app uses.
 *
 * `ignoreUnknownKeys` matters more than it looks (plan §4.5): the gateway will
 * grow fields, and a client that throws on one it has not seen cannot be
 * forward-compatible. `classDiscriminator` is set for any polymorphic type that
 * uses the generated machinery — `AgentComponent` supplies its own serializer
 * so it can fall back on unknown types instead of throwing.
 */
val JarvisJson: Json = Json {
    ignoreUnknownKeys = true
    // The gateway declares title, description and is_priority as nullable on
    // ProposalSummary, while the app models them as non-null with defaults. An
    // explicit `null` would otherwise throw mid-stream and blank a message that
    // was otherwise fine; this coerces it to the default instead.
    coerceInputValues = true
    classDiscriminator = "type"
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}
