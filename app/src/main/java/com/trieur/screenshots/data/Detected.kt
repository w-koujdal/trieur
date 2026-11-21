package com.trieur.screenshots.data

/** Élément exploitable repéré dans le texte d'une capture. */
sealed interface Detected {
    val label: String

    /** [url] est complète (https:// ajouté si besoin). */
    data class Link(override val label: String, val url: String) : Detected
    data class Email(override val label: String) : Detected
    /** [dial] = chiffres et « + » uniquement, prêt pour tel:. */
    data class Phone(override val label: String, val dial: String) : Detected
    data class Code(override val label: String) : Detected
}

/**
 * Repère liens, e-mails, numéros (format français) et codes de vérification.
 * Les e-mails sont retirés avant de chercher les liens, pour ne pas prendre « gmail.com » pour un site.
 */
fun detect(text: String, max: Int = 8): List<Detected> {
    val found = LinkedHashMap<String, Detected>()

    var rest = text
    EMAIL.findAll(text).forEach { m ->
        found.putIfAbsent("mail:" + m.value.lowercase(), Detected.Email(m.value))
        rest = rest.replace(m.value, " ")
    }

    URL.findAll(rest).forEach { m ->
        val raw = m.value.trimEnd('.', ',', ';', ':', ')', '!', '?', '»', '"', '\'')
        val url = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
        found.putIfAbsent("url:" + url.lowercase(), Detected.Link(raw, url))
    }

    PHONE.findAll(text).forEach { m ->
        // L'OCR lit souvent le 0 initial comme la lettre O (« O6 12 34 56 78 »).
        val fixed = m.value.trim().replace('O', '0').replace('o', '0')
        val dial = fixed.filter { it.isDigit() || it == '+' }
        found.putIfAbsent("tel:$dial", Detected.Phone(fixed, dial))
    }

    if (Classifier.hasCodeContext(foldText(text))) {
        val phones = found.values.filterIsInstance<Detected.Phone>().map { it.dial }
        Classifier.codeDigits(text)
            .filter { code -> phones.none { it.contains(code.filter(Char::isDigit)) } }
            .forEach { found.putIfAbsent("code:$it", Detected.Code(it)) }
    }

    return found.values.take(max)
}

private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

private val URL = Regex(
    """(?:https?://|www\.)[^\s<>"]+|""" +
        """(?<![\w@.-])[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:com|fr|net|org|io|ai|be|ch|ca|de|es|it|uk|us|app|dev|co|gg|tv|me|info|eu|ly|shop|xyz|tech)(?:/[^\s<>"]*)?(?![\w-])""",
    RegexOption.IGNORE_CASE,
)

private val PHONE = Regex(
    // France : 06 12 34 56 78, +33 6 12 34 56 78
    """(?<![\w+])(?:\+33\s?|0033\s?|[0Oo])[1-9](?:[\s.-]?\d{2}){4}(?!\d)|""" +
        // Amérique du Nord : (555) 013-4567, 555-013-4567, +1 555 013 4567
        """(?<![\w+])(?:\+1[\s.-]?)?\(?[2-9]\d{2}\)?[\s.-]\d{3}[\s.-]\d{4}(?!\d)|""" +
        // International avec indicatif : +44 20 7946 0958, +49 30 1234567
        """(?<![\w+])\+(?:[2-9]\d{0,2})(?:[\s.-]?\(?\d{1,4}\)?){2,5}(?!\d)"""
)
