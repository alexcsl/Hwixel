package edu.bluejack252.hwixel

import android.app.Application
import edu.bluejack252.hwixel.data.repository.SharedPrefsProfileSettingsRepository

class HwixelApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SharedPrefsProfileSettingsRepository(this).applyAppearance()
    }
}
