package ua.vitaliylobko.marnotrat.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Розклад оновлень і малювання обох віджетів. Цифри не рахуються тут — лише показуються з шапки. */
object Widgets {
    const val ACTION_REFRESH = "ua.vitaliylobko.marnotrat.widget.REFRESH"
    private const val APP_URL = "https://vitaliylobko.github.io/marnotrat/"

    // Шапка старша за 3 години — час з датою й помаранчевим: цифри могли змінитися
    private const val STALE_MS = 3 * 60 * 60 * 1000L

    private val CONNECTED = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Раз на 15 хв (частіше Android не дає), лише з мережею. */
    fun schedule(c: Context) {
        val work = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).setConstraints(CONNECTED).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.KEEP, work)
    }

    fun refreshNow(c: Context) {
        val work = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(CONNECTED).build()
        WorkManager.getInstance(c).enqueueUniqueWork("refresh-now", ExistingWorkPolicy.REPLACE, work)
    }

    fun any(c: Context): Boolean =
        ids(c, CompactWidget::class.java).isNotEmpty() || ids(c, LargeWidget::class.java).isNotEmpty()

    fun cancelIfNone(c: Context) {
        if (!any(c)) WorkManager.getInstance(c).cancelUniqueWork("refresh")
    }

    fun updateAll(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        for (id in ids(c, CompactWidget::class.java)) m.updateAppWidget(id, compact(c))
        for (id in ids(c, LargeWidget::class.java)) m.updateAppWidget(id, large(c, m, id))
    }

    private fun ids(c: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, cls))

    /** 4×1: баланс крупно; на день, сьогодні, в планах — довідкою праворуч; смужка місяця; «−» витрата й «+» дохід. */
    fun compact(c: Context): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_compact)
        val h = Store.head(c)
        v.setOnClickPendingIntent(R.id.root, if (Store.config(c) == null) openSetup(c) else openApp(c, ""))
        v.setOnClickPendingIntent(R.id.add_expense, openApp(c, "#add/expense"))
        v.setOnClickPendingIntent(R.id.add_income, openApp(c, "#add/income"))
        // Окремої кнопки ⟳ немає місця — оновлює тап по рядку зі смужкою
        v.setOnClickPendingIntent(R.id.status_row, refresh(c, CompactWidget::class.java))
        staleStamp(c, v, h)
        if (h == null) {
            v.setTextViewText(R.id.pace, Store.error(c) ?: if (Store.config(c) == null) "Підключи в Марнотраті → Налаштування" else "")
            v.setViewVisibility(R.id.bars, View.GONE)
            return v
        }
        head(c, v, h, short = true, unit = true)
        v.setTextViewText(R.id.today_v, num(h.today))
        v.setTextViewText(R.id.plan_v, num(h.planned))
        bars(v, h)
        v.setTextViewText(
            R.id.pace,
            when {
                h.plan <= 0 -> "бюджетів не задано"
                h.left < 0 -> "бюджет вичерпано · ${days(h.daysLeft)}"
                else -> "лишилось ${num(h.left)} · ${days(h.daysLeft)}"
            },
        )
        return v
    }

    /** 4×2: баланс і скільки можна на день, стовпчики тижня з нормою, сьогодні · в планах. */
    fun large(c: Context, m: AppWidgetManager, id: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_large)
        val h = Store.head(c)
        v.setOnClickPendingIntent(R.id.root, if (Store.config(c) == null) openSetup(c) else openApp(c, ""))
        v.setOnClickPendingIntent(R.id.add_expense, openApp(c, "#add/expense"))
        v.setOnClickPendingIntent(R.id.add_income, openApp(c, "#add/income"))
        // Увесь нижній рядок, а не лише ⟳ на 24 dp — у маленьку кнопку важко влучити
        v.setOnClickPendingIntent(R.id.status_row, refresh(c, LargeWidget::class.java))
        stamp(c, v, h)
        if (h == null) {
            v.setTextViewText(R.id.rest, Store.error(c) ?: if (Store.config(c) == null) "Підключи в Марнотраті → Налаштування" else "")
            return v
        }
        // Розмір віджета в dp з налаштувань лаунчера (у портреті — мінімальна ширина й максимальна висота);
        // під діаграму — висота мінус рядок зверху (54 dp), рядок знизу (32 dp), відступи й проміжки (36 dp)
        val o = m.getAppWidgetOptions(id)
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 320
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 180
        // На вузькому «грн» поступається місцем самому балансу
        head(c, v, h, short = false, unit = wDp >= 300)
        v.setTextViewText(R.id.today_v, "${c.getString(R.string.today)} ${num(h.today)}")
        val rest = "  ·  ${c.getString(R.string.planned).lowercase()} ${num(h.planned)}"
        v.setTextViewText(R.id.rest, if (h.plan > 0) "$rest  ·  ще ${days(h.daysLeft)}" else rest)
        val (light, dark) = WeekChart.icons(c, h, wDp - 27, (hDp - 122).coerceIn(40, 240))
        v.setIcon(R.id.chart, "setImageIcon", light, dark)
        v.setContentDescription(R.id.chart, WeekChart.describe(h))
        return v
    }

    /**
     * Спільне для обох: баланс (від'ємний — червоним) і друге число — на день, понад бюджет
     * або витрати, коли бюджетів немає; «сьогодні» помаранчевим, якщо витрачено більше за норму дня.
     */
    private fun head(c: Context, v: RemoteViews, h: Head, short: Boolean, unit: Boolean) {
        v.setTextViewText(R.id.bal_v, if (unit) money(h.balance) else num(h.balance))
        v.setColorStateList(R.id.bal_v, "setTextColor", if (h.balance < 0) R.color.over_text else R.color.text)
        val (label, value, color) = when {
            h.plan <= 0 -> Triple(c.getString(R.string.expense), h.expense, R.color.text)
            h.left >= 0 -> Triple(c.getString(R.string.per_day), h.perDay, R.color.brand_text)
            else -> Triple(if (short) "Понад" else "Понад бюджет", -h.left, R.color.over_text)
        }
        v.setTextViewText(R.id.c1_l, label)
        v.setTextViewText(R.id.c1_v, if (short || !unit) num(value) else money(value))
        v.setColorStateList(R.id.c1_v, "setTextColor", color)
        v.setColorStateList(R.id.today_v, "setTextColor", if (h.todayOver) R.color.warn_text else R.color.text)
    }

    /** «18 731 грн»: «грн» дрібніше — число читається першим. */
    private fun money(v: Double): CharSequence {
        val s = SpannableStringBuilder(num(v))
        val at = s.length
        s.append(" грн")
        s.setSpan(RelativeSizeSpan(0.5f), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /** Заливка — скільки бюджету витрачено, риска — скільки минуло періоду. Без бюджету смужки немає. */
    private fun bars(v: RemoteViews, h: Head) {
        if (h.plan <= 0) {
            v.setViewVisibility(R.id.bars, View.GONE)
            return
        }
        v.setViewVisibility(R.id.bars, View.VISIBLE)
        val over = h.left < 0
        val bar = if (over) R.id.bar_over else R.id.bar_ok
        v.setViewVisibility(R.id.bar_ok, if (over) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.bar_over, if (over) View.VISIBLE else View.GONE)
        val spent = (h.expense / h.plan * 1000).toInt().coerceIn(0, 1000)
        val time = if (h.days > 0) (h.day * 1000 / h.days).coerceIn(0, 1000) else 0
        v.setProgressBar(bar, 1000, spent, false)
        v.setInt(bar, "setSecondaryProgress", time)
    }

    private fun stamp(c: Context, v: RemoteViews, h: Head?) {
        val text = when {
            Store.busy(c) -> "…"
            h == null || h.sent <= 0 -> ""
            else -> {
                val old = System.currentTimeMillis() - h.sent > STALE_MS
                v.setColorStateList(R.id.stamp, "setTextColor", if (old) R.color.warn_text else R.color.muted)
                SimpleDateFormat(if (old) "dd.MM HH:mm" else "HH:mm", Locale.ROOT).format(Date(h.sent))
            }
        }
        v.setTextViewText(R.id.stamp, text)
    }

    /** 4×1: час лише тоді, коли варто звернути увагу, — дані застаріли чи саме оновлюються. */
    private fun staleStamp(c: Context, v: RemoteViews, h: Head?) {
        val text = when {
            Store.busy(c) -> "…"
            // Коротко, щоб не з'їдати смужку: сьогоднішні — час, давніші — дата
            h != null && h.sent > 0 && System.currentTimeMillis() - h.sent > STALE_MS ->
                "дані з " + SimpleDateFormat(if (sameDay(h.sent)) "HH:mm" else "dd.MM", Locale.ROOT).format(Date(h.sent))
            else -> null
        }
        v.setViewVisibility(R.id.stamp, if (text == null) View.GONE else View.VISIBLE)
        if (text != null) v.setTextViewText(R.id.stamp, text)
    }

    private fun sameDay(t: Long): Boolean {
        val f = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
        return f.format(Date(t)) == f.format(Date())
    }

    private val MONEY = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = ' ' })

    /** «8 731», від'ємне — зі знаком мінус, без копійок, як шапка застосунку. */
    fun num(v: Double): String {
        val n = Math.round(v)
        return (if (n < 0) "−" else "") + MONEY.format(kotlin.math.abs(n))
    }

    fun uah(v: Double): String = num(v) + " грн"

    private fun days(n: Int): String {
        val word = when {
            n % 10 == 1 && n % 100 != 11 -> "день"
            n % 10 in 2..4 && n % 100 !in 12..14 -> "дні"
            else -> "днів"
        }
        return "$n $word"
    }

    /** Марнотрат: Chrome відкриває встановлений застосунок (WebAPK), а не вкладку. */
    private fun openApp(c: Context, hash: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(APP_URL + hash)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(c, hash.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openSetup(c: Context): PendingIntent {
        val intent = Intent(c, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(c, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun refresh(c: Context, cls: Class<*>): PendingIntent {
        val intent = Intent(c, cls).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(c, cls.name.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
