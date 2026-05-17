package edu.bluejack252.hwixel

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class HwixelApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        applyDarkMode()
    }

    private fun applyDarkMode() {
        val prefs = getSharedPreferences("hwixel_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
