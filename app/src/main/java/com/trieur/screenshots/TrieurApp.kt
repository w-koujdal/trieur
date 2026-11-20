package com.trieur.screenshots

import android.app.Application
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import com.trieur.screenshots.data.AppDatabase
import com.trieur.screenshots.data.Classifier
import com.trieur.screenshots.scan.IndexJobService
import com.trieur.screenshots.scan.ScreenshotScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Point d'accès unique aux dépendances (pas de framework d'injection pour le MVP). */
class TrieurApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "trieur.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }
    val scanner: ScreenshotScanner by lazy {
        ScreenshotScanner(this, database.screenshotDao())
    }

    override fun onCreate() {
        super.onCreate()
        IndexJobService.schedule(this)
        appScope.launch { reclassifyIfRulesChanged() }
    }

    /** Recalcule appli d'origine et catégorie de toutes les captures quand les règles ont changé. */
    private suspend fun reclassifyIfRulesChanged() {
        val prefs = getSharedPreferences("trieur", MODE_PRIVATE)
        if (prefs.getInt(KEY_CLASSIFIER_VERSION, 0) >= Classifier.VERSION) return
        try {
            val dao = database.screenshotDao()
            val inputs = dao.classificationInputs()
            val tally = mutableMapOf<String, Int>()
            database.withTransaction {
                for (s in inputs) {
                    val app = Classifier.sourceApp(s.displayName)
                    val category = Classifier.classify(s.text, app)
                    dao.updateClassification(s.mediaId, app, category.name)
                    tally.merge(category.name, 1, Int::plus)
                }
            }
            prefs.edit().putInt(KEY_CLASSIFIER_VERSION, Classifier.VERSION).apply()
            Log.i(TAG, "Reclassement v${Classifier.VERSION} : ${inputs.size} capture(s) $tally")
        } catch (e: Exception) {
            Log.w(TAG, "Reclassement échoué", e)
        }
    }

    private companion object {
        const val TAG = "TrieurApp"
        const val KEY_CLASSIFIER_VERSION = "classifierVersion"
    }
}
