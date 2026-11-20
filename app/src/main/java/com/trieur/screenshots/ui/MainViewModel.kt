package com.trieur.screenshots.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trieur.screenshots.TrieurApp
import com.trieur.screenshots.data.AppCount
import com.trieur.screenshots.data.Category
import com.trieur.screenshots.data.Classifier
import com.trieur.screenshots.data.Screenshot
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

    /** Lot de nettoyage affiché (filtre supplémentaire sur ses captures), null sinon. */
    private val _cleanup = MutableStateFlow<Suggestion?>(null)
    val cleanup: StateFlow<Suggestion?> = _cleanup.asStateFlow()

    val results: StateFlow<List<Screenshot>> =
        combine(_query.debounce(200), _category, _app) { q, c, a -> Triple(q, c?.name, a) }
            .flatMapLatest { (q, c, a) ->
                toFtsQuery(q)?.let { dao.search(it, c, a) } ?: dao.filtered(c, a)
            }
            .combine(_cleanup) { list, lot -> if (lot == null) list else list.filter { it.mediaId in lot.ids } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Propositions de nettoyage, recalculées à chaque changement de l'index. */
    val suggestions: StateFlow<List<Suggestion>> = dao.filtered(null, null)
        .map { cleanupSuggestions(it, System.currentTimeMillis()) }
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
    val categoryCounts: StateFlow<List<Pair<Category, Int>>> = _app
        .flatMapLatest { dao.categoryCounts(it) }
        .map { rows ->
            val byName = rows.associate { it.category to it.count }
            Category.entries.mapNotNull { c -> byName[c.name]?.let { c to it } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appCounts: StateFlow<List<AppCount>> = dao.appCounts()
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

    /** Affiche un lot de nettoyage, toutes ses captures présélectionnées, les autres filtres retirés. */
    fun applyCleanup(lot: Suggestion) {
        _query.value = ""
        _category.value = null
        _app.value = null
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
                dao.setCategory(shot.mediaId, Classifier.classify(shot.text, shot.sourceApp).name, locked = false)
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
        viewModelScope.launch { gone.chunked(500).forEach { dao.delete(it) } }
    }

    fun startScan() {
        if (_scan.value.running) return
        _scan.value = ScanState(running = true)
        viewModelScope.launch {
            try {
                val added = trieur.scanner.scan { done, total ->
                    _scan.update { it.copy(done = done, total = total) }
                }
                _scan.value = ScanState(lastAdded = added)
            } catch (e: Exception) {
                _scan.value = ScanState(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
