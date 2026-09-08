import android.content.Context

object TokenManager {
    suspend fun getValidAccessToken(ctx: Context): String? {
        val token = TokenStore.getAccessToken(ctx)
        if (token != null) return token

        return null
    }

    fun hasValidAccessToken(ctx: Context): Boolean {
        return TokenStore.getAccessToken(ctx) != null
    }

    fun clearSession(ctx: Context) {
        TokenStore.clear(ctx)
    }
}
