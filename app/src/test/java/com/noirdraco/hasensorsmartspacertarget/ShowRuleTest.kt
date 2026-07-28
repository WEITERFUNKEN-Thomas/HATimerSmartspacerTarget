package com.noirdraco.hasensorsmartspacertarget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den Anzeige-Filter [ShowRule] (reine Textlogik, ohne Android-Ressourcen).
 * Die Beispielzustände stammen von einer Müllabfuhr-Entität („Nächste Abholung“).
 */
class ShowRuleTest {

    @Test
    fun emptyRule_alwaysShows() {
        assertTrue(ShowRule.matches("Bioabfall in 7 tagen", ""))
        assertTrue(ShowRule.matches("", ""))
        // Nur Kommas/Leerzeichen zählen nicht als Filter
        assertTrue(ShowRule.matches("Bioabfall in 7 tagen", " , "))
        assertFalse(ShowRule.isSet(" , "))
    }

    @Test
    fun keyword_matchesAsSubstring() {
        assertTrue(ShowRule.matches("Bioabfall Heute", "Heute"))
        assertFalse(ShowRule.matches("Bioabfall in 7 tagen", "Heute"))
    }

    @Test
    fun caseIsIgnored() {
        assertTrue(ShowRule.matches("Bioabfall HEUTE", "heute"))
        assertTrue(ShowRule.matches("bioabfall heute", "Heute"))
    }

    @Test
    fun anyOfSeveralKeywordsIsEnough() {
        val rule = "Heute, Morgen"
        assertTrue(ShowRule.matches("Bioabfall Heute", rule))
        assertTrue(ShowRule.matches("Restmüll Morgen", rule))
        assertFalse(ShowRule.matches("Bioabfall in 6 tagen", rule))
    }

    @Test
    fun surroundingWhitespaceIsIrrelevant() {
        assertTrue(ShowRule.matches("  Bioabfall Heute  ", "  Heute  "))
    }

    @Test
    fun unavailableStateIsFilteredOut() {
        // Ist ein Filter gesetzt, soll ein Aussetzer des Sensors das Target nicht einblenden
        assertFalse(ShowRule.matches("unavailable", "Heute"))
        assertFalse(ShowRule.matches("unknown", "Heute"))
    }

    @Test
    fun isSet_detectsConfiguredRule() {
        assertFalse(ShowRule.isSet(""))
        assertFalse(ShowRule.isSet("   "))
        assertTrue(ShowRule.isSet("Heute"))
        assertTrue(ShowRule.isSet("Heute, Morgen"))
    }
}
