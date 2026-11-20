package com.trieur.screenshots.scan

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Texte des documents courants, sans bibliothèque : les formats Office et OpenDocument sont des
 * archives zip de XML, dont on garde le texte. Limité aux premiers ~200 000 caractères.
 */
object DocText {
    private const val MAX_CHARS = 200_000
    private val PLAIN = setOf("txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "log", "rtf", "ini", "yaml", "yml", "vcf", "ics", "srt")

    /** Entrées utiles de chaque archive : corps du texte, diapositives, cellules. */
    private fun zipEntryWanted(ext: String, entry: String): Boolean = when (ext) {
        "docx", "docm", "dotx" -> entry == "word/document.xml" || entry.startsWith("word/header") || entry.startsWith("word/footer")
        "pptx" -> entry.startsWith("ppt/slides/slide") && entry.endsWith(".xml")
        "xlsx", "xlsm" -> entry == "xl/sharedStrings.xml"
        "odt", "ods", "odp", "odg" -> entry == "content.xml"
        "epub" -> entry.endsWith(".xhtml") || entry.endsWith(".html")
        else -> false
    }

    fun extension(name: String) = name.substringAfterLast('.', "").lowercase()

    fun canRead(name: String): Boolean {
        val ext = extension(name)
        return ext in PLAIN || zipEntryWanted(ext, "word/document.xml") || ext in setOf("pptx", "xlsx", "xlsm", "odt", "ods", "odp", "odg", "epub")
    }

    fun extract(context: Context, uri: Uri, name: String): String {
        val ext = extension(name)
        return context.contentResolver.openInputStream(uri)?.use { input ->
            when {
                ext in PLAIN -> readPlain(input, ext)
                else -> readZip(input, ext)
            }
        }.orEmpty()
    }

    private fun readPlain(input: InputStream, ext: String): String {
        val bytes = input.readNBytesCompat(MAX_CHARS * 2)
        val text = String(bytes, Charsets.UTF_8)
        return when (ext) {
            "html", "htm", "xml" -> stripTags(text)
            "rtf" -> text.replace(Regex("""\\[a-z]+-?\d* ?|[{}]"""), " ")
            else -> text
        }.take(MAX_CHARS)
    }

    private fun readZip(input: InputStream, ext: String): String {
        val out = StringBuilder()
        ZipInputStream(input).use { zip ->
            while (out.length < MAX_CHARS) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && zipEntryWanted(ext, e.name)) {
                    val xml = String(zip.readNBytesCompat(MAX_CHARS * 4), Charsets.UTF_8)
                    out.append(stripTags(xml)).append('\n')
                }
            }
        }
        return out.toString().take(MAX_CHARS)
    }

    /** Balises retirées ; fin de paragraphe, de cellule ou de ligne → espace. */
    fun stripTags(xml: String): String = xml
        .replace(Regex("<(/w:p|/text:p|/a:p|w:tab|w:br|/si|/p|br ?/?)>", RegexOption.IGNORE_CASE), " \n")
        .replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace(Regex("""[ \t\x0B\f\r]+"""), " ")
        .replace(Regex("""\n\s*\n+"""), "\n")
        .trim()

    private fun InputStream.readNBytesCompat(max: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (buf.size() < max) {
            val n = read(chunk, 0, minOf(chunk.size, max - buf.size()))
            if (n <= 0) break
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray()
    }
}
