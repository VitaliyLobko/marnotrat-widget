package ua.vitaliylobko.marnotrat.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * Швидкий запис витрати з віджета — шторка над головним екраном, без запуску Марнотрату: сума, коментар, «Записати».
 * Витрата йде в чергу (Store.pending) і на сервер — через RefreshWorker; Марнотрат забере її при наступному обміні.
 * Рахунку немає (старий сервер) чи він в іншій валюті, ніж монета, — відкриваємо екран суми в Марнотраті:
 * перерахунок курсів є лише там.
 */
class QuickAddActivity : Activity() {

    private val ui by lazy { Ui(this) }
    private lateinit var cat: Head.Cat
    private lateinit var acc: Head.Acc
    private var amount = ""
    private lateinit var amountView: TextView
    private lateinit var comment: EditText
    private lateinit var keypad: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getStringExtra(EXTRA_CAT_ID).orEmpty()
        if (Store.config(this) == null) {
            startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return finish()
        }
        val live = Store.head(this)?.cats?.firstOrNull { it.id == id }
        val a = live?.acc
        if (live == null || a == null || a.cur != live.cur) {
            startActivity(Widgets.openAdd(this, id))
            return finish()
        }
        cat = live
        acc = a
        setContentView(build())
    }

    private fun build(): View {
        val root = FrameLayout(this).apply {
            // Тап повз шторку — закрити, нічого не записавши
            setOnClickListener { finish() }
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(0, bars.top, 0, 0)
                (v as ViewGroup).getChildAt(0)?.let { sheet ->
                    sheet.setPadding(sheet.paddingLeft, sheet.paddingTop, sheet.paddingRight, ui.dp(16f) + bars.bottom)
                }
                insets
            }
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.sheet_bg)
            setPadding(ui.dp(20f), ui.dp(18f), ui.dp(20f), ui.dp(16f))
            isClickable = true
            addView(header())
            amountView = ui.text("", 40f, bold = true).apply {
                gravity = Gravity.CENTER
                fontFeatureSettings = "tnum"
                // Тап по сумі — назад до клавіш, якщо писали коментар
                setOnClickListener { toKeys() }
            }
            addView(amountView, ui.lp(14f))
            comment = EditText(context).apply {
                hint = "Коментар"
                textSize = 16f
                setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_DONE
                setBackgroundResource(R.drawable.field_bg)
                setPadding(ui.dp(14f), ui.dp(12f), ui.dp(14f), ui.dp(12f))
                setTextColor(getColor(R.color.text))
                setHintTextColor(getColor(R.color.muted))
                // Поки пишуть коментар — клавіші суми ховаються, щоб шторка вмістилась над клавіатурою
                setOnFocusChangeListener { _, has -> keypad.visibility = if (has) View.GONE else View.VISIBLE }
                setOnEditorActionListener { _, action, event ->
                    if (action == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) { toKeys(); true } else false
                }
            }
            addView(comment, ui.lp(12f))
            keypad = keypad()
            addView(keypad, ui.lp(12f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(ui.button("Більше", primary = false) {
                    startActivity(Widgets.openAdd(this@QuickAddActivity, cat.id))
                    finish()
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.gap(0f), LinearLayout.LayoutParams(ui.dp(10f), 1))
                addView(ui.button("Записати") { save() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
            }, ui.lp(12f))
        }
        root.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        paint()
        return root
    }

    /** Монета, назва, з якого рахунку й скільки вже витрачено цього місяця. */
    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply {
            setImageBitmap(CoinArt.bitmap(context, cat, 44))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ui.dp(44f), ui.dp(44f)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12f), 0, 0, 0)
            addView(ui.text(cat.name, 18f, bold = true))
            addView(ui.text("з ${acc.name} · ${Widgets.catSpent(cat)}", 13f, R.color.muted))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** 1–9, кома, 0, ⌫ — як клавіші суми в Марнотраті. */
    private fun keypad(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(",", "0", "⌫"))
        rows.forEachIndexed { r, keys ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                keys.forEachIndexed { i, k ->
                    val key = ui.text(k, 24f, bold = true).apply {
                        gravity = Gravity.CENTER
                        setBackgroundResource(R.drawable.key_bg)
                        isClickable = true
                        contentDescription = if (k == "⌫") "Стерти" else k
                        setOnClickListener { press(k) }
                        if (k == "⌫") setOnLongClickListener { amount = ""; paint(); true }
                    }
                    addView(key, LinearLayout.LayoutParams(0, ui.dp(54f), 1f).apply { if (i > 0) marginStart = ui.dp(8f) })
                }
            }, ui.lp(if (r > 0) 8f else 0f))
        }
    }

    private fun press(k: String) {
        val s = amount
        amount = when (k) {
            "⌫" -> s.dropLast(1)
            "," -> if (s.contains(',')) s else (if (s.isEmpty()) "0," else "$s,")
            else -> when {
                s == "0" -> k
                s.contains(',') && s.substringAfter(',').length >= 2 -> s
                !s.contains(',') && s.length >= 9 -> s
                else -> s + k
            }
        }
        paint()
    }

    /** «1 250,5 грн»: тисячі з пробілом, як у Марнотраті. */
    private fun paint() {
        val whole = amount.substringBefore(',').ifEmpty { "0" }
        val grouped = whole.reversed().chunked(3).joinToString(" ").reversed()
        val frac = if (amount.contains(',')) "," + amount.substringAfter(',') else ""
        amountView.text = "$grouped$frac ${Widgets.unit(cat.cur)}"
        amountView.setTextColor(getColor(if (amount.isEmpty()) R.color.muted else R.color.text))
    }

    private fun toKeys() {
        comment.clearFocus()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(comment.windowToken, 0)
        keypad.visibility = View.VISIBLE
    }

    /** Запис як у Марнотраті (store у js/app.js): id, from, to, amount, date, tags, comment, created + updated для злиття. */
    private fun save() {
        val v = amount.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100) / 100.0 } ?: 0.0
        if (v <= 0) {
            Toast.makeText(this, "Введи суму", Toast.LENGTH_SHORT).show()
            return
        }
        val now = System.currentTimeMillis()
        val rec = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("from", acc.id)
            .put("to", cat.id)
            .put("amount", v)
            .put("date", LocalDate.now().toString())
            .put("tags", JSONArray())
            .put("comment", comment.text.toString().trim())
            .put("created", now)
            .put("updated", now)
        Store.enqueue(this, rec)
        Widgets.refreshNow(this)
        Toast.makeText(this, "Записано: −${amountView.text} · ${cat.name}", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_CAT_ID = "cat"
    }
}
