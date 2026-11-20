package com.trieur.screenshots.data

import java.util.concurrent.TimeUnit

/** Lot de captures proposé au nettoyage. [ids] = captures à présélectionner. */
data class Suggestion(val key: String, val title: String, val detail: String, val ids: Set<Long>)

private val DAY = TimeUnit.DAYS.toMillis(1)

/**
 * Propositions de nettoyage, de la plus évidente à la moins évidente :
 * codes de vérification périmés, doublons (on garde le plus récent), vieilles captures par appli,
 * captures sans texte. Les catégories choisies à la main ne sont jamais proposées.
 */
fun cleanupSuggestions(all: List<Screenshot>, now: Long, minPerApp: Int = 5): List<Suggestion> {
    val candidates = all.filterNot { it.categoryLocked }
    val out = mutableListOf<Suggestion>()

    candidates.filter { it.category == Category.CODE.name && now - it.dateTaken > DAY }
        .takeIf { it.isNotEmpty() }
        ?.let { out += Suggestion("codes", "Codes de vérification", "${it.size} code(s) de plus d'un jour, sans doute expirés", it.ids()) }

    val extraCopies = Duplicates.groups(candidates).flatMap { it.drop(1) }
    if (extraCopies.isNotEmpty()) {
        out += Suggestion("doublons", "Doublons", "${extraCopies.size} copie(s) en trop, la plus récente est gardée", extraCopies.ids())
    }

    candidates.filter { it.sourceApp != null && now - it.dateTaken > 90 * DAY }
        .groupBy { it.sourceApp!! }
        .filterValues { it.size >= minPerApp }
        .entries.sortedByDescending { it.value.size }
        .forEach { (app, shots) ->
            out += Suggestion("vieux:$app", "$app : plus de 3 mois", "${shots.size} capture(s) anciennes", shots.ids())
        }

    candidates.filter { it.category == Category.NO_TEXT.name }
        .takeIf { it.isNotEmpty() }
        ?.let { out += Suggestion("sans-texte", "Captures sans texte", "${it.size} capture(s) où aucun texte n'a été lu", it.ids()) }

    return out
}

private fun List<Screenshot>.ids() = mapTo(LinkedHashSet()) { it.mediaId }
