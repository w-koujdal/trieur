package com.trieur.screenshots.ui

import com.trieur.screenshots.tr
import com.trieur.screenshots.data.appLabel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.InputChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trieur.screenshots.data.AppCount
import com.trieur.screenshots.data.Category
import com.trieur.screenshots.data.Source

/** Filtre par appli d'origine (menu déroulant) suivi des catégories non vides. */
@Composable
fun FilterBar(
    categories: List<Pair<Category, Int>>,
    selectedCategory: Category?,
    onCategoryClick: (Category) -> Unit,
    apps: List<AppCount>,
    selectedApp: String?,
    onAppSelected: (String?) -> Unit,
    cleanupLabel: String?,
    onClearCleanup: () -> Unit,
    sources: List<Pair<Source, Int>>,
    selectedSource: Source?,
    onSourceSelected: (Source?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Une puce qui apparaît en tête (lot de nettoyage, source) resterait hors de l'écran à gauche :
    // LazyRow garde l'ancienne première puce en place. Dès que la tête change, on revient au début.
    val showSources = sources.size > 1 || selectedSource != null
    val firstKey = when {
        cleanupLabel != null -> "cleanup"
        showSources -> "sources"
        apps.isNotEmpty() -> "apps"
        else -> categories.firstOrNull()?.first?.name
    }
    val state = rememberLazyListState()
    LaunchedEffect(firstKey) { state.scrollToItem(0) }
    LazyRow(
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (cleanupLabel != null) {
            item(key = "cleanup") {
                InputChip(
                    selected = true,
                    onClick = onClearCleanup,
                    label = { Text(cleanupLabel) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = tr("Retirer ce lot", "Remove this batch")) },
                )
            }
        }
        // Le filtre de source n'a de sens qu'avec plusieurs sources indexées.
        if (showSources) {
            item(key = "sources") {
                MenuChip(
                    label = selectedSource?.label ?: tr("Tout", "All"),
                    selected = selectedSource != null,
                    allLabel = tr("Toutes les sources", "All sources"),
                    entries = sources.map { (s, n) -> s to "${s.label} · $n" },
                    onSelected = onSourceSelected,
                )
            }
        }
        if (apps.isNotEmpty()) {
            item(key = "apps") {
                MenuChip(
                    label = selectedApp?.let(::appLabel) ?: tr("Appli", "App"),
                    selected = selectedApp != null,
                    allLabel = tr("Toutes les applis", "All apps"),
                    entries = apps.map { it.sourceApp to "${appLabel(it.sourceApp)} · ${it.count}" },
                    onSelected = onAppSelected,
                )
            }
        }
        // La catégorie active reste affichée (à 0) même si l'appli choisie n'en a aucune,
        // sinon on ne pourrait plus la désélectionner.
        val shown = if (selectedCategory == null || categories.any { it.first == selectedCategory }) {
            categories
        } else {
            (categories + (selectedCategory to 0)).sortedBy { it.first.ordinal }
        }
        items(shown, key = { it.first.name }) { (category, count) ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onCategoryClick(category) },
                label = { Text("${category.label} · $count") },
            )
        }
    }
}

/** Puce avec menu déroulant : une entrée « tout » (null) puis une par valeur. */
@Composable
private fun <T> MenuChip(
    label: String,
    selected: Boolean,
    allLabel: String,
    entries: List<Pair<T, String>>,
    onSelected: (T?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = { onSelected(null); open = false },
            )
            entries.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelected(value); open = false },
                )
            }
        }
    }
}
