# Tele: transparent family safety for phone calls and SMS

Tele is a family-safety app modeled on Google Family Link. It shows a parent the call log and texts on a child's Android phone, plus the phone's live location. It is open about what it does: the child sees a permanent notice, every permission is an Android system dialog, and the child cannot turn supervision off from the child app.

Tele does **not** support WhatsApp, record call audio, forward calls, hide its icon, or run without a visible notice.

| Part | What it is | Folder |
|---|---|---|
| **Child app** | Android app (Kotlin, Android 8.0+) on the child's phone. Visible icon, permanent supervision notice, openly requested permissions, optional location sharing. | `android/` |
| **Parent dashboard** | Web dashboard on Firebase (Auth + Firestore). Shows call log, texts, alerts, SOS events, and live location. | `web/` (single file: `web/index.html`) |
| **Security rules** | Firestore rules: each family's data is readable and writable only by that family's parents, and child phones write only their own device. | `firestore.rules` (repo root) |
| **Single-file build** | Script that produces one HTML file for Vercel: the dashboard plus a read-only copy of the project source. | `scripts/build-single-file.py` |

---

## 0. Put this project on GitHub (GitHub Desktop)

1. On github.com, create an empty repository named **`tele`** under your account. Do not add a README or .gitignore there, because this folder already has them.
2. In GitHub Desktop, choose **File → Add local repository…** and select this folder. If it says it isn't a repository, click **create a repository**.
3. Check the **Changes** list. The `google-services.json` file and any `local.properties` file are gitignored, so they should not appear.
4. Write a summary such as "Initial project: child app, dashboard, rules, location", then click **Commit to main**.
5. Click **Publish repository**, and choose the `tele` repository on GitHub.

If you see a 404 when opening `https://github.com/<you>/tele`, the repository either doesn't exist yet or is private. Step 1 creates it.

**Vercel:** the root `index.html` is the one-file dashboard that Vercel serves. The same dashboard lives in `web/index.html`. After you change `web/index.html`, rebuild the root file with `python3 scripts/build-single-file.py index.html` (run from the repo root).

---

## 1. How each requirement is met

| Requirement | Implementation |
|---|---|
| Visible icon | Launcher icon (shield) in `res/mipmap-anydpi-v26`. `HomeActivity` is the launcher, and it asks whether the phone is the parent's or the child's. |
| Permanent foreground notification with the exact text *"This device is supervised by Dad - Call log and SMS visible to parent"* | `SupervisionService` runs as a foreground service. Its notification is ongoing, so it can't be swiped away. The text is in `res/values/strings.xml` (key `supervision_notice`). |
| Permissions requested openly | `Permissions.SETUP` asks for `READ_CALL_LOG`, `READ_SMS`, `SEND_SMS`, `READ_CONTACTS` (plus `POST_NOTIFICATIONS` on Android 13+ so the notice can show). Each is a normal Android system dialog. |
| Real-time location | `LocationService` is a foreground service with `foregroundServiceType="location"`. It shares the phone's location every 5 minutes while supervision is on. It needs `ACCESS_FINE_LOCATION`, then `ACCESS_BACKGROUND_LOCATION` (\"Allow all the time\"), and each is requested in a system dialog after a plain-language explanation. It shows its own visible notice: *\"Tele is sharing this phone's location with Dad now.\"* The parent can pause sharing from the **Live location** tab. The child's notice then changes to *\"Location sharing is paused by Dad.\"* |
| Default SMS app, with a system dialog | `SmsRole.requestIntent()` uses `RoleManager.ROLE_SMS` (Android 10+) or `ACTION_CHANGE_DEFAULT` (older). Tele first shows its own dialog titled \"Set Tele as default SMS app?\", then Android's dialog, which the child must accept. |
| SMS content only when default | `SmsReader.read(includeBody = SmsRole.isDefault())`. When Tele is not the default, the `body` column is never queried, and `body` is stored as `null`. |
| Call log metadata | Number, saved name (from the call log cache, or Contacts if allowed), duration, time, and type: incoming, outgoing, missed, rejected, blocked, voicemail. |
| Parent alert: \"Unknown number 07xxxx called 3 times today\" | The dashboard counts incoming or missed calls from numbers with no saved name since midnight (Africa/Nairobi). At or above the threshold (default 3, adjustable 2–10), it shows the alert. |
| SOS with consent | The child presses SOS, confirms a dialog, and the app sends one SOS event to Firestore with the current location. If the parent has set an emergency number, Tele also texts it. |
| Child can see what the parent sees | \"View what my parent can see\" opens the same call and SMS data, read locally from the phone. |
| Child cannot unlink | There is no unlink option in the child app. Only the parent dashboard removes a device. |
| Must NOT hide the icon | Not done. Tele doesn't hide its icon or run without a notice. |
| Must NOT record call audio | Not done. Tele has no microphone or call-recording code. |
| Must NOT forward calls | Not done. |
| Parent sees data in Firebase | `families/{id}/devices/{id}/calls|sms|sos|location` in Firestore, shown by the dashboard. |

---

## 2. Setup

### 2a. Firebase project (about 10 minutes)

1. Go to <https://console.firebase.google.com>, create a project (for example `tele-family`), and add a **Web app**. Copy its config object.
2. **Authentication → Sign-in method**: enable **Email/Password** and **Anonymous**.
3. **Firestore Database**: create a database in production mode.
4. Install the CLI and deploy the rules from the **repository root** (where `firebase.json` is):
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase use --add        # select your project
   firebase deploy --only firestore:rules
   ```
   To check the rules first, run the 24 emulator tests in `tests/firestore-rules/` (see its section below).
5. In **Project settings → Your apps → Android**, register the package **`com.tele.child`** and download `google-services.json`.

### 2b. Child app (Android Studio)

1. Put `google-services.json` in `android/app/`. It is gitignored and must never be committed.
2. Open `android/` in **Android Studio (Ladybug or newer)**. It uses the Gradle wrapper (Gradle 8.7) and downloads dependencies.
3. Build and install on the **child's phone**: *Run → Run 'app'*, or build a debug APK and install it.
4. Open Tele on the child's phone and go through the steps in order:
   1. **Read what Tele shares.** The child reads and accepts the consent text.
   2. **Allow permissions.** The child sees each Android dialog and allows or denies it.
   3. **Allow location (real-time sharing).** Precise location first, then \"Allow all the time\". Both are Android system dialogs.
   4. **Set as default SMS app (optional).** Only then does Tele show text content. Android asks the child to confirm.
   5. **Link to parent with code.** Enter the 6-digit code from the dashboard. Location sharing starts after linking.

### 2c. Parent dashboard

**On the parent's phone (Android):** open Tele, choose *This is the parent's phone*, and sign in with the same email and password as the web dashboard. The screen lists each linked phone with its latest location, updated live, and opens it in Maps. A phone linked as a child's phone can't open this screen.

**Option 1: Vercel (single file, recommended).**
- Set the Vercel project's **Root Directory** to `web`, so it serves `web/index.html`.
- Or deploy the single file `tele-vercel/index.html`, which is built with `python3 scripts/build-single-file.py`. It has the same dashboard plus the project source viewer.
- Add your Vercel domain under **Firebase → Authentication → Settings → Authorized domains**.

**Option 2: local.**
1. `web/index.html` already has the web config for `calculetor-29a74` (the `FIREBASE_CONFIG` block). To use another project, replace those values.
2. Test locally:
   ```bash
   cd web && python3 -m http.server 8080
   ```
   Then open <http://localhost:8080>. Firebase Auth allows `localhost` by default.

Sign up as a parent, create your family, then click **Link a new phone**. Enter the 6-digit code on the child's phone within 10 minutes.

The dashboard has no demo mode. It always connects to Firebase. Sign-in needs Email/Password enabled, and the dashboard's domain must be listed under **Authentication → Settings → Authorized domains** (for example `tele-j1l5.vercel.app`).

### 2d. Live location: one-time Firestore setup

- Deploy the updated rules (they include `location` and `locationHistory`).
- Firestore → **TTL policies** → create a policy on the **`locationHistory`** collection group with the field **`expiresAt`**. Points then delete automatically after 7 days.
- At the 5-minute interval that is 288 location updates per device per day, and each update writes two documents (`current` and a history point), so about 576 Firestore writes per device per day. Check your Firestore usage against your plan. Adjust `UPDATE_INTERVAL_MS` in `LocationService.kt` to change the rate.

---

### Testing the Firestore rules

From the repo root, in one terminal: `firebase emulators:start --only firestore`. In another terminal: `cd tests/firestore-rules && npm install && npm test`. The tests check that a parent can read and write only their own family, that a child phone writes only its own device, and that nobody can point a parent record at another family.

---

## 3. Data model

```
parents/{uid}                         -> { familyId }
families/{familyId}                   -> { name, parentUids: [uid], createdAt }
  devices/{deviceId}                  -> { ownerUid, label, defaultSms, permissions{...},
                                           lastSeen, appVersion, sosContactPhone, alertThreshold,
                                           locationSharing }
    calls/c{callLogId}                -> { id, number, name, durationSec, ts, type }
    sms/s{smsId}                      -> { id, address, name, ts, folder, body|null, bodyVisible }
    sos/{auto}                        -> { ts, lat, lng, accuracyM, withLocation, acknowledgedBy }
    location/current                  -> { lat, lng, accuracyM, ts }          (live map)
    locationHistory/{auto}            -> { lat, lng, accuracyM, ts, expiresAt } (trail, TTL 7 days)
pairings/{6-digit code}               -> { familyId, createdBy, expiresAt }   (10-minute link codes)
```

Security rules (`firestore.rules`):
- **Parents** can read their own family, including live location. They can change the device label, emergency number, alert threshold and location sharing switch, and they can acknowledge SOS events. They cannot edit calls, texts, location or SOS content.
- **A child phone** can write only to the device record it created, using its own anonymous UID. It cannot read other families.
- **`body` in SMS** must be `null` unless `bodyVisible == true`. The rules enforce this, so the server refuses text content from a phone that isn't the default SMS app.

---

## 4. Consent and location: what changed

The first design shared location only for SOS presses. The current design adds **continuous real-time location** at the parent's request. The differences are:

- Location sharing runs in the background while supervision is on. It has its own visible notice on the child's phone.
- The parent can pause sharing at any time from the dashboard. The child's notice then says that sharing is paused.
- The child is asked for location permission in system dialogs, with an explanation first. Background location is requested separately.
- The child still cannot unlink or turn off supervision from the child app.

The consent text and the continuous-sharing design have **not yet been reviewed** by the family or by a privacy reviewer. Review them before you rely on the app.

---

## 5. Known limitations (please read before using)

1. **Build status.** `assembleDebug` builds with Gradle 8.7 and AGP 8.5.2 (Firebase BOM 33.5.1). The build needs about 1 GB of memory for Gradle on small machines; on a normal computer the default in `android/gradle.properties` is fine. Release builds (R8 minify and resource shrinking) are configured but not yet tested.
2. **Google Play.** Play restricts apps that read call logs or SMS, and it requires a policy declaration. Background location is also restricted. This build is meant for sideloading onto the family's own phones, not for Play Store distribution.
3. **Android \"Restricted settings\".** On Android 13+, sideloaded apps may be blocked from SMS and call-log access until the owner allows it: *Settings → Apps → Tele → ⋮ → Allow restricted settings*. The child or parent may need to do this.
4. **Default SMS app replaces the child's messaging app.** When Tele is the default, the child's usual texting app is replaced by Tele's basic compose screen. The child is told this in the consent text and in the default-SMS dialog.
5. **Android's dialog wording.** Tele shows its own explanation titled \"Set Tele as default SMS app?\" first. Android then shows its own system dialog, and its wording is set by the OS. Tele can't change that wording.
6. **MMS (picture messages) is not logged.** Tele is registered as the MMS receiver but does not store MMS. Quick-reply from incoming calls is not implemented.
7. **Sent texts.** Texts that Tele sends are stored through the system SMS provider. Whether they appear in the \"Sent\" folder depends on the phone's SMS implementation, so check on your device.
8. **Pairing codes.** Codes are 6 digits and last 10 minutes. Any signed-in user could guess one, so consider enabling **Firebase App Check** and rate limiting before a wide rollout.
9. **Timezone.** \"Today\" for the unknown-number alert uses Africa/Nairobi. This is hard-coded in the dashboard.
10. **Uninstalling.** The child can uninstall Tele or revoke permissions, as with any app. The dashboard shows \"Last seen\" and permission status, so a parent can notice. Tele does not block the child from removing it. The child cannot unlink from the child app, but the parent can remove the device from the dashboard. Google Family Link works differently, with the parent controlling changes.
11. **Storage and cost.** Each call and text is stored in Firestore. Consider a TTL or a cleanup job (for example, delete after 90 days). Heartbeats, syncs and location updates count toward Firestore usage.
12. **Real-time location is sensitive.** Background location is one of the most restricted permissions on Google Play. It is also the most sensitive data here. Keep the child informed, and use the pause switch openly. Android's battery saving (Doze) can delay updates, and the location service may not restart after a reboot until the app is opened. Tele then shows a \"Location: partly\" or \"off\" status on the child's phone.
13. **Legal.** This app processes a child's personal data. Check the rules that apply where you live. For Uganda, that includes the Data Protection and Privacy Act, 2019. Be open with your child about what the app does, which is what this design already does.

14. **Shared Firebase project.** Tele uses the existing Firebase project `calculetor-29a74`, with the Android package `sudifenty.com.calculetor`. Deploying `firestore.rules` replaces the Firestore rules for that whole project, so check that no other app in it depends on the current rules before you deploy. Keep the child app's visible name as "Tele".
---

## 6. Project layout

```
tele/
├── README.md
├── .gitignore
├── firebase.json                      Firestore rules config (run firebase commands from here)
├── android/                           Android Studio project (Kotlin), Gradle 8.7 wrapper
│   ├── settings.gradle.kts, build.gradle.kts, gradle.properties
│   ├── gradlew, gradlew.bat, gradle/wrapper/
│   └── app/
│       ├── build.gradle.kts
│       ├── google-services.json       (you add this; gitignored)
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/tele/child/
│           │   ├── HomeActivity.kt          launcher: parent's phone or child's phone
│           │   ├── ParentActivity.kt        parent sign-in and live location list
│           │   ├── MainActivity.kt          child setup steps, SOS button, status
│           │   ├── PairActivity.kt          enter 6-digit link code
│           │   ├── LogActivity.kt           \"what my parent can see\"
│           │   ├── ComposeActivity.kt       minimal SMS send (default-SMS requirement)
│           │   ├── SupervisionService.kt    permanent notice + sync triggers
│           │   ├── LocationService.kt       real-time location (visible notice, parent pause)
│           │   ├── SyncWorker.kt            WorkManager sync every 15 min
│           │   ├── Repo.kt                  Firebase writes/reads
│           │   ├── CallLogReader.kt         call log metadata
│           │   ├── SmsReader.kt             SMS (body only if default)
│           │   ├── SmsDeliverReceiver.kt   incoming SMS when default
│           │   ├── Sos.kt                   location + emergency text
│           │   ├── Permissions.kt           permission list + SMS role
│           │   ├── Prefs.kt, Models.kt, NotificationChannels.kt, TeleApp.kt
│           │   └── BootReceiver.kt, MmsReceiver.kt, RespondViaMessageService.kt
│           └── res/                         layouts, strings, icon
├── web/
│   └── index.html                     Parent dashboard (live Firebase, no demo mode)
└── scripts/
    └── build-single-file.py           Builds tele-vercel/index.html (one-file deliverable)
```
