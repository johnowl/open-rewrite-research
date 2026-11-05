package com.example.tickets

import org.springframework.ai.document.Document
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component

/** Ingests PDFs uploaded to tickets, using the reader affected by CVE-2026-47851. */
@Component
class PdfIngestion {
    fun ingest(pdf: Resource): List<Document> = ParagraphPdfDocumentReader(pdf).get()
}
