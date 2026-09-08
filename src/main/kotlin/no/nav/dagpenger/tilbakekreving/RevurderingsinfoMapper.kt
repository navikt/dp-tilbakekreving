package no.nav.dagpenger.tilbakekreving

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.tilbakekreving.behandling.BehandlingResponse
import no.nav.dagpenger.tilbakekreving.behandling.HendelseType
import java.time.LocalDate

/**
 * Kapsler de delene av revurderingsinfo som IKKE kan utledes direkte fra
 * dp-behandling sitt behandling-endepunkt i dag (se ADR i sesjonsmappen).
 * Injiseres slik at en reell mapping kan byttes inn uten å røre River/HTTP-kode.
 */
interface RevurderingsinfoMapper {
    fun årsak(behandling: BehandlingResponse): String

    fun årsakTilFeilutbetaling(behandling: BehandlingResponse): String

    fun vedtaksdato(behandling: BehandlingResponse): LocalDate
}

// MIDLERTIDIG implementasjon — IKKE produksjonsklar.
// All output er tydelig merket som placeholder slik at feil bruk oppdages
// raskt i test/staging og ikke forveksles med reelle forretningsdata.
class PlaceholderRevurderingsinfoMapper : RevurderingsinfoMapper {
    override fun årsak(behandling: BehandlingResponse) = "UAVKLART_NYE_OPPLYSNINGER"

    override fun årsakTilFeilutbetaling(behandling: BehandlingResponse) =
        "PLACEHOLDER: årsakTilFeilutbetaling er ikke avklart mot dp-behandling (behandlingId=${behandling.behandlingId})"

    override fun vedtaksdato(behandling: BehandlingResponse): LocalDate = behandling.sistEndret.toLocalDate()
}

/**
 * De gyldige verdiene for `revurdering.årsak` i fagsysteminfo_svar.
 */
enum class RevurderingÅrsak {
    NYE_OPPLYSNINGER,
    KORRIGERING,
    KLAGE,
    UKJENT,
}

/**
 * Beste innsats-mapping basert på faktiske felt i dp-behandling sin API-spec
 * (`behandletHendelse.type`, `forslagOm` og `avklaringer[].begrunnelse`).
 * dp-behandling har IKKE et dedikert felt for revurderingsårsak, årsak til
 * feilutbetaling eller vedtaksdato — dette er en ANTAKELSE som må verifiseres
 * med dp-behandling-teamet før den kan regnes som produksjonsklar (se ADR i
 * sesjonsmappen, punkt 4). Logger warn hver gang for å gjøre bruken synlig i drift.
 */
class BehandlingsbasertRevurderingsinfoMapper : RevurderingsinfoMapper {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    override fun årsak(behandling: BehandlingResponse): String {
        log.warn {
            "Utleder revurderingsårsak fra hendelseType=${behandling.hendelseType} for " +
                "behandlingId=${behandling.behandlingId} — ANTAKELSE, ikke bekreftet av dp-behandling-teamet."
        }
        val årsak =
            when (behandling.hendelseType) {
                HendelseType.KLAGE_FØRSTEINSTANS,
                HendelseType.KLAGE_KLAGEINSTANS,
                HendelseType.KLAGE_TRYGDERETTEN,
                -> RevurderingÅrsak.KLAGE
                HendelseType.MELDEKORT -> RevurderingÅrsak.KORRIGERING
                HendelseType.OMGJØRING -> RevurderingÅrsak.NYE_OPPLYSNINGER
                HendelseType.SØKNAD,
                HendelseType.MANUELL,
                HendelseType.ARBEIDSSØKERPERIODE,
                HendelseType.FERIETILLEGG,
                HendelseType.SAMORDNING,
                null,
                -> RevurderingÅrsak.UKJENT
            }
        return årsak.name
    }

    override fun årsakTilFeilutbetaling(behandling: BehandlingResponse): String {
        val begrunnelse = behandling.avklaringer.firstNotNullOfOrNull { it.begrunnelse }
        if (begrunnelse == null) {
            log.warn {
                "Fant ingen avklaring med begrunnelse for behandlingId=${behandling.behandlingId} — " +
                    "kan ikke utlede årsakTilFeilutbetaling fra avklaringer."
            }
        }
        return begrunnelse
            ?: "Ingen begrunnelse funnet i avklaringer for behandlingId=${behandling.behandlingId}"
    }

    override fun vedtaksdato(behandling: BehandlingResponse): LocalDate {
        log.warn {
            "Bruker sistEndret som vedtaksdato for behandlingId=${behandling.behandlingId} — " +
                "ANTAKELSE, dp-behandling har ikke et eget vedtakstidspunkt-felt på Behandling-endepunktet."
        }
        return behandling.sistEndret.toLocalDate()
    }
}
