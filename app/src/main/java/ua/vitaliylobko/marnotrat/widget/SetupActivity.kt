package ua.vitaliylobko.marnotrat.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

    override fun onResume() {
        super.onResume()
        render()
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

    private fun render() {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun line(s: String, size: Float = 16f) = box.addView(TextView(this).apply {
            text = s
            textSize = size
            setPadding(0, 0, 0, pad / 2)
        })
        fun button(s: String, onClick: () -> Unit) = box.addView(Button(this).apply {
            text = s
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        line(getString(R.string.app_name), 24f)
        val cfg = Store.config(this)
        val p = pending
        when {
            p != null -> {
                line("Підключити віджет до іншого сервера — ${Uri.parse(p.url).host}? Зараз він бере цифри з ${cfg?.url?.let { Uri.parse(it).host }}.")
                button("Підключити") { connect(p); render() }
                button("Ні") { pending = null; render() }
                line("")
            }
            cfg == null -> {
                line("Ще не підключено. Відкрий Марнотрат → Налаштування → Дані → «Встановити віджет» — адреса сервера й ключ прийдуть сюди самі.")
                setContentView(ScrollView(this).apply { addView(box) })
                return
            }
            connected -> line("Підключено до ${Uri.parse(cfg.url).host}.")
        }
        val h = Store.head(this)
        val err = Store.error(this)
        line(
            when {
                err != null -> err
                h != null -> "Цифри від ${SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(Date(h.sent))}: баланс ${Widgets.uah(h.balance)}, витрати ${Widgets.uah(h.expense)}."
                else -> "Цифр ще немає — відкрий Марнотрат, він їх надішле."
            },
            14f,
        )

        val m = AppWidgetManager.getInstance(this)
        if (m.isRequestPinAppWidgetSupported) {
            button("Додати віджет 4×1") { m.requestPinAppWidget(ComponentName(this, CompactWidget::class.java), null, null) }
            button("Додати віджет 4×2") { m.requestPinAppWidget(ComponentName(this, LargeWidget::class.java), null, null) }
        }
        button("Оновити зараз") { Widgets.refreshNow(this) }
        line("")
        line("Ще можна так: довге натискання на головному екрані → Віджети → Віджет Марнотрату. Цифри оновлюються раз на 15 хвилин і кнопкою ⟳ на віджеті; Марнотрат надсилає їх після кожного запису.", 14f)
        setContentView(ScrollView(this).apply { addView(box) })
    }
}
