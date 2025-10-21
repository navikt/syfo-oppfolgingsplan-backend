package no.nav.syfo.arkivporten.client

import java.util.UUID

data class Document(
    val documentId: UUID,
    val type: DocumentType,
    val content: ByteArray,
    val contentType: String,
    val orgnumber: String,
    val dialogTitle: String,
    val dialogSummary: String,
)

enum class DocumentType {
    OPPFOLGINGSPLAN,
}
