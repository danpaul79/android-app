package com.example.aicompanion.data.sync

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TokenManager(private val context: Context) {

    companion object {
        const val TASKS_SCOPE = "oauth2:https://www.googleapis.com/auth/tasks"
    }

    private var accountEmail: String? = null

    fun setAccount(email: String) {
        accountEmail = email
    }

    fun getAccount(): String? = accountEmail

    fun clearAccount() {
        accountEmail = null
    }

    /**
     * Gets a valid OAuth2 access token for the Google Tasks API.
     * Uses the Android account manager which handles token refresh automatically.
     *
     * @throws UserRecoverableAuthException if user consent is needed (caller should launch the intent)
     * @throws IllegalStateException if no account is set
     */
    suspend fun getAccessToken(): String {
        val email = accountEmail ?: throw IllegalStateException("No Google account set for sync")
        return withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(context, email, TASKS_SCOPE)
        }
    }

    /**
     * Invalidates the cached token so the next call to getAccessToken() fetches a fresh one.
     */
    suspend fun invalidateToken(token: String) {
        withContext(Dispatchers.IO) {
            GoogleAuthUtil.clearToken(context, token)
        }
    }
}
