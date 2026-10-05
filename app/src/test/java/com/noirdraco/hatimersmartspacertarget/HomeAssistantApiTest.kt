package com.noirdraco.hatimersmartspacertarget

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Prüft die Eingabeprüfung vor dem Speichern ([HomeAssistantApi.configError]) — ohne Netzwerk.
 */
class HomeAssistantApiTest {

    private fun settings(
        baseUrl: String = "http://homeassistant.local:8123",
        token: String = "abc.def.ghi",
        entityId: String = "sensor.waschmaschine_fertigstellungszeit"
    ) = TimerSettings(baseUrl, token, entityId)

    @Test
    fun gueltigeEingabe_ergibtKeinenFehler() {
        assertNull(HomeAssistantApi.configError(settings()))
        assertNull(HomeAssistantApi.configError(settings(baseUrl = "https://ha.example.de/")))
        assertNull(HomeAssistantApi.configError(settings(baseUrl = "http://192.168.1.5:8123")))
    }

    @Test
    fun urlOhneSchema_wirdAbgelehnt() {
        // Der Anlassfall: Adresse aus dem Browser ohne „http://“ abgetippt
        assertNotNull(HomeAssistantApi.configError(settings(baseUrl = "homeassistant.local:8123")))
        assertNotNull(HomeAssistantApi.configError(settings(baseUrl = "192.168.1.5:8123")))
    }

    @Test
    fun fremdesSchema_wirdAbgelehnt() {
        assertNotNull(HomeAssistantApi.configError(settings(baseUrl = "ftp://ha.example.de")))
    }

    @Test
    fun tokenMitZeilenumbruch_wirdAbgelehnt() {
        // Beim Kopieren mitgenommener Umbruch mitten im Token — als Header unzulässig
        assertNotNull(HomeAssistantApi.configError(settings(token = "abc\ndef")))
    }
}
