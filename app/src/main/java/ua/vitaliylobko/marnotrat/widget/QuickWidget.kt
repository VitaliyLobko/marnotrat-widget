package ua.vitaliylobko.marnotrat.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** 1×1 «Швидка витрата»: монета однієї витрати, тап — екран суми одразу в неї. */
class QuickWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, Widgets.quick(context, id))
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
        Widgets.pinned(context, intent)
    }
}
