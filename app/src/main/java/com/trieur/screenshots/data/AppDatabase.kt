package com.trieur.screenshots.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Screenshot::class, ScreenshotFts::class], version = 6)
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

        /**
         * v5 : étiquettes d'image et mots cherchables. L'index plein texte gagne une colonne `tags` :
         * une table FTS ne s'altère pas, donc on la recrée (même SQL que Room, voir schemas/5.json)
         * avec ses triggers, puis on la reconstruit depuis la table des images.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN labels TEXT")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN tags TEXT")
                for (t in listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")) {
                    db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_screenshots_fts_$t")
                }
                db.execSQL("DROP TABLE IF EXISTS screenshots_fts")
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `screenshots_fts` USING FTS4(" +
                        "`text` TEXT NOT NULL, `tags` TEXT, tokenize=unicode61, content=`screenshots`)"
                )
                FTS_TRIGGERS.forEach(db::execSQL)
                db.execSQL("INSERT INTO screenshots_fts(screenshots_fts) VALUES('rebuild')")
            }
        }

        /** v6 : date de modification, pour relire une image modifiée sur place (recadrée, annotée). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN modified INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val FTS_TRIGGERS = listOf(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_screenshots_fts_BEFORE_UPDATE BEFORE UPDATE ON `screenshots` BEGIN DELETE FROM `screenshots_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_screenshots_fts_BEFORE_DELETE BEFORE DELETE ON `screenshots` BEGIN DELETE FROM `screenshots_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_screenshots_fts_AFTER_UPDATE AFTER UPDATE ON `screenshots` BEGIN INSERT INTO `screenshots_fts`(`docid`, `text`, `tags`) VALUES (NEW.`rowid`, NEW.`text`, NEW.`tags`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_screenshots_fts_AFTER_INSERT AFTER INSERT ON `screenshots` BEGIN INSERT INTO `screenshots_fts`(`docid`, `text`, `tags`) VALUES (NEW.`rowid`, NEW.`text`, NEW.`tags`); END",
        )
    }
}
