package edu.bluejack252.hwixel.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase

interface ProfileSettingsRepository {
    fun isDarkMode(): Boolean
    fun setDarkMode(enabled: Boolean)
    fun languageTag(): String
    fun setLanguageTag(tag: String)
    fun isNotificationEnabled(type: String): Boolean
    fun setNotificationEnabled(type: String, enabled: Boolean)
    fun notificationSettings(): Map<String, Boolean>
    fun applyAppearance()
    fun consumeNavigationRecoveryRequired(): Boolean
}

class SharedPrefsProfileSettingsRepository(context: Context) : ProfileSettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var remoteMirrorAttachedFor: String? = null

    override fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, true)

    override fun setDarkMode(enabled: Boolean) {
        val changed = isDarkMode() != enabled
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        if (changed) markNavigationRecoveryRequired()
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    override fun languageTag(): String = prefs.getString(userScopedKey(KEY_LANGUAGE_TAG), DEFAULT_LANGUAGE_TAG)
        ?: DEFAULT_LANGUAGE_TAG

    override fun setLanguageTag(tag: String) {
        val normalized = if (SUPPORTED_LANGUAGE_TAGS.contains(tag)) tag else DEFAULT_LANGUAGE_TAG
        val changed = languageTag() != normalized
        prefs.edit().putString(userScopedKey(KEY_LANGUAGE_TAG), normalized).apply()
        if (changed) markNavigationRecoveryRequired()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
    }

    override fun isNotificationEnabled(type: String): Boolean {
        return prefs.getBoolean(notificationKey(preferenceTypeFor(type)), true)
    }

    override fun setNotificationEnabled(type: String, enabled: Boolean) {
        val preferenceType = preferenceTypeFor(type)
        prefs.edit().putBoolean(notificationKey(preferenceType), enabled).apply()
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            runCatching {
                FirebaseDatabase.getInstance().reference
                    .child("notificationPreferences")
                    .child(uid)
                    .child(preferenceType)
                    .setValue(enabled)
            }
        }
    }

    override fun notificationSettings(): Map<String, Boolean> {
        return NOTIFICATION_TYPES.associateWith(::isNotificationEnabled)
    }

    override fun applyAppearance() {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode()) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag()))
        syncNotificationPreferences()
        startRemotePreferenceMirror()
    }

    override fun consumeNavigationRecoveryRequired(): Boolean {
        val required = prefs.getBoolean(KEY_NAV_RECOVERY_REQUIRED, false)
        if (required) {
            prefs.edit().putBoolean(KEY_NAV_RECOVERY_REQUIRED, false).apply()
        }
        return required
    }

    private fun startRemotePreferenceMirror() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (remoteMirrorAttachedFor == uid) return
        remoteMirrorAttachedFor = uid
        runCatching {
            FirebaseDatabase.getInstance().reference
                .child("notificationPreferences")
                .child(uid)
                .addChildEventListener(object : ChildEventListener {
                    override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) =
                        writeFromRemote(uid, snapshot)
                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) =
                        writeFromRemote(uid, snapshot)
                    override fun onChildRemoved(snapshot: DataSnapshot) {}
                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    private fun writeFromRemote(uid: String, snapshot: DataSnapshot) {
        val type = snapshot.key ?: return
        val value = snapshot.getValue(Boolean::class.java) ?: return
        prefs.edit().putBoolean(notificationKey(type, uid), value).apply()
    }

    private fun notificationKey(
        type: String,
        uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: KEY_ANONYMOUS_USER
    ): String {
        return "$KEY_NOTIFICATION_PREFIX${uid}_$type"
    }

    private fun userScopedKey(key: String): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: KEY_ANONYMOUS_USER
        return "${uid}_$key"
    }

    private fun markNavigationRecoveryRequired() {
        prefs.edit().putBoolean(KEY_NAV_RECOVERY_REQUIRED, true).apply()
    }

    private fun syncNotificationPreferences() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val values = NOTIFICATION_TYPES.associateWith(::isNotificationEnabled)
        runCatching {
            FirebaseDatabase.getInstance().reference
                .child("notificationPreferences")
                .child(uid)
                .updateChildren(values)
        }
    }

    companion object {
        const val PREFS_NAME = "hwixel_prefs"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_LANGUAGE_TAG = "language_tag"
        const val DEFAULT_LANGUAGE_TAG = "en"
        private const val KEY_NOTIFICATION_PREFIX = "notification_"
        private const val KEY_ANONYMOUS_USER = "anonymous"
        private const val KEY_NAV_RECOVERY_REQUIRED = "nav_recovery_required"

        const val NOTIF_TASK_ASSIGNED = "task_assigned"
        const val NOTIF_MENTION = "mention"
        const val NOTIF_DEADLINE = "deadline"
        const val NOTIF_EVALUATION = "evaluation"
        const val NOTIF_INVITE = "invite"

        fun preferenceTypeFor(type: String): String = when (type) {
            "eval_open", "eval_close" -> NOTIF_EVALUATION
            else -> type
        }

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
