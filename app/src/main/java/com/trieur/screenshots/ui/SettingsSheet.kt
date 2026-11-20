package com.trieur.screenshots.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.trieur.screenshots.data.Source

private val Source.help: String
    get() = when (this) {
        Source.SCREENSHOT -> "Captures d'écran et sélections intelligentes"
        Source.DOWNLOAD -> "Images du dossier Téléchargements"
        Source.GALLERY -> "Photos de l'appareil photo et images enregistrées depuis les applis. " +
            "La première lecture peut prendre plusieurs minutes."
    }

/** Un interrupteur par source ; toute la ligne est cliquable. Au moins une source reste active. */
@Composable
fun SourceToggles(enabled: Set<Source>, onToggle: (Source, Boolean) -> Unit, transparent: Boolean = false) {
    Source.entries.forEach { source ->
        val on = source in enabled
        val canToggle = !on || enabled.size > 1
        ListItem(
            headlineContent = { Text(source.label) },
            supportingContent = { Text(source.help) },
            trailingContent = { Switch(checked = on, enabled = canToggle, onCheckedChange = null) },
            colors = if (transparent) ListItemDefaults.colors(containerColor = Color.Transparent) else ListItemDefaults.colors(),
            modifier = Modifier
                .toggleable(
                    value = on,
                    enabled = canToggle,
                    role = Role.Switch,
                    onValueChange = { onToggle(source, it) },
                )
                .padding(horizontal = 8.dp),
        )
    }
}

/** Réglages : sources indexées, puis à propos et confidentialité. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(enabled: Set<Source>, onToggle: (Source, Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Où chercher",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "Couper une source la retire de l'appli, sans toucher à tes fichiers.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SourceToggles(enabled, onToggle)

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            Text(
                "Confidentialité",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            PRIVACY_POINTS.forEach { point ->
                Text(
                    "• $point",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            Text(
                "Trieur $version · gratuit, sans pub, sans compte",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** Affirmations vérifiables dans le manifeste et le code (pas de permission INTERNET). */
val PRIVACY_POINTS = listOf(
    "Tout est analysé sur ton téléphone : lecture du texte et reconnaissance des images.",
    "L'appli n'a pas accès à Internet : rien ne peut être envoyé, même pas des statistiques.",
    "Elle ne modifie jamais tes images. La corbeille passe par la confirmation d'Android et reste récupérable 30 jours.",
)
