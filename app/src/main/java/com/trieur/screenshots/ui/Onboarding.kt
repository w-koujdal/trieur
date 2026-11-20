package com.trieur.screenshots.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.trieur.screenshots.data.Source

/**
 * Premier lancement : ce que fait l'appli, où elle cherche, puis la permission d'accès aux images.
 * [denied] : l'utilisateur a refusé la permission, on explique comment la donner.
 */
@Composable
fun OnboardingScreen(
    enabledSources: Set<Source>,
    onToggleSource: (Source, Boolean) -> Unit,
    denied: Boolean,
    onStart: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text("Trieur", style = MaterialTheme.typography.displaySmall)
            Text(
                "Retrouve n'importe quelle image par ce qu'elle contient.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Point(Icons.Filled.Search, "Cherche un mot", "Le texte de tes captures, photos de documents et tickets est lu automatiquement.")
            Point(Icons.Filled.Star, "Tri automatique", "Achats, voyages, codes, conversations, personnes, animaux, nourriture…")
            Point(Icons.Filled.Build, "Nettoyage guidé", "Doublons, codes expirés, vieilles captures : vérifie et mets à la corbeille.")
            Point(Icons.Filled.Lock, "100 % sur ton téléphone", "L'appli n'a pas accès à Internet. Rien n'est envoyé, jamais.")

            Text(
                "Où chercher",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            SourceToggles(enabledSources, onToggleSource, transparent = true)

            if (denied) {
                Text(
                    "Sans accès à tes images, l'appli ne peut rien lire. Tu peux aussi l'autoriser dans " +
                        "Paramètres → Applis → Trieur → Autorisations → Photos et vidéos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)) {
                Text("Autoriser l'accès aux images et commencer")
            }
        }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
