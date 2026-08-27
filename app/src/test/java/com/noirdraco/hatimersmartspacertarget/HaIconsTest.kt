package com.noirdraco.hatimersmartspacertarget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prüft die reine Zuordnungslogik von [HaIcons] (ohne Android-Ressourcen), über die
 * Enum-Identität von [HaIcon].
 */
class HaIconsTest {

    @Test
    fun exactMdiName_matches() {
        assertEquals(HaIcon.TRASH_CAN, HaIcons.resolve("mdi:trash-can"))
        assertEquals(HaIcon.THERMOMETER, HaIcons.resolve("mdi:thermometer"))
        assertEquals(HaIcon.BATTERY, HaIcons.resolve("mdi:battery"))
    }

    @Test
    fun mdiName_variantsMatchViaKeyword() {
        assertEquals(HaIcon.TRASH_CAN, HaIcons.resolve("mdi:trash-can-outline"))
        assertEquals(HaIcon.WEATHER_CLOUDY, HaIcons.resolve("mdi:weather-partly-cloudy"))
        assertEquals(HaIcon.BATTERY, HaIcons.resolve("mdi:battery-charging-80"))
    }

    @Test
    fun weatherNight_winsOverGenericSun() {
        // "weather-night" darf nicht fälschlich als "sunny" matchen (Reihenfolge speziell -> allgemein)
        assertEquals(HaIcon.WEATHER_NIGHT, HaIcons.resolve("mdi:weather-night"))
        assertEquals(HaIcon.WEATHER_RAINY, HaIcons.resolve("mdi:weather-pouring"))
    }

    @Test
    fun prefixOptional() {
        assertEquals(HaIcon.THERMOMETER, HaIcons.resolve("thermometer"))
    }

    @Test
    fun deviceClass_usedWhenNoIcon() {
        assertEquals(HaIcon.THERMOMETER, HaIcons.resolve("", "temperature"))
        assertEquals(HaIcon.WATER, HaIcons.resolve("", "humidity"))
        assertEquals(HaIcon.FLASH, HaIcons.resolve("", "power"))
        assertEquals(HaIcon.DOOR, HaIcons.resolve("", "garage_door"))
    }

    @Test
    fun iconAttribute_takesPrecedenceOverDeviceClass() {
        // Explizites icon schlägt device_class
        assertEquals(HaIcon.TRASH_CAN, HaIcons.resolve("mdi:trash-can", "temperature"))
    }

    @Test
    fun haushaltsgeraete_bekommenEigeneSymbole() {
        assertEquals(HaIcon.WASHING_MACHINE, HaIcons.resolve("mdi:washing-machine"))
        assertEquals(HaIcon.TUMBLE_DRYER, HaIcons.resolve("mdi:tumble-dryer"))
        assertEquals(HaIcon.DISHWASHER, HaIcons.resolve("mdi:dishwasher"))
    }

    @Test
    fun geschirrspueler_wirdNichtZurWaschmaschine() {
        // „dishwasher“ enthält „wash“ — die Regel greift trotzdem nicht, weil sie auf „washing“
        // prüft und der Geschirrspüler ohnehin vorher drankommt.
        assertEquals(HaIcon.DISHWASHER, HaIcons.resolve("mdi:dishwasher-alert"))
        assertEquals(HaIcon.WASHING_MACHINE, HaIcons.resolve("mdi:washing-machine-alert"))
    }

    @Test
    fun haushaltsgeraet_schlaegtTimestampKalender() {
        // Der eigentliche Anlass: Ein Waschmaschinen-Sensor mit device_class „timestamp“ soll die
        // Maschine zeigen, nicht das Kalenderblatt.
        assertEquals(HaIcon.WASHING_MACHINE, HaIcons.resolve("mdi:washing-machine", "timestamp"))
        // Ohne icon-Attribut bleibt es beim Kalender — daran ändert sich nichts.
        assertEquals(HaIcon.CALENDAR, HaIcons.resolve("", "timestamp"))
    }

    @Test
    fun friendlyName_alsDritteQuelle() {
        // Der Fall vom Geraet: Sensor ohne icon-Attribut, device_class timestamp — ohne den Namen
        // gaebe es das generische Kalenderblatt.
        assertEquals(
            HaIcon.WASHING_MACHINE,
            HaIcons.resolve("", "timestamp", "Waschmaschine Fertigstellungszeit")
        )
        assertEquals(HaIcon.TUMBLE_DRYER, HaIcons.resolve("", "timestamp", "Trockner Restzeit"))
        assertEquals(
            HaIcon.DISHWASHER,
            HaIcons.resolve("", "timestamp", "Spuelmaschine Fertig")
        )
    }

    @Test
    fun deutscheGeraetenamen_werdenErkannt() {
        // „Waschmaschine“ enthaelt die Folge „washing“ nicht — das englische Schluesselwort allein
        // wuerde hier nie greifen.
        assertEquals(HaIcon.WASHING_MACHINE, HaIcons.resolve("", "", "Waschmaschine"))
        assertEquals(HaIcon.DISHWASHER, HaIcons.resolve("", "", "Geschirrspueler"))
        assertEquals(HaIcon.DISHWASHER, HaIcons.resolve("", "", "Spülmaschine"))
        assertEquals(HaIcon.TUMBLE_DRYER, HaIcons.resolve("", "", "Waeschetrockner"))
    }

    @Test
    fun iconAttribut_schlaegtNamen() {
        // Explizites icon bleibt die staerkste Quelle
        assertEquals(
            HaIcon.THERMOMETER,
            HaIcons.resolve("mdi:thermometer", "timestamp", "Waschmaschine")
        )
    }

    @Test
    fun name_schlaegtDeviceClass() {
        // Ohne Name bleibt es beim Kalenderblatt — das ist der Vergleichsfall
        assertEquals(HaIcon.CALENDAR, HaIcons.resolve("", "timestamp", ""))
        assertEquals(HaIcon.CALENDAR, HaIcons.resolve("", "timestamp", "Muelltonne"))
    }

    @Test
    fun unknown_fallsBackToHouse() {
        assertEquals(HaIcon.FALLBACK, HaIcons.resolve("mdi:some-unknown-thing"))
        assertEquals(HaIcon.FALLBACK, HaIcons.resolve("", ""))
        assertEquals(HaIcon.FALLBACK, HaIcons.resolve("", "unmapped_class"))
    }
}
