package com.trieur.screenshots

import com.trieur.screenshots.scan.DocText
import com.trieur.screenshots.scan.PdfSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocTextTest {
    @Test
    fun wordXml_keepsTextAndParagraphs() {
        val xml = "<w:document><w:body><w:p><w:r><w:t>Jean Dupont</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>Curriculum &amp; expériences</w:t></w:r></w:p></w:body></w:document>"
        val text = DocText.stripTags(xml)
        assertTrue(text.contains("Jean Dupont"))
        assertTrue(text.contains("Curriculum & expériences"))
        assertTrue("paragraphes séparés", text.lines().size >= 2)
    }

    @Test
    fun readableFormats_andPaths() {
        assertTrue(DocText.canRead("CV_2025.docx"))
        assertTrue(DocText.canRead("notes.TXT"))
        assertTrue(DocText.canRead("budget.xlsx"))
        assertFalse(DocText.canRead("archive.zip"))
        assertEquals("Download/Candidatures/CV.pdf", PdfSource.readablePath("primary:Download/Candidatures/CV.pdf"))
    }
}
