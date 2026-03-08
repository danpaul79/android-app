package com.example.aicompanion.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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
        const val WEB_CLIENT_ID = "809575369316-gntgmi8hd2m4rcd8danc15r0oa47ij17.apps.googleusercontent.com"
    }

    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<GoogleUser?>(null)
    val currentUser: StateFlow<GoogleUser?> = _currentUser.asStateFlow()

    suspend fun signIn(activityContext: Context): Result<GoogleUser> {
        return try {
            val googleSignInOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleSignInOption)
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
            Result.failure(IllegalStateException(formatAuthError(e), e))
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

    private fun formatAuthError(error: Throwable): String {
        if (error.javaClass.simpleName == "GetCredentialCancellationException") {
            val causeMessage = error.cause?.message?.takeIf { it.isNotBlank() }
            return if (causeMessage != null) {
                "Google sign-in was canceled or interrupted: $causeMessage"
            } else {
                "Google sign-in was canceled or interrupted."
            }
        }

        val details = buildList {
            add(error.javaClass.simpleName)
            error.message?.takeIf { it.isNotBlank() }?.let(::add)
            error.cause?.let { cause ->
                add("cause=${cause.javaClass.simpleName}")
                cause.message?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return details.joinToString(": ")
    }
}
