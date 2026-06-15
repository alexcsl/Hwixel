package edu.bluejack25_2.hwixel

import android.app.Application
import edu.bluejack25_2.hwixel.data.repository.SharedPrefsProfileSettingsRepository
import edu.bluejack25_2.hwixel.data.source.remote.EvalPeriodNotificationRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HwixelApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SharedPrefsProfileSettingsRepository(this).applyAppearance()
        EvalPeriodNotificationRegistrar().register(applicationScope)
    }
}
