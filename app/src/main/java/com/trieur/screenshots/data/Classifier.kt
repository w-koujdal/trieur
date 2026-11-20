package com.trieur.screenshots.data

/**
 * Classement hors ligne, par règles simples : d'abord le contenu du texte (achat, voyage, code,
 * adresse), puis l'appli d'origine que Samsung écrit dans le nom du fichier.
 *
 * Augmenter [VERSION] quand les règles changent : les captures déjà indexées seront reclassées
 * au prochain démarrage.
 */
object Classifier {
    const val VERSION = 1

    /** « Screenshot_20261120_002436_WhatsApp.jpg » → « WhatsApp » (aussi « AISelect_… »). */
    fun sourceApp(displayName: String): String? =
        Regex("""^[A-Za-z]+_\d{8}_\d{6}_(.+)\.\w+$""").find(displayName)
            ?.groupValues?.get(1)?.trim()?.ifEmpty { null }

    fun classify(text: String, sourceApp: String?): Category {
        val t = foldText(text)
        val app = sourceApp?.let(::foldText)

        if (t.count { it.isLetter() } < 4) return Category.NO_TEXT
        if (isPurchase(t)) return Category.PURCHASE
        if (isTravel(t)) return Category.TRAVEL
        if (isCode(t)) return Category.CODE
        if (app != null && MAPS_APPS.any { app.contains(it) } || STREET.containsMatchIn(t)) return Category.PLACE
        if (app != null) {
            if (MESSAGING_APPS.any { app.contains(it) }) return Category.CONVERSATION
            if (app == "x" || SOCIAL_APPS.any { app.contains(it) }) return Category.SOCIAL
            if (BROWSER_APPS.any { app.contains(it) }) return Category.WEB
        }
        return Category.OTHER
    }

    private fun isPurchase(t: String): Boolean {
        val keywords = PURCHASE_WORDS.count { it.containsMatchIn(t) }
        return (MONEY.containsMatchIn(t) && keywords >= 1) || keywords >= 3
    }

    private fun isTravel(t: String): Boolean = TRAVEL_WORDS.count { it.containsMatchIn(t) } >= 2

    private fun isCode(t: String): Boolean = hasCodeContext(t) && CODE_DIGITS.containsMatchIn(t)

    /** [folded] = texte passé par foldText. */
    internal fun hasCodeContext(folded: String): Boolean = CODE_CONTEXT.containsMatchIn(folded)

    /** Suites de chiffres pouvant être un code (« 482913 », « 123 456 »). */
    internal fun codeDigits(text: String): List<String> = CODE_DIGITS.findAll(text).map { it.value }.distinct().toList()

    private fun words(vararg w: String) = w.map { Regex("""(?<![\p{L}\p{N}])${Regex.escape(it)}(?![\p{L}\p{N}])""") }

    // Tout est comparé sur du texte en minuscules sans accents (voir foldText).
    private val PURCHASE_WORDS = words(
        "total", "sous-total", "commande", "facture", "recu", "paiement", "payer", "paye", "panier",
        "livraison", "ttc", "tva", "achat", "remboursement", "montant", "order", "checkout", "subtotal",
        "receipt", "acheter", "ajouter au panier", "frais de port",
    )
    private val MONEY = Regex("""\d+(?:[.,]\d{2})?\s?(?:€|eur\b)|€\s?\d+""")

    private val TRAVEL_WORDS = words(
        "billet", "embarquement", "boarding", "vol", "gate", "porte", "terminal", "siege", "seat",
        "voiture", "train", "sncf", "ouigo", "tgv", "depart", "arrivee", "aeroport", "airport", "flight",
        "check-in", "hotel", "airbnb", "booking", "quai", "passager", "passenger", "bagage",
    )

    private val CODE_CONTEXT = Regex(
        """code (?:de )?(?:verification|confirmation|securite|connexion|d'acces)|votre code|ton code|""" +
            """your code|verification code|security code|one-time|otp|2fa|code est|code is|code :"""
    )
    private val CODE_DIGITS = Regex("""(?<!\d)\d{3}[ -]?\d{3}(?!\d)|(?<!\d)\d{4,8}(?!\d)""")

    private val STREET = Regex(
        """(?<!\d)\d{1,4}(?:\s?(?:bis|ter))?,?\s+(?:rue|avenue|av\.|boulevard|bd|place|chemin|allee|impasse|route|quai|cours)\s"""
    )

    private val MAPS_APPS = listOf("maps", "waze", "plans", "citymapper")
    private val MESSAGING_APPS = listOf(
        "whatsapp", "messages", "messenger", "telegram", "signal", "snapchat", "discord", "sms",
        "teams", "slack", "gmail", "outlook", "mail",
    )
    private val SOCIAL_APPS = listOf(
        "tiktok", "instagram", "facebook", "twitter", "youtube", "reddit", "pinterest", "threads",
        "linkedin", "bereal", "twitch",
    )
    private val BROWSER_APPS = listOf("chrome", "internet", "firefox", "opera", "edge", "brave", "google", "duckduckgo")
}
