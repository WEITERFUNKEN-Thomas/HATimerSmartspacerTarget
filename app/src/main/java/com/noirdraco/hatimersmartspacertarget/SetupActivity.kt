package com.noirdraco.hatimersmartspacertarget

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants

/**
 * Dient sowohl als setupActivity (erstes Einrichten) als auch als configActivity (späteres
 * Bearbeiten) — Smartspacer liefert die smartspacerId als Intent-Extra mit.
 */
class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val smartspacerId = intent.getStringExtra(SmartspacerConstants.EXTRA_SMARTSPACER_ID)
        if (smartspacerId == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_setup)

        val baseUrlField = findViewById<EditText>(R.id.input_base_url)
        val tokenField = findViewById<EditText>(R.id.input_token)
        val entityIdField = findViewById<EditText>(R.id.input_entity_id)
        val remainField = findViewById<EditText>(R.id.input_remain_minutes)
        val statusView = findViewById<TextView>(R.id.text_status)
        val testButton = findViewById<Button>(R.id.button_test)

        TimerPrefs.loadSettings(this, smartspacerId)?.let {
            baseUrlField.setText(it.baseUrl)
            tokenField.setText(it.token)
            entityIdField.setText(it.entityId)
            remainField.setText(it.remainMinutes.toString())
        }

        fun currentInput(): TimerSettings? {
            val baseUrl = baseUrlField.text.toString().trim()
            val token = tokenField.text.toString().trim()
            val entityId = entityIdField.text.toString().trim()
            if (baseUrl.isEmpty() || token.isEmpty() || entityId.isEmpty()) {
                Toast.makeText(this, R.string.setup_error_empty, Toast.LENGTH_SHORT).show()
                return null
            }
            // Leeres oder unsinniges Feld: Standard-Nachlaufzeit statt Fehlermeldung
            val remainMinutes = remainField.text.toString().trim().toIntOrNull()
                ?.coerceAtLeast(0)
                ?: TimerSettings.DEFAULT_REMAIN_MINUTES
            return TimerSettings(baseUrl, token, entityId, remainMinutes)
        }

        testButton.setOnClickListener {
            val settings = currentInput() ?: return@setOnClickListener
            testButton.isEnabled = false
            statusView.setText(R.string.setup_test_running)
            // Netzwerk-Call gehört nicht auf den Main-Thread
            Thread {
                val result = HomeAssistantApi.fetch(settings)
                runOnUiThread {
                    testButton.isEnabled = true
                    statusView.text = when (result) {
                        is FetchResult.Success -> testResultText(result.value)
                        is FetchResult.HttpError -> getString(R.string.setup_test_http, result.code)
                        is FetchResult.NetworkError -> getString(
                            R.string.setup_test_network, result.message
                        )
                    }
                }
            }.start()
        }

        findViewById<Button>(R.id.button_save).setOnClickListener {
            val settings = currentInput() ?: return@setOnClickListener
            TimerPrefs.saveSettings(this, smartspacerId, settings)
            // Fürs Auffrischen per Doze-Heartbeat registrieren, bevor der Alarm gesetzt wird
            TimerPrefs.addTargetId(this, smartspacerId)
            // Sofort abrufen, damit der Countdown nicht erst beim nächsten Heartbeat erscheint
            TimerPrefs.enqueueRefresh(this, smartspacerId)
            TimerPrefs.scheduleHeartbeat(this)
            // Der neustartfeste Anker, der den Heartbeat nach einem Boot wieder in Gang setzt
            TimerPrefs.enqueueAnchor(this)
            setResult(RESULT_OK)
            finish()
        }
    }

    /**
     * Der Verbindungstest zeigt nicht nur den Rohwert, sondern gleich, ob daraus ein Countdown
     * wird — genau daran scheitert die Einrichtung sonst still (Sensor antwortet, taugt aber nicht).
     */
    private fun testResultText(value: SensorValue): String {
        val endTime = Countdown.parseEndTime(value.state, value.unit, value.timestamp)
        val remainingMinutes = endTime
            ?.let { (it - System.currentTimeMillis()) / 60_000L }
            ?: return getString(R.string.setup_test_no_time, value.friendlyName, value.state)
        return getString(R.string.setup_test_ok, value.friendlyName, remainingMinutes)
    }
}
