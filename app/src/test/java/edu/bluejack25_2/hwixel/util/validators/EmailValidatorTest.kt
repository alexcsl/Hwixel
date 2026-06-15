package edu.bluejack25_2.hwixel.util.validators

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailValidatorTest {
    @Test
    fun validEmailReturnsTrue() {
        assertTrue(EmailValidator.isValid("student@example.com"))
    }

    @Test
    fun malformedEmailReturnsFalse() {
        assertFalse(EmailValidator.isValid("student-example.com"))
    }

    @Test
    fun blankEmailReturnsFalse() {
        assertFalse(EmailValidator.isValid(""))
    }
}
