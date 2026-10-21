package no.nav.dagpenger.tilbakekreving

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

internal class KravgrunnlagReferanseTest {
    @Test
    fun `dekoder gyldig base64-referanse til korrekt UUID`() {
        // Denne base64-strengen er faktisk brukt som eksempel i ADR-en, og
        // dekoder til en gyldig, kjent UUID når man tolker de 16 rå bytene
        // som most/least significant bits.
        val referanse = "EEio2tWRTS6BwG9FRfl6Zg=="
        val forventet = UUID.fromString("1048a8da-d591-4d2e-81c0-6f4545f97a66")

        kravgrunnlagReferanseTilBehandlingId(referanse) shouldBe forventet
    }

    @Test
    fun `kaster IllegalArgumentException når base64-strengen ikke gir 16 bytes`() {
        // "ikke gyldig UUID" tolkes her som at det ikke er mulig å konstruere
        // en UUID fra de dekodede bytene (feil lengde). Vi antar at kallende
        // kode (River) fanger denne exceptionen og håndterer den kontrollert
        // fremfor å la den ukontrollert propagere ut av onPacket.
        shouldThrow<IllegalArgumentException> {
            kravgrunnlagReferanseTilBehandlingId(
                java.util.Base64
                    .getEncoder()
                    .encodeToString("for kort".toByteArray()),
            )
        }
    }

    @Test
    fun `kaster IllegalArgumentException når strengen ikke er gyldig base64`() {
        shouldThrow<IllegalArgumentException> {
            kravgrunnlagReferanseTilBehandlingId("dette er ikke base64!!!")
        }
    }
}
