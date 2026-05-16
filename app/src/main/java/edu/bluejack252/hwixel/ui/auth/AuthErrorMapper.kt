package edu.bluejack252.hwixel.ui.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import edu.bluejack252.hwixel.R

object AuthErrorMapper {
    fun messageResId(error: Throwable): Int {
        return when {
            error is FirebaseNetworkException -> R.string.error_auth_network
            error is FirebaseAuthException -> messageResIdForFirebaseCode(error.errorCode)
            else -> R.string.error_auth_default
        }
    }

    internal fun messageResIdForFirebaseCode(errorCode: String): Int {
        return when (errorCode) {
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_WRONG_PASSWORD" -> R.string.error_auth_invalid_credentials
            "ERROR_USER_NOT_FOUND" -> R.string.error_auth_user_not_found
            "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.error_auth_email_exists
            else -> R.string.error_auth_default
        }
    }
}
