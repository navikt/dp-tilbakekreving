package no.nav.dagpenger.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.tilbakekreving.behandling.AvklaringSammendrag
import no.nav.dagpenger.tilbakekreving.behandling.BehandlingResponse
import no.nav.dagpenger.tilbakekreving.behandling.HendelseType
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class BehandlingsbasertRevurderingsinfoMapperTest {
    private val mapper = BehandlingsbasertRevurderingsinfoMapper()

    private fun behandling(
        hendelseType: HendelseType? = null,
        avklaringer: List<AvklaringSammendrag> = emptyList(),
        sistEndret: LocalDateTime = LocalDateTime.parse("2026-01-12T10:00:00"),
    ) = BehandlingResponse(
        behandlingId = UUID.randomUUID(),
        ident = "11109233444",
        opprettet = LocalDateTime.parse("2026-01-10T09:00:00"),
        sistEndret = sistEndret,
        hendelseType = hendelseType,
        avklaringer = avklaringer,
    )

    @Test
    fun `mapper klage-hendelser til KLAGE aarsak`() {
        mapper.årsak(behandling(hendelseType = HendelseType.KLAGE_FØRSTEINSTANS)) shouldBe "KLAGE"
        mapper.årsak(behandling(hendelseType = HendelseType.KLAGE_KLAGEINSTANS)) shouldBe "KLAGE"
        mapper.årsak(behandling(hendelseType = HendelseType.KLAGE_TRYGDERETTEN)) shouldBe "KLAGE"
    }

    @Test
    fun `mapper Meldekort til KORRIGERING aarsak`() {
        mapper.årsak(behandling(hendelseType = HendelseType.MELDEKORT)) shouldBe "KORRIGERING"
    }

    @Test
    fun `mapper Omgjoering til NYE_OPPLYSNINGER aarsak`() {
        mapper.årsak(behandling(hendelseType = HendelseType.OMGJØRING)) shouldBe "NYE_OPPLYSNINGER"
    }

    @Test
    fun `faller tilbake til UKJENT for oevrige hendelsetyper og manglende hendelseType`() {
        mapper.årsak(behandling(hendelseType = HendelseType.SØKNAD)) shouldBe "UKJENT"
        mapper.årsak(behandling(hendelseType = HendelseType.MANUELL)) shouldBe "UKJENT"
        mapper.årsak(behandling(hendelseType = HendelseType.ARBEIDSSØKERPERIODE)) shouldBe "UKJENT"
        mapper.årsak(behandling(hendelseType = HendelseType.FERIETILLEGG)) shouldBe "UKJENT"
        mapper.årsak(behandling(hendelseType = HendelseType.SAMORDNING)) shouldBe "UKJENT"
        mapper.årsak(behandling(hendelseType = null)) shouldBe "UKJENT"
    }

    @Test
    fun `henter foerste ikke-null begrunnelse fra avklaringer som aarsakTilFeilutbetaling`() {
        val behandling =
            behandling(
                avklaringer =
                    listOf(
                        AvklaringSammendrag(kode = "A", begrunnelse = null),
                        AvklaringSammendrag(kode = "B", begrunnelse = "Bruker sluttet på tiltaket"),
                        AvklaringSammendrag(kode = "C", begrunnelse = "En annen begrunnelse"),
                    ),
            )
        mapper.årsakTilFeilutbetaling(behandling) shouldBe "Bruker sluttet på tiltaket"
    }

    @Test
    fun `gir en tydelig fallback naar ingen avklaringer har begrunnelse`() {
        val behandling =
            behandling(
                avklaringer = listOf(AvklaringSammendrag(kode = "A", begrunnelse = null)),
            )
        mapper.årsakTilFeilutbetaling(behandling) shouldBe
            "Ingen begrunnelse funnet i avklaringer for behandlingId=${behandling.behandlingId}"
    }

    @Test
    fun `bruker sistEndret som vedtaksdato`() {
        val behandling = behandling(sistEndret = LocalDateTime.parse("2026-01-12T10:00:00"))
        mapper.vedtaksdato(behandling).toString() shouldBe "2026-01-12"
    }
}
