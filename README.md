# HA Timer Smartspacer Target

🇬🇧 **English version:** [README.en.md](README.en.md) · 🇩🇪 Deutsch (diese Datei)

Smartspacer-Plugin, das im Smartspace anzeigt (Homescreen / Sperrbildschirm), **wann** ein
Home-Assistant-Sensor fertig wird — zum Beispiel „Waschmaschine · fertig um 14:40".

Angezeigt wird bewusst der **Zeitpunkt**, nicht die Restzeit. Ein fester Zeitpunkt muss nie
aktualisiert werden und kann nicht veralten; eine Restzeit wäre schlicht falsch, sobald sie ein
paar Minuten alt ist. Das Plugin verbraucht dadurch im Betrieb praktisch keinen Strom.

## Einrichtung

### 1. Werte in Home Assistant besorgen

Du brauchst drei Angaben:

| Feld | Woher? | Beispiel |
|---|---|---|
| **Home Assistant Base-URL** | Die Adresse, unter der du Home Assistant im Browser öffnest — ohne Pfad, ohne abschließenden `/` | `http://homeassistant.local:8123` oder `https://ha.example.de` |
| **Long-Lived Access Token** | In Home Assistant: unten links auf deinen **Benutzernamen** klicken → Tab **Sicherheit** → ganz unten **Langlebige Zugangstoken** → **Token erstellen**. Der Token wird nur **einmal** angezeigt — direkt kopieren! | `eyJhbGciOiJIUzI1...` (sehr lange Zeichenkette) |
| **Entity-ID** | In Home Assistant: **Entwicklerwerkzeuge → Zustände**, dort den Sensor suchen. Die Entity-ID steht in der ersten Spalte. | `sensor.waschmaschine_fertigstellungszeit` |

> **Tipp:** Ob URL, Token und Entity-ID stimmen, kannst du vorab im Browser/Terminal testen:
> ```
> curl -H "Authorization: Bearer DEIN_TOKEN" http://homeassistant.local:8123/api/states/sensor.waschmaschine_fertigstellungszeit
> ```
> Kommt JSON mit `"state": "..."` zurück, passt alles. Alternativ in der Einrichtung den
> Button **„Verbindung testen"** nutzen — der sagt dir auch gleich, ob sich aus dem Wert
> überhaupt eine Zeit lesen lässt.

### 2. Welche Sensoren funktionieren

Zwei Arten von Sensoren ergeben eine Zielzeit:

- **Sensoren mit einem Zeitpunkt** (`device_class: timestamp`) — der Zustand ist ein Datum mit
  Uhrzeit, z. B. `2026-08-26T14:02:00+00:00`. So liefern die meisten Waschmaschinen-, Trockner-
  und Spülmaschinen-Integrationen ihre **Fertigstellungszeit**.
- **Sensoren mit einer Restzeit als Zahl** — z. B. `42` mit der Einheit `min`. Erkannt werden
  Sekunden, Minuten, Stunden und Tage; **fehlt die Einheit, werden Minuten angenommen**. Die
  Restzeit wird ab dem Zeitpunkt des Abrufs weitergerechnet.

Alles andere (Freitext wie `Bioabfall Heute`, `on`/`off`, Temperaturen) ergibt keine Zielzeit —
das Target bleibt dann unsichtbar.

### 3. Target in Smartspacer hinzufügen

1. **Smartspacer-App** öffnen
2. Zu **Targets** wechseln → **+** (Target hinzufügen)
3. In der Liste **„Home Assistant Timer"** auswählen
4. Es öffnet sich die **Einrichtungsseite dieses Plugins** — Base-URL, Token und Entity-ID eintragen
5. Optional **„Verbindung testen"** tippen, dann **Speichern**

Als Symbol wird das Icon des Sensors aus Home Assistant übernommen. Reihenfolge: das
`icon`-Attribut, sonst der **Name** des Sensors (ein Sensor mit „Waschmaschine" im Namen bekommt
eine Waschmaschine, auch ohne gesetztes Icon), sonst die `device_class`, sonst ein Haus-Symbol.

## Wie sich der Timer verhält

- **Läuft die Zeit**, zeigt das Target den Namen des Sensors und darunter die Zielzeit
  („fertig um 14:40"). Der Name wird dabei gekürzt: Aus „Waschmaschine Fertigstellungszeit" wird
  „Waschmaschine" — die Zeitangabe steht ja ohnehin darunter.
- **Ist die Zeit abgelaufen**, steht dort „Fertig" — standardmäßig **30 Minuten** lang, danach
  verschwindet das Target von selbst. Die Dauer stellst du bei **„Nach Ablauf noch anzeigen"**
  ein; `0` blendet sofort aus.
- **Ist das Gerät aus** (Sensor liefert `unavailable`/`unknown` oder keinen brauchbaren Wert),
  ist das Target **gar nicht da**. Es taucht von allein wieder auf, sobald der nächste Durchlauf
  startet.
- **Auf das Target tippen** öffnet den Verlauf dieses Sensors in der Home-Assistant-App (ohne
  installierte App die Weboberfläche im Browser).

### Mehrere Timer

Das Target kann **mehrfach hinzugefügt** werden — Waschmaschine, Trockner und Spülmaschine
nebeneinander. Jede Instanz hat ihre eigene URL, ihren eigenen Token und ihre eigene Entity-ID.

> **Hinweis zur Aktualität:** Die angezeigte Zielzeit kann nicht veralten — sie ist ein fester
> Zeitpunkt. Nachgeschaut wird nur, ob es eine **neue** Zielzeit gibt (Durchlauf gestartet, Gerät
> ausgeschaltet): etwa **alle 15 Minuten**, auch wenn das Handy längere Zeit ruht (Doze) — der
> Weckruf läuft über einen Alarm, der im Ruhezustand feuern darf, und der Abruf bekommt dabei ein
> kurzes Netzwerkfenster. Ein frisch gestarteter Waschgang kann also einige Minuten brauchen, bis er
> auftaucht; **wann er fertig ist**, stimmt danach exakt.

## Hinweise

- Das Target lässt sich **nicht wegwischen**; entfernen geht nur über die
  Smartspacer-Einstellungen (dabei werden die gespeicherten Werte inkl. Token gelöscht).
- Der Access Token wird in **EncryptedSharedPreferences** (androidx.security) verschlüsselt
  im Android-Keystore abgelegt.
- Lokale Adressen (`http://…`) werden unterstützt; für externen Zugriff (z. B. Nabu Casa)
  immer `https` verwenden.
- Der Token sollte in Home Assistant einem Benutzer mit möglichst wenig Rechten gehören,
  wenn du auf Nummer sicher gehen willst.

## Build

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Voraussetzung: [Smartspacer](https://github.com/KieronQuinn/Smartspacer) ist auf dem Gerät
installiert.
