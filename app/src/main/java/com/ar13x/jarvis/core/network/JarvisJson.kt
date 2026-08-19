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
    classDiscriminator = "type"
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}
