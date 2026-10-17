package com.payceylon.app.ui.nearby

import java.util.Locale

enum class PlaceKind { Agent, Atm, Bank }

/** An ATM, bank branch (both from OpenStreetMap) or PayCeylon cash agent. */
data class NearbyPlace(
    val id: String,
    val kind: PlaceKind,
    val name: String,
    /** Street address; may be empty (OpenStreetMap often has none). */
    val address: String,
    val latitude: Double,
    val longitude: Double,
    /** From the point that was searched around. */
    val distanceMeters: Int,
    val hasAtm: Boolean = false,
    /** OpenStreetMap `opening_hours` syntax, e.g. "Mo-Sa 08:00-20:00". */
    val openingHours: String? = null,
)

/** [banksAvailable] is false when OpenStreetMap couldn't be reached, so only agents are listed. */
data class NearbyPlaces(val places: List<NearbyPlace>, val banksAvailable: Boolean)

enum class PlaceFilter(val label: String) {
    All("All"),
    Agents("Agents"),
    Atms("ATMs"),
    Banks("Banks");

    /** "ATMs" means anywhere with a cash machine, including bank branches that have one. */
    fun matches(place: NearbyPlace): Boolean = when (this) {
        All -> true
        Agents -> place.kind == PlaceKind.Agent
        Atms -> place.hasAtm
        Banks -> place.kind == PlaceKind.Bank
    }
}

val NearbyPlace.kindLabel: String
    get() = when (kind) {
        PlaceKind.Agent -> "PayCeylon agent"
        PlaceKind.Atm -> "ATM"
        PlaceKind.Bank -> if (hasAtm) "Bank · ATM" else "Bank"
    }

/** "350 m", "1.2 km". */
fun formatDistance(meters: Int): String =
    if (meters < 1000) "$meters m" else String.format(Locale.US, "%.1f km", meters / 1000.0)
