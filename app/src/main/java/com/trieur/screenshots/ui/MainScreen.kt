package com.trieur.screenshots.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trieur.screenshots.data.Screenshot
import com.trieur.screenshots.scan.hasMediaAccess
import com.trieur.screenshots.scan.mediaPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val indexed by vm.indexedCount.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val app by vm.app.collectAsStateWithLifecycle()
    val categoryCounts by vm.categoryCounts.collectAsStateWithLifecycle()
    val appCounts by vm.appCounts.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    val cleanup by vm.cleanup.collectAsStateWithLifecycle()
    val filtering = query.isNotBlank() || category != null || app != null || cleanup != null
    var hasAccess by remember { mutableStateOf(hasMediaAccess(context)) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var showCleanup by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasAccess = granted.values.any { it }
        if (hasAccess) vm.startScan()
    }

    fun scanOrAsk() {
        if (hasMediaAccess(context)) vm.startScan() else permissionLauncher.launch(mediaPermissions)
    }

    // Corbeille : le système affiche sa propre confirmation, on ne touche à l'index que si elle est acceptée.
    var pendingDelete by remember { mutableStateOf<List<Long>>(emptyList()) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.removeFromIndex(pendingDelete)
            if (detail?.mediaId in pendingDelete) vm.openDetail(null)
        }
        pendingDelete = emptyList()
    }

    fun requestDelete(shots: List<Screenshot>) {
        val sender = deleteRequest(context, shots.map { it.uri }) ?: return
        pendingDelete = shots.map { it.mediaId }
        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    val selectedShots = results.filter { it.mediaId in selection }

    // Scan incrémental à l'ouverture si l'accès est déjà accordé.
    LaunchedEffect(Unit) { if (hasAccess) vm.startScan() }
    BackHandler(enabled = selecting) { vm.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    // Juste le nombre : tient sur une ligne même en grande police (lu « 6 sélectionnées »).
                    title = {
                        val label = "${selection.size} sélectionnée${if (selection.size > 1) "s" else ""}"
                        Text(
                            "${selection.size}",
                            maxLines = 1,
                            modifier = Modifier.semantics { contentDescription = label },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Annuler la sélection")
                        }
                    },
                    actions = {
                        TextButton(onClick = { vm.toggleSelectAll(results.map { it.mediaId }) }) { Text("Tout") }
                        IconButton(onClick = { shareImages(context, selectedShots.map { it.uri }) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Partager la sélection")
                        }
                        if (canDelete) {
                            IconButton(onClick = { requestDelete(selectedShots) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Mettre la sélection à la corbeille")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text("Trieur de captures") },
                    actions = {
                        IconButton(onClick = { showCleanup = true }) {
                            BadgedBox(badge = { if (suggestions.isNotEmpty()) Badge { Text("${suggestions.size}") } }) {
                                Icon(Icons.Filled.Build, contentDescription = "Nettoyage")
                            }
                        }
                        IconButton(onClick = ::scanOrAsk, enabled = !scan.running) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Rescanner")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Rechercher un mot", maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
            )

            FilterBar(
                categories = categoryCounts,
                selectedCategory = category,
                onCategoryClick = vm::toggleCategory,
                apps = appCounts,
                selectedApp = app,
                onAppSelected = vm::selectApp,
                cleanupLabel = cleanup?.title,
                onClearCleanup = vm::clearCleanup,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            StatusLine(scan = scan, indexed = indexed, resultCount = results.size, filtering = filtering)

            when {
                !hasAccess -> CenteredMessage(
                    "L'appli a besoin d'accéder à tes images pour lire tes captures.",
                    action = "Autoriser l'accès",
                    onAction = ::scanOrAsk,
                )
                results.isEmpty() && filtering -> CenteredMessage(
                    if (query.isNotBlank()) "Aucune capture ne contient « $query » avec ces filtres."
                    else "Aucune capture avec ces filtres."
                )
                results.isEmpty() && !scan.running -> CenteredMessage(
                    "Aucune capture indexée pour l'instant.",
                    action = "Scanner",
                    onAction = ::scanOrAsk,
                )
                else -> ScreenshotGrid(
                    items = results,
                    selection = selection,
                    onClick = { if (selecting) vm.toggleSelection(it.mediaId) else vm.openDetail(it.mediaId) },
                    onLongClick = { vm.toggleSelection(it.mediaId) },
                    scrollResetKey = listOf(query, category, app, cleanup?.key),
                )
            }
        }
    }

    detail?.let { shot ->
        DetailSheet(
            shot = shot,
            query = query,
            onShare = { shareImages(context, listOf(shot.uri)) },
            onDelete = { requestDelete(listOf(shot)) },
            onCategoryChange = { vm.setCategory(shot, it) },
            onDismiss = { vm.openDetail(null) },
        )
    }

    if (showCleanup) {
        CleanupSheet(
            suggestions = suggestions,
            onPick = { vm.applyCleanup(it); showCleanup = false },
            onDismiss = { showCleanup = false },
        )
    }
}

@Composable
private fun StatusLine(scan: ScanState, indexed: Int, resultCount: Int, filtering: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        when {
            scan.running && scan.total > 0 -> {
                Text("Lecture du texte… ${scan.done} / ${scan.total}", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { scan.done.toFloat() / scan.total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
            scan.running -> {
                Text("Recherche des captures…", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            scan.error != null -> Text(
                "Erreur pendant le scan : ${scan.error}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            filtering -> Text(
                "$resultCount résultat(s) sur $indexed captures",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Text("$indexed captures indexées", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CenteredMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, textAlign = TextAlign.Center)
            if (action != null) {
                Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(action) }
            }
        }
    }
}
