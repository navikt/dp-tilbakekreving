package no.nav.dagpenger.tilbakekreving

import java.util.UUID

/**
 * Representerer NAV-enheten som er ansvarlig for en behandling.
 */
data class Enhet(
    val id: String,
)

/**
 * Kapsler utledningen av ansvarlig enhet for en behandling. Injiseres slik at
 * mappingen kan gå fra en statisk verdi (se [StatiskAnsvarligEnhetMapper]) til
 * f.eks. et oppslag mot et annet system, uten å røre River-koden.
 */
interface AnsvarligEnhetMapper {
    fun ansvarligEnhet(behandlingId: UUID): Enhet
}

/**
 * Foreløpig løsning: alle behandlinger får samme, konfigurerte enhet.
 */
class StatiskAnsvarligEnhetMapper(
    private val enhet: Enhet = Enhet(Config.ansvarligEnhet),
) : AnsvarligEnhetMapper {
    override fun ansvarligEnhet(behandlingId: UUID): Enhet = enhet
}
