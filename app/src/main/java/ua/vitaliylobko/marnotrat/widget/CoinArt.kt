package ua.vitaliylobko.marnotrat.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Icon

/**
 * Монета витрати картинкою, як на головній Марнотрату: сіре коло, знизу доливається колір бюджету
 * (зелений — у темпі, помаранчевий — швидше, червоний — понад), без бюджету — зелений обідок; іконка посередині.
 */
object CoinArt {
    // Частки діаметра — як у css: іконка 50 %, обідок «без бюджету» 4 px на монеті 70 px
    private const val ICON_SHARE = 0.5f
    private const val RING_SHARE = 4f / 70f
    // Значок «+» на монеті 4×1: частка картинки й обвідка кольором підкладки, щоб не зливався з заливкою
    private const val BADGE_SHARE = 0.4f
    private const val BADGE_RING_DP = 2f

    /** Світла й темна — лаунчер сам бере потрібну, коли тема змінюється між оновленнями. */
    fun icons(c: Context, cat: Head.Cat, sizeDp: Int, badge: Boolean = false, slot: Widgets.Slot? = null): Pair<Icon, Icon> =
        Icon.createWithBitmap(bitmap(themed(c, false), cat, sizeDp, badge, slot)) to
            Icon.createWithBitmap(bitmap(themed(c, true), cat, sizeDp, badge, slot))

    /**
     * badge — «+» унизу праворуч (віджет 4×1): монета трохи менша, щоб значок вліз у картинку.
     * Колір значка — монети в смужці (slot), без неї зелений.
     */
    fun bitmap(c: Context, cat: Head.Cat, sizeDp: Int, badge: Boolean = false, slot: Widgets.Slot? = null): Bitmap {
        val d = c.resources.displayMetrics.density
        val full = (sizeDp * d).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val px = if (badge) (full * 0.9f).toInt() else full
        val r = px / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.getColor(R.color.coin_empty) }
        cv.drawCircle(r, r, r, paint)
        val fillColor = when (cat.st) {
            "ok" -> R.color.coin_ok
            "warn" -> R.color.coin_warn
            "over" -> R.color.coin_over
            else -> 0
        }
        if (fillColor != 0 && cat.fill > 0) {
            val clip = Path().apply { addCircle(r, r, r, Path.Direction.CW) }
            cv.save()
            cv.clipPath(clip)
            paint.color = c.getColor(fillColor)
            cv.drawRect(0f, px * (1 - cat.fill.toFloat().coerceIn(0f, 1f)), px.toFloat(), px.toFloat(), paint)
            cv.restore()
        }
        if (cat.st == "free") {
            val w = px * RING_SHARE
            paint.color = c.getColor(R.color.coin_ok)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w
            cv.drawCircle(r, r, r - w / 2, paint)
        }
        val ico = c.getDrawable(CoinIcons.of(cat.icon))?.mutate()
        if (ico != null) {
            val half = px * ICON_SHARE / 2
            ico.setTint(c.getColor(R.color.coin_ico))
            ico.setBounds((r - half).toInt(), (r - half).toInt(), (r + half).toInt(), (r + half).toInt())
            ico.draw(cv)
        }
        if (badge) drawBadge(c, cv, full, d, slot)
        return bmp
    }

    private fun drawBadge(c: Context, cv: Canvas, full: Int, d: Float, slot: Widgets.Slot?) {
        val br = full * BADGE_SHARE / 2
        val cx = full - br - BADGE_RING_DP * d / 2
        val cy = cx
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = c.getColor(slot?.soft ?: R.color.brand_soft)
        cv.drawCircle(cx, cy, br + BADGE_RING_DP * d, p)
        p.color = c.getColor(slot?.color ?: R.color.brand)
        cv.drawCircle(cx, cy, br, p)
        p.color = c.getColor(R.color.on_brand)
        p.style = Paint.Style.STROKE
        p.strokeWidth = br * 0.22f
        p.strokeCap = Paint.Cap.ROUND
        val arm = br * 0.5f
        cv.drawLine(cx - arm, cy, cx + arm, cy, p)
        cv.drawLine(cx, cy - arm, cx, cy + arm, p)
    }

    fun themed(c: Context, night: Boolean): Context {
        val conf = Configuration(c.resources.configuration)
        val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        return c.createConfigurationContext(conf)
    }
}
