package com.justjdupuis.summonpro.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small Android Keystore-backed store for the personal Fleet API token. */
object SecurePreferences {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "summonpro_personal_token_v1"
    private const val PREFS_NAME = "summonpro_auth"
    private const val PREFIX = "v1:"

    private val prefsLock = Any()

    fun putString(context: Context, key: String, value: String?) = synchronized(prefsLock) {
        val editor = prefs(context).edit()
        if (value == null) editor.remove(key) else editor.putString(key, encrypt(value))
        editor.apply()
    }

    fun getString(context: Context, key: String): String? = synchronized(prefsLock) {
        val stored = prefs(context).getString(key, null) ?: return@synchronized null
        if (!stored.startsWith(PREFIX)) {
            // One-time migration from the previous plaintext SharedPreferences format.
            putString(context, key, stored)
            return@synchronized stored
        }
        runCatching { decrypt(stored.removePrefix(PREFIX)) }
            .onFailure { prefs(context).edit().remove(key).apply() }
            .getOrNull()
    }

    fun putLong(context: Context, key: String, value: Long) {
        prefs(context).edit().putLong(key, value).apply()
    }

    fun getLong(context: Context, key: String, default: Long): Long =
        prefs(context).getLong(key, default)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12) { "Invalid encrypted token" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        return cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }
}
