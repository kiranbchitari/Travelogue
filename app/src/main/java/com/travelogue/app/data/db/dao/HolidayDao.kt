package com.travelogue.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelogue.app.data.db.entity.HolidayEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays ORDER BY date") fun observeAll(): Flow<List<HolidayEntity>>
    @Query("SELECT * FROM holidays ORDER BY date, owner") suspend fun getAll(): List<HolidayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(rows: List<HolidayEntity>)

    /** Returns -1 when that person already has the day marked. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfAbsent(row: HolidayEntity): Long

    @Query("UPDATE holidays SET label = :label WHERE owner = :owner AND date = :date AND label = ''")
    suspend fun fillBlankLabel(owner: String, date: LocalDate, label: String)

    @Query("DELETE FROM holidays WHERE owner = :owner AND date BETWEEN :start AND :end")
    suspend fun deleteRange(owner: String, start: LocalDate, end: LocalDate)

    @Query("DELETE FROM holidays WHERE owner = :owner") suspend fun deleteOwner(owner: String)
}
