# 🚗 Summon Pro — Extend Tesla Smart Summon Range

**Summon Pro** is an Android app that lifts the 85-meter geofence limit imposed by Tesla’s Smart Summon feature.  
It works by spoofing your phone’s GPS location to always appear near your Tesla, enabling full parking lot navigation — even from across town.

> ⚠️ This app does **not** drive your car. It simply removes the leash.  
> Tesla's own Autopilot/Smart Summon stack still controls all movement, braking, and obstacle detection.

---

## 🔧 How it works

Tesla Smart Summon requires your phone to be within ~85 m of the car.  
Summon Pro connects to your vehicle using the [Tesla Fleet API](https://developer.tesla.com/docs/fleet-api) and:

1. Polls the vehicle location directly from Tesla Fleet API.
2. Calculates a spoofed phone location within ~80 m of the car (like dangling a digital “carrot on a stick”).
3. Updates your phone’s mock GPS location accordingly.
4. Tesla app thinks you’re nearby → Smart Summon stays active.

This trick allows remote Smart Summon from **virtually anywhere** — as long as the car has signal.

---

## 🧪 Status

This community-maintained personal-mode fork must be built from source. It no
longer depends on the original Summon Pro services. Validate your Fleet API
region, scopes, and vehicle response format before relying on it.

---

## 📱 Requirements

- Android 8.0+
- Developer options enabled
- “Mock location app” set to **Summon Pro**
- Tesla account with Fleet API access
- Tesla vehicle with Smart Summon capability (FSD or EAP)

---

## 🛠 Features
- 🚗 **Custom route path planning** — drag & drop waypoints for smarter summon paths (experimental)
- 🔁 Direct vehicle-location polling via Tesla Fleet API
- 🧭 Dynamic fake GPS location near the car
- 📶 Works over Wi-Fi or cellular network
- 🚫 No root required
- 🌙 Material You theme (light/dark)
- 🔐 Manual short-lived access-token import for personal use

---

## ⚙️ Setup

1. Enable Developer Mode on your Android phone  
   *(Settings → About Phone → Tap Build Number 7x)*  
2. Go to **Developer options** → Select mock location app → choose **Summon Pro**
3. Generate a short-lived token with `tools/tesla_oauth.py` and import it
4. Select your vehicle and start the service
5. Open the Tesla app → Use Smart Summon as usual

### Personal mode (no Summon Pro backend)

This fork connects directly to Tesla Fleet API and no longer calls
`gate.summon-pro.cc`. Before building it:

1. Create and configure your own Tesla developer application.
2. Register `http://127.0.0.1:8765/callback` as an allowed redirect URI, then
   run the local OAuth helper (credentials remain on your computer):

   ```bash
   export TESLA_CLIENT_ID='your-client-id'
   export TESLA_CLIENT_SECRET='your-client-secret'
   export TESLA_REDIRECT_URI='http://127.0.0.1:8765/callback'
   python3 tools/tesla_oauth.py
   ```

   Import only the `access_token` value from the generated `tesla-token.json`
   and delete that file afterward. The helper validates OAuth state, uses PKCE,
   and creates the token file with owner-only (`0600`) permissions. If Tesla's
   developer console does not accept a localhost redirect for your application,
   register an HTTPS redirect, run the helper with `--manual`, and paste the final
   redirect URL from the browser. Do not put the client secret in the APK.
3. Set the Fleet API region in `~/.gradle/gradle.properties` when North America is
   not appropriate, for example:

   ```properties
   fleetApiBaseUrl=https://fleet-api.prd.eu.vn.cloud.tesla.com/
   ```

4. Build and install the app, then choose **Import Tesla access token**. Tokens
   are encrypted with an Android Keystore key, excluded from Android backup, and
   must be imported again after expiration.

Vehicle location is polled directly every ten seconds. The app rejects location
data older than two minutes and stops the mock-location service after three
consecutive failures. This mode does not configure Fleet Telemetry and does not
refresh tokens because those operations require credentials that must not be
embedded in an APK.

---

## ❓FAQ

### Does this app drive the car?
**No.** Only Tesla’s Autopilot stack handles vehicle movement. Summon Pro only tricks the app into thinking you're nearby.

### Will this work on iPhone?
Unfortunately, no. iOS does not allow mock location apps. I'm an iPhone user too 😢

### Is this safe?
Location spoofing removes a manufacturer safety boundary. Keep the vehicle in
view, remain ready to stop it, and follow all local rules and Tesla instructions.
The app stops spoofing after repeated API failures, but that is not a guarantee
of safe vehicle operation.

### Will this work in China?
Tesla's Chinese fleet is on a different API domain. Support is planned, but you’ll need a separate developer account on [developer.tesla.cn](https://developer.tesla.cn).

---

## 🧑‍💻 For developers

This project is written in **Kotlin** and uses:

- Jetpack Navigation
- Kotlin Coroutines
- Direct Tesla Fleet API polling
- Manual access-token import (OAuth exchange remains outside the APK)
- ForegroundService for GPS mocking
- Google Maps Mobile SDK

Mock location is updated using Android's `LocationManager.setTestProviderLocation`.

---

## 🧾 License

This project is licensed under the **GNU General Public License v3.0**.  
See the [LICENSE](./LICENSE) file for full terms.

---

## ❤️ Support

If you found this useful or saved you a walk, feel free to star the repo, report issues, or donate:  
[☕ Buy me a coffee](https://ko-fi.com/justjdupuis)

---

> Made for curious Tesla owners who want to push boundaries, not break rules.  
> Summon smarter. Summon farther.  
> **Summon Pro.**
