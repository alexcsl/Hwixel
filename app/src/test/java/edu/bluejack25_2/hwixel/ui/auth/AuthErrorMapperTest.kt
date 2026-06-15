package edu.bluejack25_2.hwixel.ui.auth

import edu.bluejack25_2.hwixel.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMapperTest {
    @Test
    fun firebaseUserNotFoundCodeMapsToReadableMessage() {
        assertEquals(
            R.string.error_auth_user_not_found,
            AuthErrorMapper.messageResIdForFirebaseCode("ERROR_USER_NOT_FOUND")
        )
    }

    @Test
    fun firebaseInvalidCredentialCodeMapsToReadableMessage() {
        assertEquals(
            R.string.error_auth_invalid_credentials,
            AuthErrorMapper.messageResIdForFirebaseCode("ERROR_INVALID_CREDENTIAL")
        )
    }

    @Test
    fun firebaseDuplicateEmailCodeMapsToReadableMessage() {
        assertEquals(
            R.string.error_auth_email_exists,
            AuthErrorMapper.messageResIdForFirebaseCode("ERROR_EMAIL_ALREADY_IN_USE")
        )
    }
}
