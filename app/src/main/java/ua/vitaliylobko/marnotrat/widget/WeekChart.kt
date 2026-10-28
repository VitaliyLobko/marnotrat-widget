package ua.vitaliylobko.marnotrat.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import java.time.LocalDate

/**
 * Стовпчики тижня для віджета 4×2 — картинкою: RemoteViews не вміє малювати діаграми.
 * Зелений — день у межах норми, помаранчевий — понад норму, бренд — сьогодні; пунктир — норма на день.
 */
object WeekChart {
    private val DAYS = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")

    fun draw(c: Context, h: Head, wDp: Int, hDp: Int): Bitmap {
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
        val bar = Paint(Paint.ANTI_ALIAS_FLAG)

        val w = bmp.width.toFloat()
        val labelH = small.textSize + 3 * d
        val top = labelH
        val base = bmp.height - labelH
        val area = (base - top).coerceAtLeast(1f)
        val norm = if (h.plan > 0 && h.left >= 0) h.perDay else 0.0
        val max = maxOf(week.maxOf { it.second }, norm, 1.0)
        val gap = 6 * d
        val barW = (w - gap * (week.size - 1)) / week.size

        week.forEachIndexed { i, (date, v) ->
            val last = i == week.size - 1
            val x = i * (barW + gap)
            val bh = maxOf(3 * d, (v / max * area).toFloat())
            bar.color = c.getColor(
                when {
                    last -> R.color.brand
                    norm > 0 && v > norm -> R.color.warn
                    else -> R.color.ok
                },
            )
            cv.drawRoundRect(RectF(x, base - bh, x + barW, base), 4 * d, 4 * d, bar)
            val cx = x + barW / 2
            cv.drawText(short(v), cx, base - bh - 3 * d, if (last) today else value)
            val day = runCatching { DAYS[LocalDate.parse(date).dayOfWeek.value - 1] }.getOrDefault("")
            cv.drawText(day, cx, bmp.height - 2 * d, if (last) today else small)
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
        return bmp
    }

    /** «2,1к», «850», «0» — над стовпчиком місця мало. */
    private fun short(v: Double): String {
        val n = Math.round(v)
        if (n < 1000) return n.toString()
        val k = Math.round(n / 100.0) / 10.0
        return (if (k % 1.0 == 0.0) k.toLong().toString() else k.toString().replace('.', ',')) + "к"
    }
}
