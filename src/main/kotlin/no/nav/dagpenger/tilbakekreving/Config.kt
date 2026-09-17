package no.nav.dagpenger.tilbakekreving

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.dagpenger.tilbakekreving.auth.NaisTokenClient

internal object Config {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "KAFKA_CONSUMER_GROUP_ID" to "dp-tilbakekreving-v1",
                "KAFKA_RAPID_TOPIC" to "tilbake.privat-tilbakekreving-dagpenger",
                "KAFKA_RESET_POLICY" to "latest",
                "DP_BEHANDLING_SCOPE" to "api://dev-gcp.teamdagpenger.dp-behandling/.default",
                "DP_BEHANDLING_API_URL" to "http://dp-behandling",
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

    val dpBehandlingTokenClient by lazy {
        NaisTokenClient(
            tokenEndpoint = properties[Key("NAIS_TOKEN_ENDPOINT", stringType)],
            target = dpBehandlingScope,
        )
    }

    fun asMap(): Map<String, String> =
        properties.list().reversed().fold(emptyMap()) { map, pair ->
            map + pair.second
        }
}
