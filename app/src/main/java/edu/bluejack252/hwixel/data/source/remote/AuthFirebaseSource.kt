package edu.bluejack252.hwixel.data.source.remote

import com.google.firebase.auth.FirebaseAuth

open class AuthFirebaseSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRemoteSource {
    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).awaitResult()
    }

    override suspend fun register(email: String, password: String): String {
        return auth.createUserWithEmailAndPassword(email, password).awaitResult().user?.uid.orEmpty()
    }

    override fun logout() {
        auth.signOut()
    }
}
