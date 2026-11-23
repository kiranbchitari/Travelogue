package com.travelogue.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.travelogue.app.data.db.dao.*
import com.travelogue.app.data.db.entity.*

@Database(
    entities = [
        TripEntity::class, TravellerEntity::class, TripTravellerEntity::class, CurrencyRateEntity::class, DocumentEntity::class,
        ExpenseCategoryEntity::class, ExpenseEntity::class, ExpenseSplitEntity::class, SettlementEntity::class,
        HotelEntity::class, ItineraryDayEntity::class, ItineraryActivityEntity::class, PlaceEntity::class,
        BookingEntity::class, TravelContactEntity::class,
        DiaryEntryEntity::class, TagEntity::class, DiaryEntryTagEntity::class, TripPhotoEntity::class,
        HolidayEntity::class,
    ],
    version = 3,
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
    abstract fun holidayDao(): HolidayDao
    abstract fun backupStatsDao(): BackupStatsDao

    companion object {
        const val NAME = "travelogue.db"

        /** Adds the holiday planner's days off. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `holidays` (`owner` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`owner`, `date`))",
                )
            }
        }

        /** Adds the cover framing: which part of a trip's cover shows, and how far zoomed in. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `coverFocusX` REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `coverFocusY` REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `coverZoom` REAL NOT NULL DEFAULT 1.0")
            }
        }

        /**
         * Explicit migrations only. Never use fallbackToDestructiveMigration: losing years of travel
         * memories on an app update is not acceptable. Add each new Migration(n, n+1) here and bump
         * the database version; the exported schemas in app/schemas let them be tested.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
