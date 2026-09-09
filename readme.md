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
3. Complete the [Fleet API setup below](#personal-mode-no-summon-pro-backend),
   then generate a short-lived token with `tools/tesla_oauth.py` and import it
4. Select your vehicle and start the service
5. Open the Tesla app → Use Smart Summon as usual

### Personal mode (no Summon Pro backend)

This fork connects directly to Tesla Fleet API and no longer calls
`gate.summon-pro.cc`. It still requires an HTTPS site for the developer
application's public key and one-time registration with Tesla. The OAuth helper
only obtains a user token; it does **not** host a key or register the application.
Before building it:

1. Create and configure your own Tesla developer application. Enable access to
   vehicle information (`vehicle_device_data`) and vehicle location
   (`vehicle_location`). Set its allowed origin to your own HTTPS application
   domain.
2. Generate an EC key pair using OpenSSL:

   ```bash
   openssl ecparam -name prime256v1 -genkey -noout -out private-key.pem
   openssl ec -in private-key.pem -pubout -out public-key.pem
   ```

   Keep `private-key.pem` private and out of Git. Host only the contents of
   `public-key.pem` at:

   ```text
   https://YOUR_APP_DOMAIN/.well-known/appspecific/com.tesla.3p.public-key.pem
   ```

   The public key must remain available there. This application/domain setup is
   separate from pairing a phone key to the vehicle. See Tesla's
   [Fleet API onboarding guide](https://developer.tesla.com/docs/fleet-api/getting-started/what-is-fleet-api).
3. Complete the [partner account registration](https://developer.tesla.com/docs/fleet-api/endpoints/partner-endpoints#register)
   for the Fleet API region you will use. First obtain a
   [partner token](https://developer.tesla.com/docs/fleet-api/authentication/partner-tokens)
   using `grant_type=client_credentials`, your client ID and secret, and the
   regional Fleet API URL as `audience`. Then send this request with that partner
   token (replace `YOUR_APP_DOMAIN` with your actual hostname, without a scheme
   or path):

   ```http
   POST https://fleet-api.prd.na.vn.cloud.tesla.com/api/1/partner_accounts
   Authorization: Bearer YOUR_PARTNER_TOKEN
   Content-Type: application/json

   {"domain":"YOUR_APP_DOMAIN"}
   ```

   Confirm that the registration request succeeds before continuing. The
   `register` operation's URL is `/api/1/partner_accounts`, with no `/register`
   suffix. Its domain must match the developer application's allowed origin as
   described by Tesla. Registration is required for general Fleet API access,
   including reading the vehicle list. It must be completed in each region used.
   Japan and Taiwan use the `na` endpoint above, along with other Asia-Pacific
   countries excluding China; see [regions](https://developer.tesla.com/docs/fleet-api/getting-started/regions-countries).

   The partner token is for this setup step. For personal access in the Android
   app, obtain a **user-authorized access token** in the next step.
4. Register `http://127.0.0.1:8765/callback` as an allowed redirect URI, then
   run the local OAuth helper (credentials remain on your computer):

   ```bash
   export TESLA_CLIENT_ID='your-client-id'
   export TESLA_CLIENT_SECRET='your-client-secret'
   export TESLA_REDIRECT_URI='http://127.0.0.1:8765/callback'
   export TESLA_FLEET_API_AUDIENCE='https://fleet-api.prd.na.vn.cloud.tesla.com'
   python3 tools/tesla_oauth.py
   ```

   Import only the `access_token` value from the generated `tesla-token.json`
   and delete that file afterward. The helper validates OAuth state, uses PKCE,
   and creates the token file with owner-only (`0600`) permissions. If Tesla's
   developer console does not accept a localhost redirect for your application,
   register an HTTPS redirect, run the helper with `--manual`, and paste the final
   redirect URL from the browser. Do not put the client secret in the APK.

   In the browser, sign in to the same Tesla account used in the official Tesla
   app and grant the vehicle information/location permissions. These two OAuth
   operations use different hosts, per Tesla's
   [third-party token documentation](https://developer.tesla.com/docs/fleet-api/authentication/third-party-tokens):

   | Operation | Endpoint |
   | --- | --- |
   | Browser login and consent | `https://auth.tesla.com/oauth2/v3/authorize` |
   | Authorization code/token exchange | `https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3/token` |

   The helper uses these defaults. If you previously set `TESLA_AUTH_BASE_URL`,
   it now controls only the browser authorization base URL.
   `TESLA_TOKEN_BASE_URL` independently controls token exchange; leave both
   unset for normal use. Neither base URL should include `/authorize` or `/token`.
5. Add a restricted Google Maps Android API key and the Fleet API region to
   `~/.gradle/gradle.properties`:

   ```properties
   mapsApiKey=your-google-maps-android-key
   fleetApiBaseUrl=https://fleet-api.prd.na.vn.cloud.tesla.com/
   ```

   Alternatively, export `MAPS_API_KEY` for the build. The build fails early
   when no real Maps key is configured, rather than producing an APK with a
   nonfunctional route-planning map. Restrict the key to your Android package
   name and signing certificate in Google Cloud Console.

   The OAuth helper's `TESLA_FLEET_API_AUDIENCE` must be the same regional URL
   as `fleetApiBaseUrl` (a trailing slash is optional for the helper).

6. Build and install the app, then choose **Import Tesla access token**. Tokens
   are encrypted with an Android Keystore key, excluded from Android backup, and
   must be imported again after expiration.

Vehicle location is polled directly every second so that the 5.8 m global-mode
geofence is not fed a position that is several seconds behind. The app rejects
location data older than two minutes and stops the mock-location service after three
consecutive failures. This mode does not configure Fleet Telemetry and does not
refresh tokens because those operations require credentials that must not be
embedded in an APK.

### Vehicle list troubleshooting

Entering **Select Vehicle** after importing a token only confirms local storage;
the API request is the first server-side validation. Phone-key pairing is not a
prerequisite for the vehicle-list request. The updated app displays an HTTP code
and a setup hint when Tesla rejects the request:

| Error | What to check |
| --- | --- |
| HTTP 401 | The access token may be invalid or expired. Complete OAuth again and import the new `access_token`. |
| HTTP 403 | Check the account's third-party app authorization and granted scopes. |
| HTTP 406 | Requests must include `Content-Type: application/json`; this version sends it on all Fleet API calls. |
| HTTP 412 | Check the hosted public key and partner account registration for this region. Creating credentials or obtaining a token alone does not complete registration. |
| HTTP 421 | Check the Fleet API region and token audience. |
| HTTP 429 | Wait before retrying; Tesla has limited requests. |

These are checks, not a diagnosis from a generic error toast. Other network or
response-parsing failures display the exception type. Share the error code or a
screenshot of the error dialog, not access tokens or client secrets. See Tesla's
[request conventions](https://developer.tesla.com/docs/fleet-api/getting-started/conventions)
for error-code definitions.

An existing installed APK will not change when GitHub files change. Rebuild and
install this version to get the new request headers and error messages. The
registration and OAuth corrections also need to be completed separately; a new
APK cannot perform missing account setup on its own.

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
