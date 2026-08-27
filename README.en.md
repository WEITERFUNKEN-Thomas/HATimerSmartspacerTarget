# HA Timer Smartspacer Target

🇬🇧 English (this file) · 🇩🇪 **Deutsche Version:** [README.md](README.md)

A Smartspacer plugin that shows in your Smartspace (home screen / lock screen) **when** a Home
Assistant sensor will be done — for example "Waschmaschine · done at 14:40".

It deliberately shows the **point in time**, not the time left. A fixed point never needs updating
and cannot go stale, whereas a remaining time is simply wrong once it is a few minutes old. As a
result the plugin uses practically no power while running.

## Setup

### 1. Get the values from Home Assistant

You need three things:

| Field | Where from? | Example |
|---|---|---|
| **Home Assistant base URL** | The address you use to open Home Assistant in your browser — no path, no trailing `/` | `http://homeassistant.local:8123` or `https://ha.example.com` |
| **Long-lived access token** | In Home Assistant: click your **username** at the bottom left → **Security** tab → at the very bottom **Long-lived access tokens** → **Create token**. The token is shown only **once** — copy it right away! | `eyJhbGciOiJIUzI1...` (very long string) |
| **Entity ID** | In Home Assistant: **Developer tools → States**, find your sensor. The entity ID is in the first column. | `sensor.washing_machine_finish_time` |

> **Tip:** You can check the URL, token and entity ID up front in a browser or terminal:
> ```
> curl -H "Authorization: Bearer YOUR_TOKEN" http://homeassistant.local:8123/api/states/sensor.washing_machine_finish_time
> ```
> If JSON with `"state": "..."` comes back, you're good. Alternatively use the **"Test connection"**
> button during setup — it also tells you whether a time can be read from the value at all.

### 2. Which sensors work

Two kinds of sensor produce a target time:

- **Sensors holding a point in time** (`device_class: timestamp`) — the state is a date and time,
  e.g. `2026-08-26T14:02:00+00:00`. This is how most washer, dryer and dishwasher integrations
  report their **finish time**.
- **Sensors holding the time left as a number** — e.g. `42` with unit `min`. Seconds, minutes,
  hours and days are recognised; **without a unit, minutes are assumed**. The remaining time keeps
  counting down from the moment the value was fetched.

Anything else (free text like `Bioabfall Heute`, `on`/`off`, temperatures) produces no target time —
the target then stays hidden.

### 3. Add the target in Smartspacer

1. Open the **Smartspacer app**
2. Go to **Targets** → **+** (add target)
3. Pick **"Home Assistant Timer"** from the list
4. The plugin's **setup page** opens — enter base URL, token and entity ID
5. Optionally tap **"Test connection"**, then **Save**

The sensor's icon from Home Assistant is used. In order: the `icon` attribute, then the sensor's
**name** (a sensor with "Waschmaschine" in its name gets a washing machine even without an icon
set), then `device_class`, otherwise a house symbol.

## How the timer behaves

- **While time is left**, the target shows the sensor's name with the target time below it
  ("done at 14:40"). The name is shortened: "Waschmaschine Fertigstellungszeit" becomes
  "Waschmaschine" — the time is right underneath anyway.
- **Once the time is up**, it says "Done" — by default for **30 minutes**, after which the target
  disappears on its own. Set the duration under **"Keep showing after it ends"**; `0` hides it
  immediately.
- **When the appliance is off** (the sensor reports `unavailable`/`unknown` or no usable value),
  the target is **not there at all**. It comes back by itself once the next cycle starts.
- **Tapping the target** opens that sensor's history in the Home Assistant app (or the web UI in a
  browser if the app isn't installed).

### Several timers

The target can be **added more than once** — washer, dryer and dishwasher side by side. Each
instance has its own URL, token and entity ID.

> **A note on freshness:** The displayed target time cannot go stale — it is a fixed point in time.
> What gets checked is only whether there is a **new** target time (cycle started, appliance
> switched off): roughly **every 15 minutes**, including while the phone has been idle for a while
> (Doze) — the wake-up runs through an alarm that is allowed to fire during idle, and the request
> gets a brief network window with it. So a freshly started cycle may take a few minutes to show up;
> **when it will be done** is exact from then on.

## Notes

- The target **can't be swiped away**; remove it through Smartspacer's settings (this also deletes
  the stored values including the token).
- The access token is stored in **EncryptedSharedPreferences** (androidx.security), encrypted in
  the Android keystore.
- Local addresses (`http://…`) are supported; for external access (e.g. Nabu Casa) always use
  `https`.
- If you want to be extra careful, give the token to a Home Assistant user with as few permissions
  as possible.

## Build

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires [Smartspacer](https://github.com/KieronQuinn/Smartspacer) to be installed on the device.
