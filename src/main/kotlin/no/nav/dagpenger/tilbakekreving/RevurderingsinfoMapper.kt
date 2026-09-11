package no.nav.dagpenger.tilbakekreving

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.tilbakekreving.behandling.BehandlingResponse
import no.nav.dagpenger.tilbakekreving.behandling.HendelseType
import java.time.LocalDate

/**
 * Kapsler utledningen av de delene av revurderingsinfo som ikke kommer
 * direkte fra dp-behandling sitt behandling-endepunkt, men må avledes fra
 * andre felt (se [BehandlingsbasertRevurderingsinfoMapper]). Injiseres slik
 * at mappingen kan byttes/utvides uten å røre River/HTTP-kode.
 */
interface RevurderingsinfoMapper {
    fun årsak(behandling: BehandlingResponse): String

    fun årsakTilFeilutbetaling(behandling: BehandlingResponse): String

    fun vedtaksdato(behandling: BehandlingResponse): LocalDate
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
 * Mapping basert på faktiske felt i dp-behandling sin API-spec
 * (`behandletHendelse.type` og `avklaringer[].begrunnelse`), bekreftet av
 * dp-behandling-teamet.
 */
class BehandlingsbasertRevurderingsinfoMapper : RevurderingsinfoMapper {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    override fun årsak(behandling: BehandlingResponse): String {
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

    override fun årsakTilFeilutbetaling(behandling: BehandlingResponse): String =
        "Ingen begrunnelse funnet for behandlingId=${behandling.behandlingId}"

    override fun vedtaksdato(behandling: BehandlingResponse): LocalDate = behandling.sistEndret.toLocalDate()
}
