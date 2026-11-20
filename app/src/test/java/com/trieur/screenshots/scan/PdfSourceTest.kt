package com.trieur.screenshots.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSourceTest {
    private val a = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FDocuments/document/primary%3ADownload%2FDocuments%2Fcontrat.pdf"
    private val b = a.replace("contrat", "facture")

    @Test
    fun stableId_isNegative_stable_andDistinct() {
        // Négatif : jamais en collision avec un identifiant MediaStore (positif).
        assertTrue(PdfSource.stableId(a) < 0)
        assertEquals(PdfSource.stableId(a), PdfSource.stableId(a))
        assertNotEquals(PdfSource.stableId(a), PdfSource.stableId(b))
    }
}
