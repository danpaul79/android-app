package com.example.aicompanion.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GoogleUser(
    val email: String,
    val displayName: String?,
    val idToken: String
)

class GoogleAuthManager(private val context: Context) {

    companion object {
        // This is the Web client ID from Google Cloud Console
        // You need to create an OAuth 2.0 Client ID (Web application type)
        // in the same GCP project as your Cloud Functions
        const val WEB_CLIENT_ID = "809575369316-PLACEHOLDER.apps.googleusercontent.com"
    }

    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<GoogleUser?>(null)
    val currentUser: StateFlow<GoogleUser?> = _currentUser.asStateFlow()

    suspend fun signIn(activityContext: Context): Result<GoogleUser> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            val user = GoogleUser(
                email = googleIdTokenCredential.id,
                displayName = googleIdTokenCredential.displayName,
                idToken = googleIdTokenCredential.idToken
            )
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
        _currentUser.value = null
    }

    fun isSignedIn(): Boolean = _currentUser.value != null

    fun getIdToken(): String? = _currentUser.value?.idToken
}
