package ua.vitaliylobko.marnotrat.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Підключення й додавання віджета. Марнотрат (Налаштування → «Встановити віджет») відкриває
 * marnotrat-widget://setup?url=…&key=… — адреса сервера й ключ доступу приходять самі.
 */
class SetupActivity : Activity() {

    // Посилання з іншою адресою сервера, ніж збережена, — лише після «Підключити»: його могла підсунути будь-яка сторінка
    private var pending: Store.Config? = null
    private var connected = false
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val ui by lazy { Ui(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        render()
    }

    // Шапка прийшла чи оновлення почалось — екран сам показує нове, не треба виходити й заходити
    override fun onStart() {
        super.onStart()
        listener = Store.listen(this) { runOnUiThread { render() } }
        render()
        checkLatest()
    }

    override fun onStop() {
        listener?.let { Store.unlisten(this, it) }
        listener = null
        super.onStop()
    }

    private fun handle(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme != "marnotrat-widget" || uri.host != "setup") return
        val url = uri.getQueryParameter("url")?.trim()?.trimEnd('/') ?: return
        val key = uri.getQueryParameter("key")?.trim() ?: return
        if (!url.startsWith("https://") || key.isEmpty()) return
        val cur = Store.config(this)
        if (cur == null || cur.url == url) connect(Store.Config(url, key)) else pending = Store.Config(url, key)
    }

    private fun connect(cfg: Store.Config) {
        Store.saveConfig(this, cfg.url, cfg.key)
        pending = null
        connected = true
        Widgets.updateAll(this)
        Widgets.refreshNow(this)
    }

    private fun version(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""

    private fun render() {
        val cfg = Store.config(this)
        val p = pending
        val page = ui.page {
            addView(ui.title(getString(R.string.app_name), "Віджети Марнотрату на головний екран · версія ${version()}"))
            if (p != null) addView(changeServer(cfg, p), ui.lp(8f))
            if (cfg == null) {
                addView(notConnected(), ui.lp(8f))
                return@page
            }
            addView(status(cfg), ui.lp(8f))
            update()?.let { addView(it, ui.lp(12f)) }
            placed()?.let {
                addView(ui.caption("На екрані"))
                addView(it)
            }
            addView(ui.caption("Додати на екран"))
            addView(widgets())
            addView(ui.caption("Як це працює"))
            addView(ui.card {
                setPadding(ui.dp(16f), ui.dp(14f), ui.dp(16f), ui.dp(16f))
                addView(ui.text("Цифри рахує сервер із записів Марнотрату. Віджети беруть їх раз на 15 хвилин, після розблокування екрана й тапом по нижньому рядку віджета.", 14f, R.color.muted))
                addView(ui.text("Ще можна так: довге натискання на головному екрані → Віджети → Віджет Марнотрату.", 14f, R.color.muted), ui.lp(8f))
            })
        }
        // Перемальовуємо на місці: прокрутка лишається, де була
        val old = findViewById<View>(android.R.id.content)?.let { (it as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.ScrollView }
        val y = old?.scrollY ?: 0
        setContentView(page)
        page.post { page.scrollTo(0, y) }
    }

    /** Стан: точка (зелена — є цифри, помаранчева — оновлюється чи ще нема, червона — помилка), сервер, коли перевірено. */
    private fun status(cfg: Store.Config): View {
        val h = Store.head(this)
        val err = Store.error(this)
        val busy = Store.busy(this)
        val host = Uri.parse(cfg.url).host ?: cfg.url
        val (dotColor, head, sub) = when {
            busy -> Triple(R.color.warn, "Оновлюю…", host)
            err != null -> Triple(R.color.over, err, host)
            h == null -> Triple(R.color.warn, "Цифр ще немає", "Відкрий Марнотрат — він передасть записи на сервер. $host")
            else -> Triple(
                R.color.brand,
                if (connected) "Підключено" else "Працює",
                "$host · сервер перевірив ${SimpleDateFormat(if (sameDay(h.sent)) "HH:mm" else "dd.MM HH:mm", Locale.ROOT).format(Date(h.sent))}",
            )
        }
        return ui.card {
            setPadding(ui.dp(16f), ui.dp(16f), ui.dp(16f), ui.dp(16f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ui.dot(dotColor), LinearLayout.LayoutParams(ui.dp(10f), ui.dp(10f)).apply { marginEnd = ui.dp(10f) })
                addView(ui.text(head, 17f, bold = true))
            })
            addView(ui.text(sub, 13f, R.color.muted), ui.lp(4f))
            if (h != null) addView(stats(h), ui.lp(14f))
            addView(ui.button(if (busy) "Оновлюю…" else "Оновити зараз", primary = false) { Widgets.refreshNow(this@SetupActivity) }.apply {
                isEnabled = !busy
                alpha = if (busy) 0.6f else 1f
            }, ui.lp(14f))
        }
    }

    /** Три числа, як на віджеті: баланс, на день (чи витрати без бюджетів), лишилось з бюджету. */
    private fun stats(h: Head): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        fun tile(label: String, value: String, colorId: Int = R.color.text) = addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(label, 12f, R.color.muted))
            addView(ui.text(value, 18f, colorId, bold = true).apply { fontFeatureSettings = "tnum" })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tile("Баланс", Widgets.uah(h.balance), if (h.balance < 0) R.color.over_text else R.color.text)
        when {
            h.plan <= 0 -> tile("Витрати", Widgets.num(h.expense))
            h.left >= 0 -> {
                tile("На день", Widgets.num(h.perDay), R.color.brand_text)
                tile("Лишилось", Widgets.num(h.left))
            }
            else -> tile("Понад бюджет", Widgets.num(-h.left), R.color.over_text)
        }
    }

    /** «Є нова версія» — коли на GitHub новіша за встановлену. «Встановити» в Марнотраті лише підключає, не оновлює. */
    private fun update(): View? {
        val latest = Store.latest(this) ?: return null
        if (!newer(latest, version())) return null
        return ui.card {
            setPadding(ui.dp(16f), ui.dp(16f), ui.dp(16f), ui.dp(16f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ui.dot(R.color.brand), LinearLayout.LayoutParams(ui.dp(10f), ui.dp(10f)).apply { marginEnd = ui.dp(10f) })
                addView(ui.text("Є нова версія $latest", 17f, bold = true))
            })
            addView(ui.text("Chrome завантажить файл — відкрий його зі сповіщення чи «Завантажень» і натисни «Оновити». Віджети й підключення лишаться.", 14f, R.color.muted), ui.lp(6f))
            addView(ui.button("Оновити") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)))
            }, ui.lp(14f))
        }
    }

    /**
     * Остання версія з GitHub — не частіше разу на годину, у фоні; прийшла — екран перемалює слухач Store.
     * User-Agent — назва застосунку: GitHub API без нього відповідає 403.
     */
    private fun checkLatest() {
        if (System.currentTimeMillis() - Store.latestAt(this) < 60 * 60 * 1000L) return
        val c = applicationContext
        Thread {
            runCatching {
                val conn = java.net.URL(RELEASES_API).openConnection() as java.net.HttpURLConnection
                try {
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.setRequestProperty("User-Agent", "marnotrat-widget")
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    if (conn.responseCode == 200) {
                        val tag = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optString("tag_name")
                        Store.setLatest(c, tag.removePrefix("v").ifEmpty { null }, System.currentTimeMillis())
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }.start()
    }

    /** 0.1.18 новіша за 0.1.17 — порівняння по числах, не рядком (0.1.9 < 0.1.10). */
    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun widgets(): View {
        val m = AppWidgetManager.getInstance(this)
        val canPin = m.isRequestPinAppWidgetSupported
        val pin = { cls: Class<*> -> { m.requestPinAppWidget(ComponentName(this, cls), null, null); Unit } }
        val plus = if (canPin) R.drawable.ic_plus_line else null
        return ui.card {
            // Спершу вибір того, що праворуч, потім віджет — лаунчер ставить уже готовий
            addView(ui.row(ui.thumb(R.drawable.thumb_4x1), "Коротко · 4×1", "Баланс, на день, смужка місяця; праворуч «−» і «+» або 1–3 монети витрат стопкою", plus) {
                startActivity(Intent(this@SetupActivity, QuickConfigActivity::class.java).putExtra(QuickConfigActivity.EXTRA_TARGET, QuickConfigActivity.TARGET_COMPACT))
            })
            addView(ui.divider())
            addView(ui.row(ui.thumb(R.drawable.thumb_4x2), "День і тиждень · 4×2", "Витрати за 7 днів із нормою, смужка місяця, наступний платіж", plus, onClick = pin(LargeWidget::class.java)))
            addView(ui.divider())
            // Спершу монета, потім віджет: так лаунчер ставить уже готовий, без кроку «Обрати»
            addView(ui.row(ui.thumb(R.drawable.thumb_1x1), "Швидка витрата · 1×1", "Монета однієї витрати: тап — одразу екран суми", plus) {
                startActivity(Intent(this@SetupActivity, QuickConfigActivity::class.java))
            })
            if (!canPin) addView(ui.text("Цей лаунчер не вміє додавати віджети з застосунку — додай через довге натискання на головному екрані.", 13f, R.color.muted).apply {
                setPadding(ui.dp(16f), 0, ui.dp(16f), ui.dp(14f))
            })
        }
    }

    /**
     * Уже поставлені 4×1 і 1×1 — змінити монету. Не всі лаунчери (Nova) мають «Налаштувати» на віджеті,
     * тож це єдиний певний шлях. null — таких віджетів немає.
     */
    private fun placed(): View? {
        val compact = Widgets.ids(this, CompactWidget::class.java)
        val quick = Widgets.ids(this, QuickWidget::class.java)
        if (compact.isEmpty() && quick.isEmpty()) return null
        val open = { id: Int ->
            { startActivity(Intent(this, QuickConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)) }
        }
        // Кілька однакових — з номером, щоб розрізнити
        fun title(name: String, i: Int, n: Int) = if (n > 1) "$name ${i + 1}" else name
        return ui.card {
            var first = true
            fun add(row: View) {
                if (!first) addView(ui.divider())
                first = false
                addView(row)
            }
            compact.forEachIndexed { i, id ->
                val cats = Store.quicks(this@SetupActivity, id)
                val desc = when (cats.size) {
                    0 -> "Праворуч «−» і «+» · змінити"
                    1 -> "Праворуч монета «${cats[0].name}» · змінити"
                    else -> "Праворуч стопка: ${cats.joinToString(", ") { it.name }} · змінити"
                }
                add(ui.row(ui.thumb(R.drawable.thumb_4x1), title("Коротко · 4×1", i, compact.size), desc, onClick = open(id)))
            }
            quick.forEachIndexed { i, id ->
                val cat = Store.quick(this@SetupActivity, id)
                add(ui.row(ui.thumb(R.drawable.thumb_1x1), title("Швидка витрата · 1×1", i, quick.size),
                    if (cat == null) "Монету не вибрано · обрати" else "Монета «${cat.name}» · змінити", onClick = open(id)))
            }
        }
    }

    private fun notConnected(): View = ui.card {
        setPadding(ui.dp(16f), ui.dp(16f), ui.dp(16f), ui.dp(16f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.dot(R.color.warn), LinearLayout.LayoutParams(ui.dp(10f), ui.dp(10f)).apply { marginEnd = ui.dp(10f) })
            addView(ui.text("Ще не підключено", 17f, bold = true))
        })
        addView(ui.text("Відкрий Марнотрат → Налаштування → На телефоні → «Віджет на екран» → «Встановити». Адреса сервера й ключ прийдуть сюди самі.", 14f, R.color.muted), ui.lp(6f))
        addView(ui.button("Відкрити Марнотрат") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vitaliylobko.github.io/marnotrat/#settings")))
        }, ui.lp(14f))
    }

    private fun changeServer(cfg: Store.Config?, p: Store.Config): View = ui.card {
        setPadding(ui.dp(16f), ui.dp(16f), ui.dp(16f), ui.dp(16f))
        addView(ui.text("Підключити до іншого сервера?", 17f, bold = true))
        addView(ui.text("Нове посилання: ${Uri.parse(p.url).host}. Зараз віджет бере цифри з ${cfg?.url?.let { Uri.parse(it).host }}. Якщо не відкривав «Встановити» в Марнотраті сам — тисни «Ні».", 14f, R.color.muted), ui.lp(6f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(ui.button("Ні", primary = false) { pending = null; render() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.gap(0f), LinearLayout.LayoutParams(ui.dp(8f), 1))
            addView(ui.button("Підключити") { connect(p); render() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }, ui.lp(14f))
    }

    companion object {
        private const val APK_URL = "https://github.com/VitaliyLobko/marnotrat-widget/releases/latest/download/marnotrat-widget.apk"
        private const val RELEASES_API = "https://api.github.com/repos/VitaliyLobko/marnotrat-widget/releases/latest"
    }

    private fun sameDay(t: Long): Boolean {
        val f = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
        return f.format(Date(t)) == f.format(Date())
    }
}
