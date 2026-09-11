package no.nav.dagpenger.tilbakekreving.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.tilbakekreving.FagsysteminfoSvar
import no.nav.dagpenger.tilbakekreving.Mottaker
import no.nav.dagpenger.tilbakekreving.MottakerType
import no.nav.dagpenger.tilbakekreving.Revurdering
import no.nav.dagpenger.tilbakekreving.RevurderingsinfoMapper
import no.nav.dagpenger.tilbakekreving.behandling.BehandlingKlient
import no.nav.dagpenger.tilbakekreving.behandling.BehandlingResponse
import no.nav.dagpenger.tilbakekreving.kravgrunnlagReferanseTilBehandlingId
import no.nav.dagpenger.tilbakekreving.toJson
import tools.jackson.databind.JsonNode
import java.util.UUID

/**
 * Lytter på rapid-meldinger med `hendelsestype: fagsysteminfo_behov` (custom
 * felt, IKKE @event_name-konvensjonen), slår opp behandlingId hos
 * dp-behandling og publiserer `fagsysteminfo_svar`.
 *
 * Når [dryRun] er satt løses behovet og resultatet logges, men det publiseres
 * ikke noe svar - se [no.nav.dagpenger.tilbakekreving.Config.dryRun].
 */
internal class FagsysteminfoBehovLøser(
    rapidsConnection: RapidsConnection,
    private val behandlingKlient: BehandlingKlient,
    private val revurderingsinfoMapper: RevurderingsinfoMapper,
    private val dryRun: Boolean = false,
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
                precondition { it.requireValue("hendelsestype", HENDELSESTYPE_BEHOV) }
                precondition { it.requireValue("versjon", VERSJON) }
                precondition { it.forbid("@løsning") }
                validate { it.require("eksternFagsakId", JsonNode::asString) }
                validate { it.require("kravgrunnlagReferanse", JsonNode::asString) }
                validate { it.require("hendelseOpprettet", JsonNode::asLocalDateTime) }
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

        val behandlingId = dekodeBehandlingId(packet, eksternFagsakId) ?: return

        withLoggingContext(
            "eksternFagsakId" to eksternFagsakId,
            "kravgrunnlagReferanse" to kravgrunnlagReferanse,
            "behandlingId" to behandlingId.toString(),
        ) {
            val behandling = runBlocking { behandlingKlient.hentBehandling(behandlingId) }
            val svar = byggSvar(eksternFagsakId, behandlingId, behandling)
            publiser(svar, context)
        }
    }

    /**
     * Dekoder `kravgrunnlagReferanse` til en behandlingId. Returnerer null (og
     * forkaster meldingen) dersom referansen ikke er gyldig base64/UUID -
     * dette regnes som en varig, ikke-reprosesserbar feil.
     */
    private fun dekodeBehandlingId(
        packet: JsonMessage,
        eksternFagsakId: String,
    ): UUID? {
        val kravgrunnlagReferanse = packet["kravgrunnlagReferanse"].asString()
        return try {
            kravgrunnlagReferanseTilBehandlingId(kravgrunnlagReferanse)
        } catch (e: IllegalArgumentException) {
            log.warn(e) {
                """Klarte ikke å dekode kravgrunnlagReferanse til en gyldig behandlingId for 
                |eksternFagsakId=$eksternFagsakId, kravgrunnlagReferanse=$kravgrunnlagReferanse. Forkaster meldingen.
                |Melding: ${packet.toJson()}
                """.trimMargin()
            }
            null
        }
    }

    private fun byggSvar(
        eksternFagsakId: String,
        behandlingId: UUID,
        behandling: BehandlingResponse,
    ) = FagsysteminfoSvar(
        eksternFagsakId = eksternFagsakId,
        mottaker = Mottaker(type = MottakerType.PERSON, ident = behandling.ident),
        revurdering =
            Revurdering(
                behandlingId = behandlingId,
                årsak = revurderingsinfoMapper.årsak(behandling),
                årsakTilFeilutbetaling = revurderingsinfoMapper.årsakTilFeilutbetaling(behandling),
                vedtaksdato = revurderingsinfoMapper.vedtaksdato(behandling),
            ),
    )

    private fun publiser(
        svar: FagsysteminfoSvar,
        context: MessageContext,
    ) {
        if (dryRun) {
            log.info { "DRY_RUN: publiserer ikke $HENDELSESTYPE_SVAR, ville sendt: ${svar.toJson()}" }
            return
        }
        log.info { "Publiserer $HENDELSESTYPE_SVAR" }
        context.publish(svar.toJson())
    }
}
