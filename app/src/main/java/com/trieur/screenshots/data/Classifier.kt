package com.trieur.screenshots.data

/**
 * Classement hors ligne, par règles simples : d'abord le contenu du texte (achat, voyage, code,
 * adresse), puis l'appli d'origine que Samsung écrit dans le nom du fichier.
 *
 * Augmenter [VERSION] quand les règles changent : les captures déjà indexées seront reclassées
 * au prochain démarrage.
 */
object Classifier {
    const val VERSION = 5

    /** Au-delà, une photo ou une image téléchargée est surtout du texte : un document. */
    private const val DOCUMENT_MIN_LETTERS = 200

    /**
     * Appli d'origine : d'abord le nom de fichier Samsung (« Screenshot_20261120_002436_WhatsApp.jpg »
     * → « WhatsApp », aussi « AISelect_… »), sinon le dossier (« Android/media/com.whatsapp/… »).
     */
    fun sourceApp(displayName: String, folder: String? = null): String? {
        Regex("""^[A-Za-z]+_\d{8}_\d{6}_(.+)\.\w+$""").find(displayName)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { name -> name.any { it.isLetter() } } // « …_594.jpg » : un compteur, pas une appli
            ?.let { return it }
        val f = folder?.lowercase() ?: return null
        return FOLDER_APPS.firstOrNull { (key, _) -> f.contains(key) }?.second
    }

    /**
     * [labels] : ce que montre l'image (ML Kit Image Labeling), utilisé pour les photos et images
     * enregistrées quand le texte ne suffit pas à décider. Ignoré pour les captures d'écran.
     */
    fun classify(
        text: String,
        sourceApp: String?,
        source: Source = Source.SCREENSHOT,
        labels: List<Labels.Label> = emptyList(),
    ): Category {
        val t = foldText(text)
        val app = sourceApp?.let(::foldText)
        val letters = t.count { it.isLetter() }
        val isPhoto = source != Source.SCREENSHOT

        if (letters < 4) {
            return if (isPhoto) Labels.categoryFor(labels) ?: Category.PHOTO else Category.NO_TEXT
        }
        if (isPurchase(t)) return Category.PURCHASE
        if (isTravel(t)) return Category.TRAVEL
        if (isCode(t)) return Category.CODE
        if (app != null && MAPS_APPS.any { app.contains(it) } || STREET.containsMatchIn(t)) return Category.PLACE
        // L'appli d'origine ne dit quelque chose du contenu que pour une capture : une photo reçue
        // sur WhatsApp n'est pas une conversation.
        if (isPhoto) {
            if (letters >= DOCUMENT_MIN_LETTERS) return Category.DOCUMENT
            return Labels.categoryFor(labels) ?: Category.OTHER
        }
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

    /** Fragment de dossier (en minuscules) → nom d'appli affiché. Le premier qui correspond gagne. */
    private val FOLDER_APPS = listOf(
        "whatsapp" to "WhatsApp",
        "snapchat" to "Snapchat",
        "instagram" to "Instagram",
        "telegram" to "Telegram",
        "messenger" to "Messenger",
        "facebook" to "Facebook",
        "pinterest" to "Pinterest",
        "100pint" to "Pinterest",
        "tiktok" to "TikTok",
        "discord" to "Discord",
        "pictures/messages" to "Messages",
        "dcim/camera" to CAMERA_APP,
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
