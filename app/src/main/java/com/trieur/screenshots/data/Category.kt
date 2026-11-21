package com.trieur.screenshots.data

import com.trieur.screenshots.tr

/** Catégories, dans l'ordre d'affichage des filtres. Stockées en base par leur [name]. */
enum class Category(private val fr: String, private val en: String) {
    PURCHASE("Achats", "Purchases"),
    TRAVEL("Voyages", "Travel"),
    CODE("Codes", "Codes"),
    PLACE("Lieux", "Places"),
    DOCUMENT("Documents", "Documents"),
    CONVERSATION("Conversations", "Conversations"),
    SOCIAL("Réseaux sociaux", "Social media"),
    WEB("Web", "Web"),
    PEOPLE("Personnes", "People"),
    ANIMAL("Animaux", "Animals"),
    FOOD("Nourriture", "Food"),
    NATURE("Nature", "Nature"),
    PHOTO("Photos", "Photos"),
    NO_TEXT("Sans texte", "No text"),
    OTHER("Autres", "Other");

    val label: String get() = tr(fr, en)

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
