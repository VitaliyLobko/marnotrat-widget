package ua.vitaliylobko.marnotrat.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** 4×2: скільки можна на день, смужка бюджету, сьогодні, баланс, заплановане, кнопки «Витрата» й «Дохід». */
class LargeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, Widgets.large(context))
        Widgets.schedule(context)
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
