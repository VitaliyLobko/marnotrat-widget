package ua.vitaliylobko.marnotrat.widget

import android.content.Context
import org.json.JSONObject

/** Адреса сервера й ключ доступу (з Марнотрату), остання шапка й стан оновлення — лише на цьому телефоні. */
object Store {
    data class Config(val url: String, val key: String)

    private fun prefs(c: Context) = c.getSharedPreferences("widget", Context.MODE_PRIVATE)

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

    fun busy(c: Context): Boolean = prefs(c).getBoolean("busy", false)

    fun setBusy(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("busy", on).apply()
    }
}
