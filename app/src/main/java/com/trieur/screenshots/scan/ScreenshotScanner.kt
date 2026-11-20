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
        val indexed = dao.indexedIds().toSet()

        // Images supprimées du téléphone, ou d'une source désactivée : on les retire de l'index.
        val gone = indexed - onDevice.mapTo(HashSet()) { it.id }
        gone.chunked(500).forEach { dao.delete(it) }

        // Du plus récent au plus ancien : ce qui compte le plus est cherchable en premier.
        val todo = onDevice.filter { it.id !in indexed }
        onProgress(0, todo.size)
        todo.forEachIndexed { i, item ->
            val (text, labels) = analyze(item)
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
                )
            )
            onProgress(i + 1, todo.size)
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
            } catch (e: Exception) {
                Log.w(TAG, "Étiquetage impossible pour ${m.mediaId}", e)
                emptyList()
            }
            val row = dao.get(m.mediaId) ?: return@forEach
            val category = Classifier.classify(row.text, row.sourceApp, Source.fromName(row.source), labels)
            dao.setLabels(m.mediaId, Labels.encode(labels), Labels.searchTags(labels), category.name)
        }
        todo.size
    }

    /** Texte (OCR) et, hors captures d'écran, étiquettes de ce que montre l'image. */
    private suspend fun analyze(item: MediaItem): Pair<String, List<Labels.Label>?> = try {
        val bitmap = loadScaled(item.uri, MAX_OCR_SIDE)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
            val labels = if (item.source == Source.SCREENSHOT) null else label(bitmap)
            text to labels
        } finally {
            bitmap.recycle()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Analyse impossible pour ${item.name}", e)
        "" to if (item.source == Source.SCREENSHOT) null else emptyList()
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
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
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
