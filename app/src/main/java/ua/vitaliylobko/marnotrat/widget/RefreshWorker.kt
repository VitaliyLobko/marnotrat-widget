package ua.vitaliylobko.marnotrat.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Забрати шапку з сервера й перемалювати віджети. Невдача — лишаємо старі цифри й показуємо, що сталось. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val c = applicationContext
        val cfg = Store.config(c) ?: return@withContext Result.success()
        Store.setBusy(c, true)
        Widgets.updateAll(c)
        try {
            val conn = URL("${cfg.url}/api/widget").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Authorization", "Bearer ${cfg.key}")
                when (val code = conn.responseCode) {
                    200 -> {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val head = JSONObject(body).optJSONObject("head")
                        if (head != null) Store.saveHead(c, head.toString())
                        Store.setError(c, if (head == null) "Відкрий Марнотрат — цифр ще немає" else null)
                    }
                    401 -> Store.setError(c, "Ключ не підходить — підключи заново з Марнотрату")
                    else -> Store.setError(c, "Сервер: помилка $code")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            Store.setError(c, "Немає зв’язку")
        } catch (e: org.json.JSONException) {
            Store.setError(c, "Сервер відповів незрозуміло")
        } finally {
            Store.setBusy(c, false)
            Widgets.updateAll(c)
        }
        Result.success()
    }
}
