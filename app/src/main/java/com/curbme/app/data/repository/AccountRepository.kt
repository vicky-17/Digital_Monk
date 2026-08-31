package com.curbme.app.data.repository

import android.content.Context
import com.curbme.app.data.local.prefs.PrefsManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AccountRepository(private val context: Context, private val prefs: PrefsManager) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            firebaseAuth.currentUser?.let {
                prefs.userUid = it.uid
                prefs.userEmail = it.email
            }
        }
    }

    fun signOut() {
        auth.signOut()
        prefs.userUid = null
        prefs.userEmail = null
    }

    // Modern Google Sign-In with Credentials Manager will be called from ViewModel
}
