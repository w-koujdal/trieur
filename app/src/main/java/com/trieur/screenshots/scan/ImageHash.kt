package com.trieur.screenshots.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.trieur.screenshots.data.Duplicates

/**
 * Empreinte visuelle d'une capture, calculée sur une version réduite de l'image.
 * Les barres du haut et du bas (heure, batterie, navigation) sont ignorées : elles changent
 * d'une capture à l'autre sans que le contenu change.
 */
object ImageHash {
    private const val BAR_FRACTION = 0.06

    fun compute(context: Context, uri: Uri): Long? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Décodage directement en petit (~64 px de large) : rapide et peu gourmand en mémoire.
        val options = BitmapFactory.Options().apply { inSampleSize = (bounds.outWidth / 64).coerceAtLeast(1) }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val top = (decoded.height * BAR_FRACTION).toInt()
        val height = (decoded.height * (1 - 2 * BAR_FRACTION)).toInt().coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(decoded, 0, top, decoded.width, height)
        val tiny = Bitmap.createScaledBitmap(cropped, 9, 8, true)

        val pixels = IntArray(72)
        tiny.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        listOf(decoded, cropped, tiny).distinct().forEach { it.recycle() }

        val gray = IntArray(72) { i ->
            val p = pixels[i]
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }
        Duplicates.dHash(gray)
    }.getOrNull()
}
