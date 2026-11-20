package com.trieur.screenshots.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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

/** Filtre par appli d'origine (menu déroulant) suivi des catégories non vides. */
@Composable
fun FilterBar(
    categories: List<Pair<Category, Int>>,
    selectedCategory: Category?,
    onCategoryClick: (Category) -> Unit,
    apps: List<AppCount>,
    selectedApp: String?,
    onAppSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (apps.isNotEmpty()) {
            item(key = "apps") { AppFilterChip(apps, selectedApp, onAppSelected) }
        }
        items(categories, key = { it.first.name }) { (category, count) ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onCategoryClick(category) },
                label = { Text("${category.label} · $count") },
            )
        }
    }
}

@Composable
private fun AppFilterChip(apps: List<AppCount>, selectedApp: String?, onAppSelected: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedApp != null,
            onClick = { open = true },
            label = { Text(selectedApp ?: "Appli") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Toutes les applis") },
                onClick = { onAppSelected(null); open = false },
            )
            apps.forEach { app ->
                DropdownMenuItem(
                    text = { Text("${app.sourceApp} · ${app.count}") },
                    onClick = { onAppSelected(app.sourceApp); open = false },
                )
            }
        }
    }
}
