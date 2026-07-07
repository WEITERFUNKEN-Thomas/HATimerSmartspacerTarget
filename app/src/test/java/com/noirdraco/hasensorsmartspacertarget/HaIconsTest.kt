package com.noirdraco.hasensorsmartspacertarget

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
    fun unknown_fallsBackToHouse() {
        assertEquals(HaIcon.FALLBACK, HaIcons.resolve("mdi:some-unknown-thing"))
        assertEquals(HaIcon.FALLBACK, HaIcons.resolve("", ""))
        assertEquals(HaIcon.FALLBACK, HaIcons.resolve("", "unmapped_class"))
    }
}
