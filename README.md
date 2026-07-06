# HA Sensor Smartspacer Target

Smartspacer-Plugin, das einen Home-Assistant-Sensorwert per REST-API abruft und als
Smartspacer-Target (z. B. auf dem Homescreen / Sperrbildschirm) anzeigt.

## Einrichtung

### 1. Werte in Home Assistant besorgen

Du brauchst drei Angaben:

| Feld | Woher? | Beispiel |
|---|---|---|
| **Home Assistant Base-URL** | Die Adresse, unter der du Home Assistant im Browser öffnest — ohne Pfad, ohne abschließenden `/` | `http://homeassistant.local:8123` oder `https://ha.example.de` |
| **Long-Lived Access Token** | In Home Assistant: unten links auf deinen **Benutzernamen** klicken → Tab **Sicherheit** → ganz unten **Langlebige Zugangstoken** → **Token erstellen**. Der Token wird nur **einmal** angezeigt — direkt kopieren! | `eyJhbGciOiJIUzI1...` (sehr lange Zeichenkette) |
| **Entity-ID** | In Home Assistant: **Entwicklerwerkzeuge → Zustände**, dort den Sensor suchen. Die Entity-ID steht in der ersten Spalte. | `sensor.aussentemperatur` |

> **Tipp:** Ob URL, Token und Entity-ID stimmen, kannst du vorab im Browser/Terminal testen:
> ```
> curl -H "Authorization: Bearer DEIN_TOKEN" http://homeassistant.local:8123/api/states/sensor.aussentemperatur
> ```
> Kommt JSON mit `"state": "..."` zurück, passt alles.

### 2. Target in Smartspacer hinzufügen und Werte eintragen

1. **Smartspacer-App** öffnen
2. Zu **Targets** wechseln → **+** (Target hinzufügen)
3. In der Liste **„Home Assistant Sensor"** auswählen
4. Es öffnet sich automatisch die **Einrichtungsseite dieses Plugins** — hier die drei Werte eintragen:
   - **Home Assistant Base-URL**
   - **Long-Lived Access Token**
   - **Entity-ID**
5. **Speichern** tippen

Direkt nach dem Speichern wird der erste Wert im Hintergrund abgerufen; bis dahin zeigt das
Target kurz „Lädt …". Danach aktualisiert sich der Wert automatisch etwa alle **15 Minuten**.

### 3. Werte später ändern

Zwei Wege führen zurück zur Einrichtungsseite:

- **Auf das Target tippen** (auf dem Homescreen/Sperrbildschirm), oder
- in der Smartspacer-App unter **Targets** das „Home Assistant Sensor"-Target antippen und
  dessen **Einstellungen** öffnen

Die Felder sind dort mit den gespeicherten Werten vorausgefüllt.

### Mehrere Sensoren

Das Target kann **mehrfach hinzugefügt** werden — jede Instanz hat ihre eigene URL, ihren
eigenen Token und ihre eigene Entity-ID. Einfach in Smartspacer ein weiteres
„Home Assistant Sensor"-Target anlegen.

## Hinweise

- Das Target lässt sich **nicht wegwischen**; entfernen geht nur über die
  Smartspacer-Einstellungen (dabei werden die gespeicherten Werte inkl. Token gelöscht).
- Bei Netzwerkfehlern bleibt der **letzte bekannte Wert** stehen — es wird nichts geleert.
- Der Access Token liegt in normalen, **unverschlüsselten** SharedPreferences. Für den
  privaten Gebrauch ok; bei Bedarf auf `androidx.security:security-crypto`
  (EncryptedSharedPreferences) umstellen.
- Der Token sollte in Home Assistant einem Benutzer mit möglichst wenig Rechten gehören,
  wenn du auf Nummer sicher gehen willst.

## Build

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Voraussetzung: [Smartspacer](https://github.com/KieronQuinn/Smartspacer) ist auf dem Gerät
installiert.
