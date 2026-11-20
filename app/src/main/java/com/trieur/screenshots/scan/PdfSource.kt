package com.trieur.screenshots.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.security.MessageDigest

/**
 * PDF des dossiers choisis par l'utilisateur (sélecteur de dossier d'Android, accès dossier par
 * dossier). La permission « photos » ne donne pas accès aux PDF : c'est la seule voie sans
 * permission étendue.
 */
object PdfSource {
    /** Pages lues au plus : l'essentiel d'un document est au début, et l'OCR coûte ~0,3 s par page. */
    const val MAX_PAGES = 5
    const val RENDER_WIDTH = 1600
    const val MAX_RENDER_HEIGHT = 4096
    private const val THUMB_WIDTH = 540

    data class PdfFile(
        val id: Long,
        val uri: Uri,
        val name: String,
        val modified: Long,
        val tree: String,
        val mime: String?,
        /** Chemin lisible : « Download/Candidatures/CV.pdf ». */
        val path: String,
        val size: Long,
    ) {
        val isPdf get() = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
    }

    /** « primary:Download/CV.pdf » → « Download/CV.pdf » ; autres stockages : identifiant tel quel. */
    fun readablePath(documentId: String): String =
        documentId.substringAfter(':', documentId).trim('/')

    /**
     * Identifiant stable et négatif (les identifiants du MediaStore sont positifs) tiré de l'URI.
     * Il sert de clé primaire et de rowid de l'index plein texte.
     */
    fun stableId(uri: String): Long {
        val bytes = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (bytes[i].toLong() and 0xFF)
        return -((v and Long.MAX_VALUE).coerceAtLeast(1))
    }

    /**
     * Tous les documents d'un dossier et de ses sous-dossiers : PDF, Word, texte… Les images,
     * vidéos et musiques sont laissées au MediaStore (sources Captures, Galerie). Les fichiers
     * cachés (« .nomedia ») sont ignorés. Lève une exception si l'accès a été retiré.
     */
    fun list(context: Context, tree: Uri): List<PdfFile> {
        val out = mutableListOf<PdfFile>()
        val pending = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(tree)) }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        while (pending.isNotEmpty()) {
            val parent = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            context.contentResolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    when {
                        name.startsWith(".") -> Unit
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> pending.add(docId)
                        mime != null && (mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")) -> Unit
                        else -> {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                            out += PdfFile(stableId(uri.toString()), uri, name, c.getLong(3), tree.toString(), mime, readablePath(docId), c.getLong(4))
                        }
                    }
                }
            }
        }
        return out
    }

    /** Pages rendues en images (fond blanc), pour l'OCR. [onPage] reçoit chaque page puis la libère. */
    // inline : l'OCR (suspendu) peut être appelé directement pour chaque page.
    inline fun renderPages(context: Context, uri: Uri, onPage: (index: Int, Bitmap) -> Unit) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { pdf ->
                for (i in 0 until minOf(pdf.pageCount, MAX_PAGES)) {
                    pdf.openPage(i).use { page ->
                        // Hauteur plafonnée : une page très haute (ticket, page web exportée) ferait
                        // une image de plusieurs centaines de Mo. On réduit alors la largeur aussi.
                        val ratio = page.height.toFloat() / page.width.coerceAtLeast(1)
                        val width = minOf(RENDER_WIDTH.toFloat(), MAX_RENDER_HEIGHT / ratio).toInt().coerceAtLeast(1)
                        val height = (width * ratio).toInt().coerceIn(1, MAX_RENDER_HEIGHT)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        try {
                            onPage(i, bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    /** Fichier de vignette (première page) dans le stockage privé de l'appli. */
    fun thumbnailFile(context: Context, id: Long): File = File(context.filesDir, "thumbs/pdf_${-id}.jpg")

    fun saveThumbnail(context: Context, id: Long, firstPage: Bitmap) {
        val file = thumbnailFile(context, id).apply { parentFile?.mkdirs() }
        val height = (THUMB_WIDTH.toFloat() * firstPage.height / firstPage.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(firstPage, THUMB_WIDTH, height, true)
        file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (small !== firstPage) small.recycle()
    }
}
