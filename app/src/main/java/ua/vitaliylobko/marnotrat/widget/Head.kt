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
    /** Сім днів, останній — сьогодні: дата (yyyy-MM-dd) і витрати за день. */
    val week: List<Pair<String, Double>>,
    /** Найближчий регулярний платіж до кінця періоду (прострочений теж) або null. */
    val next: Next?,
    /** Монети витрат за порядком на головній — для віджета 1×1. Старий сервер їх не шле — порожньо. */
    val cats: List<Cat>,
) {
    data class Next(val date: String, val name: String, val v: Double)

    /** Монета витрати: витрачено й бюджет — у валюті монети; st — колір (zero, free, ok, warn, over), fill — заливка 0–1. */
    data class Cat(
        val id: String,
        val name: String,
        val icon: String,
        val cur: String,
        val spent: Double,
        val budget: Double,
        val st: String,
        val fill: Double,
    )

    val daysLeft: Int get() = (days - day + 1).coerceAtLeast(0)

    /** Норма на сьогодні, як її рахувало б зранку: perDay уже зменшений на сьогоднішні витрати. */
    val todayNorm: Double get() = if (daysLeft > 0) maxOf(left + today, 0.0) / daysLeft else 0.0

    /** Сьогодні витрачено більше за норму дня; без бюджетів — ніколи. */
    val todayOver: Boolean get() = plan > 0 && today > todayNorm

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
            week = o.optJSONArray("week")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let { it.optString("date") to it.optDouble("v", 0.0) }
                }
            } ?: emptyList(),
            next = o.optJSONObject("next")?.let { Next(it.optString("date"), it.optString("name"), it.optDouble("v", 0.0)) },
            cats = o.optJSONArray("cats")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::cat) }
            } ?: emptyList(),
        )

        fun cat(o: JSONObject) = Cat(
            id = o.optString("id"),
            name = o.optString("name"),
            icon = o.optString("icon"),
            cur = o.optString("cur", "UAH"),
            spent = o.optDouble("spent", 0.0),
            budget = o.optDouble("budget", 0.0),
            st = o.optString("st", "zero"),
            fill = o.optDouble("fill", 0.0),
        )
    }
}
