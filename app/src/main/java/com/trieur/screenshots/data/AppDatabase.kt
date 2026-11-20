package com.trieur.screenshots.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Screenshot::class, ScreenshotFts::class], version = 4)
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

        /** v3 : catégorie choisie à la main, et empreinte visuelle (calculée ensuite par le scanner). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN categoryLocked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN dhash INTEGER")
            }
        }

        /** v4 : source (captures, téléchargements, galerie) et dossier. Tout ce qui existe déjà est une capture. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN source TEXT NOT NULL DEFAULT 'SCREENSHOT'")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN folder TEXT")
            }
        }
    }
}
