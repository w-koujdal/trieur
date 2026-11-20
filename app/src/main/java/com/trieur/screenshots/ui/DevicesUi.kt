package com.trieur.screenshots.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trieur.screenshots.lan.Found
import com.trieur.screenshots.lan.Lan
import com.trieur.screenshots.lan.Peer
import com.trieur.screenshots.lan.RemoteHit
import kotlinx.coroutines.launch
import java.io.File

private fun icon(kind: String) = when (kind) {
    "pdf" -> "📕"; "doc" -> "📄"; "sheet" -> "📊"; "slides" -> "📽️"; "image" -> "🖼️"; "screenshot" -> "📱"
    "video" -> "🎬"; "audio" -> "🎵"; "archive" -> "🗜️"; else -> "📁"
}

private fun deviceIcon(kind: String) = if (kind == "pc") "💻" else "📱"

/** Résultats trouvés chez les autres appareils, au-dessus des résultats du téléphone. */
@Composable
fun RemoteResults(state: MainViewModel.RemoteState, vm: MainViewModel) {
    if (!state.loading && state.groups.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Pair<Peer, RemoteHit>?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 8.dp).width(16.dp).height(16.dp), strokeWidth = 2.dp)
                Text("Recherche dans tes autres appareils…", style = MaterialTheme.typography.bodySmall)
            }
        }
        for (g in state.groups) {
            Text(
                "${deviceIcon(g.peer.kind)} ${g.peer.name}" + if (g.error == null) " · ${g.hits.size} résultat${if (g.hits.size > 1) "s" else ""}" else "",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            if (g.error != null) {
                Text("⚠️ ${g.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                continue
            }
            for (h in g.hits.take(8)) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { picked = g.peer to h },
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(icon(h.kind), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("📍 ${h.location}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (h.snippet.isNotBlank()) Text(highlight(h.snippet), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (busy == h.id) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (g.hits.size > 8) Text("… et ${g.hits.size - 8} autres : précise la recherche", style = MaterialTheme.typography.bodySmall)
        }
    }

    picked?.let { (peer, hit) ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(hit.name) },
            text = { Text("Sur ${peer.name} :\n${hit.location}") },
            confirmButton = {
                TextButton(onClick = {
                    picked = null
                    busy = hit.id
                    scope.launch {
                        vm.fetchRemote(peer, hit).onSuccess { openLocal(context, it) }
                            .onFailure { Toast.makeText(context, "Transfert impossible : ${it.message}", Toast.LENGTH_LONG).show() }
                        busy = null
                    }
                }) { Text("Ouvrir ici") }
            },
            dismissButton = {
                TextButton(onClick = {
                    picked = null
                    busy = hit.id
                    scope.launch {
                        vm.fetchRemote(peer, hit).onSuccess { saveToDownloads(context, it) }
                            .onFailure { Toast.makeText(context, "Transfert impossible : ${it.message}", Toast.LENGTH_LONG).show() }
                        busy = null
                    }
                }) { Text("Enregistrer dans Téléchargements") }
            },
        )
    }
}

private fun highlight(snippet: String) = buildAnnotatedString {
    var bold = false
    for (part in snippet.split('[', ']')) {
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
        bold = !bold
    }
}

private fun mimeOf(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

private fun openLocal(context: Context, f: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(i, f.name)) }
        .onFailure { Toast.makeText(context, "Aucune appli pour ouvrir ce fichier", Toast.LENGTH_LONG).show() }
}

/** Copie dans Téléchargements/Trieur (visible dans Mes fichiers). */
private fun saveToDownloads(context: Context, f: File) {
    if (Build.VERSION.SDK_INT < 29) return
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, f.name)
        put(MediaStore.Downloads.MIME_TYPE, mimeOf(f))
        put(MediaStore.Downloads.RELATIVE_PATH, "Download/Trieur")
    }
    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        Toast.makeText(context, "Enregistrement impossible", Toast.LENGTH_LONG).show()
        return
    }
    context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
    Toast.makeText(context, "Enregistré dans Téléchargements › Trieur", Toast.LENGTH_LONG).show()
}

/** Réglages « Mes appareils » : visibilité, code, appareils appairés, ajout d'un PC. */
@Composable
fun DevicesSection(vm: MainViewModel) {
    val peers by vm.peers.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text("Mes appareils (même Wi-Fi)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Cherche aussi dans ton PC (avec Trieur PC) et laisse ton PC chercher ici. Tout passe par ton réseau " +
                "local, jamais par Internet, et seulement entre appareils appairés avec un code.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        for (p in peers) {
            ListItem(
                headlineContent = { Text("${deviceIcon(p.kind)} ${p.name}") },
                supportingContent = { Text(p.host ?: "", style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    IconButton(onClick = { vm.unpair(p.id) }) { Icon(Icons.Filled.Close, contentDescription = "Oublier ${p.name}") }
                },
            )
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.padding(vertical = 4.dp)) { Text("Ajouter un PC") }
        Row(
            Modifier.fillMaxWidth().toggleable(value = visible, role = Role.Switch, onValueChange = vm::setVisible).padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Visible sur le Wi-Fi", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (visible) "Ton PC peut chercher ici. Code pour l'appairer : ${vm.pairCode().chunked(3).joinToString(" ")}" +
                        Lan.localAddresses().firstOrNull()?.let { "\nAdresse : $it" }.orEmpty()
                    else "Pour chercher dans ce téléphone depuis ton PC.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = visible, onCheckedChange = null)
        }
    }
    if (adding) PairDialog(vm) { adding = false }
}

/** Ajout d'un PC : ceux trouvés sur le Wi-Fi, ou adresse saisie à la main ; puis le code affiché sur le PC. */
@Composable
private fun PairDialog(vm: MainViewModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<Found>?>(null) }
    var target by remember { mutableStateOf<Found?>(null) }
    var ip by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { found = vm.discover() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (target == null) "Ajouter un PC" else "Code affiché sur ${target!!.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val t = target
                if (t == null) {
                    when {
                        found == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Recherche sur le Wi-Fi…")
                        }
                        found!!.isEmpty() -> Text(
                            "Aucun PC trouvé. Lance Trieur PC sur l'ordinateur (même Wi-Fi), ou saisis l'adresse qu'il affiche.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        else -> found!!.forEach { f ->
                            ListItem(headlineContent = { Text("${deviceIcon(f.kind)} ${f.name}") }, modifier = Modifier.clickable { target = f })
                        }
                    }
                    OutlinedTextField(
                        value = ip, onValueChange = { ip = it }, singleLine = true,
                        label = { Text("Ou adresse du PC (ex. 192.168.1.20)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                } else {
                    Text("Dans Trieur PC, le code est affiché dans « Appairer un téléphone ».", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                        label = { Text("Code à 6 chiffres") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
                error?.let { Text("⚠️ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working && (if (target == null) ip.isNotBlank() else code.length == 6),
                onClick = {
                    error = null
                    working = true
                    scope.launch {
                        val t = target
                        if (t == null) {
                            runCatching { vm.probe(ip) }.onSuccess { target = it }.onFailure { error = "Pas de Trieur PC à cette adresse" }
                        } else {
                            vm.pair(t, code).onSuccess { onClose() }.onFailure { error = it.message ?: "Échec" }
                        }
                        working = false
                    }
                },
            ) { Text(if (target == null) "Se connecter" else "Appairer") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Annuler") } },
    )
}
