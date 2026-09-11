package no.nav.dagpenger.tilbakekreving.behandling

import com.fasterxml.jackson.annotation.JsonInclude
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson3.jackson
import no.nav.dagpenger.tilbakekreving.behandling.api.client.ApiConfiguration
import no.nav.dagpenger.tilbakekreving.behandling.api.client.BehandlingBehandlingsresultatClient
import no.nav.dagpenger.tilbakekreving.behandling.api.client.NetworkError
import no.nav.dagpenger.tilbakekreving.behandling.api.client.NetworkResult
import no.nav.dagpenger.tilbakekreving.behandling.api.models.BehandlingsresultatDTO
import no.nav.dagpenger.tilbakekreving.behandling.api.models.HendelseDTOTypeDTO
import tools.jackson.databind.DeserializationFeature
import java.io.Closeable
import java.time.LocalDateTime
import java.util.UUID

/**
 * Respons fra dp-behandling (kun det vi faktisk bruker).
 * Ikke map hele swagger-objektet; utvid ved behov fremfor å speile alt.
 *
 * [hendelseType] og [avklaringer] brukes til å utlede revurderingsårsak og
 * begrunnelse (se [no.nav.dagpenger.tilbakekreving.BehandlingsbasertRevurderingsinfoMapper]),
 * bekreftet av dp-behandling-teamet.
 */
data class BehandlingResponse(
    val behandlingId: UUID,
    val ident: String,
    val sistEndret: LocalDateTime,
    val hendelseType: HendelseType? = null,
)

/**
 * Egen domeneversjon av dp-behandling sin `Hendelse.type`-enum — hvilken type
 * hendelse som utløste behandlingen. Brukes sammen med [avklaringer]
 * til å utlede revurderingsårsak (se [no.nav.dagpenger.tilbakekreving.BehandlingsbasertRevurderingsinfoMapper]).
 */
enum class HendelseType {
    SØKNAD,
    MELDEKORT,
    MANUELL,
    OMGJØRING,
    ARBEIDSSØKERPERIODE,
    FERIETILLEGG,
    SAMORDNING,
    KLAGE_FØRSTEINSTANS,
    KLAGE_KLAGEINSTANS,
    KLAGE_TRYGDERETTEN,
}

/**
 * Kun det vi trenger fra en avklaring: saksbehandlers fritekstbegrunnelse.
 */
data class AvklaringSammendrag(
    val begrunnelse: String?,
)

/**
 * Klient mot dp-behandling.
 */
interface BehandlingKlient {
    suspend fun hentBehandling(behandlingId: UUID): BehandlingResponse
}

/**
 * Kastes når kallet mot dp-behandling feiler (HTTP-, nettverks- eller serialiseringsfeil).
 */
class BehandlingKlientException(
    val networkError: NetworkError,
) : RuntimeException("Kall mot dp-behandling feilet: $networkError")

/**
 * HTTP-implementasjon av [BehandlingKlient] mot dp-behandling, med Azure AD
 * app-to-app-autentisering (client credentials), etter samme mønster som
 * PDL-oppslaget i dp-oppslag-person. Selve HTTP-kallet gjøres av en
 * fabrikt-generert Ktor-klient (se :openapi-modulen).
 */
class BehandlingHttpKlient(
    private val url: String,
    private val tokenSupplier: suspend () -> String,
    private val httpClient: HttpClient = nyHttpClient(),
) : BehandlingKlient,
    Closeable {
    private val behandlingClient = BehandlingBehandlingsresultatClient(httpClient)

    override suspend fun hentBehandling(behandlingId: UUID): BehandlingResponse {
        val apiConfiguration =
            ApiConfiguration(
                basePath = url,
                customHeaders = mapOf(HttpHeaders.Authorization to "Bearer ${tokenSupplier()}"),
            )
        return when (val result = behandlingClient.getByBehandlingId(behandlingId, apiConfiguration)) {
            is NetworkResult.Success -> result.data.tilBehandlingResponse()
            is NetworkResult.Failure -> throw BehandlingKlientException(result.error)
        }
    }

    override fun close() = httpClient.close()

    companion object {
        fun nyHttpClient() =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    jackson {
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
                    }
                }
                install(HttpRequestRetry) {
                    retryOnExceptionOrServerErrors(maxRetries = 5)
                    constantDelay(millis = 100, randomizationMs = 0)
                }
            }
    }
}

/**
 * Oversetter den fabrikt-genererte, tekniske DTO-en til vår slanke domenetype.
 * Holdt som egen funksjon slik at [BehandlingHttpKlient.hentBehandling] kan
 * holde seg på ett abstraksjonsnivå (kall + resultathåndtering), ikke
 * felt-for-felt-mapping.
 */
private fun BehandlingsresultatDTO.tilBehandlingResponse() =
    BehandlingResponse(
        behandlingId = behandlingId,
        ident = ident,
        sistEndret = sistEndret,
        hendelseType = behandletHendelse.type.tilHendelseType(),
    )

private fun HendelseDTOTypeDTO.tilHendelseType(): HendelseType =
    when (this) {
        HendelseDTOTypeDTO.SØKNAD -> HendelseType.SØKNAD
        HendelseDTOTypeDTO.MELDEKORT -> HendelseType.MELDEKORT
        HendelseDTOTypeDTO.MANUELL -> HendelseType.MANUELL
        HendelseDTOTypeDTO.OMGJØRING -> HendelseType.OMGJØRING
        HendelseDTOTypeDTO.ARBEIDSSØKERPERIODE -> HendelseType.ARBEIDSSØKERPERIODE
        HendelseDTOTypeDTO.FERIETILLEGG -> HendelseType.FERIETILLEGG
        HendelseDTOTypeDTO.SAMORDNING -> HendelseType.SAMORDNING
        HendelseDTOTypeDTO.KLAGE_FØRSTEINSTANS -> HendelseType.KLAGE_FØRSTEINSTANS
        HendelseDTOTypeDTO.KLAGE_KLAGEINSTANS -> HendelseType.KLAGE_KLAGEINSTANS
        HendelseDTOTypeDTO.KLAGE_TRYGDERETTEN -> HendelseType.KLAGE_TRYGDERETTEN
    }
