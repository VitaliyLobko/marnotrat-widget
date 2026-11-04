package ua.vitaliylobko.marnotrat.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** 4×1: баланс, на день, сьогодні, платежі, смужка місяця; праворуч «−» і «+» або монета вибраної витрати. */
class CompactWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) Widgets.updateCompact(context, manager, id)
        Widgets.schedule(context)
    }

    override fun onEnabled(context: Context) {
        Widgets.schedule(context)
        Widgets.refreshNow(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        Store.dropQuick(context, ids)
    }

    override fun onDisabled(context: Context) {
        Widgets.cancelIfNone(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Widgets.ACTION_REFRESH) Widgets.refreshNow(context)
        Widgets.pinned(context, intent)
    }
}
