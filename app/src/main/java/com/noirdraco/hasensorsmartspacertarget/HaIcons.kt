package com.noirdraco.hasensorsmartspacertarget

import androidx.annotation.DrawableRes

/**
 * Kuratierte Auswahl an Sensor-Symbolen (Variante B: statt komplettem MDI-Font ein Grundset
 * gebündelter Vektor-Icons). Jeder Eintrag kennt sein Drawable; die Zuordnungslogik selbst
 * ist frei von Android-Ressourcen und dadurch als reine Unit-Logik testbar.
 */
enum class HaIcon(@DrawableRes val drawableRes: Int) {
    TRASH_CAN(R.drawable.ic_mdi_trash_can),
    THERMOMETER(R.drawable.ic_mdi_thermometer),
    WATER(R.drawable.ic_mdi_water),
    WEATHER_SUNNY(R.drawable.ic_mdi_weather_sunny),
    WEATHER_CLOUDY(R.drawable.ic_mdi_weather_cloudy),
    WEATHER_RAINY(R.drawable.ic_mdi_weather_rainy),
    WEATHER_NIGHT(R.drawable.ic_mdi_weather_night),
    BATTERY(R.drawable.ic_mdi_battery),
    FLASH(R.drawable.ic_mdi_flash),
    LIGHTBULB(R.drawable.ic_mdi_lightbulb),
    DOOR(R.drawable.ic_mdi_door),
    WINDOW(R.drawable.ic_mdi_window),
    HOME(R.drawable.ic_mdi_home),
    CALENDAR(R.drawable.ic_mdi_calendar),
    CLOCK(R.drawable.ic_mdi_clock),
    GAUGE(R.drawable.ic_mdi_gauge),
    ACCOUNT(R.drawable.ic_mdi_account),
    POWER_PLUG(R.drawable.ic_mdi_power_plug),

    // Fallback, wenn nichts passt: das App-/Haus-Symbol
    FALLBACK(R.drawable.ic_home_assistant),
}

object HaIcons {

    // Zuordnung über den MDI-Namen (icon-Attribut), von speziell nach allgemein sortiert
    private val nameRules: List<Pair<List<String>, HaIcon>> = listOf(
        listOf("weather-night", "night", "moon") to HaIcon.WEATHER_NIGHT,
        listOf("rain", "pour", "hail") to HaIcon.WEATHER_RAINY,
        listOf("cloud") to HaIcon.WEATHER_CLOUDY,
        listOf("sunny", "sun", "clear-day") to HaIcon.WEATHER_SUNNY,
        listOf("thermo", "temperature") to HaIcon.THERMOMETER,
        listOf("water", "humidity", "drop") to HaIcon.WATER,
        listOf("battery") to HaIcon.BATTERY,
        listOf("plug", "socket") to HaIcon.POWER_PLUG,
        listOf("flash", "lightning", "bolt", "power") to HaIcon.FLASH,
        listOf("bulb", "light") to HaIcon.LIGHTBULB,
        listOf("trash", "delete", "recycle", "bin") to HaIcon.TRASH_CAN,
        listOf("door") to HaIcon.DOOR,
        listOf("window") to HaIcon.WINDOW,
        listOf("calendar") to HaIcon.CALENDAR,
        listOf("clock", "time", "timer") to HaIcon.CLOCK,
        listOf("gauge", "speed", "meter") to HaIcon.GAUGE,
        listOf("account", "human", "person", "walk", "motion") to HaIcon.ACCOUNT,
        listOf("home", "house") to HaIcon.HOME,
    )

    // Zweitquelle: HA-device_class, falls kein passendes icon-Attribut vorhanden ist
    private val deviceClassMap: Map<String, HaIcon> = mapOf(
        "temperature" to HaIcon.THERMOMETER,
        "humidity" to HaIcon.WATER,
        "moisture" to HaIcon.WATER,
        "battery" to HaIcon.BATTERY,
        "power" to HaIcon.FLASH,
        "energy" to HaIcon.FLASH,
        "current" to HaIcon.FLASH,
        "voltage" to HaIcon.FLASH,
        "power_factor" to HaIcon.FLASH,
        "door" to HaIcon.DOOR,
        "garage_door" to HaIcon.DOOR,
        "opening" to HaIcon.DOOR,
        "window" to HaIcon.WINDOW,
        "timestamp" to HaIcon.CALENDAR,
        "date" to HaIcon.CALENDAR,
        "duration" to HaIcon.CLOCK,
        "illuminance" to HaIcon.LIGHTBULB,
        "motion" to HaIcon.ACCOUNT,
        "occupancy" to HaIcon.ACCOUNT,
        "presence" to HaIcon.ACCOUNT,
        "pressure" to HaIcon.GAUGE,
        "atmospheric_pressure" to HaIcon.GAUGE,
        "aqi" to HaIcon.GAUGE,
        "gas" to HaIcon.GAUGE,
    )

    /** Bestimmt das Symbol: zuerst über den MDI-Namen, sonst über device_class, sonst Fallback. */
    fun resolve(mdiIcon: String, deviceClass: String = ""): HaIcon {
        val name = mdiIcon.removePrefix("mdi:").lowercase()
        if (name.isNotBlank()) {
            for ((keywords, icon) in nameRules) {
                if (keywords.any { name.contains(it) }) return icon
            }
        }
        deviceClassMap[deviceClass.trim().lowercase()]?.let { return it }
        return HaIcon.FALLBACK
    }
}
