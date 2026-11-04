package ua.vitaliylobko.marnotrat.widget

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Будівельні блоки екранів застосунку (підключення, вибір монети) — як налаштування Марнотрату:
 * заголовок, підписи груп великими літерами, картки з рядками «іконка · назва · опис», кнопки бренду.
 */
class Ui(private val a: Activity) {
    private val dm = a.resources.displayMetrics

    fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm).toInt()

    private fun color(id: Int) = a.getColor(id)

    private fun medium(t: TextView) = t.apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    /** Сторінка з прокруткою; відступи під рядок стану й навігацію — Android 15 малює вікно під ними. */
    fun page(build: LinearLayout.() -> Unit): ScrollView {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(24f))
            build()
        }
        return ScrollView(a).apply {
            isFillViewport = true
            clipToPadding = false
            addView(box)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
    }

    fun title(text: String, sub: String? = null): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4f), dp(16f), dp(4f), dp(8f))
        addView(medium(TextView(a).apply {
            this.text = text
            textSize = 26f
            setTextColor(color(R.color.text))
        }))
        if (sub != null) addView(TextView(a).apply {
            this.text = sub
            textSize = 14f
            setTextColor(color(R.color.muted))
        })
    }

    /** «ДОДАТИ НА ЕКРАН» — як .set-caption у Марнотраті. */
    fun caption(text: String): View = TextView(a).apply {
        this.text = text.uppercase()
        textSize = 12f
        letterSpacing = 0.06f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.muted))
        setPadding(dp(4f), dp(22f), dp(4f), dp(8f))
    }

    fun card(build: LinearLayout.() -> Unit): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        clipToOutline = true
        build()
    }

    fun text(s: CharSequence, size: Float = 15f, colorId: Int = R.color.text, bold: Boolean = false): TextView =
        TextView(a).apply {
            text = s
            textSize = size
            setTextColor(color(colorId))
            setLineSpacing(0f, 1.15f)
            if (bold) medium(this)
        }

    /** Рядок картки: мініатюра чи монета, назва й опис, праворуч «+» чи нічого. Весь рядок — одна кнопка. */
    fun row(icon: View, title: String, desc: String?, trailing: Int? = null, trailingView: View? = null, onClick: () -> Unit): View =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64f)
            setPadding(dp(16f), dp(10f), dp(12f), dp(10f))
            setBackgroundResource(R.drawable.row_press)
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(title, desc).joinToString(". ")
            setOnClickListener { onClick() }
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(icon, LinearLayout.LayoutParams(dp(48f), dp(48f)))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f), 0, dp(8f), 0)
                addView(medium(text(title, 16f)))
                if (desc != null) addView(text(desc, 13f, R.color.muted).apply { setPadding(0, dp(2f), 0, 0) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (trailing != null) addView(ImageView(a).apply {
                setImageResource(trailing)
                imageTintList = ColorStateList.valueOf(color(R.color.brand_text))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24f), dp(24f)))
            if (trailingView != null) addView(trailingView, LinearLayout.LayoutParams(dp(26f), dp(26f)))
        }

    /** Номер у колі бренду — порядок монети в стопці 4×1. */
    fun num(n: Int): View = medium(TextView(a)).apply {
        text = n.toString()
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(color(R.color.on_brand))
        setBackgroundResource(R.drawable.dot)
        backgroundTintList = ColorStateList.valueOf(color(R.color.brand))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Лінія між рядками картки — від тексту, не з краю, як у списках Android. */
    fun divider(): View = View(a).apply {
        setBackgroundColor(color(R.color.line))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply { marginStart = dp(78f) }
    }

    fun thumb(res: Int): View = ImageView(a).apply {
        setImageResource(res)
        setBackgroundResource(R.drawable.thumb_bg)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
    }

    fun button(label: String, primary: Boolean = true, onClick: () -> Unit): TextView = medium(TextView(a)).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        minHeight = dp(48f)
        setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
        setTextColor(color(if (primary) R.color.on_brand else R.color.brand_text))
        setBackgroundResource(if (primary) R.drawable.btn_primary else R.drawable.btn_tonal)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun dot(colorId: Int): View = View(a).apply {
        setBackgroundResource(R.drawable.dot)
        backgroundTintList = ColorStateList.valueOf(color(colorId))
    }

    fun gap(h: Float): View = View(a).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    fun lp(top: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
}
