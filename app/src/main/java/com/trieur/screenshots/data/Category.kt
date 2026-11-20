package com.trieur.screenshots.data

/** Catégories, dans l'ordre d'affichage des filtres. Stockées en base par leur [name]. */
enum class Category(val label: String) {
    PURCHASE("Achats"),
    TRAVEL("Voyages"),
    CODE("Codes"),
    PLACE("Lieux"),
    DOCUMENT("Documents"),
    CONVERSATION("Conversations"),
    SOCIAL("Réseaux sociaux"),
    WEB("Web"),
    PEOPLE("Personnes"),
    ANIMAL("Animaux"),
    FOOD("Nourriture"),
    NATURE("Nature"),
    PHOTO("Photos"),
    NO_TEXT("Sans texte"),
    OTHER("Autres");

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
