// TokenStore.kt

package com.justjdupuis.summonpro.utils

import android.content.Context

object TokenStore {
    private const val KEY_ACCESS = "access_token"
    private const val KEY_EXPIRES = "expires_at"

    fun savePersonalAccessToken(ctx: Context, accessToken: String, expiresInSeconds: Long) {
        val normalized = if (accessToken.startsWith("Bearer ", ignoreCase = true)) {
            accessToken
        } else {
            "Bearer $accessToken"
        }
        SecurePreferences.putString(ctx, KEY_ACCESS, normalized)
        SecurePreferences.putLong(ctx, KEY_EXPIRES, System.currentTimeMillis() + expiresInSeconds * 1000)
    }

    fun getAccessToken(ctx: Context): String? {
        val expiresAt = SecurePreferences.getLong(ctx, KEY_EXPIRES, 0)
        val earlyOffset = 5 * 60 * 1000L
        return if (System.currentTimeMillis() >= (expiresAt - earlyOffset)) {
            null
        } else {
            SecurePreferences.getString(ctx, KEY_ACCESS)
        }
    }

    fun clear(ctx: Context) {
        SecurePreferences.clear(ctx)
    }

    fun isExpired(ctx: Context): Boolean =
        System.currentTimeMillis() >= SecurePreferences.getLong(ctx, KEY_EXPIRES, 0)
}
