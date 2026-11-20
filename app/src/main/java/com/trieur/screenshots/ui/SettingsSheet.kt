package com.trieur.screenshots.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trieur.screenshots.data.Source

private val Source.help: String
    get() = when (this) {
        Source.SCREENSHOT -> "Captures d'écran et sélections intelligentes"
        Source.DOWNLOAD -> "Images du dossier Téléchargements"
        Source.GALLERY -> "Photos de l'appareil photo et images enregistrées depuis les applis. " +
            "La première lecture peut prendre plusieurs minutes."
    }

/** Choix des sources indexées. Couper une source retire ses images de l'appli (pas du téléphone). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(enabled: Set<Source>, onToggle: (Source, Boolean) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Où chercher",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "Tout est lu sur le téléphone, rien n'est envoyé. Couper une source la retire de l'appli, " +
                    "sans toucher à tes fichiers.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Source.entries.forEach { source ->
                val on = source in enabled
                ListItem(
                    headlineContent = { Text(source.label) },
                    supportingContent = { Text(source.help) },
                    trailingContent = {
                        Switch(
                            checked = on,
                            // Au moins une source reste active.
                            enabled = !on || enabled.size > 1,
                            onCheckedChange = { onToggle(source, it) },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
