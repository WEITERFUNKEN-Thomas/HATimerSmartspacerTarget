package com.noirdraco.hasensorsmartspacertarget

import androidx.annotation.DrawableRes

/**
 * Bildet den MDI-Icon-Namen aus dem Home-Assistant-Attribut "icon" (z. B. "mdi:trash-can")
 * auf ein gebündeltes Vektor-Drawable ab (Variante B: kuratierte Auswahl statt komplettem
 * MDI-Font). Unbekannte Icons fallen auf das Haus-Symbol zurück.
 *
 * Statt jeden einzelnen MDI-Namen zu pflegen (es gibt tausende), wird per Schlüsselwort
 * gematcht — die Liste ist von speziell nach allgemein sortiert (das erste passende gewinnt).
 */
object HaIcons {

    private val rules: List<Pair<List<String>, Int>> = listOf(
        listOf("weather-night", "night", "moon") to R.drawable.ic_mdi_weather_night,
        listOf("rain", "pour", "hail") to R.drawable.ic_mdi_weather_rainy,
        listOf("cloud") to R.drawable.ic_mdi_weather_cloudy,
        listOf("sunny", "sun", "clear-day") to R.drawable.ic_mdi_weather_sunny,
        listOf("thermo", "temperature") to R.drawable.ic_mdi_thermometer,
        listOf("water", "humidity", "drop") to R.drawable.ic_mdi_water,
        listOf("battery") to R.drawable.ic_mdi_battery,
        listOf("plug", "socket") to R.drawable.ic_mdi_power_plug,
        listOf("flash", "lightning", "bolt", "power") to R.drawable.ic_mdi_flash,
        listOf("bulb", "light") to R.drawable.ic_mdi_lightbulb,
        listOf("trash", "delete", "recycle", "bin") to R.drawable.ic_mdi_trash_can,
        listOf("door") to R.drawable.ic_mdi_door,
        listOf("window") to R.drawable.ic_mdi_window,
        listOf("calendar") to R.drawable.ic_mdi_calendar,
        listOf("clock", "time", "timer") to R.drawable.ic_mdi_clock,
        listOf("gauge", "speed", "meter") to R.drawable.ic_mdi_gauge,
        listOf("account", "human", "person", "walk", "motion") to R.drawable.ic_mdi_account,
        listOf("home", "house") to R.drawable.ic_mdi_home,
    )

    @DrawableRes
    fun resolve(mdiIcon: String): Int {
        val name = mdiIcon.removePrefix("mdi:").lowercase()
        if (name.isBlank()) return R.drawable.ic_home_assistant
        for ((keywords, res) in rules) {
            if (keywords.any { name.contains(it) }) return res
        }
        return R.drawable.ic_home_assistant
    }
}
