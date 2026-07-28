package com.noirdraco.hasensorsmartspacertarget

/**
 * Entscheidet, ob das Target bei einem bestimmten Sensor-Zustand überhaupt angezeigt wird.
 *
 * Hintergrund: Viele Sensoren haben *immer* einen Zustand (z. B. eine Müllabfuhr-Entität mit
 * „Bioabfall Heute“ / „Bioabfall in 7 tagen“). Ohne Filter stünde das Target dauerhaft im
 * Smartspace, obwohl es nur in bestimmten Fällen interessiert.
 *
 * Die Regel ist bewusst ein einfacher Textvergleich (kommagetrennte Begriffe, Teiltreffer,
 * Groß-/Kleinschreibung egal) statt einer Zahlen-Schwelle — HA-Zustände sind oft Freitext, und
 * so lassen sich neue Fälle ohne Code-Änderung abdecken.
 *
 * Frei von Android-Ressourcen, damit die Logik als reiner Unit-Test läuft (wie [HaIcons]).
 */
object ShowRule {

    /**
     * `true`, wenn das Target angezeigt werden soll. Eine leere Regel bedeutet „immer anzeigen“
     * (Verhalten wie vor der Einführung des Filters).
     */
    fun matches(state: String, rule: String): Boolean {
        val keywords = keywords(rule)
        if (keywords.isEmpty()) return true
        val haystack = state.trim().lowercase()
        return keywords.any { haystack.contains(it) }
    }

    /** `true`, wenn überhaupt ein Filter gesetzt ist (Regel enthält mindestens einen Begriff). */
    fun isSet(rule: String): Boolean = keywords(rule).isNotEmpty()

    private fun keywords(rule: String): List<String> =
        rule.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}
