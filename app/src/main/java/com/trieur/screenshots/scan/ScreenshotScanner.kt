package com.trieur.screenshots.scan

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trieur.screenshots.data.Classifier
import com.trieur.screenshots.data.Labels
import com.trieur.screenshots.data.Screenshot
import com.trieur.screenshots.data.ScreenshotDao
import com.trieur.screenshots.data.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Parcourt les images du MediaStore des sources activées (captures, téléchargements, galerie)
 * et OCRise hors ligne celles pas encore indexées. Désactiver une source retire ses images de l'index.
 */
class ScreenshotScanner(
    private val context: Context,
    private val dao: ScreenshotDao,
    private val enabledSources: () -> Set<Source>,
    /** Dossiers de PDF choisis par l'utilisateur (URI d'arborescence du sélecteur d'Android). */
    private val pdfTrees: () -> Set<String> = { emptySet() },
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(Labels.KEEP_MIN_CONFIDENCE).build()
    )

    private data class MediaItem(
        val id: Long,
        val uri: Uri,
        val name: String,
        val dateTaken: Long,
        val folder: String?,
        val source: Source,
        /** DATE_MODIFIED (secondes) : change quand l'image est retouchée sur place. */
        val modified: Long,
    )

    // L'appli et la tâche d'arrière-plan partagent ce scanner : un seul scan à la fois.
    private val mutex = Mutex()

    /** Renvoie le nombre de nouvelles images indexées. */
    suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): Int = mutex.withLock {
        scanLocked(onProgress)
    }

    private suspend fun scanLocked(onProgress: (done: Int, total: Int) -> Unit): Int = withContext(Dispatchers.IO) {
        // Sans permission, le MediaStore renvoie une liste vide : on croirait toutes les images
        // supprimées et on viderait l'index. On ne touche à rien tant que l'accès n'est pas rendu.
        if (!hasMediaAccess(context)) return@withContext 0

        val sources = enabledSources()
        val onDevice = queryImages().filter { it.source in sources }

        // PDF : un dossier illisible (accès retiré, carte SD absente) ne doit pas vider ses entrées.
        val pdfs = mutableListOf<PdfSource.PdfFile>()
        val unreadable = mutableListOf<String>()
        for (tree in pdfTrees()) {
            try {
                pdfs += PdfSource.list(context, Uri.parse(tree))
            } catch (e: Exception) {
                Log.w(TAG, "Dossier PDF illisible : $tree", e)
                unreadable += tree
            }
        }
        val indexed = dao.indexedIds().toSet()

        // Fichiers supprimés, ou d'une source / d'un dossier retirés : on les sort de l'index.
        val present = HashSet<Long>().apply {
            onDevice.mapTo(this) { it.id }
            pdfs.mapTo(this) { it.id }
            addAll(dao.idsInFolders(unreadable))
        }
        // Accès limité (« photos choisies », Android 14+) : les images non choisies sont absentes
        // de la liste sans avoir été supprimées. On ne retire alors aucune image, seulement des PDF.
        val fullAccess = hasFullMediaAccess(context)
        val gone = (indexed - present).filter { fullAccess || it < 0 }
        gone.chunked(500).forEach { dao.delete(it) }
        gone.filter { it < 0 }.forEach { PdfSource.thumbnailFile(context, it).delete() }

        // Images retouchées sur place (même identifiant, date de modification différente) : à relire.
        // Avant la v6 la date n'était pas connue (0) : on l'enregistre sans relire.
        val known = dao.modifiedInfo().associateBy { it.mediaId }
        val changed = mutableListOf<MediaItem>()
        for (item in onDevice) {
            val k = known[item.id] ?: continue
            when {
                k.modified == 0L -> dao.setModified(item.id, item.modified)
                k.modified != item.modified -> changed += item
            }
        }
        // PDF modifié : on le retire puis on le relit comme un nouveau.
        val pdfChanged = pdfs.filter { p -> known[p.id]?.let { it.dateTaken != p.modified } == true }
        pdfChanged.forEach { dao.delete(listOf(it.id)) }
        val pdfChangedIds = pdfChanged.mapTo(HashSet()) { it.id }

        // Du plus récent au plus ancien : ce qui compte le plus est cherchable en premier.
        val todo = onDevice.filter { it.id !in indexed }
        val pdfTodo = pdfs.filter { it.id !in indexed || it.id in pdfChangedIds }
        val total = todo.size + changed.size + pdfTodo.size
        onProgress(0, total)
        todo.forEachIndexed { i, item ->
            onProgress(i + 1, total)
            // Échec (fichier en cours d'écriture, illisible) : rien n'est enregistré, l'image sera
            // reprise au prochain scan au lieu de rester « sans texte » (et proposée à la corbeille).
            val (text, labels) = analyze(item) ?: return@forEachIndexed
            val sourceApp = Classifier.sourceApp(item.name, item.folder)
            dao.insert(
                Screenshot(
                    mediaId = item.id,
                    uri = item.uri.toString(),
                    displayName = item.name,
                    dateTaken = item.dateTaken,
                    text = text,
                    sourceApp = sourceApp,
                    category = Classifier.classify(text, sourceApp, item.source, labels.orEmpty()).name,
                    dhash = ImageHash.compute(context, item.uri),
                    source = item.source.name,
                    folder = item.folder,
                    labels = labels?.let(Labels::encode),
                    tags = labels?.let(Labels::searchTags),
                    modified = item.modified,
                )
            )
        }

        changed.forEachIndexed { i, item ->
            onProgress(todo.size + i + 1, total)
            val (text, labels) = analyze(item) ?: return@forEachIndexed
            val sourceApp = Classifier.sourceApp(item.name, item.folder)
            dao.updateContent(
                mediaId = item.id,
                text = text,
                labels = labels?.let(Labels::encode),
                tags = labels?.let(Labels::searchTags),
                dhash = ImageHash.compute(context, item.uri),
                category = Classifier.classify(text, sourceApp, item.source, labels.orEmpty()).name,
                modified = item.modified,
            )
        }

        pdfTodo.forEachIndexed { i, pdf ->
            if (pdf.isPdf) indexPdf(pdf) else indexDoc(pdf)
            onProgress(todo.size + changed.size + i + 1, total)
        }

        // Rattrapage des empreintes (images indexées avant leur introduction, ou échec précédent).
        dao.missingHashes().forEach { h ->
            ImageHash.compute(context, Uri.parse(h.uri))?.let { dao.setHash(h.mediaId, it) }
        }

        // Rattrapage de l'étiquetage (photos indexées avant son introduction) : image réduite, ~50 ms.
        dao.missingLabels().forEach { m ->
            val labels = try {
                val bitmap = loadScaled(Uri.parse(m.uri), MAX_LABEL_SIDE)
                try { label(bitmap) } finally { bitmap.recycle() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Étiquetage impossible pour ${m.mediaId}", e)
                emptyList()
            }
            val row = dao.get(m.mediaId) ?: return@forEach
            val category = Classifier.classify(row.text, row.sourceApp, Source.fromName(row.source), labels)
            dao.setLabels(m.mediaId, Labels.encode(labels), Labels.searchTags(labels), category.name)
        }
        total
    }

    /** PDF : OCR des premières pages, vignette de la première, empreinte pour les doublons. */
    private suspend fun indexPdf(pdf: PdfSource.PdfFile) {
        val text = StringBuilder()
        var hash: Long? = null
        try {
            PdfSource.renderPages(context, pdf.uri) { index, page ->
                if (index == 0) {
                    PdfSource.saveThumbnail(context, pdf.id, page)
                    hash = ImageHash.ofBitmap(page, cropBars = false)
                }
                text.appendLine(recognizer.process(InputImage.fromBitmap(page, 0)).await().text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Protégé par mot de passe, abîmé, trop gros : rien n'est enregistré, nouvel essai au prochain scan.
            Log.w(TAG, "PDF illisible : ${pdf.name}", e)
            return
        }
        val content = text.toString().trim()
        dao.insert(
            Screenshot(
                mediaId = pdf.id,
                uri = pdf.uri.toString(),
                displayName = pdf.name,
                dateTaken = pdf.modified,
                text = content,
                sourceApp = null,
                category = Classifier.classify(content, null, Source.PDF).name,
                dhash = hash,
                source = Source.PDF.name,
                folder = pdf.tree,
                labels = "",
                tags = "pdf document",
                path = pdf.path,
            )
        )
    }

    /** Autre document : texte lu quand le format le permet (Word, texte, tableur…), sinon le nom seul. */
    private fun indexDocText(doc: PdfSource.PdfFile): String =
        if (!DocText.canRead(doc.name)) ""
        else try {
            DocText.extract(context, doc.uri, doc.name)
        } catch (e: Exception) {
            Log.w(TAG, "Document illisible : ${doc.name}", e)
            ""
        }

    private suspend fun indexDoc(doc: PdfSource.PdfFile) {
        val content = indexDocText(doc).trim()
        val category = Classifier.classify(content, null, Source.DOC)
        dao.insert(
            Screenshot(
                mediaId = doc.id,
                uri = doc.uri.toString(),
                displayName = doc.name,
                dateTaken = doc.modified,
                text = content,
                sourceApp = null,
                category = category.name,
                dhash = null,
                source = Source.DOC.name,
                folder = doc.tree,
                labels = "",
                tags = "document " + DocText.extension(doc.name),
                path = doc.path,
            )
        )
    }

    /** Texte (OCR) et, hors captures d'écran, étiquettes ; null si l'image n'a pas pu être lue. */
    private suspend fun analyze(item: MediaItem): Pair<String, List<Labels.Label>?>? = try {
        val bitmap = loadScaled(item.uri, MAX_OCR_SIDE)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
            val labels = if (item.source == Source.SCREENSHOT) null else label(bitmap)
            text to labels
        } finally {
            bitmap.recycle()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) { // y compris OutOfMemoryError : une image ne doit pas faire planter le scan
        Log.w(TAG, "Analyse impossible pour ${item.name}", e)
        null
    }

    private suspend fun label(bitmap: Bitmap): List<Labels.Label> =
        labeler.process(InputImage.fromBitmap(bitmap, 0)).await().map { Labels.Label(it.text, it.confidence) }

    /**
     * Image réduite : une photo de 50 Mpx décodée en entier ferait ~200 Mo en mémoire.
     * ImageDecoder applique l'orientation EXIF, donc le texte d'une photo prise en portrait est droit.
     */
    private fun loadScaled(uri: Uri, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }

    private fun queryImages(): List<MediaItem> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_MODIFIED,
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val folder = c.getString(pathCol)
                val taken = if (c.isNull(takenCol)) c.getLong(addedCol) * 1000 else c.getLong(takenCol)
                result += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    name = c.getString(nameCol) ?: "image_$id",
                    dateTaken = taken,
                    folder = folder,
                    source = Source.fromPath(folder),
                    modified = c.getLong(modCol),
                )
            }
        }
        return result
    }

    private companion object {
        const val TAG = "ScreenshotScanner"
        const val MAX_OCR_SIDE = 2560
        const val MAX_LABEL_SIDE = 640
    }
}
