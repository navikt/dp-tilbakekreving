package no.nav.dagpenger.tilbakekreving.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.tilbakekreving.RevurderingsinfoMapper
import no.nav.dagpenger.tilbakekreving.behandling.BehandlingKlient
import no.nav.dagpenger.tilbakekreving.behandling.BehandlingResponse
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class FagsysteminfoBehovLøserTest {
    // Samme eksempel som i ADR-en: base64 av 16 rå bytes som tolkes som UUID.
    private val kravgrunnlagReferanse = "EEio2tWRTS6BwG9FRfl6Zg=="
    private val forventetBehandlingId = UUID.fromString("1048a8da-d591-4d2e-81c0-6f4545f97a66")
    private val eksternFagsakId = "4UA79Qo"
    private val ident = "11109233444"

    private val behandlingResponse =
        BehandlingResponse(
            behandlingId = forventetBehandlingId,
            ident = ident,
            opprettet = LocalDateTime.parse("2026-01-10T09:00:00"),
            sistEndret = LocalDateTime.parse("2026-01-12T10:00:00"),
        )

    private val behandlingKlient: BehandlingKlient =
        mockk<BehandlingKlient>().also {
            coEvery { it.hentBehandling(forventetBehandlingId) } returns behandlingResponse
        }

    private val revurderingsinfoMapper: RevurderingsinfoMapper =
        mockk<RevurderingsinfoMapper>().also {
            every { it.årsak(behandlingResponse) } returns "TEST_ÅRSAK"
            every { it.årsakTilFeilutbetaling(behandlingResponse) } returns "TEST_ÅRSAK_TIL_FEILUTBETALING"
            every { it.vedtaksdato(behandlingResponse) } returns LocalDate.of(2026, 1, 12)
        }

    private val testRapid =
        TestRapid().also {
            FagsysteminfoBehovLøser(it, behandlingKlient, revurderingsinfoMapper)
        }

    @Test
    fun `løser fagsysteminfo_behov og publiserer korrekt fagsysteminfo_svar`() {
        testRapid.sendTestMessage(behovJson())

        val inspektør = testRapid.inspektør
        inspektør.size shouldBe 1

        val svar = inspektør.message(0)
        svar["hendelsestype"].asString() shouldBe "fagsysteminfo_svar"
        svar["versjon"].asString() shouldBe "1"
        svar["eksternFagsakId"].asString() shouldBe eksternFagsakId
        svar["mottaker"]["type"].asString() shouldBe "PERSON"
        svar["mottaker"]["ident"].asString() shouldBe ident
        svar["revurdering"]["behandlingId"].asString() shouldBe forventetBehandlingId.toString()
        svar["revurdering"]["årsak"].asString() shouldBe "TEST_ÅRSAK"
        svar["revurdering"]["årsakTilFeilutbetaling"].asString() shouldBe "TEST_ÅRSAK_TIL_FEILUTBETALING"
        svar["revurdering"]["vedtaksdato"].asString() shouldBe "2026-01-12"
    }

    @Test
    fun `plukker ikke opp melding med feil hendelsestype`() {
        testRapid.sendTestMessage(behovJson(hendelsestype = "annen_hendelsestype"))
        testRapid.inspektør.size shouldBe 0
    }

    @Test
    fun `plukker ikke opp melding med feil versjon`() {
        testRapid.sendTestMessage(behovJson(versjon = 2))
        testRapid.inspektør.size shouldBe 0
    }

    @Test
    fun `håndterer ugyldig kravgrunnlagReferanse uten å kaste ukontrollert unntak`() {
        // Antakelse: en kravgrunnlagReferanse som ikke dekoder til en gyldig
        // UUID skal håndteres kontrollert (f.eks. logges og meldingen
        // forkastes) fremfor å la unntaket boble videre til rapid-rammeverket.
        // Denne testen forventes å feile (rødt) helt til implementer-agenten
        // har lagt inn feilhåndtering rundt base64/UUID-dekodingen i onPacket.
        testRapid.sendTestMessage(behovJson(kravgrunnlagReferanse = "ikke-gyldig-base64!!!"))
        testRapid.inspektør.size shouldBe 0
    }

    // language=JSON
    private fun behovJson(
        hendelsestype: String = "fagsysteminfo_behov",
        versjon: Int = 1,
        kravgrunnlagReferanse: String = this.kravgrunnlagReferanse,
    ) = """
        {
          "hendelsestype": "$hendelsestype",
          "versjon": $versjon,
          "eksternFagsakId": "$eksternFagsakId",
          "kravgrunnlagReferanse": "$kravgrunnlagReferanse",
          "hendelseOpprettet": "2026-01-13T12:15:27.087202255"
        }
        """.trimIndent()
}
