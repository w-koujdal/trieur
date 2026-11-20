package com.trieur.screenshots.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassifierTest {

    private fun classify(text: String, app: String? = null) = Classifier.classify(text, app)

    @Test
    fun sourceApp_readFromSamsungFileNames() {
        assertEquals("WhatsApp", Classifier.sourceApp("Screenshot_20261120_002436_WhatsApp.jpg"))
        assertEquals("One UI Home", Classifier.sourceApp("Screenshot_20261120_011131_One UI Home.jpg"))
        assertEquals("TikTok", Classifier.sourceApp("AISelect_20260825_145249_TikTok.png"))
        assertNull(Classifier.sourceApp("Screenshot_20261120_011753.jpg"))
        assertNull(Classifier.sourceApp("IMG_1234.jpg"))
    }

    @Test
    fun noText() {
        assertEquals(Category.NO_TEXT, classify(""))
        assertEquals(Category.NO_TEXT, classify("12:45 5G"))
    }

    @Test
    fun purchase_needsMoneyAndKeyword_orSeveralKeywords() {
        assertEquals(Category.PURCHASE, classify("Récapitulatif de commande\nTotal 49,99 €", "Chrome"))
        assertEquals(Category.PURCHASE, classify("Votre panier\nLivraison offerte\nPaiement sécurisé"))
        // Un prix seul dans une vidéo n'en fait pas un achat.
        assertEquals(Category.SOCIAL, classify("Ce resto à 12 € est incroyable", "TikTok"))
    }

    @Test
    fun travel() {
        assertEquals(Category.TRAVEL, classify("Carte d'embarquement\nVol AF1234\nPorte B32 Siège 14C"))
        assertEquals(Category.TRAVEL, classify("SNCF Connect\nTrain TGV INOUI départ 08:12"))
    }

    @Test
    fun code() {
        assertEquals(Category.CODE, classify("Votre code de vérification est 482913", "Messages"))
        assertEquals(Category.CODE, classify("Your verification code is 123 456"))
        // Le mot « code » sans chiffres ne suffit pas.
        assertEquals(Category.OTHER, classify("Le code de la route a changé cette année"))
    }

    @Test
    fun place() {
        assertEquals(Category.PLACE, classify("Rendez-vous au 12 rue de la Paix à 19h"))
        assertEquals(Category.PLACE, classify("Itinéraire 25 min en voiture", "Maps"))
    }

    @Test
    fun byApp() {
        assertEquals(Category.CONVERSATION, classify("Salut tu viens ce soir ?", "WhatsApp"))
        assertEquals(Category.CONVERSATION, classify("Envoyé il y a 2 min", "Snapchat"))
        assertEquals(Category.SOCIAL, classify("Abonnés 1,2 M J'aime", "Instagram"))
        assertEquals(Category.SOCIAL, classify("Pour toi Suivis", "X"))
        assertEquals(Category.WEB, classify("Les meilleures recettes de crêpes", "Chrome"))
        assertEquals(Category.OTHER, classify("Paramètres Wi-Fi Bluetooth", "Settings"))
    }
}
