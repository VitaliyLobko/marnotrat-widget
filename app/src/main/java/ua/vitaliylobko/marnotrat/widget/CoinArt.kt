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

    /** Світла й темна — лаунчер сам бере потрібну, коли тема змінюється між оновленнями. */
    fun icons(c: Context, cat: Head.Cat, sizeDp: Int): Pair<Icon, Icon> =
        Icon.createWithBitmap(bitmap(themed(c, false), cat, sizeDp)) to Icon.createWithBitmap(bitmap(themed(c, true), cat, sizeDp))

    fun bitmap(c: Context, cat: Head.Cat, sizeDp: Int): Bitmap {
        val px = (sizeDp * c.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
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
        return bmp
    }

    fun themed(c: Context, night: Boolean): Context {
        val conf = Configuration(c.resources.configuration)
        val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        return c.createConfigurationContext(conf)
    }
}
