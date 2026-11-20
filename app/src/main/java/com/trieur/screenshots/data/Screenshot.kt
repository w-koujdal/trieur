package com.trieur.screenshots.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/** Une capture d'écran indexée. [mediaId] = identifiant MediaStore (sert aussi de rowid). */
@Entity(tableName = "screenshots")
data class Screenshot(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val displayName: String,
    val dateTaken: Long,
    val text: String,
    /** Appli d'origine lue dans le nom du fichier (Samsung), null si inconnue. */
    val sourceApp: String? = null,
    /** Nom d'une [Category]. */
    @ColumnInfo(defaultValue = "OTHER") val category: String = Category.OTHER.name,
)

/**
 * Index plein texte adossé à [Screenshot] : Room crée les triggers qui le tiennent à jour.
 * unicode61 ignore les accents ("ecole" trouve "École").
 */
@Fts4(contentEntity = Screenshot::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "screenshots_fts")
data class ScreenshotFts(val text: String)
