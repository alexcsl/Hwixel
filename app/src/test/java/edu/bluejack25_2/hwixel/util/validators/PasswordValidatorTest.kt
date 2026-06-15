package edu.bluejack25_2.hwixel.util.validators

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordValidatorTest {
    @Test
    fun validPasswordReturnsTrue() {
        assertTrue(PasswordValidator.isValid("Password1!"))
    }

    @Test
    fun shortPasswordReturnsFalse() {
        assertFalse(PasswordValidator.isValid("Pass1!"))
    }

    @Test
    fun passwordWithoutUppercaseReturnsFalse() {
        assertFalse(PasswordValidator.isValid("password1!"))
    }

    @Test
    fun passwordWithoutSpecialCharacterReturnsFalse() {
        assertFalse(PasswordValidator.isValid("Password1"))
    }
}
