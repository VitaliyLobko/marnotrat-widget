package ua.vitaliylobko.marnotrat.widget

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Розблокування екрана — привід оновити віджет, не чекаючи 15 хв. Android з 8.0 не будить застосунок цією подією
 * через маніфест, тож приймач живе, лише поки процес запущено (оновлення за розкладом, тап по віджету).
 */
class WidgetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(Unlock, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(Unlock, filter)
    }

    private object Unlock : BroadcastReceiver() {
        // Частіше — марно: десяток розблокувань поспіль дав би десяток запитів
        private const val GAP_MS = 5 * 60 * 1000L

        override fun onReceive(c: Context, intent: Intent) {
            if (Store.config(c) == null || !Widgets.any(c)) return
            val now = System.currentTimeMillis()
            if (now - Store.unlockAt(c) < GAP_MS) return
            Store.setUnlockAt(c, now)
            Widgets.refreshNow(c)
        }
    }
}
