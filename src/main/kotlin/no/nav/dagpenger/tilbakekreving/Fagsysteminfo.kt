package no.nav.dagpenger.tilbakekreving

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

// --- Utgående svar ---
data class FagsysteminfoSvar(
    val hendelsestype: String = "fagsysteminfo_svar",
    val versjon: Int = 1,
    val eksternFagsakId: UUID,
    val hendelseOpprettet: LocalDateTime = LocalDateTime.now(),
    val mottaker: Mottaker,
    val revurdering: Revurdering,
    val behandlendeEnhet: String,
)

/**
 * Serialiserer via [JsonMessage] (samme Jackson-oppsett som resten av
 * meldingsflyten), slik at River kun har ett sted å slå opp for hvordan et
 * svar bygges og sendes.
 */
fun FagsysteminfoSvar.toJson(): String =
    JsonMessage
        .newMessage(
            mapOf(
                "hendelsestype" to hendelsestype,
                "versjon" to versjon,
                "eksternFagsakId" to eksternFagsakId,
                "hendelseOpprettet" to hendelseOpprettet,
                "mottaker" to mottaker,
                "revurdering" to revurdering,
                "behandlendeEnhet" to behandlendeEnhet,
            ),
        ).toJson()

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
 * Dekoder en base64-streng (16 rå bytes) til en [UUID]. Brukes for både
 * eksternFagsakId og kravgrunnlagReferanse.
 *
 * Kaster [IllegalArgumentException] dersom strengen ikke er gyldig base64,
 * eller ikke inneholder nøyaktig 16 bytes.
 */
fun dekodeBase64TilUuid(base64: String): UUID {
    val bytes = Base64.getDecoder().decode(base64)
    require(bytes.size == 16) {
        "Forventet 16 bytes etter base64-dekoding, fikk ${bytes.size}"
    }
    val buffer = ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long)
}
