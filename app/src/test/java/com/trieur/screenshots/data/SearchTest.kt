package com.trieur.screenshots.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTest {

    @Test
    fun ftsQuery_prefixesEachWordAndDropsPunctuation() {
        assertEquals("rendez* vous*", toFtsQuery("rendez-vous"))
        assertEquals("billet* 2024* or*", toFtsQuery("  Billet, 2024 \"OR\" "))
        assertNull(toFtsQuery(" *** "))
    }

    @Test
    fun highlights_ignoreCaseAndAccents_andCoverWholeWord() {
        val text = "Réservation confirmée à l'École"
        val ranges = findHighlights(text, listOf("reserv", "ecole"))
        assertEquals(listOf("Réservation", "École"), ranges.map { text.substring(it) })
    }

    @Test
    fun highlights_onlyMatchWordStarts() {
        val text = "préparation"
        assertEquals(emptyList<IntRange>(), findHighlights(text, listOf("para")))
    }
}
