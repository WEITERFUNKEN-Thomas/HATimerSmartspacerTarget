package com.noirdraco.hatimersmartspacertarget

import java.time.Instant
import java.time.OffsetDateTime

/**
 * Was aus dem Sensorwert gerade zu zeigen ist.
 */
sealed class CountdownState {
    /** Zielzeit liegt in der Zukunft — der Countdown läuft. */
    data class Running(val endTimeMs: Long) : CountdownState()

    /** Zielzeit erreicht, Nachlauf läuft noch — „Fertig“ anzeigen. */
    data class Finished(val endTimeMs: Long) : CountdownState()

    /** Kein verwertbarer Wert oder Nachlauf vorbei — Target ausblenden. */
    data object None : CountdownState()
}

/**
 * Rechnet einen Home-Assistant-Zustand in einen Zielzeitpunkt um und entscheidet daraus, ob gerade
 * ein Countdown läuft, „Fertig“ dransteht oder nichts angezeigt wird.
 *
 * Zwei Sensorarten werden verstanden:
 *  - **Zeitstempel** (`device_class: timestamp`), Zustand ist ISO-8601, z. B.
 *    `2026-08-26T14:02:00+00:00` — so liefert etwa eine Waschmaschine ihre Fertigstellungszeit.
 *  - **Restzeit als Zahl**, Einheit aus `unit_of_measurement` (s/min/h/d). Basis ist dann der
 *    Zeitpunkt des **Abrufs**, nicht „jetzt“: Der Wert war beim Abruf gültig und läuft seitdem
 *    weiter. Ohne Einheit werden Minuten angenommen.
 *
 * Frei von Android-Ressourcen, damit die Logik als reiner JVM-Unit-Test läuft (wie [HaIcons]).
 */
object Countdown {

    // Zustände, die „kein echter Wert“ bedeuten (Gerät aus, Sensor offline)
    private val UNAVAILABLE_STATES = setOf("unavailable", "unknown", "none", "")

    /**
     * Zielzeitpunkt in Millisekunden seit Epoch, oder `null`, wenn der Zustand keinen hergibt.
     *
     * [fetchedAtMs] ist der Zeitpunkt, zu dem der Wert von Home Assistant geholt wurde — nur für
     * Restzeit-Sensoren relevant, Zeitstempel sind absolut.
     */
    fun parseEndTime(state: String, unit: String, fetchedAtMs: Long): Long? {
        val raw = state.trim()
        if (raw.lowercase() in UNAVAILABLE_STATES) return null
        parseTimestamp(raw)?.let { return it }
        return parseDuration(raw, unit, fetchedAtMs)
    }

    /**
     * Zielzeitpunkt nach einem neuen Abruf, mit Blick auf den vorherigen ([previousEndMs]).
     *
     * Nötig wegen eines Sonderfalls bei Restzeit-Sensoren: „0“ heißt entweder „gerade fertig
     * geworden“ oder „Gerät im Leerlauf“. Für sich genommen ergäbe „0“ bei jedem Abruf ein Ende
     * *jetzt* — ein Gerät, das im Leerlauf 0 meldet, stünde dann dauerhaft auf „Fertig“, weil der
     * Nachlauf mit jedem Abruf von vorn begänne. Deshalb gilt „0“ nur als Ende, wenn vorher ein
     * Zielzeitpunkt bekannt war, und zwar *dieser* (höchstens jetzt, falls früher fertig). Ohne
     * vorherigen Zielzeitpunkt ist es Leerlauf.
     *
     * Zeitstempel-Sensoren betrifft das nicht: Ein vergangener Zeitpunkt ist dort ein echtes Ende.
     */
    fun nextEndTime(previousEndMs: Long?, state: String, unit: String, fetchedAtMs: Long): Long? {
        val end = parseEndTime(state, unit, fetchedAtMs) ?: return null
        val zeroDuration = end == fetchedAtMs && parseTimestamp(state.trim()) == null
        if (!zeroDuration) return end
        return previousEndMs?.coerceAtMost(fetchedAtMs)
    }

    /**
     * [remainMinutes] ist die Nachlaufzeit: So lange nach Ablauf bleibt „Fertig“ stehen, danach
     * verschwindet das Target.
     */
    fun evaluate(endTimeMs: Long?, nowMs: Long, remainMinutes: Int): CountdownState {
        if (endTimeMs == null) return CountdownState.None
        if (nowMs < endTimeMs) return CountdownState.Running(endTimeMs)
        val remainMs = remainMinutes.coerceAtLeast(0) * 60_000L
        return if (nowMs - endTimeMs <= remainMs) {
            CountdownState.Finished(endTimeMs)
        } else {
            CountdownState.None
        }
    }

    private fun parseTimestamp(raw: String): Long? = runCatching {
        // HA liefert den Zeitstempel mit Zonenversatz („…+00:00“), nicht als reines „…Z“
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    }.recoverCatching {
        Instant.parse(raw).toEpochMilli()
    }.getOrNull()

    private fun parseDuration(raw: String, unit: String, fetchedAtMs: Long): Long? {
        // Manche Sensoren schreiben die Einheit in den Zustand („42 min“) statt ins Attribut
        val number = raw.substringBefore(' ').replace(',', '.').toDoubleOrNull() ?: return null
        if (number < 0) return null
        val perUnit = millisPerUnit(unit.ifBlank { raw.substringAfter(' ', "") }) ?: return null
        return fetchedAtMs + (number * perUnit).toLong()
    }

    private fun millisPerUnit(unit: String): Long? = when (unit.trim().lowercase()) {
        "s", "sec", "secs", "second", "seconds" -> 1_000L
        // Ohne Einheit Minuten annehmen — die mit Abstand häufigste Angabe bei Restzeit-Sensoren
        "", "min", "mins", "minute", "minutes" -> 60_000L
        "h", "hr", "hrs", "hour", "hours" -> 3_600_000L
        "d", "day", "days", "tag", "tage" -> 86_400_000L
        else -> null
    }
}
