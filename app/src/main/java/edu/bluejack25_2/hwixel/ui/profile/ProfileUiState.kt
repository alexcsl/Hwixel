package edu.bluejack25_2.hwixel.ui.profile

import edu.bluejack25_2.hwixel.data.model.User

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isDarkMode: Boolean = true,
    val languageTag: String = "en",
    val notificationSettings: Map<String, Boolean> = emptyMap()
)
