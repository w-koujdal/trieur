package com.trieur.screenshots

import android.app.Application
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import com.trieur.screenshots.data.AppDatabase
import com.trieur.screenshots.data.Classifier
import com.trieur.screenshots.data.Labels
import com.trieur.screenshots.data.Source
import com.trieur.screenshots.scan.IndexJobService
import com.trieur.screenshots.scan.ScreenshotScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Point d'accès unique aux dépendances (pas de framework d'injection). */
class TrieurApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { getSharedPreferences("trieur", MODE_PRIVATE) }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "trieur.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()
    }

    private val _sources by lazy { MutableStateFlow(loadSources()) }

    /** Sources indexées (réglage), lues aussi par le scan d'arrière-plan. */
    val sources: StateFlow<Set<Source>> get() = _sources.asStateFlow()

    private val _pdfTrees by lazy { MutableStateFlow(prefs.getStringSet(KEY_PDF_TREES, null)?.toSet() ?: emptySet()) }

    /** Dossiers de PDF choisis (URI d'arborescence, accès conservé par takePersistableUriPermission). */
    val pdfTrees: StateFlow<Set<String>> get() = _pdfTrees.asStateFlow()

    val scanner: ScreenshotScanner by lazy {
        ScreenshotScanner(this, database.screenshotDao(), { _sources.value }, { _pdfTrees.value })
    }

    fun addPdfTree(uri: String) = savePdfTrees(_pdfTrees.value + uri)

    fun removePdfTree(uri: String) = savePdfTrees(_pdfTrees.value - uri)

    private fun savePdfTrees(next: Set<String>) {
        _pdfTrees.value = next
        prefs.edit().putStringSet(KEY_PDF_TREES, next).apply()
    }

    override fun onCreate() {
        super.onCreate()
        IndexJobService.schedule(this)
        appScope.launch { reclassifyIfRulesChanged() }
    }

    fun setSourceEnabled(source: Source, enabled: Boolean) {
        val next = if (enabled) _sources.value + source else _sources.value - source
        if (next.isEmpty()) return // au moins une source
        _sources.value = next
        prefs.edit().putStringSet(KEY_SOURCES, next.mapTo(HashSet()) { it.name }).apply()
    }

    private fun loadSources(): Set<Source> =
        prefs.getStringSet(KEY_SOURCES, null)
            ?.mapNotNull { name -> Source.entries.firstOrNull { it.name == name } }
            ?.toSet()?.ifEmpty { null }
            ?: Source.DEFAULT_ENABLED

    /** Recalcule appli d'origine et catégorie de toutes les images quand les règles ont changé. */
    private suspend fun reclassifyIfRulesChanged() {
        if (prefs.getInt(KEY_CLASSIFIER_VERSION, 0) >= Classifier.VERSION) return
        try {
            val dao = database.screenshotDao()
            val tally = mutableMapOf<String, Int>()
            // Lecture dans la transaction : un scan en parallèle ne peut pas écrire entre la lecture
            // et la mise à jour (ses étiquettes seraient écrasées par des valeurs périmées).
            val count = database.withTransaction {
                val inputs = dao.classificationInputs()
                for (s in inputs) {
                    val app = Classifier.sourceApp(s.displayName, s.folder)
                    val labels = Labels.decode(s.labels)
                    val category = Classifier.classify(s.text, app, Source.fromName(s.source), labels)
                    // Les mots cherchables suivent la table de traduction, qui peut avoir changé.
                    val tags = s.labels?.let { Labels.searchTags(labels) }
                    dao.updateClassification(s.mediaId, app, category.name, tags)
                    tally.merge(category.name, 1, Int::plus)
                }
                inputs.size
            }
            prefs.edit().putInt(KEY_CLASSIFIER_VERSION, Classifier.VERSION).apply()
            Log.i(TAG, "Reclassement v${Classifier.VERSION} : $count image(s) $tally")
        } catch (e: Exception) {
            Log.w(TAG, "Reclassement échoué", e)
        }
    }

    private companion object {
        const val TAG = "TrieurApp"
        const val KEY_CLASSIFIER_VERSION = "classifierVersion"
        const val KEY_SOURCES = "sources"
        const val KEY_PDF_TREES = "pdfTrees"
    }
}
