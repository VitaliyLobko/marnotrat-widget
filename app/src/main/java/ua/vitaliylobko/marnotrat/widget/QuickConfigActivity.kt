package ua.vitaliylobko.marnotrat.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/**
 * Вибір монети для віджета 1×1. Три входи: лаунчер додає віджет (чи «Налаштувати» на ньому) — номер віджета
 * в EXTRA_APPWIDGET_ID; тап по ще не налаштованому віджету — так само; «Підключення» → «Швидка витрата» —
 * без номера: тоді після вибору просимо лаунчер поставити віджет, монету він отримає через QuickWidget.ACTION_PINNED.
 */
class QuickConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val main = Handler(Looper.getMainLooper())
    private var waiting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Закрили, нічого не вибравши, — лаунчер не ставить віджет (чи лишає старий вибір)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun render() {
        val pad = dp(24f)
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

        line("Швидка витрата", 24f)
        val cats = Store.head(this)?.cats.orEmpty()
        when {
            Store.config(this) == null -> {
                line("Спершу підключи віджет: Марнотрат → Налаштування → На телефоні → «Встановити віджет».")
                button("Відкрити підключення") { startActivity(Intent(this, SetupActivity::class.java)) }
            }
            cats.isEmpty() -> {
                line(
                    if (waiting || Store.busy(this)) "Беру монети із сервера…"
                    else Store.error(this) ?: "Монет ще немає — онови дані. Якщо не з’являться, сервер ще старий: відкрий Марнотрат і спробуй пізніше.",
                    14f,
                )
                button("Оновити") { refreshAndWait() }
            }
            else -> {
                line("Яку витрату записувати з головного екрана? Тап по віджету — одразу екран суми цієї монети.", 14f)
                for (cat in cats) box.addView(row(cat))
            }
        }
        setContentView(ScrollView(this).apply { addView(box) })
    }

    /** Рядок списку: монета, як на головній, назва й скільки витрачено цього місяця. */
    private fun row(cat: Head.Cat): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56f)
        setPadding(0, dp(6f), 0, dp(6f))
        isClickable = true
        isFocusable = true
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        background = ta.getDrawable(0)
        ta.recycle()
        contentDescription = cat.name
        setOnClickListener { choose(cat) }
        addView(ImageView(context).apply {
            setImageBitmap(CoinArt.bitmap(context, cat, 40))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), 0, 0, 0)
            addView(TextView(context).apply {
                text = cat.name
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(context).apply {
                text = Widgets.catSpent(cat)
                textSize = 13f
                alpha = 0.7f
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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

    /** Оновлення йде у фоні (WorkManager) — дивимось раз на пів секунди, чи прийшли монети, до 15 с. */
    private fun refreshAndWait() {
        waiting = true
        Widgets.refreshNow(this)
        render()
        var left = 30
        val tick = object : Runnable {
            override fun run() {
                left -= 1
                if (Store.head(this@QuickConfigActivity)?.cats.orEmpty().isNotEmpty() || left <= 0) {
                    waiting = false
                    render()
                } else {
                    main.postDelayed(this, 500)
                }
            }
        }
        main.postDelayed(tick, 500)
    }
}
