package com.trieur.screenshots

import android.app.Application
import androidx.room.Room
import com.trieur.screenshots.data.AppDatabase
import com.trieur.screenshots.scan.ScreenshotScanner

/** Point d'accès unique aux dépendances (pas de framework d'injection pour le MVP). */
class TrieurApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "trieur.db").build()
    }
    val scanner: ScreenshotScanner by lazy {
        ScreenshotScanner(this, database.screenshotDao())
    }
}
