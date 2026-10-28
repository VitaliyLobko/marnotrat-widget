package ua.vitaliylobko.marnotrat.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** 4×2: скільки можна на день, стовпчики тижня з нормою, сьогодні · баланс · в планах, «−» і «+». */
class LargeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, Widgets.large(context, manager, id))
        Widgets.schedule(context)
    }

    // Змінили розмір — діаграму перемалювати під нову висоту
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, Widgets.large(context, manager, id))
    }

    override fun onEnabled(context: Context) {
        Widgets.schedule(context)
        Widgets.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        Widgets.cancelIfNone(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Widgets.ACTION_REFRESH) Widgets.refreshNow(context)
    }
}
