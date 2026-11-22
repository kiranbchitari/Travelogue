package com.travelogue.app.data.repository

import androidx.room.withTransaction
import com.travelogue.app.data.db.AppDatabase
import com.travelogue.app.data.db.entity.HolidayEntity
import com.travelogue.app.data.db.entity.HolidayOwner
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class HolidayRepository(private val db: AppDatabase) {
    private val dao = db.holidayDao()

    fun observeAll(): Flow<List<HolidayEntity>> = dao.observeAll()

    /**
     * Applies the day sheet to every day from [start] to [end]. People in [mark] get the days off and
     * people in [clear] lose any mark on them; anyone in neither set is left exactly as they were, so
     * marking one person's leave never touches the other's calendar.
     *
     * Only [start], the day the user was looking at, is renamed to [label]. Later days that were
     * already marked keep their own name (and gain [label] only if they had none), so a named holiday
     * inside a stretch of leave is not renamed.
     */
    suspend fun setDays(start: LocalDate, end: LocalDate, mark: Set<HolidayOwner>, clear: Set<HolidayOwner>, label: String) {
        require(!end.isBefore(start) && end.toEpochDay() - start.toEpochDay() < MAX_RANGE_DAYS)
        val name = label.trim().take(HolidayEntity.MAX_LABEL)
        val laterDays = (start.toEpochDay() + 1..end.toEpochDay()).map(LocalDate::ofEpochDay)
        db.withTransaction {
            (clear - mark).forEach { owner -> dao.deleteRange(owner, start, end) }
            mark.forEach { owner ->
                dao.upsert(listOf(HolidayEntity(owner, start, name)))
                laterDays.forEach { day ->
                    val added = dao.insertIfAbsent(HolidayEntity(owner, day, name)) != -1L
                    if (!added && name.isNotEmpty()) dao.fillBlankLabel(owner, day, name)
                }
            }
        }
    }

    /** Marks or clears a single day for one person, leaving the other person's calendar alone. */
    suspend fun setDay(owner: HolidayOwner, date: LocalDate, off: Boolean, label: String) {
        if (off) dao.upsert(listOf(HolidayEntity(owner, date, label.trim().take(HolidayEntity.MAX_LABEL))))
        else dao.deleteRange(owner, date, date)
    }

    /** Marks each day (date to name) off for both people, keeping any mark that is already there. */
    suspend fun markForBoth(days: Map<LocalDate, String>) = db.withTransaction {
        days.forEach { (date, label) ->
            HolidayOwner.entries.forEach { owner -> dao.insertIfAbsent(HolidayEntity(owner, date, label.trim().take(HolidayEntity.MAX_LABEL))) }
        }
    }

    companion object {
        /** Longest stretch that can be marked in one go. */
        const val MAX_RANGE_DAYS = 366
    }
}
