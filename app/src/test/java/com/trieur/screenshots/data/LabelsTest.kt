package com.trieur.screenshots.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {
    private fun l(name: String, c: Float) = Labels.Label(name, c)

    @Test
    fun category_fromMostConfidentKnownLabel() {
        assertEquals(Category.ANIMAL, Labels.categoryFor(listOf(l("Pet", 0.8f), l("Dog", 0.95f), l("Sky", 0.75f))))
        assertEquals(Category.FOOD, Labels.categoryFor(listOf(l("Tableware", 0.9f), l("Food", 0.85f))))
        // Trop incertain pour décider.
        assertNull(Labels.categoryFor(listOf(l("Dog", 0.65f))))
        // Étiquette sans catégorie (Car) : on passe à la suivante.
        assertEquals(Category.NATURE, Labels.categoryFor(listOf(l("Car", 0.99f), l("Mountain", 0.8f))))
    }

    @Test
    fun subjectBeatsBackground() {
        // Le décor (ciel, plantes) est souvent plus « sûr » que le sujet : le sujet gagne quand même.
        assertEquals(Category.PEOPLE, Labels.categoryFor(listOf(l("Sky", 0.97f), l("Plant", 0.93f), l("Smile", 0.8f))))
        assertEquals(Category.PEOPLE, Labels.categoryFor(listOf(l("Plant", 0.95f), l("Jeans", 0.85f))))
        assertEquals(Category.FOOD, Labels.categoryFor(listOf(l("Smile", 0.9f), l("Food", 0.8f))))
        assertEquals(Category.NATURE, Labels.categoryFor(listOf(l("Sky", 0.97f), l("Plant", 0.93f))))
        // « Space » (images sombres) ne décide plus de la catégorie.
        assertNull(Labels.categoryFor(listOf(l("Space", 0.9f))))
    }

    @Test
    fun searchTags_frenchThenEnglish() {
        val tags = Labels.searchTags(listOf(l("Dog", 0.9f), l("Beach", 0.7f), l("Bento", 0.61f), l("Wakeboarding", 0.6f)))
        assertEquals("chien dog plage mer beach bento wakeboarding", tags)
    }

    @Test
    fun encodeDecode_roundTrip_andEmpty() {
        val labels = listOf(l("Dog", 0.93f), l("Hot dog", 0.71f))
        assertEquals("Dog:0.93|Hot dog:0.71", Labels.encode(labels))
        assertEquals(labels, Labels.decode(Labels.encode(labels)))
        assertTrue(Labels.decode("").isEmpty())
        assertTrue(Labels.decode(null).isEmpty())
    }

    @Test
    fun photosClassifiedByContent_textRulesStillWin() {
        val dog = listOf(l("Dog", 0.95f))
        assertEquals(Category.ANIMAL, Classifier.classify("", CAMERA_APP, Source.GALLERY, dog))
        assertEquals(Category.PHOTO, Classifier.classify("", CAMERA_APP, Source.GALLERY, emptyList()))
        // Ticket photographié : le texte l'emporte sur l'image.
        assertEquals(Category.PURCHASE, Classifier.classify("TOTAL 12,50 € Paiement CB", CAMERA_APP, Source.GALLERY, dog))
        // Les étiquettes n'influencent jamais une capture d'écran.
        assertEquals(Category.NO_TEXT, Classifier.classify("", "TikTok", Source.SCREENSHOT, dog))
    }
}
