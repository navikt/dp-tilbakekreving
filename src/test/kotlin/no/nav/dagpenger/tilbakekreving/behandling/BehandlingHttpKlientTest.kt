package no.nav.dagpenger.tilbakekreving.behandling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson3.jackson
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.tilbakekreving.behandling.api.client.NetworkError
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BehandlingHttpKlientTest {
    private val behandlingId = UUID.fromString("1048a8da-d591-4d2e-81c0-6f4545f97a66")
    private val token = "et-fint-access-token"

    @Test
    fun `sender riktig url og Authorization-header`() {
        var mottattUrl = ""
        var mottattAuthHeader: String? = null

        val httpClient =
            HttpClient(
                MockEngine { request ->
                    mottattUrl = request.url.toString()
                    mottattAuthHeader = request.headers[HttpHeaders.Authorization]
                    respond(
                        content = behandlingJson(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) { jackson() }
            }

        val klient = BehandlingHttpKlient(url = "http://dp-behandling", tokenSupplier = { token }, httpClient = httpClient)

        runBlocking { klient.hentBehandling(behandlingId) }

        mottattUrl shouldBe "http://dp-behandling/behandling/$behandlingId/behandlingsresultat"
        mottattAuthHeader shouldBe "Bearer $token"
    }

    @Test
    fun `mapper vellykket respons til BehandlingResponse`() {
        val httpClient =
            HttpClient(
                MockEngine {
                    respond(
                        content = behandlingJson(ident = "11109233444"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) { jackson() }
            }

        val klient = BehandlingHttpKlient(url = "http://dp-behandling", tokenSupplier = { token }, httpClient = httpClient)

        val behandling = runBlocking { klient.hentBehandling(behandlingId) }

        behandling.behandlingId shouldBe behandlingId
        behandling.ident shouldBe "11109233444"
        behandling.hendelseType shouldBe HendelseType.MELDEKORT
    }

    @Test
    fun `kaster BehandlingKlientException ved feilrespons fra dp-behandling`() {
        val httpClient =
            HttpClient(
                MockEngine {
                    respond(
                        content = """{"melding": "fant ikke behandling"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) { jackson() }
            }

        val klient = BehandlingHttpKlient(url = "http://dp-behandling", tokenSupplier = { token }, httpClient = httpClient)

        val exception =
            shouldThrow<BehandlingKlientException> {
                runBlocking { klient.hentBehandling(behandlingId) }
            }
        (exception.networkError as NetworkError.Http).statusCode shouldBe 404
    }

    // language=JSON
    private fun behandlingJson(ident: String = "11109233444") =
        """
        {
          "behandlingId": "$behandlingId",
          "behandletHendelse": {
            "datatype": "UUID",
            "id": "${UUID.randomUUID()}",
            "type": "Meldekort",
            "skjedde": "2026-01-10"
          },
          "regelverk": "Dagpenger",
          "behandlingskjedeId": "${UUID.randomUUID()}",
          "automatisk": false,
          "ident": "$ident",
          "rettighetsperioder": [],
          "opprettet": "2026-01-10T09:00:00",
          "sistEndret": "2026-01-12T10:00:00",
          "kreverTotrinnskontroll": false,
          "tilstand": "Ferdig",
          "vilkår": [],
          "fastsettelser": [],
          "opplysninger": [],
          "utbetalinger": [],
          "behandletAv": [],
          "førteTil": "Endring"
        }
        """.trimIndent()
}
