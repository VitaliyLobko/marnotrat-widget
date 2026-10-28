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
        for (id in ids(c, LargeWidget::class.java)) m.updateAppWidget(id, large(c, m, id))
    }

    private fun ids(c: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, cls))

    /** 4×1: на день, сьогодні, баланс, в планах; смужка місяця; «−» витрата й «+» дохід. */
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
        when {
            h.plan <= 0 -> {
                v.setTextViewText(R.id.c1_l, c.getString(R.string.expense))
                v.setTextViewText(R.id.c1_v, num(h.expense))
                v.setColorStateList(R.id.c1_v, "setTextColor", R.color.text)
            }
            h.left >= 0 -> {
                v.setTextViewText(R.id.c1_l, "На день")
                v.setTextViewText(R.id.c1_v, num(h.perDay))
                v.setColorStateList(R.id.c1_v, "setTextColor", R.color.brand_text)
            }
            else -> {
                v.setTextViewText(R.id.c1_l, "Понад бюджет")
                v.setTextViewText(R.id.c1_v, num(-h.left))
                v.setColorStateList(R.id.c1_v, "setTextColor", R.color.over_text)
            }
        }
        v.setTextViewText(R.id.today_v, num(h.today))
        v.setTextViewText(R.id.bal_v, num(h.balance))
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

    /** 4×2: скільки можна на день, стовпчики тижня з нормою, сьогодні · баланс · в планах. */
    fun large(c: Context, m: AppWidgetManager, id: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_large)
        val h = Store.head(c)
        v.setOnClickPendingIntent(R.id.root, if (Store.config(c) == null) openSetup(c) else openApp(c, ""))
        v.setOnClickPendingIntent(R.id.add_expense, openApp(c, "#add/expense"))
        v.setOnClickPendingIntent(R.id.add_income, openApp(c, "#add/income"))
        v.setOnClickPendingIntent(R.id.refresh, refresh(c, LargeWidget::class.java))
        stamp(c, v, h)
        if (h == null) {
            v.setTextViewText(R.id.hero_l, c.getString(R.string.app_name))
            v.setTextViewText(R.id.stats, Store.error(c) ?: if (Store.config(c) == null) "Підключи в Марнотраті → Налаштування" else "")
            return v
        }
        when {
            h.plan <= 0 -> {
                v.setTextViewText(R.id.hero_l, "Витрати за період")
                v.setTextViewText(R.id.hero_v, uah(h.expense))
                v.setColorStateList(R.id.hero_v, "setTextColor", R.color.text)
            }
            h.left >= 0 -> {
                v.setTextViewText(R.id.hero_l, "Можна ще на день · ще ${days(h.daysLeft)}")
                v.setTextViewText(R.id.hero_v, uah(h.perDay))
                v.setColorStateList(R.id.hero_v, "setTextColor", R.color.brand_text)
            }
            else -> {
                v.setTextViewText(R.id.hero_l, "Понад бюджет · ще ${days(h.daysLeft)}")
                v.setTextViewText(R.id.hero_v, uah(-h.left))
                v.setColorStateList(R.id.hero_v, "setTextColor", R.color.over_text)
            }
        }
        v.setTextViewText(R.id.stats, "Сьогодні ${num(h.today)}   Баланс ${num(h.balance)}   В планах ${num(h.planned)}")
        // Розмір віджета в dp з налаштувань лаунчера (у портреті — мінімальна ширина й максимальна висота);
        // під діаграму — висота мінус рядок зверху (~58 dp), рядок знизу (~24 dp) і відступи
        val o = m.getAppWidgetOptions(id)
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 320
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 180
        v.setImageViewBitmap(R.id.chart, WeekChart.draw(c, h, wDp - 28, (hDp - 122).coerceIn(40, 240)))
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
            h != null && h.sent > 0 && System.currentTimeMillis() - h.sent > STALE_MS ->
                "дані з " + SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(Date(h.sent))
            else -> null
        }
        v.setViewVisibility(R.id.stamp, if (text == null) View.GONE else View.VISIBLE)
        if (text != null) v.setTextViewText(R.id.stamp, text)
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
