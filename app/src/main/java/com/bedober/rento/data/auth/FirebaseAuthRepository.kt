package com.bedober.rento.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Authentication primitives. PhoneAuthProvider callbacks and Activity lifecycle
 * handling belong in the presentation layer; no SMS provider secret is shipped here.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun registerWithEmail(name: String, email: String, password: String, phone: String) {
        require(name.isNotBlank() && phone.isNotBlank())
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = requireNotNull(result.user).uid
        firestore.collection("users").document(uid).set(
            mapOf("uid" to uid, "name" to name.trim(), "email" to email.trim(),
                "phone" to phone, "role" to "UNSELECTED", "createdAt" to System.currentTimeMillis())
        ).await()
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        val result = auth.signInWithCredential(credential).await()
        val user = requireNotNull(result.user)
        val profile = firestore.collection("users").document(user.uid).get().await()
        if (!profile.exists()) {
            firestore.collection("users").document(user.uid).set(
                mapOf("uid" to user.uid, "phone" to user.phoneNumber,
                    "email" to user.email, "role" to "UNSELECTED",
                    "createdAt" to System.currentTimeMillis())
            ).await()
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        require(idToken.isNotBlank())
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = requireNotNull(result.user)
        val profileRef = firestore.collection("users").document(user.uid)
        if (!profileRef.get().await().exists()) {
            profileRef.set(mapOf("uid" to user.uid, "name" to user.displayName,
                "email" to user.email, "profilePhoto" to user.photoUrl?.toString(),
                "role" to "UNSELECTED", "createdAt" to System.currentTimeMillis())).await()
        }
    }

    fun signOut() = auth.signOut()
}
