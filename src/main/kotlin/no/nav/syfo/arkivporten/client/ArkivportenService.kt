package no.nav.syfo.arkivporten.client

class ArkivportenService(private val arkivportenClient: IArkivportenClient) {
    suspend fun arkiverOppfolginsplan(document: Document) {
        arkivportenClient.publishOppfolginsplan(document)
    }
}
