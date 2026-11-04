package ua.vitaliylobko.marnotrat.widget

import android.content.Context
import org.json.JSONArray
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
     * Перша монета віджета. Разом з id — назва й іконка на момент вибору: віджет малюється й тоді,
     * коли шапки ще немає чи сервер старий і монет не шле.
     */
    fun quick(c: Context, widgetId: Int): Head.Cat? = quicks(c, widgetId).firstOrNull()

    /** Монети віджета: 1×1 — одна, 4×1 — до трьох по черзі. Старі версії зберігали одну монету об'єктом, не масивом. */
    fun quicks(c: Context, widgetId: Int): List<Head.Cat> =
        parseCats(prefs(c).getString("quick:$widgetId", null))

    fun parseCats(json: String?): List<Head.Cat> = runCatching {
        val s = json?.trim() ?: return emptyList()
        if (s.startsWith("[")) JSONArray(s).let { a -> (0 until a.length()).map { Head.cat(a.getJSONObject(it)) } }
        else listOf(Head.cat(JSONObject(s)))
    }.getOrDefault(emptyList())

    fun catsJson(cats: List<Head.Cat>): String = JSONArray().apply {
        for (cat in cats) put(JSONObject().put("id", cat.id).put("name", cat.name).put("icon", cat.icon).put("cur", cat.cur))
    }.toString()

    fun setQuick(c: Context, widgetId: Int, cat: Head.Cat) = setQuicks(c, widgetId, listOf(cat))

    fun setQuicks(c: Context, widgetId: Int, cats: List<Head.Cat>) {
        prefs(c).edit().putString("quick:$widgetId", catsJson(cats)).remove("quickAt:$widgetId").apply()
    }

    /** Котра з монет 4×1 зараз на віджеті (тап по сегменту смужки — саме ця). */
    fun quickAt(c: Context, widgetId: Int): Int = prefs(c).getInt("quickAt:$widgetId", 0)

    fun setQuickAt(c: Context, widgetId: Int, at: Int) {
        prefs(c).edit().putInt("quickAt:$widgetId", at).apply()
    }

    /**
     * Витрати з вікна швидкого запису, ще не відправлені на сервер: записи /api/records (store ops).
     * Без зв'язку чекають тут; RefreshWorker відправляє й прибирає лише ті, що сервер прийняв.
     */
    fun pending(c: Context): List<JSONObject> = runCatching {
        val a = JSONArray(prefs(c).getString("pending", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }
    }.getOrDefault(emptyList())

    @Synchronized
    fun enqueue(c: Context, rec: JSONObject) {
        val a = JSONArray().apply { for (r in pending(c)) put(r); put(rec) }
        prefs(c).edit().putString("pending", a.toString()).commit()
    }

    @Synchronized
    fun dropPending(c: Context, ids: Set<String>) {
        val a = JSONArray().apply { for (r in pending(c)) if (r.optString("id") !in ids) put(r) }
        prefs(c).edit().putString("pending", a.toString()).commit()
    }

    /** Остання версія застосунку на GitHub і коли перевіряли — «Є нова версія» на екрані підключення. */
    fun latest(c: Context): String? = prefs(c).getString("latest", null)

    fun latestAt(c: Context): Long = prefs(c).getLong("latestAt", 0L)

    fun setLatest(c: Context, version: String?, at: Long) {
        prefs(c).edit().putString("latest", version).putLong("latestAt", at).apply()
    }

    fun dropQuick(c: Context, widgetIds: IntArray) {
        prefs(c).edit().apply { for (id in widgetIds) remove("quick:$id").remove("quickAt:$id") }.apply()
    }

    fun busy(c: Context): Boolean = prefs(c).getBoolean("busy", false)

    fun setBusy(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("busy", on).apply()
    }
}
