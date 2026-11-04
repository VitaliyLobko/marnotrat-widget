package ua.vitaliylobko.marnotrat.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Toast

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
    // Монети стопки 4×1 у порядку вибору; поставлений віджет — починаємо з його монет
    private val picked = mutableListOf<Head.Cat>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Закрили, нічого не вибравши, — лаунчер не ставить віджет (чи лишає старий вибір)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (compact && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) picked += Store.quicks(this, widgetId)
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
        val page = ui.page {
            addView(
                if (compact) ui.title("Коротко · 4×1", "Що буде праворуч: кнопки «−» і «+» чи монети витрат — тап по монеті одразу відкриває екран суми")
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
                compact -> {
                    addView(ui.caption("Або монети витрат — до трьох"))
                    addView(ui.text(
                        if (picked.isEmpty()) "Тапни одну-три монети. Кілька — стопкою: свайп угору-вниз гортає, тап по верхній — екран суми."
                        else "Стопка: " + picked.joinToString(" → ") { it.name } + ". Перша — зверху.",
                        14f, R.color.muted,
                    ).apply { setPadding(ui.dp(4f), 0, ui.dp(4f), 0) })
                    addView(ui.button(if (picked.isEmpty()) "Вибери монети" else "Готово") { done() }.apply {
                        isEnabled = picked.isNotEmpty()
                        alpha = if (picked.isEmpty()) 0.6f else 1f
                    }, ui.lp(12f))
                    addView(ui.card {
                        cats.forEachIndexed { i, cat ->
                            if (i > 0) addView(ui.divider())
                            val coin = ImageView(context).apply { setImageBitmap(CoinArt.bitmap(context, cat, 48)) }
                            val at = picked.indexOfFirst { it.id == cat.id }
                            addView(ui.row(coin, cat.name, Widgets.catSpent(cat), trailingView = if (at >= 0) ui.num(at + 1) else null) { toggle(cat) })
                        }
                    }, ui.lp(12f))
                }
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
        }
        // Тап по монеті перемальовує екран — прокрутка лишається, де була
        val y = ((findViewById<View>(android.R.id.content) as? android.view.ViewGroup)?.getChildAt(0) as? ScrollView)?.scrollY ?: 0
        setContentView(page)
        page.post { page.scrollTo(0, y) }
    }

    /** 4×1: тап по монеті — додати в кінець стопки чи прибрати; більше трьох — ні. */
    private fun toggle(cat: Head.Cat) {
        val at = picked.indexOfFirst { it.id == cat.id }
        when {
            at >= 0 -> picked.removeAt(at)
            picked.size >= MAX_STACK -> {
                Toast.makeText(this, "Не більше трьох — спершу прибери одну", Toast.LENGTH_SHORT).show()
                return
            }
            else -> picked += cat
        }
        render()
    }

    /** 4×1 без монети — «−» і «+». */
    private fun buttons() = save(emptyList())

    private fun done() = save(picked.toList())

    private fun choose(cat: Head.Cat) = save(listOf(cat))

    /** Поставлений віджет — зберегти й перемалювати; ще не поставлений — попросити лаунчер поставити. */
    private fun save(cats: List<Head.Cat>) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return pin(cats)
        if (cats.isEmpty()) Store.dropQuick(this, intArrayOf(widgetId)) else Store.setQuicks(this, widgetId, cats)
        Widgets.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    /**
     * Просимо лаунчер поставити вже налаштований віджет. Номер віджета ще невідомий: лаунчер допише його в намір
     * колбеку, тому PendingIntent змінний. Прев'ю у вікні лаунчера — справжній 4×1 з вибраним (не всі лаунчери його показують).
     */
    private fun pin(cats: List<Head.Cat>) {
        val m = AppWidgetManager.getInstance(this)
        val cls = if (compact) CompactWidget::class.java else QuickWidget::class.java
        val callback = cats.takeIf { it.isNotEmpty() }?.let {
            val done = Intent(this, cls).setAction(Widgets.ACTION_PINNED).putExtra(Widgets.EXTRA_CAT, Store.catsJson(it))
            val code = (cls.name + it.joinToString(",") { x -> x.id }).hashCode()
            PendingIntent.getBroadcast(this, code, done, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val extras = if (compact) Bundle().apply {
            putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, Widgets.compact(this@QuickConfigActivity, AppWidgetManager.INVALID_APPWIDGET_ID, cats))
        } else null
        if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(this, cls), extras, callback)
        finish()
    }

    companion object {
        const val EXTRA_TARGET = "target"
        const val TARGET_COMPACT = "compact"
        private const val MAX_STACK = 3
    }
}
