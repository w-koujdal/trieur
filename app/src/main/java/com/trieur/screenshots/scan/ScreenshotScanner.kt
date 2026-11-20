package com.trieur.screenshots.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trieur.screenshots.data.Classifier
import com.trieur.screenshots.data.Screenshot
import com.trieur.screenshots.data.ScreenshotDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Parcourt les captures du MediaStore et OCRise (hors ligne) celles pas encore indexées. */
class ScreenshotScanner(
    private val context: Context,
    private val dao: ScreenshotDao,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private data class MediaItem(val id: Long, val uri: Uri, val name: String, val dateTaken: Long)

    // L'appli et le worker d'arrière-plan partagent ce scanner : un seul scan à la fois.
    private val mutex = Mutex()

    /** Renvoie le nombre de nouvelles captures indexées. */
    suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): Int = mutex.withLock {
        scanLocked(onProgress)
    }

    private suspend fun scanLocked(onProgress: (done: Int, total: Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val onDevice = queryScreenshots()
        val indexed = dao.indexedIds().toSet()

        // Captures supprimées du téléphone : on les retire de l'index.
        val gone = indexed - onDevice.mapTo(HashSet()) { it.id }
        gone.chunked(500).forEach { dao.delete(it) }

        val todo = onDevice.filter { it.id !in indexed }
        onProgress(0, todo.size)
        todo.forEachIndexed { i, item ->
            val text = try {
                recognizer.process(InputImage.fromFilePath(context, item.uri)).await().text
            } catch (e: Exception) {
                Log.w(TAG, "OCR impossible pour ${item.name}", e)
                ""
            }
            val sourceApp = Classifier.sourceApp(item.name)
            dao.insert(
                Screenshot(
                    mediaId = item.id,
                    uri = item.uri.toString(),
                    displayName = item.name,
                    dateTaken = item.dateTaken,
                    text = text,
                    sourceApp = sourceApp,
                    category = Classifier.classify(text, sourceApp).name,
                    dhash = ImageHash.compute(context, item.uri),
                )
            )
            onProgress(i + 1, todo.size)
        }

        // Rattrapage des empreintes (captures indexées avant leur introduction, ou échec précédent).
        // Rapide (image réduite) : quelques ms par capture, sans OCR.
        dao.missingHashes().forEach { h ->
            ImageHash.compute(context, Uri.parse(h.uri))?.let { dao.setHash(h.mediaId, it) }
        }
        todo.size
    }

    private fun queryScreenshots(): List<MediaItem> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        // Samsung : DCIM/Screenshots/ ; la plupart des autres : Pictures/Screenshots/
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%Screenshot%")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = if (c.isNull(takenCol)) c.getLong(addedCol) * 1000 else c.getLong(takenCol)
                result += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    name = c.getString(nameCol) ?: "capture_$id",
                    dateTaken = taken,
                )
            }
        }
        return result
    }

    private companion object {
        const val TAG = "ScreenshotScanner"
    }
}
