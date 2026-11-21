package com.travelogue.app.di

import android.content.Context
import com.travelogue.app.core.storage.MediaStorage
import com.travelogue.app.data.backup.BackupManager
import com.travelogue.app.data.backup.DemoDataSeeder
import com.travelogue.app.data.db.AppDatabase
import com.travelogue.app.data.repository.*
import com.travelogue.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-wired dependency graph (no DI framework). One instance lives in [com.travelogue.app.TravelogueApp].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Scope for work that must outlive a screen (imports, exports). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val media: MediaStorage by lazy { MediaStorage(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val trips: TripRepository by lazy { TripRepository(database, media) }
    val expenses: ExpenseRepository by lazy { ExpenseRepository(database, media) }
    val photos: PhotoRepository by lazy { PhotoRepository(database, media) }
    val journal: JournalRepository by lazy { JournalRepository(database, media, photos, trips) }
    val diary: DiaryRepository by lazy { DiaryRepository(database, photos) }

    val backup: BackupManager by lazy { BackupManager(appContext, database, media, expenses, settings) }
    val demo: DemoDataSeeder by lazy { DemoDataSeeder(appContext, database, media, trips, expenses, journal, diary, photos) }
}
