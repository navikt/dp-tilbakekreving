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
 * mappingen kan gå fra en statisk verdi (se [StatiskBehandlendeEnhetOppslag]) til
 * f.eks. et oppslag mot et annet system, uten å røre River-koden.
 */
interface BehandlendeEnhetOppslag {
    fun behandlendeEnhet(behandlingId: UUID): Enhet
}

/**
 * Foreløpig løsning: alle behandlinger får samme, konfigurerte enhet.
 */
class StatiskBehandlendeEnhetOppslag(
    private val enhet: Enhet = Enhet(Config.ansvarligEnhet),
) : BehandlendeEnhetOppslag {
    override fun behandlendeEnhet(behandlingId: UUID): Enhet = enhet
}
