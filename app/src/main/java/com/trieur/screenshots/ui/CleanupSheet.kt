package com.trieur.screenshots.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trieur.screenshots.data.Suggestion

/** Liste des lots proposés au nettoyage. Toucher un lot l'affiche avec ses captures présélectionnées. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupSheet(suggestions: List<Suggestion>, onPick: (Suggestion) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Nettoyage",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                if (suggestions.isEmpty()) "Rien à nettoyer pour l'instant."
                else "Touche un lot : ses captures seront sélectionnées, tu vérifies puis tu mets à la corbeille.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            suggestions.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                ListItem(
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(s.detail) },
                    trailingContent = { Text("${s.ids.size}", style = MaterialTheme.typography.titleMedium) },
                    modifier = Modifier
                        .clickable { onPick(s) }
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}
