package ua.vitaliylobko.marnotrat.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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

    fun cancelIfNone(c: Context) {
        if (ids(c, CompactWidget::class.java).isEmpty() && ids(c, LargeWidget::class.java).isEmpty()) {
            WorkManager.getInstance(c).cancelUniqueWork("refresh")
        }
    }

    fun updateAll(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        for (id in ids(c, CompactWidget::class.java)) m.updateAppWidget(id, compact(c))
        for (id in ids(c, LargeWidget::class.java)) m.updateAppWidget(id, large(c))
    }

    private fun ids(c: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, cls))

    fun compact(c: Context): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_compact)
        val h = Store.head(c)
        v.setOnClickPendingIntent(R.id.root, if (Store.config(c) == null) openSetup(c) else openApp(c, ""))
        v.setOnClickPendingIntent(R.id.add, openApp(c, "#add/expense"))
        v.setOnClickPendingIntent(R.id.refresh, refresh(c, CompactWidget::class.java))
        stamp(c, v, h)
        if (h == null) {
            v.setTextViewText(R.id.pace, Store.error(c) ?: if (Store.config(c) == null) "Підключи в Марнотраті → Налаштування" else "")
            v.setViewVisibility(R.id.bars, View.GONE)
            return v
        }
        v.setTextViewText(R.id.bal_v, uah(h.balance))
        v.setTextViewText(R.id.exp_v, uah(h.expense))
        when {
            h.plan <= 0 -> {
                v.setTextViewText(R.id.third_l, c.getString(R.string.planned))
                v.setTextViewText(R.id.third_v, uah(h.planned))
                v.setColorStateList(R.id.third_v, "setTextColor", R.color.text)
            }
            h.left >= 0 -> {
                v.setTextViewText(R.id.third_l, "Лишилось")
                v.setTextViewText(R.id.third_v, uah(h.left))
                v.setColorStateList(R.id.third_v, "setTextColor", R.color.brand_text)
            }
            else -> {
                v.setTextViewText(R.id.third_l, "Понад бюджет")
                v.setTextViewText(R.id.third_v, uah(-h.left))
                v.setColorStateList(R.id.third_v, "setTextColor", R.color.over_text)
            }
        }
        bars(v, h)
        v.setTextViewText(R.id.pace, pace(h))
        return v
    }

    fun large(c: Context): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_large)
        val h = Store.head(c)
        v.setOnClickPendingIntent(R.id.root, if (Store.config(c) == null) openSetup(c) else openApp(c, ""))
        v.setOnClickPendingIntent(R.id.add_expense, openApp(c, "#add/expense"))
        v.setOnClickPendingIntent(R.id.add_income, openApp(c, "#add/income"))
        v.setOnClickPendingIntent(R.id.refresh, refresh(c, LargeWidget::class.java))
        stamp(c, v, h)
        if (h == null) {
            v.setTextViewText(R.id.hero_l, "")
            v.setTextViewText(R.id.hero_sub, Store.error(c) ?: if (Store.config(c) == null) "Підключи в Марнотраті → Налаштування" else "")
            v.setViewVisibility(R.id.bars, View.GONE)
            return v
        }
        v.setTextViewText(R.id.period, if (h.days > 0) "${h.period} · ${h.day} з ${h.days}" else h.period)
        when {
            h.plan <= 0 -> {
                v.setTextViewText(R.id.hero_l, "Витрати за період")
                v.setTextViewText(R.id.hero_v, uah(h.expense))
                v.setColorStateList(R.id.hero_v, "setTextColor", R.color.text)
                v.setTextViewText(R.id.hero_sub, "бюджетів не задано")
            }
            h.left >= 0 -> {
                v.setTextViewText(R.id.hero_l, "Можна ще на день")
                v.setTextViewText(R.id.hero_v, uah(h.perDay))
                v.setColorStateList(R.id.hero_v, "setTextColor", R.color.brand_text)
                v.setTextViewText(R.id.hero_sub, "лишилось ${uah(h.left)} з ${uah(h.plan)}")
            }
            else -> {
                v.setTextViewText(R.id.hero_l, "Понад бюджет")
                v.setTextViewText(R.id.hero_v, uah(-h.left))
                v.setColorStateList(R.id.hero_v, "setTextColor", R.color.over_text)
                v.setTextViewText(R.id.hero_sub, "витрачено ${uah(h.expense)} з ${uah(h.plan)} · ще ${days(h.daysLeft)}")
            }
        }
        bars(v, h)
        v.setTextViewText(R.id.today_v, uah(h.today))
        v.setTextViewText(R.id.bal_v, uah(h.balance))
        v.setTextViewText(R.id.plan_l, if (h.plannedN > 0) "В планах · ${h.plannedN}" else c.getString(R.string.planned))
        v.setTextViewText(R.id.plan_v, uah(h.planned))
        return v
    }

    /** Зелене — скільки бюджету витрачено, сіре під ним — скільки минуло періоду. Без бюджету смужки немає. */
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

    private fun pace(h: Head): String = when {
        h.plan <= 0 -> "сьогодні ${uah(h.today)}"
        h.left < 0 -> "бюджет вичерпано · ще ${days(h.daysLeft)}"
        else -> "${uah(h.perDay)}/день · ще ${days(h.daysLeft)}"
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

    private val MONEY = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = ' ' })

    /** «8 731 грн», від'ємне — зі знаком мінус, без копійок, як шапка застосунку. */
    fun uah(v: Double): String {
        val n = Math.round(v)
        return (if (n < 0) "−" else "") + MONEY.format(kotlin.math.abs(n)) + " грн"
    }

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
