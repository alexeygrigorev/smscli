# SMS Gate (Android app)

Runs on the phone itself, so SMS access works without the desktop or ADB (phone network only).
It is the SMS plugin endpoint of the Resource Gate.

## What leaves the phone

Only `{sender, code, received_at}` for the **one** sender the gate's SMS resource allows, and only
**while the gate reports an open grant**. Message bodies are never sent or logged; codes are never logged
(`Code.toString()` masks the value). Outside a grant the app sends nothing but heartbeats.

## Protocol (phone → gate, bearer = per-device token)

- `POST /v1/heartbeat/{resource}` every 60 s, body `{app_version, sms_permission}`;
  response `{grant_open, sender, max_age_s}`. The gate marks the resource stale after 180 s and alerts;
  `sms_permission=false` alerts immediately.
- `POST /v1/push/{resource}` `{sender, code, received_at}` — sent when a grant is open (newest matching
  code within `max_age_s`, and live on arrival).

All calls: HTTPS only, 5 s connect / 10 s read timeout. Failures are shown in the app (red PROBLEM line),
raise a high-priority notification after two missed heartbeats, and the gate alerts on the missing heartbeat.

## Build

CI builds the APK on every PR (`android` job) and uploads it as the `smsgate-debug-apk` artifact.
Locally: `cd android && ./gradlew testDebugUnitTest assembleDebug` (JDK 21, Android SDK 36).

## Install (sideload)

1. Download `app-debug.apk` from the CI run artifact (`gh run download <run-id> -n smsgate-debug-apk`).
2. With USB debugging on: `adb install -r app-debug.apk` (or copy the APK to the phone and open it,
   allowing "install unknown apps" for the file manager).
3. Open **SMS Gate**, tap **Grant SMS permission** (allow SMS + notifications).
4. Pair: enter gate URL, resource id and device token, then **Save pairing** — or open a pairing link
   `smsgate://pair?url=https://…&resource=<id>&token=<device token>`.
   The device token comes from the gate's CLI/UI; keep it in dataops secrets, never in chat or logs.
5. Tap **Start**. The status line must read `OK` with a recent heartbeat.
6. On vendor ROMs, exempt the app from battery optimization so the heartbeat service is not killed
   (Settings → Apps → SMS Gate → Battery → Unrestricted).

Restricted-settings note: on Android 13+ a sideloaded app may need *Settings → Apps → SMS Gate → ⋮ →
Allow restricted settings* before the SMS permission can be granted.
