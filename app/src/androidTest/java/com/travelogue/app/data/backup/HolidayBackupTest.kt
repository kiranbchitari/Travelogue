package com.travelogue.app.data.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.travelogue.app.TravelogueApp
import com.travelogue.app.domain.HolidayPerson
import com.travelogue.app.domain.HolidayPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A full backup must bring the whole holiday planner back: everyone in it, their weekly days off,
 * the days marked for each of them and the public holiday switch.
 *
 * Kept apart from [BackupRoundTripTest], which loads and removes the sample trips: this one only
 * touches a year nobody plans for yet and puts the planner's settings back, so it is safe to run on
 * a device that holds real trips.
 */
@RunWith(AndroidJUnit4::class)
class HolidayBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val c = (context.applicationContext as TravelogueApp).container
    private val db = c.database

    @Test fun holidayPlannerSurvivesFullBackup() = runBlocking {
        val day = LocalDate.of(2031, 3, 14)
        val mum = HolidayPerson("test-mum", "Mum", setOf(DayOfWeek.SUNDAY))
        val everyone = setOf(HolidayPerson.ME, HolidayPerson.PARTNER, mum.id)
        val prefsBefore = c.settings.holidayPrefs.first()
        val zipFile = File(context.cacheDir, "holidays.zip").apply { delete() }
        try {
            c.holidays.setDays(day, day.plusDays(2), everyone, emptySet(), "Spring break")
            c.holidays.setDays(day.plusDays(10), day.plusDays(10), setOf(HolidayPerson.PARTNER), emptySet(), "")
            c.holidays.setDays(day.plusDays(20), day.plusDays(20), setOf(mum.id), emptySet(), "Mum's day")
            val savedPrefs = HolidayPrefs("Kiran", "Asha", setOf(DayOfWeek.SUNDAY), emptySet(), showIndianHolidays = false, extras = listOf(mum))
            c.settings.setHolidayPrefs(savedPrefs)
            val saved = db.holidayDao().getAll().filter { it.date.year == 2031 }
            assertEquals(11, saved.size)

            assertTrue(c.backup.exportAll(Uri.fromFile(zipFile)).holidays >= 11)

            // Lose everything, keeping one day whose name the backup should fill in.
            c.holidays.setDays(day, day.plusDays(20), emptySet(), everyone, "")
            c.holidays.setDays(day, day, setOf(HolidayPerson.ME), emptySet(), "")
            // Someone added on this device after the backup was made must still be there afterwards.
            val dad = HolidayPerson("test-dad", "Dad")
            c.settings.setHolidayPrefs(HolidayPrefs(extras = listOf(dad)))

            val session = c.backup.inspect(Uri.fromFile(zipFile))
            assertTrue(session.preview.holidays >= 11)
            val result = c.backup.import(session, DuplicateStrategy.SKIP)
            assertEquals(10, result.holidays)

            val restored = db.holidayDao().getAll().filter { it.date.year == 2031 }
            assertEquals(saved.map { Triple(it.owner, it.date, it.label) }, restored.map { Triple(it.owner, it.date, it.label) })
            // Names, weekly days off (including none at all), the holiday switch and the people.
            assertEquals(savedPrefs.copy(extras = listOf(mum, dad)), c.settings.holidayPrefs.first())
        } finally {
            c.holidays.setDays(day, day.plusDays(20), emptySet(), everyone, "")
            c.settings.setHolidayPrefs(prefsBefore)
            zipFile.delete()
        }
    }
}
