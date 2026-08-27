package com.noirdraco.hatimersmartspacertarget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prüft das Kürzen des `friendly_name` für die Smartspace-Karte ([SensorTitle]).
 */
class SensorTitleTest {

    @Test
    fun zeitEndungWirdAbgeschnitten() {
        // Der Anlassfall vom Gerät
        assertEquals("Waschmaschine", SensorTitle.shorten("Waschmaschine Fertigstellungszeit"))
        assertEquals("Trockner", SensorTitle.shorten("Trockner Restzeit"))
        assertEquals("Spuelmaschine", SensorTitle.shorten("Spuelmaschine Laufzeit"))
    }

    @Test
    fun englischeEndungenAuch() {
        assertEquals("Washer", SensorTitle.shorten("Washer Completion Time"))
        assertEquals("Dryer", SensorTitle.shorten("Dryer Time Remaining"))
        assertEquals("Dishwasher", SensorTitle.shorten("Dishwasher ETA"))
    }

    @Test
    fun grossKleinschreibungEgal() {
        assertEquals("Waschmaschine", SensorTitle.shorten("Waschmaschine FERTIGSTELLUNGSZEIT"))
        assertEquals("Waschmaschine", SensorTitle.shorten("Waschmaschine fertigstellungszeit"))
    }

    @Test
    fun laengsteEndungGewinnt() {
        // Darf nicht an „Laufzeit“ hängenbleiben und „Rest“ stehen lassen
        assertEquals("Waschmaschine", SensorTitle.shorten("Waschmaschine Restlaufzeit"))
        assertEquals("Waschmaschine", SensorTitle.shorten("Waschmaschine Verbleibende Laufzeit"))
    }

    @Test
    fun trennzeichenVerschwindenMit() {
        assertEquals("Waschmaschine", SensorTitle.shorten("Waschmaschine - Restzeit"))
        assertEquals("Waschmaschine", SensorTitle.shorten("Waschmaschine: Restzeit"))
        assertEquals("Waschmaschine", SensorTitle.shorten("Waschmaschine_Restzeit"))
    }

    @Test
    fun nurZeitangabe_bleibtStehen() {
        // Sonst bliebe gar kein Name uebrig
        assertEquals("Restzeit", SensorTitle.shorten("Restzeit"))
        assertEquals("ETA", SensorTitle.shorten("ETA"))
    }

    @Test
    fun ohnePassendeEndung_unveraendert() {
        assertEquals("Waschmaschine", SensorTitle.shorten("Waschmaschine"))
        assertEquals("Muelltonne Abholung", SensorTitle.shorten("Muelltonne Abholung"))
    }

    @Test
    fun randfaelle() {
        assertEquals("", SensorTitle.shorten(""))
        assertEquals("Waschmaschine", SensorTitle.shorten("  Waschmaschine Restzeit  "))
        // Endung mitten im Namen wird nicht angefasst, nur am Ende
        assertEquals("Restzeit Waschmaschine", SensorTitle.shorten("Restzeit Waschmaschine"))
    }
}
