package ua.vitaliylobko.marnotrat.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import org.json.JSONObject

/**
 * Вибір монети для віджета 1×1. Три входи: лаунчер додає віджет (чи «Налаштувати» на ньому) — номер віджета
 * в EXTRA_APPWIDGET_ID; тап по ще не налаштованому віджету — так само; «Підключення» → «Швидка витрата» —
 * без номера: тоді після вибору просимо лаунчер поставити віджет, монету він отримає через QuickWidget.ACTION_PINNED.
 */
class QuickConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val ui by lazy { Ui(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Закрили, нічого не вибравши, — лаунчер не ставить віджет (чи лишає старий вибір)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        render()
    }

    // Монети прийшли з сервера — список з'являється сам
    override fun onStart() {
        super.onStart()
        listener = Store.listen(this) { runOnUiThread { render() } }
        render()
    }

    override fun onStop() {
        listener?.let { Store.unlisten(this, it) }
        listener = null
        super.onStop()
    }

    private fun render() {
        val cats = Store.head(this)?.cats.orEmpty()
        val current = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) Store.quick(this, widgetId)?.id else null
        setContentView(ui.page {
            addView(ui.title("Швидка витрата", "Тап по віджету — одразу екран суми в цю монету"))
            when {
                Store.config(this@QuickConfigActivity) == null -> addView(ui.card {
                    setPadding(ui.dp(16f), ui.dp(16f), ui.dp(16f), ui.dp(16f))
                    addView(ui.text("Спершу підключи віджет до Марнотрату.", 15f))
                    addView(ui.button("Відкрити підключення") {
                        startActivity(Intent(this@QuickConfigActivity, SetupActivity::class.java))
                    }, ui.lp(14f))
                }, ui.lp(8f))
                cats.isEmpty() -> addView(ui.card {
                    setPadding(ui.dp(16f), ui.dp(16f), ui.dp(16f), ui.dp(16f))
                    val busy = Store.busy(this@QuickConfigActivity)
                    addView(ui.text(
                        if (busy) "Беру монети із сервера…"
                        else Store.error(this@QuickConfigActivity) ?: "Монет ще немає — онови дані. Не з’являться — відкрий Марнотрат: він передасть записи на сервер.",
                        15f,
                    ))
                    addView(ui.button(if (busy) "Оновлюю…" else "Оновити", primary = false) { Widgets.refreshNow(this@QuickConfigActivity) }.apply {
                        isEnabled = !busy
                        alpha = if (busy) 0.6f else 1f
                    }, ui.lp(14f))
                }, ui.lp(8f))
                else -> {
                    addView(ui.caption("Витрати"))
                    addView(ui.card {
                        cats.forEachIndexed { i, cat ->
                            if (i > 0) addView(ui.divider())
                            val coin = ImageView(context).apply { setImageBitmap(CoinArt.bitmap(context, cat, 48)) }
                            val check = if (cat.id == current) R.drawable.ic_check_line else null
                            addView(ui.row(coin, cat.name, Widgets.catSpent(cat), check) { choose(cat) })
                        }
                    })
                }
            }
        })
    }

    private fun choose(cat: Head.Cat) {
        val m = AppWidgetManager.getInstance(this)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Store.setQuick(this, widgetId, cat)
            m.updateAppWidget(widgetId, Widgets.quick(this, widgetId))
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
            return
        }
        // Номер віджета ще невідомий: лаунчер допише його в цей намір, тому PendingIntent змінний
        val json = JSONObject().put("id", cat.id).put("name", cat.name).put("icon", cat.icon).put("cur", cat.cur).toString()
        val done = Intent(this, QuickWidget::class.java).setAction(QuickWidget.ACTION_PINNED).putExtra(QuickWidget.EXTRA_CAT, json)
        val callback = PendingIntent.getBroadcast(this, cat.id.hashCode(), done, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(this, QuickWidget::class.java), null, callback)
        finish()
    }
}
