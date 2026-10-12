# Tele: transparent family safety for phone calls and SMS

Tele is two parts:

| Part | What it is | Folder |
|---|---|---|
| **Child app** | Android app (Kotlin, Android 8.0+) on the child's phone. Visible icon, permanent supervision notice, and openly requested permissions. | `child-app/` |
| **Parent dashboard** | Web dashboard on Firebase (Auth + Firestore). Shows the child's call log, texts, alerts and SOS events. | `parent-dashboard/` |
| **Security rules** | Firestore rules that limit each side to its own data. | `firebase/` |

Tele does **not** support WhatsApp, record call audio, forward calls, hide its icon, or run without a visible notice.

---

## 1. How each requirement is met

| Requirement | Implementation |
|---|---|
| Visible icon | Launcher icon (shield) in `res/mipmap-anydpi-v26`. The app has a normal launcher activity. |
| Permanent foreground notification with the exact text *"This device is supervised by Dad - Call log and SMS visible to parent"* | `SupervisionService` runs as a `dataSync` foreground service. Its notification is `setOngoing(true)`, so it can't be swiped away. The text is in `res/values/strings.xml`. |
| Permissions requested openly | `Permissions.SETUP` asks for `READ_CALL_LOG`, `READ_SMS`, `SEND_SMS`, `READ_CONTACTS` (plus `POST_NOTIFICATIONS` on Android 13+ so the notice can show). Each is a normal Android system dialog. |
| Real-time location | `LocationService` shares the phone's location about once a minute while supervision is on. It needs `ACCESS_FINE_LOCATION` and then `ACCESS_BACKGROUND_LOCATION` ("Allow all the time"), each asked in a system dialog after an explanation. It has its own visible notice: "Tele is sharing this phone's location with Dad now." The parent can pause it from the Live location tab, and the child's notice then changes to "paused by Dad". |
| Default SMS app, with a system dialog | `SmsRole.requestIntent()` uses `RoleManager.ROLE_SMS` (Android 10+) or `ACTION_CHANGE_DEFAULT` (older). Tele first shows its own dialog titled "Set Tele as default SMS app?", then Android's dialog, which the child must accept. |
| SMS content only when default | `SmsReader.read(includeBody = SmsRole.isDefault())`. When Tele is not the default, the `body` column is never queried, and `body` is stored as `null`. |
| Call log metadata | Number, saved name (from the call log cache, or Contacts if allowed), duration, time, and type: incoming, outgoing, missed, rejected, blocked, voicemail. |
| Parent alert: "Unknown number 07xxxx called 3 times today" | The dashboard counts incoming or missed calls from numbers with no saved name since midnight (Africa/Nairobi). At or above the threshold (default 3, adjustable 2–10), it shows the alert. |
| SOS with consent | The child presses SOS, confirms a dialog, and the app sends one SOS event to Firestore with the current location. If the parent has set an emergency number, Tele also texts it. |
| Child can see what the parent sees | "View what my parent can see" opens the same call and SMS data, read locally from the phone. |
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
4. Install the CLI and deploy the rules:
   ```bash
   npm install -g firebase-tools
   firebase login
   cd firebase
   firebase use --add        # select your project
   firebase deploy --only firestore:rules
   ```
5. In **Project settings → Your apps → Android**, register the package **`com.tele.child`** and download `google-services.json`.

### 2b. Child app (Android Studio)

1. Put `google-services.json` in `child-app/app/`.
2. Open `child-app/` in **Android Studio (Ladybug or newer)**. It generates the Gradle wrapper and downloads dependencies.
3. Build and install on the **child's phone**: *Run → Run 'app'*, or build a debug APK and install it.
4. Open Tele on the child's phone and go through the steps in order:
   1. **Read what Tele shares.** The child reads and accepts the consent text.
   2. **Allow permissions.** The child sees each Android dialog and allows or denies it.
   3. **Allow location (real-time sharing).** Precise location first, then "Allow all the time". Both are Android system dialogs.
   4. **Set as default SMS app (optional).** Only then does Tele show text content. Android asks the child to confirm.
   5. **Link to parent with code.** Enter the 6-digit code from the dashboard. Location sharing starts after linking.

### 2c. Parent dashboard

1. Open `parent-dashboard/index.html`, and replace the `FIREBASE_CONFIG` placeholder values with your web config.
2. Test locally:
   ```bash
   cd parent-dashboard && python3 -m http.server 8080
   ```
   Then open <http://localhost:8080>. Firebase Auth allows `localhost` by default.
3. Or host it: `cd firebase && firebase deploy --only hosting`. Firebase Hosting uses the `parent-dashboard/` folder (see `firebase/firebase.json`). Add your hosting domain under **Authentication → Settings → Authorized domains**.
4. Sign up as a parent, create your family, then click **Link a new phone**. Enter the 6-digit code on the child's phone within 10 minutes.

**Demo mode:** while the config still says `REPLACE_ME`, the dashboard shows sample data, so you can see the layout without Firebase.

---

### 2d. Live location: one-time Firestore setup

- Deploy the updated rules (they include `location` and `locationHistory`).
- Firestore → **TTL policies** → create a policy on the **`locationHistory`** collection group with the field **`expiresAt`**. Points then delete automatically after 7 days.
- At a one-minute interval that is about 1,440 location updates per device per day, and each update writes two documents (`current` and a history point), so about 2,880 Firestore writes per device per day. Check your Firestore usage against your plan. Adjust `UPDATE_INTERVAL_MS` in `LocationService.kt` to change the rate.

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

Security rules (`firebase/firestore.rules`):
- **Parents** can read their own family, including live location. They can change the device label, emergency number, alert threshold and location sharing switch, and they can acknowledge SOS events. They cannot edit calls, texts, location or SOS content.
- **A child phone** can write only to the device record it created, using its own anonymous UID. It cannot read other families.
- **`body` in SMS** must be `null` unless `bodyVisible == true`. The rules enforce this, so the server refuses text content from a phone that isn't the default SMS app.

---

## 4. Known limitations (please read before using)

1. **Not compiled in this environment.** I wrote the Android project without an Android SDK or Gradle available, so I couldn't build the APK. Opening it in Android Studio may show small fixes. The dashboard JavaScript was syntax-checked and tested in a headless DOM (demo mode), but the **Firestore rules have not been run against the emulator**. Test them with `firebase emulators:start` before you rely on them.
2. **Google Play.** Play restricts apps that read call logs or SMS, and it requires a policy declaration. This build is meant for sideloading onto the family's own phones, not for Play Store distribution.
3. **Android "Restricted settings".** On Android 13+, sideloaded apps may be blocked from SMS and call-log access until the owner allows it: *Settings → Apps → Tele → ⋮ → Allow restricted settings*. The child or parent may need to do this.
4. **Default SMS app replaces the child's messaging app.** When Tele is the default, the child's usual texting app is replaced by Tele's basic compose screen. The child is told this in the consent text and in the default-SMS dialog.
5. **Android's dialog wording.** Tele shows its own explanation titled "Set Tele as default SMS app?" first. Android then shows its own system dialog, and its wording is set by the OS. Tele can't change that wording.
6. **MMS (picture messages) is not logged.** Tele is registered as the MMS receiver but does not store MMS. Quick-reply from incoming calls is not implemented.
7. **Sent texts.** Texts that Tele sends are stored through the system SMS provider. Whether they appear in the "Sent" folder depends on the phone's SMS implementation, so check on your device.
8. **Pairing codes.** Codes are 6 digits and last 10 minutes. Any signed-in user could guess one, so consider enabling **Firebase App Check** and rate limiting before a wide rollout.
9. **Timezone.** "Today" for the unknown-number alert uses Africa/Nairobi. This is hard-coded in the dashboard.
10. **Uninstalling.** The child can uninstall Tele or revoke permissions, as with any app. The dashboard shows "Last seen" and permission status, so a parent can notice. Tele does not block the child from removing it. Decide with your family whether that is acceptable. Google Family Link works differently, with the parent controlling changes.
11. **Storage and cost.** Each call and text is stored in Firestore. Consider a TTL or a cleanup job (for example, delete after 90 days). Heartbeats and syncs count toward Firestore usage.
12. **Real-time location is sensitive.** Background location is one of the most restricted permissions on Google Play. It is also the most sensitive data here. Keep the child informed, and use the pause switch openly. Be aware that Android's battery saving (Doze) can delay updates, and that the location service may not restart after a reboot until the app is opened. Tele then shows a "Location: partly" or "off" status on the child's phone.
13. **Legal.** This app processes a child's personal data. Check the rules that apply where you live. For Uganda, that includes the Data Protection and Privacy Act, 2019. Be open with your child about what the app does, which is what this design already does.

---

## 5. Project layout

```
tele/
├── README.md
├── child-app/                    Android Studio project (Kotlin)
│   ├── settings.gradle.kts, build.gradle.kts
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/tele/child/
│           │   ├── MainActivity.kt          setup steps, SOS button, status
│           │   ├── PairActivity.kt          enter 6-digit link code
│           │   ├── LogActivity.kt           "what my parent can see"
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
├── parent-dashboard/
│   └── index.html                Firebase dashboard (single file, demo mode included)
└── firebase/
    ├── firestore.rules
    └── firebase.json
```
