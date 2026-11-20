package com.trieur.screenshots.data

/** D'où vient une image, d'après son dossier. Stockée en base par son [name]. */
enum class Source(val label: String) {
    SCREENSHOT("Captures"),
    DOWNLOAD("Téléchargements"),
    /** Tout le reste : appareil photo, images reçues ou enregistrées depuis les applis. */
    GALLERY("Galerie"),

    /** PDF des dossiers choisis dans les réglages (pas une source du MediaStore). */
    PDF("PDF"),

    /** Autres documents de ces dossiers (Word, texte, tableurs…) : nom et texte quand il se lit. */
    DOC("Documents");

    /** Fichier lu dans un dossier choisi (et non une image du MediaStore). */
    val isFile get() = this == PDF || this == DOC

    companion object {
        val DEFAULT_ENABLED = setOf(SCREENSHOT, DOWNLOAD)

        /** Sources d'images, activables une à une (les PDF dépendent des dossiers choisis). */
        val MEDIA = listOf(SCREENSHOT, DOWNLOAD, GALLERY)

        fun fromName(name: String?): Source = entries.firstOrNull { it.name == name } ?: SCREENSHOT

        /** [relativePath] = RELATIVE_PATH du MediaStore, ex. « DCIM/Screenshots/ », « Download/ ». */
        fun fromPath(relativePath: String?): Source {
            val p = relativePath.orEmpty()
            return when {
                p.contains("screenshot", ignoreCase = true) -> SCREENSHOT
                p.startsWith("Download/", ignoreCase = true) -> DOWNLOAD
                else -> GALLERY
            }
        }
    }
}
