package no.nav.dagpenger.tilbakekreving

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config

internal object Config {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "KAFKA_CONSUMER_GROUP_ID" to "dp-tilbakekreving-v1",
                "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
                "KAFKA_RESET_POLICY" to "latest",
                "DP_BEHANDLING_SCOPE" to "api://dev-gcp.teamdagpenger.dp-behandling/.default",
                "DP_BEHANDLING_API_URL" to "http://dp-behandling",
                "DRY_RUN" to "false",
            ),
        )
    private val prodProperties =
        ConfigurationMap(
            mapOf(
                "DP_BEHANDLING_SCOPE" to "api://prod-gcp.teamdagpenger.dp-behandling/.default",
            ),
        )

    val properties: Configuration by lazy {
        val systemAndEnvProperties = ConfigurationProperties.systemProperties() overriding EnvironmentVariables()
        when (System.getenv().getOrDefault("NAIS_CLUSTER_NAME", "LOCAL")) {
            "prod-gcp" -> systemAndEnvProperties overriding prodProperties overriding defaultProperties
            else -> systemAndEnvProperties overriding defaultProperties
        }
    }

    val dpBehandlingScope by lazy { properties[Key("DP_BEHANDLING_SCOPE", stringType)] }

    val dpBehandlingApiUrl by lazy { properties[Key("DP_BEHANDLING_API_URL", stringType)] }

    val dpBehandlingTokenProvider by lazy {
        val azureAd = OAuth2Config.AzureAd(properties)
        CachedOauth2Client(
            tokenEndpointUrl = azureAd.tokenEndpointUrl,
            authType = azureAd.clientSecret(),
        )
    }

    /**
     * Når satt, kjøres appen i "tørrkjøring": innkommende behov løses og logges,
     * men det publiseres ikke noe svar. rapids-and-rivers-biblioteket committer
     * likevel Kafka-offset for hver behandlet batch uansett (det finnes ingen
     * støttet måte å slå av dette fra applikasjonskoden på - KAFKA_AUTO_COMMIT
     * styrer kun Kafka sin egen bakgrunns-autocommit, ikke bibliotekets manuelle
     * per-batch-commit). Kjør derfor tørrkjøringer under en egen
     * KAFKA_CONSUMER_GROUP_ID (satt eksplisitt ved deploy) fremfor den ordinære
     * forbrukergruppen.
     */
    val dryRun by lazy { properties[Key("DRY_RUN", booleanType)] }

    fun asMap(): Map<String, String> =
        properties.list().reversed().fold(emptyMap()) { map, pair ->
            map + pair.second
        }
}
