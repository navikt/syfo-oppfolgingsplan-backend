package no.nav.syfo.arkivporten.client

import java.util.UUID

data class Document(
    val documentId: UUID,
    val type: DocumentType,
    val content: ByteArray,
    val contentType: String,
    val orgnumber: String,
    val fnr: String,
    val fullName: String,
    val title: String,
    val summary: String,
)

enum class DocumentType {
    OPPFOLGINGSPLAN,
}
