package edu.bluejack252.hwixel.util.validators

import android.util.Patterns

object EmailValidator {
    private val fallbackEmailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    fun isValid(email: String): Boolean {
        if (email.isBlank()) return false
        val androidPattern = Patterns.EMAIL_ADDRESS
        return if (androidPattern != null) {
            androidPattern.matcher(email).matches()
        } else {
            fallbackEmailRegex.matches(email)
        }
    }
}
