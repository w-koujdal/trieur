package com.trieur.screenshots.ui

import android.app.Activity
import android.widget.Toast
import com.trieur.screenshots.data.Source
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    val source by vm.source.collectAsStateWithLifecycle()
    val sourceCounts by vm.sourceCounts.collectAsStateWithLifecycle()
    val enabledSources by vm.enabledSources.collectAsStateWithLifecycle()
    val pdfTrees by vm.pdfTrees.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    val cleanup by vm.cleanup.collectAsStateWithLifecycle()
    val filtering = query.isNotBlank() || category != null || app != null || source != null || cleanup != null
    var hasAccess by remember { mutableStateOf(hasMediaAccess(context)) }
    var accessDenied by remember { mutableStateOf(false) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var showCleanup by remember { mutableStateOf(false) }
    val remote by vm.remote.collectAsStateWithLifecycle()
    val hasRemote = remote.loading || remote.groups.isNotEmpty()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasAccess = granted.values.any { it }
        accessDenied = !hasAccess
        if (hasAccess) vm.startScan()
    }

    fun scanOrAsk() {
        if (hasMediaAccess(context)) vm.startScan() else permissionLauncher.launch(mediaPermissions)
    }

    // Corbeille : le système affiche sa propre confirmation, on ne touche à l'index que si elle est acceptée.
    // Sauvegardé : survit à une rotation pendant que la confirmation système est affichée.
    var pendingDelete by rememberSaveable { mutableStateOf(longArrayOf()) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.removeFromIndex(pendingDelete.toList())
            if (detail?.mediaId?.let { it in pendingDelete } == true) vm.openDetail(null)
        }
        pendingDelete = longArrayOf()
    }

    fun requestDelete(shots: List<Screenshot>) {
        // Android ne permet pas de mettre un PDF (fichier hors MediaStore) à la corbeille d'ici.
        val media = shots.filter { !Source.fromName(it.source).isFile }
        if (media.size < shots.size) {
            Toast.makeText(context, "Les PDF restent en place : supprime-les depuis Mes fichiers", Toast.LENGTH_LONG).show()
        }
        val sender = deleteRequest(context, media.map { it.uri }) ?: return
        pendingDelete = media.map { it.mediaId }.toLongArray()
        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    val selectedShots = results.filter { it.mediaId in selection }

    // Scan incrémental à l'ouverture si l'accès est déjà accordé.
    LaunchedEffect(Unit) { if (hasAccess) vm.startScan() }
    BackHandler(enabled = selecting) { vm.clearSelection() }

    // Retour des réglages d'Android : l'accès a pu être donné (ou retiré) entre-temps.
    LifecycleResumeEffect(Unit) {
        val now = hasMediaAccess(context)
        if (now != hasAccess) {
            hasAccess = now
            if (now) vm.startScan()
        }
        onPauseOrDispose { }
    }

    if (!hasAccess) {
        OnboardingScreen(
            enabledSources = enabledSources,
            onToggleSource = { s, on -> vm.setSourceEnabled(s, on) },
            denied = accessDenied,
            onStart = { permissionLauncher.launch(mediaPermissions) },
        )
        return
    }

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
                        IconButton(onClick = { shareItems(context, selectedShots) }) {
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
                    title = { Text("Trieur") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Sources")
                        }
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
                sources = sourceCounts,
                selectedSource = source,
                onSourceSelected = vm::selectSource,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            StatusLine(scan = scan, indexed = indexed, resultCount = results.size, filtering = filtering)

            when {

                results.isEmpty() && filtering && hasRemote -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                ) {
                    Text("Rien sur ce téléphone.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    RemoteResults(remote, vm)
                }
                results.isEmpty() && filtering -> CenteredMessage(
                    if (query.isNotBlank()) "Rien ne contient « $query » avec ces filtres."
                    else "Rien avec ces filtres."
                )
                results.isEmpty() && !scan.running -> CenteredMessage(
                    "Aucune image indexée pour l'instant.",
                    action = "Scanner",
                    onAction = ::scanOrAsk,
                )
                else -> ScreenshotGrid(
                    items = results,
                    selection = selection,
                    onClick = { if (selecting) vm.toggleSelection(it.mediaId) else vm.openDetail(it.mediaId) },
                    onLongClick = { vm.toggleSelection(it.mediaId) },
                    scrollResetKey = listOf(query, category, app, source, cleanup?.key),
                    header = if (hasRemote) ({ RemoteResults(remote, vm) }) else null,
                )
            }
        }
    }

    detail?.let { shot ->
        DetailSheet(
            shot = shot,
            query = query,
            onShare = { shareItems(context, listOf(shot)) },
            onDelete = { requestDelete(listOf(shot)) },
            onCategoryChange = { vm.setCategory(shot, it) },
            onDismiss = { vm.openDetail(null) },
        )
    }

    if (showSettings) {
        SettingsSheet(
            enabled = enabledSources,
            onToggle = vm::setSourceEnabled,
            pdfTrees = pdfTrees,
            onAddPdfTree = vm::addPdfTree,
            onRemovePdfTree = vm::removePdfTree,
            devices = { DevicesSection(vm) },
            onDismiss = { showSettings = false },
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
                "$resultCount résultat(s) sur $indexed images",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Text("$indexed images indexées", style = MaterialTheme.typography.bodySmall)
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
