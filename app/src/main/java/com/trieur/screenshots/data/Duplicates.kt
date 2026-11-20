package com.trieur.screenshots.data

/**
 * Doublons : deux captures sont considérées identiques si leurs empreintes visuelles sont très
 * proches ET si leurs textes se ressemblent. Le texte évite les faux positifs entre deux écrans
 * d'une même appli qui se ressemblent en miniature, comme deux pages d'une conversation sombre.
 */
object Duplicates {
    /** Bits différents tolérés sur 64 (heure, batterie, légère animation). */
    const val MAX_HASH_DISTANCE = 6

    /**
     * Sans texte des deux côtés, seule l'image compte : seuil plus strict, sinon deux photos
     * différentes mais semblables en miniature (scènes sombres, ciel) passeraient pour des doublons.
     */
    const val MAX_HASH_DISTANCE_NO_TEXT = 2
    const val MIN_TEXT_SIMILARITY = 0.8

    /** dHash sur une image 9×8 en niveaux de gris (ligne par ligne) : 1 si un pixel est plus clair que son voisin de droite. */
    fun dHash(gray9x8: IntArray): Long {
        require(gray9x8.size == 72)
        var bits = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (gray9x8[y * 9 + x] > gray9x8[y * 9 + x + 1]) bits = bits or (1L shl bit)
            bit++
        }
        return bits
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Indice de Jaccard sur les mots (sans casse ni accents). Deux textes vides sont identiques. */
    fun textSimilarity(a: String, b: String): Double {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() && wb.isEmpty()) return 1.0
        if (wa.isEmpty() || wb.isEmpty()) return 0.0
        return (wa intersect wb).size.toDouble() / (wa union wb).size
    }

    private fun words(s: String): Set<String> =
        // Lettres isolées ignorées (bruit d'OCR), mais pas les chiffres : « 1 » et « 2 » doivent différer.
        foldText(s).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 || it.any(Char::isDigit) }.toSet()

    fun isDuplicate(a: Screenshot, b: Screenshot): Boolean {
        val d = distance(a.dhash ?: return false, b.dhash ?: return false)
        if (d > MAX_HASH_DISTANCE) return false
        val noText = words(a.text).isEmpty() && words(b.text).isEmpty()
        return if (noText) d <= MAX_HASH_DISTANCE_NO_TEXT else textSimilarity(a.text, b.text) >= MIN_TEXT_SIMILARITY
    }

    /** Groupes de doublons (2 captures ou plus), chaque groupe trié du plus récent au plus ancien. */
    fun groups(items: List<Screenshot>): List<List<Screenshot>> {
        val hashed = items.filter { it.dhash != null }
        val parent = IntArray(hashed.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            return r
        }
        for (i in hashed.indices) for (j in i + 1 until hashed.size) {
            val a = hashed[i]
            val b = hashed[j]
            if (isDuplicate(a, b)) parent[find(i)] = find(j)
        }
        return hashed.indices.groupBy(::find).values
            .filter { it.size > 1 }
            .map { idx -> idx.map { hashed[it] }.sortedByDescending { it.dateTaken } }
            .sortedByDescending { it.first().dateTaken }
    }
}
