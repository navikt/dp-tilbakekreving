package no.nav.dagpenger.tilbakekreving

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.tilbakekreving.behandling.BehandlingHttpKlient
import no.nav.dagpenger.tilbakekreving.rivers.FagsysteminfoBehovLøser
import no.nav.helse.rapids_rivers.RapidApplication

private val logg = KotlinLogging.logger {}

fun main() {
    ApplicationBuilder(Config.asMap()).start()
}

internal class ApplicationBuilder(
    env: Map<String, String>,
) : RapidsConnection.StatusListener {
    private val behandlingKlient =
        BehandlingHttpKlient(
            url = Config.dpBehandlingApiUrl,
            tokenSupplier = {
                Config.dpBehandlingTokenProvider
                    .clientCredentials(Config.dpBehandlingScope)
                    .access_token ?: throw RuntimeException("Kunne ikke hente token")
            },
        )

    private val rapidsConnection =
        RapidApplication
            .create(env)
            .apply {
                FagsysteminfoBehovLøser(
                    rapidsConnection = this,
                    behandlingKlient = behandlingKlient,
                    revurderingsinfoMapper = BehandlingsbasertRevurderingsinfoMapper(),
                    dryRun = Config.dryRun,
                )
            }

    init {
        rapidsConnection.register(this)
    }

    fun start() = rapidsConnection.start()

    override fun onStartup(rapidsConnection: RapidsConnection) {
        logg.info { "Starter dp-tilbakekreving${if (Config.dryRun) " (DRY_RUN)" else ""}" }
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        behandlingKlient.close()
    }
}
