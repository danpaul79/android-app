package com.example.aicompanion.data.sync

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TokenManager(private val context: Context) {

    companion object {
        const val TASKS_SCOPE = "oauth2:https://www.googleapis.com/auth/tasks"
        const val GMAIL_SCOPE = "oauth2:https://www.googleapis.com/auth/gmail.readonly"
        const val COMBINED_SCOPE =
            "oauth2:https://www.googleapis.com/auth/tasks https://www.googleapis.com/auth/gmail.readonly"
    }

    private var accountEmail: String? = null

    fun setAccount(email: String) {
        accountEmail = email
    }

    fun getAccount(): String? = accountEmail

    fun clearAccount() {
        accountEmail = null
    }

    suspend fun getAccessToken(scope: String = TASKS_SCOPE): String {
        val email = accountEmail ?: throw IllegalStateException("No Google account set for sync")
        return withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(context, email, scope)
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
