package edu.bluejack252.hwixel

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import edu.bluejack252.hwixel.data.source.remote.EvalPeriodNotificationRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HwixelApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applyDarkMode()
        EvalPeriodNotificationRegistrar().register(applicationScope)
    }

    private fun applyDarkMode() {
        val prefs = getSharedPreferences("hwixel_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
