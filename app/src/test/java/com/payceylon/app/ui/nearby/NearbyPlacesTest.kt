package com.payceylon.app.ui.nearby

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyPlacesTest {
    private fun place(kind: PlaceKind, hasAtm: Boolean = false) =
        NearbyPlace("id-$kind-$hasAtm", kind, "Name", "", 7.48, 80.36, 100, hasAtm)

    private val agent = place(PlaceKind.Agent)
    private val atm = place(PlaceKind.Atm, hasAtm = true)
    private val bankWithAtm = place(PlaceKind.Bank, hasAtm = true)
    private val bank = place(PlaceKind.Bank)
    private val all = listOf(agent, atm, bankWithAtm, bank)

    @Test
    fun atmFilterIncludesBanksWithAnAtm() {
        assertEquals(listOf(atm, bankWithAtm), all.filter(PlaceFilter.Atms::matches))
        assertEquals(listOf(bankWithAtm, bank), all.filter(PlaceFilter.Banks::matches))
        assertEquals(listOf(agent), all.filter(PlaceFilter.Agents::matches))
        assertEquals(all, all.filter(PlaceFilter.All::matches))
    }

    @Test
    fun distancesUseMetresThenKilometres() {
        assertEquals("0 m", formatDistance(0))
        assertEquals("999 m", formatDistance(999))
        assertEquals("1.0 km", formatDistance(1000))
        assertEquals("2.5 km", formatDistance(2460))
    }

    @Test
    fun labelsMentionAnAtmAtABank() {
        assertEquals("Bank · ATM", bankWithAtm.kindLabel)
        assertEquals("Bank", bank.kindLabel)
        assertEquals("PayCeylon agent", agent.kindLabel)
    }
}
