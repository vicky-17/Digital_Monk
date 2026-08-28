package com.digitalmonk.app.ui.auth

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.data.repository.AccountRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val prefs = PrefsManager(context)
    private val repository = AccountRepository(context, prefs)
    
    val currentUser = repository.currentUser

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    private val credentialManager = CredentialManager.create(context)

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            try {
                _signInError.value = "Starting sign-in..."
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts = false)
                    .setServerClientId("138646516495-0lk6j53kpaniqu5578c63h7bke4fbu3p.apps.googleusercontent.com")
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(activity, request)
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                Log.e("AccountVM", "Credential Error: ${e.type}", e)
                _signInError.value = "Google Error: ${e.message}"
            } catch (e: Exception) {
                Log.e("AccountVM", "Unknown Error", e)
                _signInError.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                .addOnSuccessListener {
                    _signInError.value = null
                }
                .addOnFailureListener {
                    _signInError.value = "Firebase Auth failed"
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _signInError.value = "Please enter email and password"
            return
        }
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { _signInError.value = null }
            .addOnFailureListener { _signInError.value = it.localizedMessage }
    }

    fun signUpWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _signInError.value = "Please enter email and password"
            return
        }
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { _signInError.value = null }
            .addOnFailureListener { _signInError.value = it.localizedMessage }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _signInError.value = "Please enter email"
            return
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener { _signInError.value = "Reset email sent!" }
            .addOnFailureListener { _signInError.value = it.localizedMessage }
    }
}
