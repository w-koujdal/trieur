package com.trieur.screenshots.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Galerie et téléchargements : dossiers vus sur le Galaxy S24. */
class SourcesTest {

    @Test
    fun sourceFromFolder() {
        assertEquals(Source.SCREENSHOT, Source.fromPath("DCIM/Screenshots/"))
        assertEquals(Source.SCREENSHOT, Source.fromPath("Pictures/Screenshots/"))
        assertEquals(Source.DOWNLOAD, Source.fromPath("Download/"))
        assertEquals(Source.DOWNLOAD, Source.fromPath("Download/Prestly_toutes_les_images/"))
        assertEquals(Source.GALLERY, Source.fromPath("DCIM/Camera/"))
        assertEquals(Source.GALLERY, Source.fromPath("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
        assertEquals(Source.GALLERY, Source.fromPath(null))
    }

    @Test
    fun appFromFolder_whenFileNameSaysNothing() {
        assertEquals("WhatsApp", Classifier.sourceApp("IMG-20261101-WA0003.jpg", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
        assertEquals("Pinterest", Classifier.sourceApp("a1b2.jpg", "Pictures/100PINT/Pins/"))
        assertEquals("Messages", Classifier.sourceApp("x.jpg", "Pictures/Messages/"))
        assertEquals(CAMERA_APP, Classifier.sourceApp("20261101_101010.jpg", "DCIM/Camera/"))
        assertNull(Classifier.sourceApp("photo.jpg", "Download/"))
        // Le nom de fichier Samsung reste prioritaire.
        assertEquals("Chrome", Classifier.sourceApp("Screenshot_20261116_223243_Chrome.jpg", "DCIM/Screenshots/"))
    }

    @Test
    fun galleryImages_photoOrDocument_neverConversation() {
        assertEquals(Category.PHOTO, Classifier.classify("", CAMERA_APP, Source.GALLERY))
        assertEquals(Category.NO_TEXT, Classifier.classify("", "TikTok", Source.SCREENSHOT))

        val letter = "Madame, Monsieur, ".repeat(15) + "veuillez agréer mes salutations distinguées."
        assertEquals(Category.DOCUMENT, Classifier.classify(letter, CAMERA_APP, Source.GALLERY))

        // Photo reçue sur WhatsApp avec un peu de texte : pas une conversation.
        assertEquals(Category.OTHER, Classifier.classify("Joyeux anniversaire", "WhatsApp", Source.GALLERY))
        assertEquals(Category.CONVERSATION, Classifier.classify("Joyeux anniversaire", "WhatsApp", Source.SCREENSHOT))

        // Les règles de contenu valent pour toutes les sources (ticket de caisse photographié).
        assertEquals(Category.PURCHASE, Classifier.classify("CARREFOUR\nTOTAL 23,40 €\nPaiement CB", CAMERA_APP, Source.GALLERY))
    }

    @Test
    fun cleanup_neverProposesOldCameraPhotos() {
        val now = 1_800_000_000_000L
        val old = now - TimeUnit.DAYS.toMillis(200)
        fun img(id: Long, app: String) = Screenshot(
            mediaId = id, uri = "content://x/$id", displayName = "$id.jpg", dateTaken = old,
            text = "texte $id", sourceApp = app, category = Category.OTHER.name,
            source = Source.GALLERY.name, dhash = id * 0x9E3779B97F4A7C15uL.toLong(),
        )
        val items = (1L..10L).map { img(it, CAMERA_APP) } + (11L..16L).map { img(it, "WhatsApp") }
        val keys = cleanupSuggestions(items, now).map { it.key }
        assertEquals(listOf("vieux:WhatsApp"), keys)
        assertTrue(keys.none { it.contains(CAMERA_APP) })
    }
}
