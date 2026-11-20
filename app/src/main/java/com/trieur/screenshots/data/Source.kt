package com.trieur.screenshots.data

/** D'où vient une image, d'après son dossier. Stockée en base par son [name]. */
enum class Source(val label: String) {
    SCREENSHOT("Captures"),
    DOWNLOAD("Téléchargements"),
    /** Tout le reste : appareil photo, images reçues ou enregistrées depuis les applis. */
    GALLERY("Galerie");

    companion object {
        val DEFAULT_ENABLED = setOf(SCREENSHOT, DOWNLOAD)

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
