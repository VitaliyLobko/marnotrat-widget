package ua.vitaliylobko.marnotrat.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

/**
 * Оновлення без Chrome: APK з GitHub пишеться просто в сесію PackageInstaller, далі Android показує своє «Оновити».
 * Підпис той самий — віджети й підключення лишаються. Відповідь системи ловить InstallReceiver.
 */
object Updater {
    /** Іде завантаження — кнопка «Оновити» чекає, другий тап не запускає ще одне. */
    @Volatile
    var busy = false
        private set

    /** done — на головному потоці: null — сесію віддано системі, інакше текст помилки. */
    fun start(c: Context, url: String, done: (String?) -> Unit) {
        if (busy) return
        busy = true
        val app = c.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val err = runCatching { install(app, url) }.exceptionOrNull()
            busy = false
            main.post { done(err?.let { it.message ?: it.javaClass.simpleName }) }
        }.start()
    }

    private fun install(app: Context, url: String) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(app.packageName) }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                // releases/latest/download веде переадресаціями на сховище GitHub — HttpURLConnection іде за ними сам
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    conn.setRequestProperty("User-Agent", "marnotrat-widget")
                    if (conn.responseCode != 200) error("GitHub відповів ${conn.responseCode}")
                    session.openWrite("marnotrat-widget.apk", 0, conn.contentLengthLong).use { out ->
                        conn.inputStream.use { it.copyTo(out) }
                        session.fsync(out)
                    }
                } finally {
                    conn.disconnect()
                }
                // MUTABLE — система дописує в intent статус і екран підтвердження; intent явний, тож це дозволено
                val pi = PendingIntent.getBroadcast(
                    app, id, Intent(app, InstallReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }
}
