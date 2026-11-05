package com.example.tickets.cve

import com.example.tickets.PdfIngestion
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import java.io.ByteArrayOutputStream

/** High. A deeply nested PDF outline overflows the stack of the thread ingesting it. */
class Cve202647851PdfRecursionTest {

    private fun pdfWithNestedOutline(depth: Int): ByteArray {
        PDDocument().use { document ->
            val page = PDPage().also(document::addPage)
            val outline = PDDocumentOutline()
            document.documentCatalog.documentOutline = outline
            var parent = PDOutlineItem().apply { title = "root"; destination = null }
            outline.addLast(parent)
            repeat(depth) {
                val child = PDOutlineItem().apply { title = "level $it"; setDestination(page) }
                parent.addLast(child)
                parent = child
            }
            return ByteArrayOutputStream().also(document::save).toByteArray()
        }
    }

    @Test
    fun `a malicious outline does not overflow the stack`() {
        val pdf = ByteArrayResource(pdfWithNestedOutline(depth = 20_000))

        val failure = runCatching { PdfIngestion().ingest(pdf) }.exceptionOrNull()

        assertThat(failure)
            .describedAs("VULNERABLE to CVE-2026-47851: a crafted PDF caused a StackOverflowError")
            .isNotInstanceOf(StackOverflowError::class.java)
    }
}
