package com.noirdraco.hatimersmartspacertarget

/**
 * Kürzt den `friendly_name` eines Home-Assistant-Sensors auf das, was auf der Smartspace-Karte
 * wirklich gebraucht wird.
 *
 * Timer-Sensoren heißen in HA typischerweise „<Gerät> <Zeitangabe>“, also etwa „Waschmaschine
 * Fertigstellungszeit“. Auf der schmalen Karte ist das zu lang und wird abgeschnitten — dabei steht
 * die Zeitangabe ohnehin direkt darunter als Countdown. Das Gerät allein genügt.
 *
 * Frei von Android-Ressourcen, damit die Logik als reiner JVM-Unit-Test läuft (wie [Countdown] und
 * [HaIcons]).
 */
object SensorTitle {

    /**
     * Endungen, die abgeschnitten werden — lang vor kurz geprüft, damit „Restlaufzeit“ nicht schon
     * an „Laufzeit“ hängenbleibt und ein „Rest“ stehen lässt.
     *
     * Bewusst nur eindeutige Zeitbegriffe: Ein bloßes „Zeit“ oder „Ende“ würde auch Namen zerlegen,
     * die gar keine Zeitangabe im Sinn haben.
     */
    private val SUFFIXES: List<String> = listOf(
        // Deutsch
        "fertigstellungszeit", "fertigstellung", "restlaufzeit", "restzeit",
        "verbleibende laufzeit", "verbleibende zeit", "endzeit", "laufzeit",
        // Englisch
        "completion time", "finish time", "finished at", "time remaining",
        "remaining time", "time left", "end time", "remaining", "eta"
    ).sortedByDescending { it.length }

    // Trennzeichen, die nach dem Abschneiden am Ende zurückbleiben können
    private val SEPARATORS = charArrayOf(' ', '-', '–', '—', ':', ',', '_', '/', '|')

    /**
     * Gibt den gekürzten Namen zurück — oder den unveränderten, wenn nichts Sinnvolles übrig bliebe.
     * Ein Name, der *nur* aus einer Zeitangabe besteht („Restzeit“), bleibt deshalb stehen.
     */
    fun shorten(friendlyName: String): String {
        val name = friendlyName.trim()
        val lower = name.lowercase()
        for (suffix in SUFFIXES) {
            if (lower.length <= suffix.length || !lower.endsWith(suffix)) continue
            val cut = name.substring(0, name.length - suffix.length).trimEnd(*SEPARATORS)
            if (cut.isNotBlank()) return cut
        }
        return name
    }
}
