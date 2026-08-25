package com.ar13x.jarvis.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.credentials: DataStore<Preferences> by preferencesDataStore("credentials")

/**
 * The single shared bearer token (plan §4.1), set once on first run.
 *
 * Stored in DataStore, encrypted with an AES-GCM key that lives in the Android
 * Keystore and never leaves it. Tailscale-only is the real perimeter — this
 * guards against another device on the tailnet, and against the token being
 * readable in a backup or an adb pull.
 *
 * Hand-rolled rather than `androidx.security:security-crypto`: that library's
 * `EncryptedSharedPreferences` is deprecated, and pulling in a deprecated
 * dependency to store one string is a worse trade than thirty lines of Keystore.
 */
@Singleton
class TokenStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private companion object {
        const val KEY_ALIAS = "jarvis.gateway.token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        val TOKEN = stringPreferencesKey("gateway_token")
        val GATEWAY_URL = stringPreferencesKey("gateway_url")
    }

    /**
     * The token as of right now, readable without suspending.
     *
     * `AuthInterceptor` is not a coroutine and cannot wait for a flow. Feeding it
     * only from [token] left a race: `save()` returns as soon as DataStore has
     * written, but the flow emission and the interceptor's update happen on
     * another coroutine — so a request made immediately after saving could go
     * out with no Authorization header and come back 401, which reads as "that
     * token was refused" when the token was in fact fine.
     *
     * Written before `save()` returns, so a request made on the next line
     * carries it.
     */
    @Volatile
    var current: String? = null
        private set

    /** Null until first run completes. Emits again the moment it changes. */
    val token: Flow<String?> = context.credentials.data
        .map { prefs -> prefs[TOKEN]?.let(::decrypt) }
        // Keeps [current] true after a change this process did not make — a
        // restore, or the store being cleared from elsewhere.
        .onEach { current = it }

    /**
     * The gateway URL as of right now, readable without suspending.
     *
     * Same reason [current] exists: `GatewayUrlInterceptor` is not a coroutine
     * and cannot wait for a flow. And the same race — `save()` returns as soon
     * as DataStore has written, but the emission lands on another coroutine, so
     * a request made on the next line would otherwise still go to the old host.
     */
    @Volatile
    var currentUrl: String? = null
        private set

    val gatewayUrl: Flow<String?> = context.credentials.data
        .map { it[GATEWAY_URL] }
        .onEach { currentUrl = it }

    /**
     * Read synchronously for the OkHttp interceptor, which is not a coroutine.
     *
     * Blocking on every request would be wrong, so the interceptor caches this
     * and only re-reads when the store emits — see `AuthInterceptor`.
     */
    suspend fun currentToken(): String? = token.first()

    suspend fun save(token: String, gatewayUrl: String) {
        // Set first, so the very next request is authenticated and correctly
        // addressed even though the flow has not emitted yet. The probe that
        // immediately follows pairing is exactly that request.
        current = token
        currentUrl = gatewayUrl
        context.credentials.edit { prefs ->
            prefs[TOKEN] = encrypt(token)
            prefs[GATEWAY_URL] = gatewayUrl
        }
    }

    /** A 401 means the token is no longer valid, so it is not worth keeping. */
    suspend fun clear() {
        current = null
        context.credentials.edit { it.remove(TOKEN) }
    }

    // --- crypto ---------------------------------------------------------------

    private fun secretKey(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately not requiring user authentication: reminders fire
                // and refresh while the phone is locked, and a token that could
                // only be read after an unlock would break background work.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    /** Stored as `iv:ciphertext`, both base64 — the IV is not secret, only unique. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val bytes = cipher.doFinal(plain.toByteArray())
        return base64(cipher.iv) + ":" + base64(bytes)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val (iv, payload) = stored.split(":", limit = 2).let { it[0] to it[1] }
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, unbase64(iv)))
        }
        String(cipher.doFinal(unbase64(payload)))
        // A key invalidated by a device change or a restored backup fails here.
        // Returning null sends the user back to the token screen, which is the
        // only thing that can actually fix it.
    }.getOrNull()

    private fun base64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unbase64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
