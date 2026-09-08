package com.justjdupuis.summonpro.utils

import com.google.gson.JsonParser
import java.util.Base64

object TokenMetadata {
    fun expiresInSeconds(token: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Long? {
        val raw = token.removePrefix("Bearer ").trim()
        val payload = raw.split('.').getOrNull(1) ?: return null
        return runCatching {
            val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            val expiresAt = JsonParser.parseString(json).asJsonObject.get("exp").asLong
            (expiresAt - nowEpochSeconds).takeIf { it > 0 }
        }.getOrNull()
    }
}
