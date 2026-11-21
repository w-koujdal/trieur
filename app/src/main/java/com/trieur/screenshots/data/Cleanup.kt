package com.trieur.screenshots.data

import com.trieur.screenshots.plural

import com.trieur.screenshots.tr

import java.util.concurrent.TimeUnit

/** Lot de captures proposé au nettoyage. [ids] = captures à présélectionner. */
data class Suggestion(val key: String, val title: String, val detail: String, val ids: Set<Long>)

private val DAY = TimeUnit.DAYS.toMillis(1)

/** Nom donné par [Classifier.sourceApp] au dossier DCIM/Camera. */
const val CAMERA_APP = "Appareil photo"

/** Nom d'appli à afficher : [CAMERA_APP] est stocké en français, traduit à l'affichage. */
fun appLabel(app: String): String = if (app == CAMERA_APP) tr("Appareil photo", "Camera") else app

/**
 * Propositions de nettoyage, de la plus évidente à la moins évidente :
 * codes de vérification périmés, doublons (on garde le plus récent), vieilles captures par appli,
 * captures sans texte. Les catégories choisies à la main ne sont jamais proposées.
 */
fun cleanupSuggestions(all: List<Screenshot>, now: Long, minPerApp: Int = 5): List<Suggestion> {
    // PDF exclus : Android ne permet pas de les mettre à la corbeille depuis l'appli.
    val candidates = all.filterNot { it.categoryLocked || Source.fromName(it.source).isFile }
    val out = mutableListOf<Suggestion>()

    candidates.filter { it.category == Category.CODE.name && now - it.dateTaken > DAY }
        .takeIf { it.isNotEmpty() }
        ?.let { out += Suggestion("codes", tr("Codes de vérification", "Verification codes"), plural(it.size, "1 code de plus d'un jour, sans doute expiré", "${it.size} codes de plus d'un jour, sans doute expirés", "1 code older than a day, probably expired", "${it.size} codes older than a day, probably expired"), it.ids()) }

    val extraCopies = Duplicates.groups(candidates).flatMap { it.drop(1) }
    if (extraCopies.isNotEmpty()) {
        out += Suggestion("doublons", tr("Doublons", "Duplicates"), plural(extraCopies.size, "1 copie en trop, la plus récente est gardée", "${extraCopies.size} copies en trop, la plus récente est gardée", "1 extra copy, the most recent one is kept", "${extraCopies.size} extra copies, the most recent one is kept"), extraCopies.ids())
    }

    // Jamais les photos de l'appareil photo : ce sont des souvenirs, pas du désordre.
    candidates.filter { it.sourceApp != null && it.sourceApp != CAMERA_APP && now - it.dateTaken > 90 * DAY }
        .groupBy { it.sourceApp!! }
        .filterValues { it.size >= minPerApp }
        .entries.sortedByDescending { it.value.size }
        .forEach { (app, shots) ->
            out += Suggestion("vieux:$app", tr("$app : plus de 3 mois", "$app: older than 3 months"), plural(shots.size, "1 capture ancienne", "${shots.size} captures anciennes", "1 old screenshot", "${shots.size} old screenshots"), shots.ids())
        }

    candidates.filter { it.category == Category.NO_TEXT.name }
        .takeIf { it.isNotEmpty() }
        ?.let { out += Suggestion("sans-texte", tr("Captures sans texte", "Screenshots without text"), plural(it.size, "1 capture où aucun texte n'a été lu", "${it.size} captures où aucun texte n'a été lu", "1 screenshot with no text found", "${it.size} screenshots with no text found"), it.ids()) }

    return out
}

private fun List<Screenshot>.ids() = mapTo(LinkedHashSet()) { it.mediaId }
