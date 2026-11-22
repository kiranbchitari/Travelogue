package com.travelogue.app.domain

import java.time.LocalDate
import java.time.Month

/** A public holiday that ships with the app. Nobody is assumed to have it off until they mark it. */
data class PublicHoliday(val date: LocalDate, val name: String, val emoji: String)

/**
 * India's central government (gazetted) holidays.
 *
 * Republic Day, Independence Day, Gandhi Jayanti and Christmas fall on the same date every year.
 * Festival dates follow the lunar calendar and are only known once the government publishes them,
 * so they are listed per year from the Department of Personnel & Training's office memoranda
 * (F.No.12/2/2023-JCA: 3 July 2025 for 2026, 16 July 2026 for 2027). Years outside [festivalYears]
 * show the four fixed holidays only; add a year here when its list is published. Id-ul-Fitr,
 * Id-ul-Zuha, Muharram and Milad-un-Nabi depend on the sighting of the moon and can move by a day.
 */
object IndianHolidays {
    val festivalYears: IntRange = 2026..2027

    private class Entry(val month: Month, val day: Int, val name: String, val emoji: String)

    private val fixed = listOf(
        Entry(Month.JANUARY, 26, "Republic Day", "🇮🇳"),
        Entry(Month.AUGUST, 15, "Independence Day", "🇮🇳"),
        Entry(Month.OCTOBER, 2, "Gandhi Jayanti", "🕊️"),
        Entry(Month.DECEMBER, 25, "Christmas", "🎄"),
    )

    private const val HOLI = "🎨"
    private const val EID = "🌙"
    private const val RAM_NAVAMI = "🏹"
    private const val MAHAVIR = "🙏"
    private const val GOOD_FRIDAY = "✝️"
    private const val BUDDHA = "☸️"
    private const val JANMASHTAMI = "🦚"
    private const val DUSSEHRA = "🔥"
    private const val DIWALI = "🪔"
    private const val GURU_NANAK = "🙏"

    private val festivals: Map<Int, List<Entry>> = mapOf(
        2026 to listOf(
            Entry(Month.MARCH, 4, "Holi", HOLI),
            Entry(Month.MARCH, 21, "Id-ul-Fitr", EID),
            Entry(Month.MARCH, 26, "Ram Navami", RAM_NAVAMI),
            Entry(Month.MARCH, 31, "Mahavir Jayanti", MAHAVIR),
            Entry(Month.APRIL, 3, "Good Friday", GOOD_FRIDAY),
            Entry(Month.MAY, 1, "Buddha Purnima", BUDDHA),
            Entry(Month.MAY, 27, "Id-ul-Zuha (Bakrid)", EID),
            Entry(Month.JUNE, 26, "Muharram", EID),
            Entry(Month.AUGUST, 26, "Milad-un-Nabi", EID),
            Entry(Month.SEPTEMBER, 4, "Janmashtami", JANMASHTAMI),
            Entry(Month.OCTOBER, 20, "Dussehra", DUSSEHRA),
            Entry(Month.NOVEMBER, 8, "Diwali", DIWALI),
            Entry(Month.NOVEMBER, 24, "Guru Nanak Jayanti", GURU_NANAK),
        ),
        2027 to listOf(
            Entry(Month.MARCH, 10, "Id-ul-Fitr", EID),
            Entry(Month.MARCH, 23, "Holi", HOLI),
            Entry(Month.MARCH, 26, "Good Friday", GOOD_FRIDAY),
            Entry(Month.APRIL, 15, "Ram Navami", RAM_NAVAMI),
            Entry(Month.APRIL, 19, "Mahavir Jayanti", MAHAVIR),
            Entry(Month.MAY, 17, "Id-ul-Zuha (Bakrid)", EID),
            Entry(Month.MAY, 20, "Buddha Purnima", BUDDHA),
            Entry(Month.JUNE, 16, "Muharram", EID),
            Entry(Month.AUGUST, 15, "Milad-un-Nabi", EID),
            Entry(Month.AUGUST, 25, "Janmashtami", JANMASHTAMI),
            Entry(Month.OCTOBER, 9, "Dussehra", DUSSEHRA),
            Entry(Month.OCTOBER, 29, "Diwali", DIWALI),
            Entry(Month.NOVEMBER, 14, "Guru Nanak Jayanti", GURU_NANAK),
        ),
    )

    /** One entry per date, in date order. Two holidays on the same day are shown together. */
    fun forYear(year: Int): List<PublicHoliday> =
        (fixed + festivals[year].orEmpty())
            .groupBy { LocalDate.of(year, it.month, it.day) }
            .map { (date, entries) -> PublicHoliday(date, entries.joinToString(" & ") { it.name }, entries.first().emoji) }
            .sortedBy { it.date }

    /** Holidays from [from] to [to] inclusive, keyed by date. */
    fun between(from: LocalDate, to: LocalDate): Map<LocalDate, PublicHoliday> =
        (from.year..to.year).flatMap(::forYear).filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.associateBy { it.date }
}
