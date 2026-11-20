package com.trieur.screenshots.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class DuplicatesAndCleanupTest {

    private val now = 1_800_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun shot(
        id: Long,
        daysAgo: Long = 0,
        text: String = "",
        app: String? = null,
        category: Category = Category.OTHER,
        hash: Long? = null,
        locked: Boolean = false,
    ) = Screenshot(
        mediaId = id, uri = "content://x/$id", displayName = "s$id.jpg",
        dateTaken = now - daysAgo * day, text = text, sourceApp = app,
        category = category.name, categoryLocked = locked, dhash = hash,
    )

    @Test
    fun dHash_brighterLeftPixelsSetBits() {
        val gradient = IntArray(72) { i -> 255 - (i % 9) * 20 } // chaque pixel plus clair que son voisin de droite
        assertEquals(-1L, Duplicates.dHash(gradient))
        assertEquals(0L, Duplicates.dHash(IntArray(72) { 100 }))
    }

    @Test
    fun duplicates_needCloseHashAndSimilarText() {
        val text = "Billet de train Paris Lyon départ 08h12 voiture 5"
        val a = shot(1, daysAgo = 2, text = text, hash = 0b1011L)
        val b = shot(2, daysAgo = 1, text = text, hash = 0b1001L)                     // 1 bit d'écart
        val c = shot(3, daysAgo = 3, text = "Tout autre chose ici", hash = 0b1011L)   // même image, autre texte
        val d = shot(4, daysAgo = 4, text = text, hash = 0x0FFFL)                     // même texte, image différente

        val groups = Duplicates.groups(listOf(a, b, c, d))
        assertEquals(listOf(listOf(2L, 1L)), groups.map { g -> g.map { it.mediaId } })
    }

    @Test
    fun photosWithoutText_needNearlyIdenticalImages() {
        val a = shot(1, hash = 0L)
        val twoBits = shot(2, hash = 0b11L)
        val fiveBits = shot(3, hash = 0b11111L)
        assertTrue(Duplicates.isDuplicate(a, twoBits))
        // 5 bits suffiraient avec du texte identique, pas sans texte.
        assertTrue(!Duplicates.isDuplicate(a, fiveBits))
        assertTrue(Duplicates.isDuplicate(a.copy(text = "même texte ici"), fiveBits.copy(text = "même texte ici")))
    }

    @Test
    fun suggestions_keepNewestDuplicate_andSkipLockedOnes() {
        val items = listOf(
            shot(1, daysAgo = 3, category = Category.CODE),
            shot(2, daysAgo = 0, category = Category.CODE),                 // trop récent
            shot(3, daysAgo = 3, category = Category.CODE, locked = true),  // choisi à la main
            shot(10, daysAgo = 5, text = "même écran", hash = 7),
            shot(11, daysAgo = 1, text = "même écran", hash = 7),
        ) + (20L..25L).map { shot(it, daysAgo = 120, text = "vidéo $it", app = "TikTok", hash = it * 1_000_003) } +
            shot(30, category = Category.NO_TEXT)

        val byKey = cleanupSuggestions(items, now).associateBy { it.key }

        assertEquals(setOf(1L), byKey.getValue("codes").ids)
        assertEquals(setOf(10L), byKey.getValue("doublons").ids)
        assertEquals((20L..25L).toSet(), byKey.getValue("vieux:TikTok").ids)
        assertEquals(setOf(30L), byKey.getValue("sans-texte").ids)
        assertEquals(listOf("codes", "doublons", "vieux:TikTok", "sans-texte"), cleanupSuggestions(items, now).map { it.key })
    }

    @Test
    fun noSuggestionsForRecentDistinctScreenshots() {
        // Même image approximative (2 bits d'écart) mais textes différents : pas des doublons.
        val items = (1L..5L).map { shot(it, daysAgo = it, text = "texte $it différent", app = "TikTok", hash = it shl 20) }
        assertTrue(cleanupSuggestions(items, now).isEmpty())
    }
}
