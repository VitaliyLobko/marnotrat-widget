package ua.vitaliylobko.marnotrat.widget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * Швидкий запис витрати з віджета — шторка над головним екраном, без запуску Марнотрату: сума, коментар,
 * часті теги монети, рахунок (тап — інший у тій самій валюті), «Вчора» чи «Записати».
 * Витрата йде в чергу (Store.pending) і на сервер — через RefreshWorker; Марнотрат забере її при наступному обміні.
 * Рахунку немає (старий сервер) чи він в іншій валюті, ніж монета, — відкриваємо екран суми в Марнотраті:
 * перерахунок курсів є лише там.
 */
class QuickAddActivity : Activity() {

    private val ui by lazy { Ui(this) }
    private lateinit var cat: Head.Cat
    private lateinit var acc: Head.Acc
    // Рахунки у валюті монети — з них можна вибрати інший; один чи старий сервер — вибору нема
    private var accs: List<Head.Acc> = emptyList()
    private lateinit var accLine: TextView
    private var amount = ""
    private lateinit var amountView: TextView
    private lateinit var comment: EditText
    private lateinit var keypad: LinearLayout
    // Вибрані теги в порядку тапів — так їх і запише Марнотрат
    private val picked = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getStringExtra(EXTRA_CAT_ID).orEmpty()
        if (Store.config(this) == null) {
            startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return finish()
        }
        val head = Store.head(this)
        val live = head?.cats?.firstOrNull { it.id == id }
        val a = live?.acc
        if (live == null || a == null || a.cur != live.cur) {
            startActivity(Widgets.openAdd(this, id))
            return finish()
        }
        cat = live
        acc = a
        accs = head?.accs.orEmpty().filter { it.cur == live.cur }
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
            // Теги — над клавішами, як у Марнотраті; нових тегів монета ще не має — ряду нема, решта — «Більше»
            if (cat.tags.isNotEmpty()) addView(tagRow(), ui.lp(10f))
            keypad = keypad()
            addView(keypad, ui.lp(12f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                // Набране не губиться: Марнотрат відкриє екран суми з ним
                addView(ui.button("Більше", primary = false) {
                    startActivity(Widgets.openAdd(this@QuickAddActivity, cat.id, draft()))
                    finish()
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.gap(0f), LinearLayout.LayoutParams(ui.dp(8f), 1))
                // Як «Вчора» на екрані суми Марнотрату — записує одразу, вчорашньою датою
                addView(ui.button("Вчора", primary = false) { save(LocalDate.now().minusDays(1)) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.gap(0f), LinearLayout.LayoutParams(ui.dp(8f), 1))
                addView(ui.button("Записати") { save(LocalDate.now()) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f))
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
            accLine = ui.text("", 13f, R.color.muted)
            addView(accLine)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        paintAcc()
        if (accs.size > 1) {
            accLine.setTextColor(getColor(R.color.brand_text))
            accLine.minHeight = ui.dp(32f)
            accLine.gravity = Gravity.CENTER_VERTICAL
            accLine.isClickable = true
            accLine.isFocusable = true
            accLine.setOnClickListener { pickAcc() }
        }
    }

    /** «з Приват ▾ · витрачено …»: стрілка — лише коли є з чого вибрати. */
    private fun paintAcc() {
        val arrow = if (accs.size > 1) " ▾" else ""
        accLine.text = "з ${acc.name}$arrow · ${Widgets.catSpent(cat)}"
        accLine.contentDescription = if (accs.size > 1) "Рахунок ${acc.name}. Змінити" else null
    }

    /** Рахунки у валюті монети із залишком; вибраний — до кінця цього запису (звичний сервер рахує сам). */
    private fun pickAcc() {
        val menu = PopupMenu(this, accLine)
        accs.forEachIndexed { i, x ->
            val mark = if (x.id == acc.id) "✓ " else ""
            menu.menu.add(0, i, i, "$mark${x.name} · ${Widgets.num(x.bal)} ${Widgets.unit(x.cur)}")
        }
        menu.setOnMenuItemClickListener { item ->
            accs.getOrNull(item.itemId)?.let { acc = it; paintAcc() }
            true
        }
        menu.show()
    }

    /** Часті теги монети чипами в один ряд, що гортається вбік; тап — вибрати чи зняти. */
    private fun tagRow(): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            cat.tags.forEachIndexed { i, t ->
                val chip = ui.text(t, 15f).apply {
                    gravity = Gravity.CENTER
                    minHeight = ui.dp(40f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    maxWidth = ui.dp(200f)
                    setPadding(ui.dp(14f), 0, ui.dp(14f), 0)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Тег $t"
                    setOnClickListener { v ->
                        if (!picked.remove(t)) picked.add(t)
                        paintChip(v as TextView, t)
                    }
                }
                paintChip(chip, t)
                addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (i > 0) marginStart = ui.dp(8f)
                })
            }
        })
    }

    /** Вибраний тег — заливка бренду, як .amt-tag.on у Марнотраті; isSelected — щоб TalkBack казав «вибрано». */
    private fun paintChip(chip: TextView, t: String) {
        val on = t in picked
        chip.isSelected = on
        chip.setBackgroundResource(if (on) R.drawable.chip_on else R.drawable.chip_off)
        chip.setTextColor(getColor(if (on) R.color.on_brand else R.color.text))
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

    /** Сума з клавіш, округлена до копійок; нічого не набрано — 0. */
    private fun value(): Double = amount.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100) / 100.0 } ?: 0.0

    /** Набране для «Більше» — query для widgetDraft у Марнотраті: a — сума, c — коментар, t — теги, f — рахунок. */
    private fun draft(): String? = Uri.Builder().apply {
        value().takeIf { it > 0 }?.let { appendQueryParameter("a", it.toString()) }
        comment.text.toString().trim().takeIf { it.isNotEmpty() }?.let { appendQueryParameter("c", it) }
        picked.forEach { appendQueryParameter("t", it) }
        appendQueryParameter("f", acc.id)
    }.build().encodedQuery

    /** Запис як у Марнотраті (store у js/app.js): id, from, to, amount, date, tags, comment, created + updated для злиття. */
    private fun save(date: LocalDate) {
        val v = value()
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
            .put("date", date.toString())
            .put("tags", JSONArray(picked.toList()))
            .put("comment", comment.text.toString().trim())
            .put("created", now)
            .put("updated", now)
        Store.enqueue(this, rec)
        Widgets.refreshNow(this)
        val tags = if (picked.isEmpty()) "" else " · " + picked.joinToString(" ") { "#$it" }
        val day = if (date == LocalDate.now()) "" else " вчора"
        Toast.makeText(this, "Записано$day: −${amountView.text} · ${cat.name}$tags", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_CAT_ID = "cat"
    }
}
