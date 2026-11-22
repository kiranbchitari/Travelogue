package com.travelogue.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.travelogue.app.TravelogueApp
import com.travelogue.app.data.db.entity.HolidayOwner
import com.travelogue.app.data.db.entity.HolidayOwner.ME
import com.travelogue.app.data.db.entity.HolidayOwner.PARTNER
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Saving the day sheet over a stretch of days must not disturb what is already marked inside it. */
@RunWith(AndroidJUnit4::class)
class HolidayRepositoryTest {
    private val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TravelogueApp).container
    private val everyone = HolidayOwner.entries.toSet()
    // A year nobody plans for yet, so real data on the device is never touched.
    private val first = LocalDate.of(2032, 12, 21)
    private val last = LocalDate.of(2032, 12, 31)
    private val christmas = LocalDate.of(2032, 12, 25)

    @After fun cleanup() = runBlocking { c.holidays.setDays(first, last, emptySet(), everyone, "") }

    private suspend fun marks(owner: HolidayOwner) =
        c.database.holidayDao().getAll().filter { it.owner == owner && it.date.year == 2032 }.associate { it.date to it.label }

    @Test fun markingOnePersonsLeaveLeavesTheOtherAlone() = runBlocking {
        c.holidays.setDays(christmas, christmas, everyone, emptySet(), "Christmas")

        c.holidays.setDays(first, last, mark = setOf(ME), clear = emptySet(), label = "Annual leave")

        assertEquals(mapOf(christmas to "Christmas"), marks(PARTNER))
        val mine = marks(ME)
        assertEquals(11, mine.size)
        assertEquals("Christmas", mine[christmas])
        assertEquals("Annual leave", mine[first])
        assertEquals("Annual leave", mine[last])
    }

    @Test fun unnamedDaysInAStretchTakeItsNameAndTheFirstDayIsRenamed() = runBlocking {
        c.holidays.setDays(first, first.plusDays(1), setOf(ME), emptySet(), "")
        c.holidays.setDays(first, first.plusDays(2), setOf(ME), emptySet(), "Trip")
        assertEquals(listOf("Trip", "Trip", "Trip"), marks(ME).toSortedMap().values.toList())

        c.holidays.setDays(first, first, setOf(ME), emptySet(), "Renamed")
        assertEquals("Renamed", marks(ME)[first])
    }

    @Test fun switchingAPersonOffClearsOnlyThem() = runBlocking {
        c.holidays.setDays(first, first.plusDays(2), everyone, emptySet(), "Break")

        c.holidays.setDays(first, first.plusDays(2), mark = setOf(ME), clear = setOf(PARTNER), label = "Break")

        assertEquals(3, marks(ME).size)
        assertEquals(0, marks(PARTNER).size)
    }
}
