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
 * Вибір монети для віджета 1×1 і для монети праворуч на 4×1. Входи: лаунчер додає віджет (чи «Налаштувати» на ньому),
 * тап по ще не налаштованому 1×1, «Підключення» → «На екрані» — номер віджета в EXTRA_APPWIDGET_ID;
 * «Підключення» → «Додати на екран» — без номера: тоді після вибору просимо лаунчер поставити віджет,
 * монету він отримає через Widgets.ACTION_PINNED. Який віджет ставити — EXTRA_TARGET.
 */
class QuickConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    // 4×1, а не 1×1: інший заголовок, пункт «Кнопки «−» і «+»» і віджет, який ставимо
    private val compact by lazy {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) intent?.getStringExtra(EXTRA_TARGET) == TARGET_COMPACT
        else AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className == CompactWidget::class.java.name
    }
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
            addView(
                if (compact) ui.title("Коротко · 4×1", "Що буде праворуч: кнопки «−» і «+» чи монета витрати — тап по ній одразу відкриває екран суми")
                else ui.title("Швидка витрата", "Тап по віджету — одразу екран суми в цю монету"),
            )
            val connected = Store.config(this@QuickConfigActivity) != null
            // 4×1 може лишитись як був: «−» будь-яка витрата, «+» дохід — і тоді, коли монет із сервера ще немає
            if (compact && connected) {
                addView(ui.caption("Без монети"))
                addView(ui.card {
                    val placed = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    val check = if (placed && current == null) R.drawable.ic_check_line else null
                    addView(ui.row(ui.thumb(R.drawable.thumb_4x1), "Кнопки «−» і «+»", "Будь-яка витрата чи дохід", check) { buttons() })
                })
            }
            when {
                !connected -> addView(ui.card {
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
                    addView(ui.caption(if (compact) "Або монета витрати" else "Витрати"))
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

    /** 4×1 без монети — «−» і «+». */
    private fun buttons() {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return pin(null)
        Store.dropQuick(this, intArrayOf(widgetId))
        Widgets.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun choose(cat: Head.Cat) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return pin(cat)
        Store.setQuick(this, widgetId, cat)
        Widgets.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    /**
     * Просимо лаунчер поставити вже налаштований віджет. Номер віджета ще невідомий: лаунчер допише його в намір
     * колбеку, тому PendingIntent змінний. Прев'ю у вікні лаунчера — справжній 4×1 з вибраним (не всі лаунчери його показують).
     */
    private fun pin(cat: Head.Cat?) {
        val m = AppWidgetManager.getInstance(this)
        val cls = if (compact) CompactWidget::class.java else QuickWidget::class.java
        val callback = cat?.let {
            val json = JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("cur", it.cur).toString()
            val done = Intent(this, cls).setAction(Widgets.ACTION_PINNED).putExtra(Widgets.EXTRA_CAT, json)
            PendingIntent.getBroadcast(this, (cls.name + it.id).hashCode(), done, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val extras = if (compact) Bundle().apply {
            putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, Widgets.compact(this@QuickConfigActivity, AppWidgetManager.INVALID_APPWIDGET_ID, cat))
        } else null
        if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(this, cls), extras, callback)
        finish()
    }

    companion object {
        const val EXTRA_TARGET = "target"
        const val TARGET_COMPACT = "compact"
    }
}
