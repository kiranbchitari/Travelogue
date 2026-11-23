package com.travelogue.app.data.settings

import com.travelogue.app.data.backup.BackupJson
import com.travelogue.app.data.backup.HolidayPersonArchive
import com.travelogue.app.data.backup.SettingsArchive
import com.travelogue.app.data.backup.TripArchive
import com.travelogue.app.data.backup.allHolidays
import com.travelogue.app.data.backup.withHolidays
import com.travelogue.app.data.db.entity.HolidayEntity
import com.travelogue.app.data.db.entity.TripEntity
import kotlinx.serialization.Serializable
import com.travelogue.app.domain.HolidayPerson
import com.travelogue.app.domain.HolidayPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** How people added to the holiday planner are kept in settings and in backups. */
class HolidayPeopleTest {
    @Test fun addedPeopleSurviveBeingSaved() {
        val people = listOf(
            HolidayPerson("a1b2c3d4", "Mum", setOf(DayOfWeek.SUNDAY)),
            HolidayPerson("e5f6a7b8", "Rahul", emptySet()),
        )
        assertEquals(people, SettingsRepository.decodePeople(SettingsRepository.encodePeople(people)))
    }

    @Test fun nothingSavedMeansNobodyAdded() {
        assertTrue(SettingsRepository.decodePeople(null).isEmpty())
        assertTrue(SettingsRepository.decodePeople("not json").isEmpty())
    }

    @Test fun aListFromOutsideIsTidied() {
        val people = SettingsRepository.toPeople(
            listOf(
                HolidayPersonArchive("x", "  Mum  "),
                HolidayPersonArchive("x", "Same id again"),
                HolidayPersonArchive(HolidayPerson.ME, "Pretending to be me"),
                HolidayPersonArchive("", "No id"),
                HolidayPersonArchive("y", "", listOf(7, 99)),
            ) + (1..10).map { HolidayPersonArchive("extra$it", "Extra $it") },
        )
        assertEquals(HolidayPrefs.MAX_PEOPLE - 2, people.size)
        assertEquals(HolidayPerson("x", "Mum"), people[0])
        // A missing name gets a plain one, and weekday numbers that are not weekdays are dropped.
        assertEquals(HolidayPerson("y", "Person 4", setOf(DayOfWeek.SUNDAY)), people[1])
    }

    @Test fun aBackupMadeBeforePeopleCouldBeAddedStillReads() {
        val old = """
            {
              "defaultCurrency": "INR",
              "holidays": [
                { "owner": "ME", "date": "2026-11-06", "label": "Diwali break", "createdAt": 1 },
                { "owner": "PARTNER", "date": "2026-11-06", "label": "", "createdAt": 2 }
              ],
              "holidayPrefs": { "myName": "Kiran", "partnerName": "Asha", "myWeeklyOff": [7], "partnerWeeklyOff": [6, 7], "showIndianHolidays": false }
            }
        """.trimIndent()
        val archive = BackupJson.decodeFromString(SettingsArchive.serializer(), old)
        assertEquals(listOf(HolidayPerson.ME, HolidayPerson.PARTNER), archive.holidays.map { it.owner })
        assertEquals(LocalDate.of(2026, 11, 6), archive.holidays[0].date)
        assertEquals("Kiran", archive.holidayPrefs?.myName)
        assertTrue(archive.holidayPrefs!!.extras.isEmpty())
    }

    @Test fun aNewBackupCarriesAddedPeopleAndTheirDays() {
        val mum = HolidayPerson("a1b2c3d4", "Mum", setOf(DayOfWeek.SUNDAY))
        val christmas = HolidayEntity(mum.id, LocalDate.of(2026, 12, 25), "Christmas", 1)
        val archive = SettingsArchive(
            holidayPrefs = com.travelogue.app.data.backup.HolidayPrefsArchive("Kiran", "Asha", extras = listOf(SettingsRepository.archive(mum))),
        ).withHolidays(listOf(christmas))
        val back = BackupJson.decodeFromString(SettingsArchive.serializer(), BackupJson.encodeToString(SettingsArchive.serializer(), archive))
        assertEquals(archive, back)
        assertEquals(listOf(christmas), back.allHolidays)
        assertEquals(listOf(mum), SettingsRepository.toPeople(back.holidayPrefs!!.extras))
    }

    @Test fun version100CanStillReadANewBackup() {
        val day = LocalDate.of(2026, 11, 6)
        val archive = SettingsArchive().withHolidays(
            listOf(
                HolidayEntity(HolidayPerson.ME, day, "Diwali", 1),
                HolidayEntity(HolidayPerson.PARTNER, day, "", 2),
                HolidayEntity("a1b2c3d4", day, "Mum's day", 3),
            ),
        )
        // Only the first two people's days are where 1.0.0 looks; the added person's are kept apart.
        assertEquals(listOf(HolidayPerson.ME, HolidayPerson.PARTNER), archive.holidays.map { it.owner })
        assertEquals(listOf("a1b2c3d4"), archive.peopleHolidays.map { it.owner })
        val old = BackupJson.decodeFromString(V100Settings.serializer(), BackupJson.encodeToString(SettingsArchive.serializer(), archive))
        assertEquals(listOf(V100Owner.ME, V100Owner.PARTNER), old.holidays.map { it.owner })
    }

    /** settings.json as version 1.0.0 reads it: owner is a two-value enum with no fallback. */
    @Serializable private enum class V100Owner { ME, PARTNER }
    @Serializable private data class V100Holiday(val owner: V100Owner, val date: String, val label: String = "")
    @Serializable private data class V100Settings(val holidays: List<V100Holiday> = emptyList())

    // ---- Export then import ----

    /** Through the same JSON a backup file holds, then restored onto a device that has [current]. */
    private fun backUpAndRestore(saved: HolidayPrefs, current: HolidayPrefs = HolidayPrefs()): HolidayPrefs {
        val json = BackupJson.encodeToString(SettingsArchive.serializer(), SettingsArchive(holidayPrefs = SettingsRepository.archive(saved)))
        return SettingsRepository.restore(BackupJson.decodeFromString(SettingsArchive.serializer(), json).holidayPrefs!!, current)
    }

    @Test fun everyPlannerSettingComesBackFromABackup() {
        val saved = HolidayPrefs(
            myName = "Kiran",
            partnerName = "Asha",
            myWeeklyOff = setOf(DayOfWeek.SUNDAY),
            // No weekly day off at all must not turn back into Saturday and Sunday.
            partnerWeeklyOff = emptySet(),
            showIndianHolidays = false,
            extras = listOf(
                HolidayPerson("a1b2c3d4", "Mum", setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)),
                HolidayPerson("e5f6a7b8", "Rahul", emptySet()),
                HolidayPerson("c9d0e1f2", "Priya"),
                HolidayPerson("0a1b2c3d", "Dad", setOf(DayOfWeek.MONDAY)),
            ),
        )
        // On a fresh install, and over a device that had its own names and switches.
        assertEquals(saved, backUpAndRestore(saved))
        assertEquals(saved, backUpAndRestore(saved, HolidayPrefs("Someone", "Else", emptySet(), HolidayPrefs.Weekend, showIndianHolidays = true)))
    }

    @Test fun restoringKeepsPeopleAddedSinceTheBackup() {
        val mum = HolidayPerson("a1b2c3d4", "Mum", setOf(DayOfWeek.SUNDAY))
        val dad = HolidayPerson("0a1b2c3d", "Dad")
        val saved = HolidayPrefs("Kiran", "Asha", extras = listOf(mum))
        // Dad was added after the backup, and Mum was renamed on this device: the backup's Mum wins.
        val current = HolidayPrefs("Kiran", "Asha", extras = listOf(dad, mum.copy(name = "Mother", weeklyOff = emptySet())))
        assertEquals(listOf(mum, dad), backUpAndRestore(saved, current).extras)
    }

    @Test fun everyoneInTheBackupComesBackEvenWhenThePlannerIsFull() {
        val saved = HolidayPrefs(extras = (1..4).map { HolidayPerson("backup$it", "Backup $it") })
        val current = HolidayPrefs(extras = (1..4).map { HolidayPerson("here$it", "Here $it") })
        assertEquals(saved.extras, backUpAndRestore(saved, current).extras)
    }

    @Test fun aTripsCoverFramingIsPartOfItsBackup() {
        val trip = TripEntity(
            uuid = "u-1", name = "Kyoto", destination = "Kyoto",
            startDate = LocalDate.of(2026, 12, 17), endDate = LocalDate.of(2026, 12, 24),
            coverPath = "images/cover/a.jpg", coverFocusX = 0.2f, coverFocusY = 0.85f, coverZoom = 2.5f,
        )
        val back = BackupJson.decodeFromString(TripArchive.serializer(), BackupJson.encodeToString(TripArchive.serializer(), TripArchive(trip = trip)))
        assertEquals(trip, back.trip)
    }

    @Test fun theChosenLookIsPartOfABackup() {
        val archive = SettingsArchive(themeMode = "DARK", appTheme = "FIESTA")
        val back = BackupJson.decodeFromString(SettingsArchive.serializer(), BackupJson.encodeToString(SettingsArchive.serializer(), archive))
        assertEquals("DARK", back.themeMode)
        assertEquals("FIESTA", back.appTheme)
    }
}
