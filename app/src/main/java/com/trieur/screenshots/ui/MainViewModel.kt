package com.trieur.screenshots.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trieur.screenshots.TrieurApp
import com.trieur.screenshots.data.AppCount
import com.trieur.screenshots.data.Category
import com.trieur.screenshots.data.Classifier
import com.trieur.screenshots.data.Labels
import com.trieur.screenshots.data.Screenshot
import com.trieur.screenshots.data.Source
import com.trieur.screenshots.data.Suggestion
import com.trieur.screenshots.data.cleanupSuggestions
import com.trieur.screenshots.data.toFtsQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val lastAdded: Int? = null,
    val error: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val trieur = app as TrieurApp
    private val dao = trieur.database.screenshotDao()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    private val _app = MutableStateFlow<String?>(null)
    val app: StateFlow<String?> = _app.asStateFlow()

    private val _source = MutableStateFlow<Source?>(null)
    val source: StateFlow<Source?> = _source.asStateFlow()

    /** Sources indexées (réglage). */
    val enabledSources: StateFlow<Set<Source>> = trieur.sources

    /** Dossiers de PDF choisis (réglage). */
    val pdfTrees: StateFlow<Set<String>> = trieur.pdfTrees

    fun addPdfTree(uri: String) {
        trieur.addPdfTree(uri)
        startScan(force = true)
    }

    /** Retire le dossier : ses PDF sortent de l'index au prochain scan (les fichiers restent). */
    fun removePdfTree(uri: String) {
        trieur.removePdfTree(uri)
        if (_source.value == Source.PDF && trieur.pdfTrees.value.isEmpty()) selectSource(null)
        startScan(force = true)
    }

    /** Lot de nettoyage affiché (filtre supplémentaire sur ses images), null sinon. */
    private val _cleanup = MutableStateFlow<Suggestion?>(null)
    val cleanup: StateFlow<Suggestion?> = _cleanup.asStateFlow()

    private data class Filters(val query: String, val category: String?, val app: String?, val source: String?)

    val results: StateFlow<List<Screenshot>> =
        combine(_query.debounce(200), _category, _app, _source) { q, c, a, s -> Filters(q, c?.name, a, s?.name) }
            .flatMapLatest { f ->
                toFtsQuery(f.query)?.let { dao.search(it, f.category, f.app, f.source) }
                    ?: dao.filtered(f.category, f.app, f.source)
            }
            .combine(_cleanup) { list, lot -> if (lot == null) list else list.filter { it.mediaId in lot.ids } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Propositions de nettoyage, recalculées à chaque changement de l'index. */
    val suggestions: StateFlow<List<Suggestion>> = dao.filtered(null, null, null)
        // Pendant un scan, chaque image ajoutée relancerait le calcul (doublons : O(n²)) :
        // on attend une demi-seconde de calme, et un calcul en cours est abandonné pour le suivant.
        .debounce(500)
        .mapLatest { cleanupSuggestions(it, System.currentTimeMillis()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Capture ouverte dans le panneau de détails, suivie en direct (changement de catégorie…). */
    private val _detailId = MutableStateFlow<Long?>(null)
    val detail: StateFlow<Screenshot?> = _detailId
        .flatMapLatest { id -> if (id == null) flowOf(null) else dao.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val indexedCount: StateFlow<Int> = dao.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Nombre de captures par catégorie, dans l'ordre d'affichage, sans les catégories vides. */
    val categoryCounts: StateFlow<List<Pair<Category, Int>>> = combine(_app, _source) { a, s -> a to s?.name }
        .flatMapLatest { (a, s) -> dao.categoryCounts(a, s) }
        .map { rows ->
            val byName = rows.associate { it.category to it.count }
            Category.entries.mapNotNull { c -> byName[c.name]?.let { c to it } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appCounts: StateFlow<List<AppCount>> = _source
        .flatMapLatest { dao.appCounts(it?.name) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Nombre d'images indexées par source (pour le filtre), dans l'ordre de [Source]. */
    val sourceCounts: StateFlow<List<Pair<Source, Int>>> = dao.sourceCounts()
        .map { rows ->
            val byName = rows.associate { it.source to it.count }
            Source.entries.mapNotNull { s -> byName[s.name]?.let { s to it } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    /** Captures sélectionnées (mode sélection actif dès qu'il y en a une). */
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    // Changer de recherche ou de filtre vide la sélection : on n'agit jamais sur des captures
    // qui ne sont plus affichées.
    fun onQueryChange(value: String) {
        if (value != _query.value) clearSelection()
        _query.value = value
    }

    /** Re-toucher la catégorie active la désélectionne. */
    fun toggleCategory(value: Category) {
        clearSelection()
        _category.update { if (it == value) null else value }
    }

    fun selectApp(value: String?) {
        clearSelection()
        _app.value = value
    }

    fun selectSource(value: Source?) {
        clearSelection()
        _source.value = value
        _app.value = null // les applis dépendent de la source
    }

    /** Active ou coupe une source, puis rescanne : ses images entrent dans l'index ou en sortent. */
    fun setSourceEnabled(value: Source, enabled: Boolean) {
        trieur.setSourceEnabled(value, enabled)
        if (!enabled && _source.value == value) selectSource(null)
        startScan(force = true)
    }

    /** Affiche un lot de nettoyage, toutes ses captures présélectionnées, les autres filtres retirés. */
    fun applyCleanup(lot: Suggestion) {
        _query.value = ""
        _category.value = null
        _app.value = null
        _source.value = null
        _cleanup.value = lot
        _selection.value = lot.ids
    }

    fun clearCleanup() {
        clearSelection()
        _cleanup.value = null
    }

    fun openDetail(id: Long?) {
        _detailId.value = id
    }

    /** [category] null = revenir au classement automatique. */
    fun setCategory(shot: Screenshot, category: Category?) {
        viewModelScope.launch {
            if (category == null) {
                val auto = Classifier.classify(shot.text, shot.sourceApp, Source.fromName(shot.source), Labels.decode(shot.labels))
                dao.setCategory(shot.mediaId, auto.name, locked = false)
            } else {
                dao.setCategory(shot.mediaId, category.name, locked = true)
            }
        }
    }

    fun toggleSelection(id: Long) {
        _selection.update { if (id in it) it - id else it + id }
    }

    /** Tout sélectionner parmi [visible], ou tout désélectionner si c'est déjà le cas. */
    fun toggleSelectAll(visible: Collection<Long>) {
        _selection.update { if (it.containsAll(visible)) emptySet() else visible.toSet() }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** Après suppression confirmée par le système : retire tout de suite les captures de l'index. */
    fun removeFromIndex(ids: Collection<Long>) {
        val gone = ids.toSet()
        _selection.update { it - gone }
        // Un lot de nettoyage vidé n'a plus rien à montrer : on le ferme.
        _cleanup.update { lot -> lot?.let { it.copy(ids = it.ids - gone) }?.takeIf { it.ids.isNotEmpty() } }
        viewModelScope.launch { gone.chunked(500).forEach { dao.delete(it) } }
    }

    /** Un réglage a changé pendant un scan : en relancer un juste après, avec les nouvelles sources. */
    private var rescanRequested = false

    /** [force] : rescanner même si un scan tourne déjà (il sera relancé à la fin). */
    fun startScan(force: Boolean = false) {
        if (_scan.value.running) {
            if (force) rescanRequested = true
            return
        }
        _scan.value = ScanState(running = true)
        viewModelScope.launch {
            try {
                var added = 0
                do {
                    rescanRequested = false
                    added += trieur.scanner.scan { done, total ->
                        _scan.update { it.copy(done = done, total = total) }
                    }
                } while (rescanRequested)
                _scan.value = ScanState(lastAdded = added)
            } catch (e: Exception) {
                _scan.value = ScanState(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
