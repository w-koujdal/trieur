package com.trieur.screenshots.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Screenshot::class, ScreenshotFts::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        /** v2 : appli d'origine et catégorie. Les valeurs sont remplies ensuite par le reclassement. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN sourceApp TEXT")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
            }
        }
    }
}
