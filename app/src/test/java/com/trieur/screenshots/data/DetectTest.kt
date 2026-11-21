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
    fun aiDomain_withPathAndFragment() {
        // Cas vu sur le téléphone : barre d'adresse de Chrome capturée.
        assertEquals(
            listOf("https://claude.ai/new#settir"),
            detect("A claude.ai/new#settir +").filterIsInstance<Detected.Link>().map { it.url },
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
    fun phone_withLetterOReadByOcr() {
        // Cas vu sur l'émulateur : « 06 » lu « O6 ».
        val found = detect("Appelle-moi au O6 12 34 56 78 si tu es en retard")
        assertEquals(listOf("0612345678"), found.filterIsInstance<Detected.Phone>().map { it.dial })
        // Mais pas au milieu d'un mot.
        assertTrue(detect("BO6 12 34 56 78").none { it is Detected.Phone })
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

    @Test
    fun internationalPhoneNumbers() {
        val dials = detect("Call (555) 013-4567 or +44 20 7946 0958, office 555-013-9999").filterIsInstance<Detected.Phone>().map { it.dial }
        assertEquals(listOf("5550134567", "+442079460958", "5550139999"), dials)
        // Dates, prix et codes ne sont pas des numéros.
        assertTrue(detect("Order 2026-11-20 total $1,299.99, code 482913").none { it is Detected.Phone })
    }
}
