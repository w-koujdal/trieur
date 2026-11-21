package com.trieur.screenshots.ui

import com.trieur.screenshots.tr

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.trieur.screenshots.scan.PdfSource
import android.os.Environment
import android.provider.Settings
import java.io.File

private val Source.help: String
    get() = when (this) {
        Source.SCREENSHOT -> tr("Captures d'écran et sélections intelligentes", "Screenshots and smart selections")
        Source.DOWNLOAD -> tr("Images du dossier Téléchargements", "Images in the Download folder")
        Source.GALLERY -> tr("Photos de l'appareil photo et images enregistrées depuis les applis. La première lecture peut prendre plusieurs minutes.", "Camera photos and images saved from apps. The first pass can take a few minutes.")
        Source.PDF -> tr("Documents PDF des dossiers choisis", "PDF documents in the chosen folders")
        Source.DOC -> tr("Word, texte, tableurs… des dossiers choisis", "Word, text, spreadsheets… in the chosen folders")
    }

/** « primary:Download/Factures » → « Download/Factures ». */
private fun treeLabel(uri: String): String =
    if (uri.startsWith("file:")) (Uri.parse(uri).path.orEmpty().removePrefix(PdfSource.storageRoot.absolutePath).trim('/').ifEmpty { tr("Stockage", "Storage") }) + tr(" (accès complet)", " (full access)")
    else runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':').ifEmpty { tr("Stockage", "Storage") } }
        .getOrDefault(uri)

/** Un interrupteur par source ; toute la ligne est cliquable. Au moins une source reste active. */
@Composable
fun SourceToggles(enabled: Set<Source>, onToggle: (Source, Boolean) -> Unit, transparent: Boolean = false) {
    Source.MEDIA.forEach { source ->
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
fun SettingsSheet(
    enabled: Set<Source>,
    onToggle: (Source, Boolean) -> Unit,
    pdfTrees: Set<String>,
    onAddPdfTree: (String) -> Unit,
    onRemovePdfTree: (String) -> Unit,
    devices: @Composable () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Sélecteur de dossier d'Android : accès à ce dossier seulement, conservé après redémarrage.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onAddPdfTree(uri.toString())
        }
    }
    // Download entier : Android l'interdit dans le sélecteur, seul « accès à tous les fichiers » le permet.
    val downloadTree = PdfSource.fileTree(File(PdfSource.storageRoot, Environment.DIRECTORY_DOWNLOADS))
    val allFiles = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (PdfSource.hasAllFiles()) onAddPdfTree(downloadTree)
    }
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
                tr("Où chercher", "Where to look"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                tr("Couper une source la retire de l'appli, sans toucher à tes fichiers.", "Turning a source off removes it from the app without touching your files."),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SourceToggles(enabled, onToggle)

            Text(
                tr("Dossiers de documents", "Document folders"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
            Text(
                tr("Android ne donne pas accès aux documents avec la permission photos : choisis les dossiers à lire (ex. Download, Documents). Tous les fichiers sont cherchables par leur nom ; le texte des PDF (5 premières pages), Word, texte et tableurs est lu aussi.", "Android doesn't give access to documents with the photos permission: choose the folders to read (e.g. Download, Documents). Every file is searchable by name; the text of PDFs (first 5 pages), Word, text and spreadsheet files is read too."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            pdfTrees.sorted().forEach { tree ->
                ListItem(
                    headlineContent = { Text(treeLabel(tree)) },
                    trailingContent = {
                        IconButton(onClick = {
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(
                                    Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }
                            onRemovePdfTree(tree)
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = tr("Retirer ", "Remove ") + treeLabel(tree))
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            OutlinedButton(
                onClick = { pickFolder.launch(null) },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            ) { Text(tr("Ajouter un dossier", "Add a folder")) }
            if (downloadTree !in pdfTrees && android.os.Build.VERSION.SDK_INT >= 30) {
                OutlinedButton(
                    onClick = {
                        if (PdfSource.hasAllFiles()) onAddPdfTree(downloadTree)
                        else allFiles.launch(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                ) { Text(tr("Lire tout Download", "Read all of Download")) }
                Text(
                    tr("Android interdit de choisir Download en entier. Ce bouton demande « accès à tous les fichiers » : l'appli lit alors Download directement. Rien ne sort du téléphone.", "Android doesn't let apps pick the whole Download folder. This button asks for “All files access”: the app then reads Download directly. Nothing leaves the phone."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            devices()

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            Text(
                tr("Confidentialité", "Privacy"),
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
                "Trieur $version · " + tr("gratuit, sans pub, sans compte", "free, no ads, no account"),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** Affirmations vérifiables dans le manifeste et le code. */
val PRIVACY_POINTS: List<String> get() = listOf(
    tr("Tout est analysé sur ton téléphone : lecture du texte et reconnaissance des images.",
        "Everything is analyzed on your phone: text reading and image recognition."),
    tr("Rien ne part sur Internet : le réseau ne sert qu'entre tes appareils du même Wi-Fi, appairés avec un code, et les échanges sont chiffrés. L'envoi de statistiques de ML Kit à Google est coupé.",
        "Nothing goes to the internet: the network is only used between your own devices on the same Wi-Fi, paired with a code, and the exchanges are encrypted. ML Kit's usage statistics to Google are turned off."),
    tr("Elle ne modifie jamais tes images. La corbeille passe par la confirmation d'Android et reste récupérable 30 jours.",
        "It never modifies your images. Trash goes through Android's confirmation and stays recoverable for 30 days."),
)
