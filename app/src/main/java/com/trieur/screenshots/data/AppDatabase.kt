package com.trieur.screenshots.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Screenshot::class, ScreenshotFts::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao
}
