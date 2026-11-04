package ua.vitaliylobko.marnotrat.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
            // Спершу — витрати з вікна швидкого запису: тоді шапка нижче вже з ними
            flush(c, cfg)
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
        // Витрата лишилась у черзі (немає зв'язку, сервер не прийняв) — WorkManager спробує ще, з мережею
        if (Store.pending(c).isNotEmpty()) Result.retry() else Result.success()
    }

    /**
     * Черга → POST /api/records, як обмін Марнотрату: той самий ключ, app marnotrat, store ops. Марнотрат забере
     * витрату при наступному обміні. pull false — інакше у відповідь прийшли б усі записи. Повтор після втраченої
     * відповіді нешкідливий: той самий id і updated сервер не перезаписує.
     */
    private fun flush(c: Context, cfg: Store.Config) {
        val queue = Store.pending(c)
        if (queue.isEmpty()) return
        val put = JSONArray()
        for (rec in queue) put.put(JSONObject().put("store", "ops").put("id", rec.optString("id")).put("data", rec).put("updated", rec.optLong("updated")))
        val body = JSONObject().put("app", "marnotrat").put("put", put).put("pull", false).toString().toByteArray()
        val conn = URL("${cfg.url}/api/records").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer ${cfg.key}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode == 200) Store.dropPending(c, queue.map { it.optString("id") }.toSet())
        } finally {
            conn.disconnect()
        }
    }
}
