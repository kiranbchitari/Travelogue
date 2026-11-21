package com.travelogue.app.core.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** Currencies offered in pickers. Users can still type any ISO code. */
val CommonCurrencies = listOf(
    "INR", "USD", "EUR", "GBP", "AED", "THB", "SGD", "JPY", "AUD", "CAD", "CHF", "LKR", "NPR", "MYR", "IDR", "VND",
    "CNY", "HKD", "KRW", "NZD", "ZAR", "SAR", "QAR", "TRY", "MVR", "BTN", "PHP", "EGP",
)

object Fmt {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    private val weekdayLong = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    private val weekdayShort = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun currencySymbol(code: String): String = runCatching { Currency.getInstance(code).getSymbol(Locale.getDefault()) }.getOrDefault(code)

    /** "₹12,450" — no decimals for whole amounts, two otherwise. */
    fun money(amount: Double, currency: String, compact: Boolean = false): String {
        if (compact && abs(amount) >= 100_000) return currencySymbol(currency) + compactNumber(amount)
        val nf = NumberFormat.getNumberInstance(Locale.getDefault())
        val whole = abs(amount - Math.round(amount)) < 0.005
        nf.minimumFractionDigits = if (whole) 0 else 2
        nf.maximumFractionDigits = if (whole) 0 else 2
        val sign = if (amount < 0) "−" else ""
        return sign + currencySymbol(currency) + nf.format(abs(amount))
    }

    fun compactNumber(v: Double): String {
        val a = abs(v)
        val s = when {
            a >= 10_000_000 -> String.format(Locale.getDefault(), "%.1fCr", a / 10_000_000)
            a >= 100_000 -> String.format(Locale.getDefault(), "%.1fL", a / 100_000)
            a >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", a / 1_000)
            else -> String.format(Locale.getDefault(), "%.0f", a)
        }.replace(".0", "")
        return (if (v < 0) "−" else "") + s
    }

    fun number(v: Double, decimals: Int = 2): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault())
        nf.maximumFractionDigits = decimals
        nf.isGroupingUsed = false
        return nf.format(v)
    }

    fun date(d: LocalDate): String = d.format(dayMonthYear)
    fun dayMonth(d: LocalDate): String = d.format(dayMonth)
    fun monthYear(d: LocalDate): String = d.format(monthYear)
    fun monthShort(d: LocalDate): String = d.format(monthShort)
    fun weekday(d: LocalDate): String = d.format(weekdayLong)
    fun weekdayShort(d: LocalDate): String = d.format(weekdayShort)
    fun time(t: LocalTime): String = t.format(time)
    fun dateTime(dt: LocalDateTime): String = "${date(dt.toLocalDate())} · ${time(dt.toLocalTime())}"

    /** "12 – 18 Mar 2026", "28 Feb – 3 Mar 2026", "30 Dec 2025 – 2 Jan 2026" */
    fun dateRange(start: LocalDate, end: LocalDate): String = when {
        start == end -> date(start)
        start.year != end.year -> "${date(start)} – ${date(end)}"
        start.month != end.month -> "${start.format(dayMonth)} – ${end.format(dayMonthYear)}"
        else -> "${start.dayOfMonth} – ${end.format(dayMonthYear)}"
    }

    fun days(n: Int): String = if (n == 1) "1 day" else "$n days"
    fun nights(n: Int): String = if (n == 1) "1 night" else "$n nights"

    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.getDefault(), "%.2f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(Locale.getDefault(), "%.1f MB", b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> String.format(Locale.getDefault(), "%.0f KB", b / (1L shl 10).toDouble())
        else -> "$b B"
    }

    /** Relative phrasing for upcoming trips: "Tomorrow", "In 12 days". */
    fun countdown(to: LocalDate, today: LocalDate = LocalDate.now()): String {
        val d = to.toEpochDay() - today.toEpochDay()
        return when {
            d < 0 -> "Started"
            d == 0L -> "Today"
            d == 1L -> "Tomorrow"
            d < 60 -> "In $d days"
            else -> "In ${d / 30} months"
        }
    }
}

/** Parses user-typed amounts leniently ("1,250.50", "1250"). */
fun String.toAmountOrNull(): Double? = trim().replace(",", "").replace(" ", "").takeIf { it.isNotEmpty() }?.toDoubleOrNull()
