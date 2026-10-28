package ua.vitaliylobko.marnotrat.widget

import org.json.JSONObject

/** Шапка, яку Марнотрат порахував і поклав на сервер (worker.js, /api/widget). Суми — у гривнях. */
data class Head(
    val sent: Long,
    val period: String,
    val day: Int,
    val days: Int,
    val balance: Double,
    val expense: Double,
    val today: Double,
    val plan: Double,
    val left: Double,
    val perDay: Double,
    val planned: Double,
    val plannedN: Int,
) {
    val daysLeft: Int get() = (days - day + 1).coerceAtLeast(0)

    companion object {
        fun parse(o: JSONObject) = Head(
            sent = o.optLong("sent"),
            period = o.optString("period"),
            day = o.optInt("day"),
            days = o.optInt("days"),
            balance = o.optDouble("balance", 0.0),
            expense = o.optDouble("expense", 0.0),
            today = o.optDouble("today", 0.0),
            plan = o.optDouble("plan", 0.0),
            left = o.optDouble("left", 0.0),
            perDay = o.optDouble("perDay", 0.0),
            planned = o.optDouble("planned", 0.0),
            plannedN = o.optInt("plannedN"),
        )
    }
}
