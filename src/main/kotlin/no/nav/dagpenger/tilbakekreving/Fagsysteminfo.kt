package no.nav.dagpenger.tilbakekreving

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// --- Innkommende behov ---
data class FagsysteminfoBehov(
    val hendelsestype: String, // "fagsysteminfo_behov"
    val versjon: Int,
    val eksternFagsakId: String,
    val kravgrunnlagReferanse: String, // base64, dekodes til behandlingId (UUID)
    val hendelseOpprettet: LocalDateTime,
)

// --- Utgående svar ---
data class FagsysteminfoSvar(
    val hendelsestype: String = "fagsysteminfo_svar",
    val versjon: Int = 1,
    val eksternFagsakId: String,
    val hendelseOpprettet: LocalDateTime = LocalDateTime.now(),
    val mottaker: Mottaker,
    val revurdering: Revurdering,
)

data class Mottaker(
    val type: MottakerType,
    val ident: String,
)

enum class MottakerType {
    PERSON,
    ORGANISASJON,
}

data class Revurdering(
    val behandlingId: UUID,
    val årsak: String,
    val årsakTilFeilutbetaling: String,
    val vedtaksdato: LocalDate,
    val utvidPerioder: List<UtvidPeriode>? = null,
)

data class UtvidPeriode(
    val kravgrunnlagPeriode: Periode,
    val vedtaksperiode: Periode,
)

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
)

/**
 * Dekoder [kravgrunnlagReferanse] (base64 av 16 rå bytes) til en [UUID].
 *
 * Kaster [IllegalArgumentException] dersom strengen ikke er gyldig base64,
 * eller ikke inneholder nøyaktig 16 bytes (dvs. ikke kan tolkes som en UUID).
 */
fun kravgrunnlagReferanseTilBehandlingId(kravgrunnlagReferanse: String): UUID {
    val bytes =
        java.util.Base64
            .getDecoder()
            .decode(kravgrunnlagReferanse)
    require(bytes.size == 16) {
        "Forventet 16 bytes etter base64-dekoding av kravgrunnlagReferanse, fikk ${bytes.size}"
    }
    val buffer = java.nio.ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long)
}
