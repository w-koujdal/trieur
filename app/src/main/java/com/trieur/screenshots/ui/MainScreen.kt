package com.trieur.screenshots.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.trieur.screenshots.data.findHighlights
import com.trieur.screenshots.data.searchTokens
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.trieur.screenshots.data.Screenshot

private val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaAccess(context: Context) = mediaPermissions.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val indexed by vm.indexedCount.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    var hasAccess by remember { mutableStateOf(hasMediaAccess(context)) }
    var selected by remember { mutableStateOf<Screenshot?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasAccess = granted.values.any { it }
        if (hasAccess) vm.startScan()
    }

    fun scanOrAsk() {
        if (hasMediaAccess(context)) vm.startScan() else permissionLauncher.launch(mediaPermissions)
    }

    // Scan incrémental à l'ouverture si l'accès est déjà accordé.
    LaunchedEffect(Unit) { if (hasAccess) vm.startScan() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trieur de captures") },
                actions = {
                    IconButton(onClick = ::scanOrAsk, enabled = !scan.running) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rescanner")
                    }
                },
            )
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
                placeholder = { Text("Rechercher un mot dans tes captures") },
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

            StatusLine(scan = scan, indexed = indexed, resultCount = results.size, query = query)

            when {
                !hasAccess -> CenteredMessage(
                    "L'appli a besoin d'accéder à tes images pour lire tes captures.",
                    action = "Autoriser l'accès",
                    onAction = ::scanOrAsk,
                )
                results.isEmpty() && query.isNotBlank() -> CenteredMessage("Aucune capture ne contient « $query ».")
                results.isEmpty() && !scan.running -> CenteredMessage(
                    "Aucune capture indexée pour l'instant.",
                    action = "Scanner",
                    onAction = ::scanOrAsk,
                )
                else -> ScreenshotGrid(
                    items = results,
                    onClick = { openImage(context, it) },
                    onLongClick = { selected = it },
                )
            }
        }
    }

    selected?.let { shot ->
        OcrTextSheet(
            shot = shot,
            query = query,
            onOpen = { openImage(context, shot) },
            onDismiss = { selected = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrTextSheet(shot: Screenshot, query: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val onHighlightColor = MaterialTheme.colorScheme.onTertiaryContainer
    val annotated = remember(shot.text, query, highlightColor) {
        buildAnnotatedString {
            append(shot.text)
            findHighlights(shot.text, searchTokens(query)).forEach { range ->
                addStyle(
                    SpanStyle(background = highlightColor, color = onHighlightColor, fontWeight = FontWeight.Bold),
                    range.first,
                    range.last + 1,
                )
            }
        }
    }
    val date = remember(shot.dateTaken) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(shot.dateTaken))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(shot.displayName, style = MaterialTheme.typography.titleMedium)
            Text(date, style = MaterialTheme.typography.bodySmall)
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (shot.text.isBlank()) {
                    Text("Aucun texte reconnu sur cette capture.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    SelectionContainer { Text(annotated, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir l'image") }
        }
    }
}

@Composable
private fun StatusLine(scan: ScanState, indexed: Int, resultCount: Int, query: String) {
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
            query.isNotBlank() -> Text(
                "$resultCount résultat(s) sur $indexed captures",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Text("$indexed captures indexées", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScreenshotGrid(
    items: List<Screenshot>,
    onClick: (Screenshot) -> Unit,
    onLongClick: (Screenshot) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.mediaId }) { shot ->
            AsyncImage(
                model = Uri.parse(shot.uri),
                contentDescription = shot.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.5f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onClick(shot) },
                        onLongClick = { onLongClick(shot) },
                        onLongClickLabel = "Voir le texte reconnu",
                    ),
            )
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

private fun openImage(context: Context, shot: Screenshot) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(shot.uri), "image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune appli pour afficher l'image", Toast.LENGTH_SHORT).show()
    }
}
