package com.travelogue.app

import android.app.Application
import com.travelogue.app.di.AppContainer
import kotlinx.coroutines.launch

class TravelogueApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.expenses.ensureDefaultCategories()
            // Leftover camera captures are copied into trip storage when saved; clear the temp folder.
            cacheDir.resolve("camera").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        }
    }
}
