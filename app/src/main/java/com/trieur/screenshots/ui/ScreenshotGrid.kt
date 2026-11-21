package com.trieur.screenshots.ui

import com.trieur.screenshots.tr

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trieur.screenshots.data.Screenshot
import com.trieur.screenshots.data.Source
import java.time.LocalDate

/** Grille de vignettes regroupées par date. Les résultats arrivent déjà triés du plus récent au plus ancien. */
@Composable
fun ScreenshotGrid(
    items: List<Screenshot>,
    selection: Set<Long>,
    onClick: (Screenshot) -> Unit,
    onLongClick: (Screenshot) -> Unit,
    /** Quand cette valeur change (recherche, filtres), la grille revient en haut. */
    scrollResetKey: Any? = null,
    /** En tête de grille, sur toute la largeur (résultats des autres appareils). */
    header: (@Composable () -> Unit)? = null,
) {
    val state = rememberLazyGridState()
    LaunchedEffect(scrollResetKey) { state.scrollToItem(0) }
    val today = remember { LocalDate.now() }
    val sections = remember(items, today) { items.groupBy { dateBucket(it.dateTaken, today) } }
    val selecting = selection.isNotEmpty()

    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        sections.forEach { (title, shots) ->
            item(key = "section:$title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
            }
            items(shots, key = { it.mediaId }) { shot ->
                Thumbnail(
                    shot = shot,
                    selecting = selecting,
                    selected = shot.mediaId in selection,
                    onClick = { onClick(shot) },
                    onLongClick = { onLongClick(shot) },
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(
    shot: Screenshot,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.5f)
            .clip(shape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = if (selecting) tr("Sélectionner", "Select") else tr("Voir les détails", "View details"),
                onLongClickLabel = tr("Sélectionner", "Select"),
            ),
    ) {
        if (shot.source == Source.DOC.name) {
            DocTile(shot)
        } else {
            AsyncImage(
                model = thumbnailModel(LocalContext.current, shot),
                contentDescription = shot.displayName,
                // Page de document : toute la largeur, le haut de la page ; image : recadrée au centre.
                contentScale = if (shot.source == Source.PDF.name) ContentScale.FillWidth else ContentScale.Crop,
                alignment = if (shot.source == Source.PDF.name) Alignment.TopCenter else Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
        }
        if (selecting) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
}

/** Document sans aperçu (Word, texte…) : son type en grand, et son nom. */
@Composable
private fun DocTile(shot: com.trieur.screenshots.data.Screenshot) {
    val ext = shot.displayName.substringAfterLast('.', "").uppercase().take(5).ifEmpty { "DOC" }
    val color = when (ext) {
        "DOC", "DOCX", "ODT", "RTF" -> Color(0xFF2B579A)
        "XLS", "XLSX", "ODS", "CSV" -> Color(0xFF217346)
        "PPT", "PPTX", "ODP" -> Color(0xFFD24726)
        "TXT", "MD" -> Color(0xFF5F6368)
        else -> Color(0xFF6D4C9F)
    }
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().background(color.copy(alpha = 0.12f)).padding(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(ext, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(
            shot.displayName.substringBeforeLast('.'),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 4,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
