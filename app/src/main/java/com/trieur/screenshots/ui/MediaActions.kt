package com.trieur.screenshots.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import com.trieur.screenshots.data.Detected

fun openImage(context: Context, uri: String) {
    start(context, Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "image/*"), "afficher l'image")
}

fun shareImages(context: Context, uris: List<String>) {
    if (uris.isEmpty()) return
    val list = ArrayList(uris.map(Uri::parse))
    val intent = if (list.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, list[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
    }.setType("image/*")
    // Le sélecteur transmet les images aux applis choisies avec le droit de lecture.
    start(context, Intent.createChooser(intent, null), "partager")
}

/**
 * Demande système de mise à la corbeille (une seule confirmation pour toutes), Android 11+.
 * Corbeille plutôt que suppression définitive : récupérable pendant 30 jours. Les captures
 * à la corbeille n'apparaissent plus dans le MediaStore, donc le prochain scan les oublie aussi.
 */
fun deleteRequest(context: Context, uris: List<String>): IntentSender? =
    if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
        MediaStore.createTrashRequest(context.contentResolver, uris.map(Uri::parse), true).intentSender
    } else {
        null
    }

val canDelete: Boolean get() = Build.VERSION.SDK_INT >= 30

fun openDetected(context: Context, item: Detected) {
    when (item) {
        is Detected.Link -> start(context, Intent(Intent.ACTION_VIEW, Uri.parse(item.url)), "ouvrir ce lien")
        is Detected.Email -> start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${item.label}")), "écrire un e-mail")
        is Detected.Phone -> start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.dial}")), "appeler")
        is Detected.Code -> {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("Code", item.label.filter(Char::isDigit)))
            // Android 13+ affiche déjà sa propre confirmation.
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Code copié", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun start(context: Context, intent: Intent, what: String) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune appli pour $what", Toast.LENGTH_SHORT).show()
    }
}
