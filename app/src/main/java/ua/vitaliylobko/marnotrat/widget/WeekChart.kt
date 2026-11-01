package ua.vitaliylobko.marnotrat.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.util.TypedValue
import java.time.LocalDate

/**
 * Стовпчики тижня для віджета 4×2 — картинкою: RemoteViews не вміє малювати діаграми.
 * З нормою: бренд — день у межах, помаранчевий — понад норму, пунктир — норма на день.
 * Без норми (бюджетів немає або перевитрата) — сірі: «у межах» тоді нема з чим порівнювати.
 */
object WeekChart {
    private val DAYS = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")

    /** Дві картинки — світла й темна: лаунчер сам бере потрібну, коли тема змінюється між оновленнями. */
    fun icons(c: Context, h: Head, wDp: Int, hDp: Int): Pair<Icon, Icon> =
        Icon.createWithBitmap(draw(themed(c, false), h, wDp, hDp)) to Icon.createWithBitmap(draw(themed(c, true), h, wDp, hDp))

    /** Те саме словами — для TalkBack. */
    fun describe(h: Head): String {
        val days = h.week.mapIndexed { i, (date, v) ->
            val day = dayName(date) + if (i == h.week.size - 1) " (сьогодні)" else ""
            "$day ${Widgets.num(v)}"
        }
        val norm = norm(h)
        return "Витрати за 7 днів: " + days.joinToString(", ") + if (norm > 0) ". Норма ${Widgets.num(norm)} на день" else ""
    }

    private fun norm(h: Head): Double = if (h.plan > 0 && h.left >= 0) h.perDay else 0.0

    private fun themed(c: Context, night: Boolean): Context {
        val conf = Configuration(c.resources.configuration)
        val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        return c.createConfigurationContext(conf)
    }

    private fun draw(c: Context, h: Head, wDp: Int, hDp: Int): Bitmap {
        val dm = c.resources.displayMetrics
        val d = dm.density
        val bmp = Bitmap.createBitmap((wDp * d).toInt().coerceAtLeast(1), (hDp * d).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val week = h.week
        if (week.isEmpty()) return bmp
        val cv = Canvas(bmp)
        val sp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm) }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
            color = c.getColor(R.color.muted)
        }
        val value = Paint(small).apply { color = c.getColor(R.color.text) }
        val today = Paint(small).apply {
            color = c.getColor(R.color.brand_text)
            typeface = Typeface.DEFAULT_BOLD
        }
        // Обвідка кольору тла: пунктир норми не перекреслює підписи
        val halo = Paint(value).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3 * d
            strokeJoin = Paint.Join.ROUND
            color = c.getColor(R.color.surface)
        }
        val bar = Paint(Paint.ANTI_ALIAS_FLAG)

        val w = bmp.width.toFloat()
        val labelH = small.textSize + 3 * d
        val top = labelH
        val base = bmp.height - labelH
        val area = (base - top).coerceAtLeast(1f)
        val norm = norm(h)
        val max = maxOf(week.maxOf { it.second }, norm, 1.0)
        val gap = 6 * d
        val barW = (w - gap * (week.size - 1)) / week.size
        val labels = mutableListOf<Triple<String, Float, Float>>()

        week.forEachIndexed { i, (_, v) ->
            val last = i == week.size - 1
            val x = i * (barW + gap)
            val bh = maxOf(3 * d, (v / max * area).toFloat())
            // Сьогодні порівнюється з нормою дня, а не з perDay — той уже зменшений на сьогоднішні витрати
            val over = if (last) h.todayOver else norm > 0 && v > norm
            bar.color = c.getColor(
                when {
                    over -> R.color.warn
                    norm > 0 -> R.color.brand
                    else -> R.color.muted
                },
            )
            cv.drawRoundRect(RectF(x, base - bh, x + barW, base), 4 * d, 4 * d, bar)
            labels += Triple(short(v), x + barW / 2, base - bh - 3 * d)
        }

        if (norm > 0) {
            val y = (base - norm / max * area).toFloat()
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = c.getColor(R.color.muted)
                strokeWidth = 1.5f * d
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(5 * d, 4 * d), 0f)
                alpha = 140
            }
            cv.drawLine(0f, y, w, y, line)
        }

        labels.forEachIndexed { i, (text, cx, y) ->
            val last = i == labels.size - 1
            val paint = if (last) today else value
            halo.typeface = paint.typeface
            cv.drawText(text, cx, y, halo)
            cv.drawText(text, cx, y, paint)
            cv.drawText(dayName(week[i].first), cx, bmp.height - 2 * d, if (last) today else small)
        }
        return bmp
    }

    private fun dayName(date: String): String =
        runCatching { DAYS[LocalDate.parse(date).dayOfWeek.value - 1] }.getOrDefault("")

    /** «2,1к», «850», «0» — над стовпчиком місця мало. */
    private fun short(v: Double): String {
        val n = Math.round(v)
        if (n < 1000) return n.toString()
        val k = Math.round(n / 100.0) / 10.0
        return (if (k % 1.0 == 0.0) k.toLong().toString() else k.toString().replace('.', ',')) + "к"
    }
}
