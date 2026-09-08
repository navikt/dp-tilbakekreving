package no.nav.dagpenger.tilbakekreving.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.tilbakekreving.RevurderingsinfoMapper
import no.nav.dagpenger.tilbakekreving.behandling.BehandlingKlient
import no.nav.dagpenger.tilbakekreving.kravgrunnlagReferanseTilBehandlingId

/**
 * Lytter på rapid-meldinger med `hendelsestype: fagsysteminfo_behov` (custom
 * felt, IKKE @event_name-konvensjonen), slår opp behandlingId hos
 * dp-behandling og publiserer `fagsysteminfo_svar`.
 */
internal class FagsysteminfoBehovLøser(
    rapidsConnection: RapidsConnection,
    private val behandlingKlient: BehandlingKlient,
    private val revurderingsinfoMapper: RevurderingsinfoMapper,
) : River.PacketListener {
    companion object {
        private const val HENDELSESTYPE_BEHOV = "fagsysteminfo_behov"
        private const val HENDELSESTYPE_SVAR = "fagsysteminfo_svar"
        private const val VERSJON = 1
        private val log = KotlinLogging.logger { }
    }

    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("hendelsestype", HENDELSESTYPE_BEHOV) }
                validate { it.demandValue("versjon", VERSJON) }
                validate { it.requireKey("eksternFagsakId", "kravgrunnlagReferanse", "hendelseOpprettet") }
                validate { it.rejectKey("@løsning") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        log.info { "Mottok $HENDELSESTYPE_BEHOV" }
        val eksternFagsakId = packet["eksternFagsakId"].asString()
        val kravgrunnlagReferanse = packet["kravgrunnlagReferanse"].asString()

        val behandlingId =
            try {
                kravgrunnlagReferanseTilBehandlingId(kravgrunnlagReferanse)
            } catch (e: IllegalArgumentException) {
                log.warn(e) {
                    "Klarte ikke å dekode kravgrunnlagReferanse til en gyldig behandlingId " +
                        "for eksternFagsakId=$eksternFagsakId. Forkaster meldingen."
                }
                return
            }

        withMDC(mapOf("eksternFagsakId" to eksternFagsakId, "behandlingId" to behandlingId.toString())) {
            val behandling = runBlocking { behandlingKlient.hentBehandling(behandlingId) }

            val svar =
                mapOf(
                    "hendelsestype" to HENDELSESTYPE_SVAR,
                    "versjon" to VERSJON,
                    "eksternFagsakId" to eksternFagsakId,
                    "mottaker" to
                        mapOf(
                            "type" to "PERSON",
                            "ident" to behandling.ident,
                        ),
                    "revurdering" to
                        mapOf(
                            "behandlingId" to behandlingId.toString(),
                            "årsak" to revurderingsinfoMapper.årsak(behandling),
                            "årsakTilFeilutbetaling" to revurderingsinfoMapper.årsakTilFeilutbetaling(behandling),
                            "vedtaksdato" to revurderingsinfoMapper.vedtaksdato(behandling).toString(),
                            "utvidPerioder" to null,
                        ),
                )

            log.info { "Publiserer $HENDELSESTYPE_SVAR" }
            context.publish(JsonMessage.newMessage(svar).toJson())
        }
    }
}
