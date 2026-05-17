package edu.bluejack252.hwixel.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

interface ProfileSettingsRepository {
    fun isDarkMode(): Boolean
    fun setDarkMode(enabled: Boolean)
    fun languageTag(): String
    fun setLanguageTag(tag: String)
    fun isNotificationEnabled(type: String): Boolean
    fun setNotificationEnabled(type: String, enabled: Boolean)
    fun notificationSettings(): Map<String, Boolean>
    fun applyAppearance()
}

class SharedPrefsProfileSettingsRepository(context: Context) : ProfileSettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, true)

    override fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    override fun languageTag(): String = prefs.getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG)
        ?: DEFAULT_LANGUAGE_TAG

    override fun setLanguageTag(tag: String) {
        val normalized = if (SUPPORTED_LANGUAGE_TAGS.contains(tag)) tag else DEFAULT_LANGUAGE_TAG
        prefs.edit().putString(KEY_LANGUAGE_TAG, normalized).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
    }

    override fun isNotificationEnabled(type: String): Boolean {
        return prefs.getBoolean(notificationKey(type), true)
    }

    override fun setNotificationEnabled(type: String, enabled: Boolean) {
        prefs.edit().putBoolean(notificationKey(type), enabled).apply()
    }

    override fun notificationSettings(): Map<String, Boolean> {
        return NOTIFICATION_TYPES.associateWith(::isNotificationEnabled)
    }

    override fun applyAppearance() {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode()) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag()))
    }

    private fun notificationKey(type: String): String = "$KEY_NOTIFICATION_PREFIX$type"

    companion object {
        const val PREFS_NAME = "hwixel_prefs"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_LANGUAGE_TAG = "language_tag"
        const val DEFAULT_LANGUAGE_TAG = "en"
        private const val KEY_NOTIFICATION_PREFIX = "notification_"

        const val NOTIF_TASK_ASSIGNED = "task_assigned"
        const val NOTIF_MENTION = "mention"
        const val NOTIF_DEADLINE = "deadline"
        const val NOTIF_EVALUATION = "evaluation"
        const val NOTIF_INVITE = "invite"

        val SUPPORTED_LANGUAGE_TAGS = setOf("en", "id")
        val NOTIFICATION_TYPES = listOf(
            NOTIF_TASK_ASSIGNED,
            NOTIF_MENTION,
            NOTIF_DEADLINE,
            NOTIF_EVALUATION,
            NOTIF_INVITE
        )
    }
}
