package com.ontimestack.webkiosk.data

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class KioskSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var homePageUrl: String
        get() = normalizeHomePageUrl(
            preferences.getString(KEY_HOME_PAGE_URL, DEFAULT_HOME_PAGE_URL) ?: DEFAULT_HOME_PAGE_URL
        ) ?: DEFAULT_HOME_PAGE_URL
        set(value) {
            val normalized = requireNotNull(normalizeHomePageUrl(value)) { "Home Page must use HTTPS" }
            preferences.edit { putString(KEY_HOME_PAGE_URL, normalized) }
        }

    var rotation: Rotation
        get() = Rotation.entries.firstOrNull {
            it.degrees == preferences.getInt(KEY_ROTATION, Rotation.ROTATION_0.degrees)
        } ?: Rotation.ROTATION_0
        set(value) = preferences.edit { putInt(KEY_ROTATION, value.degrees) }

    var openOnStartup: Boolean
        get() = preferences.getBoolean(KEY_OPEN_ON_STARTUP, true)
        set(value) = preferences.edit { putBoolean(KEY_OPEN_ON_STARTUP, value) }

    var keepInForeground: Boolean
        get() = preferences.getBoolean(KEY_KEEP_IN_FOREGROUND, true)
        set(value) = preferences.edit { putBoolean(KEY_KEEP_IN_FOREGROUND, value) }

    var isConfigured: Boolean
        get() = preferences.getBoolean(KEY_CONFIGURED, false) && hasAdminPin()
        set(value) = preferences.edit { putBoolean(KEY_CONFIGURED, value) }

    fun hasAdminPin(): Boolean =
        preferences.contains(KEY_PIN_SALT) && preferences.contains(KEY_PIN_HASH)

    fun setAdminPin(pin: CharArray) {
        val salt = ByteArray(PIN_SALT_BYTES).also(SecureRandom()::nextBytes)
        val algorithm = preferredPinAlgorithm()
        val hash = derivePin(pin, salt, algorithm)

        preferences.edit {
            putString(KEY_PIN_SALT, salt.toHex())
            putString(KEY_PIN_HASH, hash.toHex())
            putString(KEY_PIN_ALGORITHM, algorithm)
        }
    }

    fun verifyAdminPin(pin: CharArray): Boolean {
        return runCatching {
            val salt = preferences.getString(KEY_PIN_SALT, null)?.hexToBytes() ?: return false
            val expectedHash = preferences.getString(KEY_PIN_HASH, null)?.hexToBytes() ?: return false
            val algorithm = preferences.getString(KEY_PIN_ALGORITHM, PIN_ALGORITHM_SHA1)
                ?: PIN_ALGORITHM_SHA1
            val actualHash = derivePin(pin, salt, algorithm)
            MessageDigest.isEqual(expectedHash, actualHash)
        }.getOrDefault(false)
    }

    private fun preferredPinAlgorithm(): String = try {
        SecretKeyFactory.getInstance(PIN_ALGORITHM_SHA256)
        PIN_ALGORITHM_SHA256
    } catch (_: Exception) {
        PIN_ALGORITHM_SHA1
    }

    private fun derivePin(pin: CharArray, salt: ByteArray, algorithm: String): ByteArray {
        val spec = PBEKeySpec(pin, salt, PIN_ITERATIONS, PIN_KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_HOME_PAGE_URL = "https://ontimestack.com"

        fun normalizeHomePageUrl(value: String): String? {
            val candidate = value.trim()
            if (candidate.any(Char::isWhitespace)) return null
            val uri = runCatching { candidate.toUri() }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
                return null
            }
            return uri.toString()
        }

        private const val PREFERENCES_NAME = "ots_web_kiosk_settings"
        private const val KEY_HOME_PAGE_URL = "home_page_url"
        private const val KEY_ROTATION = "rotation"
        private const val KEY_OPEN_ON_STARTUP = "open_on_startup"
        private const val KEY_KEEP_IN_FOREGROUND = "keep_in_foreground"
        private const val KEY_CONFIGURED = "configured"
        private const val KEY_PIN_SALT = "admin_pin_salt"
        private const val KEY_PIN_HASH = "admin_pin_hash"
        private const val KEY_PIN_ALGORITHM = "admin_pin_algorithm"
        private const val PIN_ALGORITHM_SHA256 = "PBKDF2WithHmacSHA256"
        private const val PIN_ALGORITHM_SHA1 = "PBKDF2WithHmacSHA1"
        private const val PIN_ITERATIONS = 150_000
        private const val PIN_KEY_LENGTH_BITS = 256
        private const val PIN_SALT_BYTES = 16
    }
}
