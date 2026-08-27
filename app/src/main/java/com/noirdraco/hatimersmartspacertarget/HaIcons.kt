package com.noirdraco.hatimersmartspacertarget

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

    // Haushaltsgeräte — die typischen Countdown-Quellen dieser App
    WASHING_MACHINE(R.drawable.ic_mdi_washing_machine),
    TUMBLE_DRYER(R.drawable.ic_mdi_tumble_dryer),
    DISHWASHER(R.drawable.ic_mdi_dishwasher),

    // Fallback, wenn nichts passt: das App-/Haus-Symbol
    FALLBACK(R.drawable.ic_home_assistant),
}

object HaIcons {

    // Zuordnung über den MDI-Namen (icon-Attribut), von speziell nach allgemein sortiert
    private val nameRules: List<Pair<List<String>, HaIcon>> = listOf(
        // Haushaltsgeräte zuerst: Sie sind der Hauptfall dieser App und dürfen nicht an einer
        // allgemeineren Regel hängenbleiben. Geschirrspüler und Trockner stehen vor der
        // Waschmaschine, damit „wasch“ ihnen nicht dazwischenfunkt.
        //
        // Deutsche Begriffe sind nötig, weil die Regeln auch gegen den friendly_name laufen: In
        // „Waschmaschine“ steckt die Folge „washing“ nicht, das englische Schlüsselwort allein
        // greift dort also nie.
        listOf("dishwasher", "dish-washer", "geschirrspuel", "geschirrspül", "spuelmaschine", "spülmaschine")
            to HaIcon.DISHWASHER,
        listOf("tumble-dryer", "dryer", "trockner") to HaIcon.TUMBLE_DRYER,
        listOf("washing", "laundry", "waschmaschine", "wasch") to HaIcon.WASHING_MACHINE,
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

    /**
     * Bestimmt das Symbol in vier Stufen: MDI-Name aus dem `icon`-Attribut, dann der
     * [friendlyName], dann `device_class`, sonst Fallback.
     *
     * Der Name steht **vor** device_class, weil der viel unspezifischer ist: Ein Timer-Sensor hat
     * fast immer `device_class: timestamp` und bekäme sonst ausnahmslos das Kalenderblatt — auch
     * wenn er „Waschmaschine Fertigstellungszeit“ heißt und damit klar sagt, worum es geht.
     * Genau dieser Fall trat am Gerät auf (27.08.2026): Der Sensor liefert gar kein
     * `icon`-Attribut.
     */
    fun resolve(mdiIcon: String, deviceClass: String = "", friendlyName: String = ""): HaIcon {
        matchByKeyword(mdiIcon.removePrefix("mdi:"))?.let { return it }
        matchByKeyword(friendlyName)?.let { return it }
        deviceClassMap[deviceClass.trim().lowercase()]?.let { return it }
        return HaIcon.FALLBACK
    }

    private fun matchByKeyword(raw: String): HaIcon? {
        val text = raw.lowercase()
        if (text.isBlank()) return null
        for ((keywords, icon) in nameRules) {
            if (keywords.any { text.contains(it) }) return icon
        }
        return null
    }
}
