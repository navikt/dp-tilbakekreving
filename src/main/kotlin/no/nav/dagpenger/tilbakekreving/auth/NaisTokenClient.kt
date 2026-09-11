package no.nav.dagpenger.tilbakekreving.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.serialization.jackson3.jackson
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Henter Entra ID M2M-token via Nais sitt token-endepunkt (texas-sidecaren),
 * se https://doc.nais.io/auth/entra-id/how-to/consume-m2m/.
 * Tokenet caches i minnet og fornyes automatisk et lite stykke før det utløper.
 */
class NaisTokenClient(
    private val tokenEndpoint: String,
    private val target: String,
    private val httpClient: HttpClient = nyHttpClient(),
) {
    private val cachedToken = AtomicReference<CachedToken?>(null)

    suspend fun hentToken(): String {
        cachedToken.get()?.let { if (it.fortsattGyldig()) return it.accessToken }
        return hentNyttToken().also { cachedToken.set(it) }.accessToken
    }

    private suspend fun hentNyttToken(): CachedToken {
        val respons: TokenResponse =
            httpClient
                .submitForm(
                    url = tokenEndpoint,
                    formParameters =
                        Parameters.build {
                            append("identity_provider", "entra_id")
                            append("target", target)
                        },
                ).body()
        return CachedToken.fra(respons)
    }

    private data class CachedToken(
        val accessToken: String,
        val utløperVed: Instant,
    ) {
        fun fortsattGyldig() = Instant.now().isBefore(utløperVed)

        companion object {
            // Fornyer et lite stykke før faktisk utløp, slik at vi ikke risikerer å
            // bruke et token som utløper midt i et pågående HTTP-kall.
            private const val UTLØPSMARGIN_SEKUNDER = 30L

            fun fra(respons: TokenResponse) =
                CachedToken(
                    accessToken = respons.access_token,
                    utløperVed = Instant.now().plusSeconds((respons.expires_in - UTLØPSMARGIN_SEKUNDER).coerceAtLeast(0)),
                )
        }
    }

    // Feltnavnene speiler JSON-responsen fra Nais' token-endepunkt direkte.
    private data class TokenResponse(
        val access_token: String,
        val expires_in: Long,
        val token_type: String,
    )

    companion object {
        private fun nyHttpClient() =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    jackson()
                }
            }
    }
}
