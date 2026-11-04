package ua.vitaliylobko.marnotrat.widget

import android.content.Context
import org.json.JSONObject

/** Адреса сервера й ключ доступу (з Марнотрату), остання шапка й стан оновлення — лише на цьому телефоні. */
object Store {
    data class Config(val url: String, val key: String)

    private fun prefs(c: Context) = c.getSharedPreferences("widget", Context.MODE_PRIVATE)

    /**
     * Екран застосунку перемальовується сам, коли прийшла шапка, помилка чи почалось оновлення.
     * Слухача Android тримає слабким посиланням — його треба зберегти в полі й зняти в onStop.
     */
    fun listen(c: Context, onChange: () -> Unit): android.content.SharedPreferences.OnSharedPreferenceChangeListener {
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs(c).registerOnSharedPreferenceChangeListener(l)
        return l
    }

    fun unlisten(c: Context, l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(c).unregisterOnSharedPreferenceChangeListener(l)
    }

    fun config(c: Context): Config? {
        val p = prefs(c)
        val url = p.getString("url", null) ?: return null
        val key = p.getString("key", null) ?: return null
        return Config(url, key)
    }

    fun saveConfig(c: Context, url: String, key: String) {
        prefs(c).edit().putString("url", url).putString("key", key).remove("head").remove("error").apply()
    }

    fun head(c: Context): Head? =
        prefs(c).getString("head", null)?.let { runCatching { Head.parse(JSONObject(it)) }.getOrNull() }

    fun saveHead(c: Context, json: String?) {
        prefs(c).edit().putString("head", json).apply()
    }

    fun error(c: Context): String? = prefs(c).getString("error", null)

    fun setError(c: Context, text: String?) {
        prefs(c).edit().putString("error", text).apply()
    }

    fun unlockAt(c: Context): Long = prefs(c).getLong("unlock", 0L)

    fun setUnlockAt(c: Context, at: Long) {
        prefs(c).edit().putLong("unlock", at).apply()
    }

    /**
     * Монета віджета 1×1. Разом з id — назва й іконка на момент вибору: віджет малюється й тоді,
     * коли шапки ще немає чи сервер старий і монет не шле.
     */
    fun quick(c: Context, widgetId: Int): Head.Cat? =
        prefs(c).getString("quick:$widgetId", null)?.let { runCatching { Head.cat(JSONObject(it)) }.getOrNull() }

    fun setQuick(c: Context, widgetId: Int, cat: Head.Cat) {
        val o = JSONObject().put("id", cat.id).put("name", cat.name).put("icon", cat.icon).put("cur", cat.cur)
        prefs(c).edit().putString("quick:$widgetId", o.toString()).apply()
    }

    fun dropQuick(c: Context, widgetIds: IntArray) {
        prefs(c).edit().apply { for (id in widgetIds) remove("quick:$id") }.apply()
    }

    fun busy(c: Context): Boolean = prefs(c).getBoolean("busy", false)

    fun setBusy(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("busy", on).apply()
    }
}
