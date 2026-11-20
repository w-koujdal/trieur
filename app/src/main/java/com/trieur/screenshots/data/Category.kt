package com.trieur.screenshots.data

/** Catégories, dans l'ordre d'affichage des filtres. Stockées en base par leur [name]. */
enum class Category(val label: String) {
    PURCHASE("Achats"),
    TRAVEL("Voyages"),
    CODE("Codes"),
    PLACE("Lieux"),
    CONVERSATION("Conversations"),
    SOCIAL("Réseaux sociaux"),
    WEB("Web"),
    NO_TEXT("Sans texte"),
    OTHER("Autres");

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
