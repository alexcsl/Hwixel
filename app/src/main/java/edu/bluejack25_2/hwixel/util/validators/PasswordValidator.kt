package edu.bluejack25_2.hwixel.util.validators

object PasswordValidator {
    private val uppercaseRegex = Regex("[A-Z]")
    private val specialCharRegex = Regex("[^A-Za-z0-9]")

    fun isValid(password: String): Boolean {
        return password.length >= 8 &&
            uppercaseRegex.containsMatchIn(password) &&
            specialCharRegex.containsMatchIn(password)
    }
}
