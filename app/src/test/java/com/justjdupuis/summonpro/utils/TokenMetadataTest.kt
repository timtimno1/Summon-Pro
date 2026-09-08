package com.justjdupuis.summonpro.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class TokenMetadataTest {
    @Test
    fun readsJwtExpirationWithoutTrustingOtherClaims() {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"exp\":2000}".toByteArray())
        assertEquals(1000L, TokenMetadata.expiresInSeconds("x.$payload.y", 1000))
    }

    @Test
    fun rejectsOpaqueAndExpiredTokens() {
        assertNull(TokenMetadata.expiresInSeconds("opaque", 1000))
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"exp\":999}".toByteArray())
        assertNull(TokenMetadata.expiresInSeconds("x.$payload.y", 1000))
    }
}
