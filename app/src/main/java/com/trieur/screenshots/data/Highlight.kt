package com.trieur.screenshots.data

import java.text.Normalizer

/** Découpe la saisie en mots, comme [toFtsQuery]. */
fun searchTokens(input: String): List<String> =
    input.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

/**
 * Plages (début inclus, fin exclue) des mots de [text] qui commencent par l'un des [tokens],
 * sans tenir compte de la casse ni des accents : même logique que la recherche FTS.
 */
fun findHighlights(text: String, tokens: List<String>): List<IntRange> {
    if (tokens.isEmpty() || text.isEmpty()) return emptyList()
    val folded = fold(text)
    val prefixes = tokens.map(::fold)
    val ranges = mutableListOf<IntRange>()
    var i = 0
    while (i < folded.length) {
        if (!folded[i].isLetterOrDigit()) { i++; continue }
        var end = i
        while (end < folded.length && folded[end].isLetterOrDigit()) end++
        val word = folded.substring(i, end)
        if (prefixes.any { word.startsWith(it) }) ranges += i until end
        i = end
    }
    return ranges
}

/** Minuscules sans accents, caractère par caractère, pour garder les mêmes indices que le texte d'origine. */
private fun fold(s: String): String = buildString(s.length) {
    for (c in s) {
        val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
        append(base.lowercaseChar())
    }
}
