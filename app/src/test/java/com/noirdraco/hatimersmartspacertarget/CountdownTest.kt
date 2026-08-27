package com.noirdraco.hatimersmartspacertarget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Prüft die reine Countdown-Logik (ohne Android-Ressourcen): Zustand → Zielzeitpunkt → Anzeige.
 */
class CountdownTest {

    private val fetchedAt = Instant.parse("2026-08-26T12:00:00Z").toEpochMilli()

    // --- Zeitstempel-Sensoren -------------------------------------------------------------

    @Test
    fun timestampMitZonenversatz_wirdGelesen() {
        // So liefert Home Assistant device_class "timestamp"
        assertEquals(
            Instant.parse("2026-08-26T14:02:00Z").toEpochMilli(),
            Countdown.parseEndTime("2026-08-26T14:02:00+00:00", "", fetchedAt)
        )
    }

    @Test
    fun timestampMitZ_wirdGelesen() {
        assertEquals(
            Instant.parse("2026-08-26T14:02:00Z").toEpochMilli(),
            Countdown.parseEndTime("2026-08-26T14:02:00Z", "", fetchedAt)
        )
    }

    @Test
    fun timestampInAndererZone_wirdKorrektUmgerechnet() {
        assertEquals(
            Instant.parse("2026-08-26T12:02:00Z").toEpochMilli(),
            Countdown.parseEndTime("2026-08-26T14:02:00+02:00", "", fetchedAt)
        )
    }

    // --- Restzeit-Sensoren ----------------------------------------------------------------

    @Test
    fun restzeitRechnetAbAbrufzeitpunkt() {
        // Der Wert war beim Abruf gültig, nicht "jetzt" — sonst würde der Countdown bei jedem
        // Blick auf den Homescreen von vorn beginnen.
        assertEquals(fetchedAt + 42 * 60_000L, Countdown.parseEndTime("42", "min", fetchedAt))
    }

    @Test
    fun restzeitEinheitenWerdenUnterschieden() {
        assertEquals(fetchedAt + 90_000L, Countdown.parseEndTime("90", "s", fetchedAt))
        assertEquals(fetchedAt + 2 * 3_600_000L, Countdown.parseEndTime("2", "h", fetchedAt))
        assertEquals(fetchedAt + 86_400_000L, Countdown.parseEndTime("1", "d", fetchedAt))
    }

    @Test
    fun restzeitOhneEinheit_giltAlsMinuten() {
        assertEquals(fetchedAt + 15 * 60_000L, Countdown.parseEndTime("15", "", fetchedAt))
    }

    @Test
    fun einheitImZustand_wirdErkannt() {
        // Manche Sensoren schreiben die Einheit in den Zustand statt ins Attribut
        assertEquals(fetchedAt + 42 * 60_000L, Countdown.parseEndTime("42 min", "", fetchedAt))
    }

    @Test
    fun kommazahl_wirdGelesen() {
        assertEquals(fetchedAt + 30_000L, Countdown.parseEndTime("0,5", "min", fetchedAt))
    }

    // --- Was keinen Countdown ergibt -------------------------------------------------------

    @Test
    fun geraetAus_ergibtKeineZielzeit() {
        assertNull(Countdown.parseEndTime("unavailable", "", fetchedAt))
        assertNull(Countdown.parseEndTime("unknown", "", fetchedAt))
        assertNull(Countdown.parseEndTime("", "", fetchedAt))
    }

    @Test
    fun freitext_ergibtKeineZielzeit() {
        assertNull(Countdown.parseEndTime("Bioabfall Heute", "", fetchedAt))
        assertNull(Countdown.parseEndTime("on", "", fetchedAt))
    }

    @Test
    fun unbekannteEinheit_ergibtKeineZielzeit() {
        assertNull(Countdown.parseEndTime("21.5", "°C", fetchedAt))
    }

    @Test
    fun negativeRestzeit_ergibtKeineZielzeit() {
        assertNull(Countdown.parseEndTime("-5", "min", fetchedAt))
    }

    // --- Anzeigeentscheidung ---------------------------------------------------------------

    @Test
    fun zielzeitInDerZukunft_laeuft() {
        val end = fetchedAt + 60_000L
        assertEquals(CountdownState.Running(end), Countdown.evaluate(end, fetchedAt, 30))
    }

    @Test
    fun geradeAbgelaufen_zeigtFertig() {
        val end = fetchedAt - 5 * 60_000L
        assertEquals(CountdownState.Finished(end), Countdown.evaluate(end, fetchedAt, 30))
    }

    @Test
    fun nachlaufVorbei_blendetAus() {
        val end = fetchedAt - 31 * 60_000L
        assertEquals(CountdownState.None, Countdown.evaluate(end, fetchedAt, 30))
    }

    @Test
    fun nachlaufNull_blendetSofortAus() {
        assertEquals(CountdownState.None, Countdown.evaluate(fetchedAt - 1, fetchedAt, 0))
    }

    @Test
    fun ohneZielzeit_blendetAus() {
        assertEquals(CountdownState.None, Countdown.evaluate(null, fetchedAt, 30))
    }


}
