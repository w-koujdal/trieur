package com.trieur.screenshots.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectTest {

    @Test
    fun links_withAndWithoutScheme_trailingPunctuationRemoved() {
        val found = detect("Va voir https://exemple.fr/promo?id=3. Ou www.site.com, ou boutique.shop !")
        assertEquals(
            listOf("https://exemple.fr/promo?id=3", "https://www.site.com", "https://boutique.shop"),
            found.filterIsInstance<Detected.Link>().map { it.url },
        )
    }

    @Test
    fun email_isNotAlsoALink() {
        val found = detect("Écris à jean.dupont@gmail.com pour réserver")
        assertEquals(listOf(Detected.Email("jean.dupont@gmail.com")), found)
    }

    @Test
    fun frenchPhoneNumbers() {
        val found = detect("Appelle le 06 12 34 56 78 ou le +33 1.23.45.67.89")
        assertEquals(
            listOf("0612345678", "+33123456789"),
            found.filterIsInstance<Detected.Phone>().map { it.dial },
        )
    }

    @Test
    fun code_onlyWithContext_andNotInsidePhone() {
        assertEquals(
            listOf(Detected.Code("482913")),
            detect("Votre code de vérification est 482913. Ne le partagez pas."),
        )
        assertTrue(detect("Il a marqué 482913 points").none { it is Detected.Code })
    }

    @Test
    fun timesAndPricesAreNotLinks() {
        assertTrue(detect("Il est 12.30, ça coûte 4.99 €").isEmpty())
    }
}
