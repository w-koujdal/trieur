package com.trieur.screenshots.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Permissions à demander pour lire les images, selon la version d'Android. */
val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Au moins un accès, même limité aux photos choisies (Android 14+). */
fun hasMediaAccess(context: Context): Boolean = mediaPermissions.any { granted(context, it) }

/**
 * Accès à toutes les images. Avec l'accès limité, le MediaStore ne renvoie que les photos
 * choisies : une image absente de la liste n'est pas forcément supprimée.
 */
fun hasFullMediaAccess(context: Context): Boolean = granted(
    context,
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE,
)

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
