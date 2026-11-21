package com.travelogue.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.travelogue.app.data.db.dao.*
import com.travelogue.app.data.db.entity.*

@Database(
    entities = [
        TripEntity::class, TravellerEntity::class, TripTravellerEntity::class, CurrencyRateEntity::class, DocumentEntity::class,
        ExpenseCategoryEntity::class, ExpenseEntity::class, ExpenseSplitEntity::class, SettlementEntity::class,
        HotelEntity::class, ItineraryDayEntity::class, ItineraryActivityEntity::class, PlaceEntity::class,
        BookingEntity::class, TravelContactEntity::class,
        DiaryEntryEntity::class, TagEntity::class, DiaryEntryTagEntity::class, TripPhotoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun travellerDao(): TravellerDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun hotelDao(): HotelDao
    abstract fun itineraryDao(): ItineraryDao
    abstract fun placeDao(): PlaceDao
    abstract fun bookingDao(): BookingDao
    abstract fun contactDao(): ContactDao
    abstract fun diaryDao(): DiaryDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val NAME = "travelogue.db"

        /**
         * Explicit migrations only. Never use fallbackToDestructiveMigration: losing years of travel
         * memories on an app update is not acceptable. Add each new Migration(n, n+1) here and bump
         * the database version; the exported schemas in app/schemas let them be tested.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
