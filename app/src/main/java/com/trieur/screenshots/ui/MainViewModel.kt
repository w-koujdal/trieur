package com.trieur.screenshots.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trieur.screenshots.TrieurApp
import com.trieur.screenshots.data.Screenshot
import com.trieur.screenshots.data.toFtsQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
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

    val results: StateFlow<List<Screenshot>> = _query
        .debounce(200)
        .flatMapLatest { q -> toFtsQuery(q)?.let(dao::search) ?: dao.all() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val indexedCount: StateFlow<Int> = dao.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
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
