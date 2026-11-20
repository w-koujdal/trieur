package com.trieur.screenshots.scan

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trieur.screenshots.data.Classifier
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
            val text = recognize(item)
            val sourceApp = Classifier.sourceApp(item.name, item.folder)
            dao.insert(
                Screenshot(
                    mediaId = item.id,
                    uri = item.uri.toString(),
                    displayName = item.name,
                    dateTaken = item.dateTaken,
                    text = text,
                    sourceApp = sourceApp,
                    category = Classifier.classify(text, sourceApp, item.source).name,
                    dhash = ImageHash.compute(context, item.uri),
                    source = item.source.name,
                    folder = item.folder,
                )
            )
            onProgress(i + 1, todo.size)
        }

        // Rattrapage des empreintes (images indexées avant leur introduction, ou échec précédent).
        dao.missingHashes().forEach { h ->
            ImageHash.compute(context, Uri.parse(h.uri))?.let { dao.setHash(h.mediaId, it) }
        }
        todo.size
    }

    private suspend fun recognize(item: MediaItem): String = try {
        val bitmap = loadForOcr(item.uri)
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } finally {
            bitmap.recycle()
        }
    } catch (e: Exception) {
        Log.w(TAG, "OCR impossible pour ${item.name}", e)
        ""
    }

    /**
     * Image réduite pour l'OCR : une photo de 50 Mpx décodée en entier ferait ~200 Mo en mémoire.
     * ImageDecoder applique l'orientation EXIF, donc le texte d'une photo prise en portrait est droit.
     */
    private fun loadForOcr(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_OCR_SIDE) {
                val scale = MAX_OCR_SIDE.toFloat() / longest
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
    }
}
