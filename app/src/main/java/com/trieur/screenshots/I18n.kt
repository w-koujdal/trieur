package com.trieur.screenshots

import java.util.Locale

/**
 * Langue de l'appli : français si le téléphone est en français, anglais sinon.
 * Les textes sont écrits côte à côte (`tr("Ouvrir", "Open")`) : un seul endroit à lire et à
 * corriger, et utilisable partout (interface, notifications, serveur Wi-Fi).
 */
val isFrench: Boolean get() = Locale.getDefault().language == "fr"

fun tr(fr: String, en: String): String = if (isFrench) fr else en

/** « 1 image » / « 3 images » : [one] pour 0 et 1 en français, 1 seulement en anglais. */
fun plural(n: Int, frOne: String, frMany: String, enOne: String, enMany: String): String =
    if (isFrench) (if (n <= 1) frOne else frMany) else (if (n == 1) enOne else enMany)
