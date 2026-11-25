# Ascend: offline strength training tracker for Android

Ascend is a gym logbook that ranks every lift. Plan your week, set up each session, log sets with a rest timer, and watch each exercise climb through 10 strength ranks, from Rookie to Galactic demon. Everything is stored on your phone: no account, no ads, no tracking.

**Download:** grab the APK from [Releases](../../releases) and install it (Android 8.0 or newer; allow "install unknown apps").

## Features

- **1,700+ exercises** with photos, instructions and a YouTube demo link; a "muscles worked" diagram when there's no photo.
- **Strength ranks per exercise:** 10 tiers (Rookie, Bronze, Silver, Gold, Platinum, Diamond, Titan, Spartan, Mythic, Galactic demon). Ranks are tuned to each lift and adjusted for bodyweight, sex and age, so a good curl and a good bench press both count. About 970 exercises are ranked.
- **Plan your week:** 20 famous splits (PPL, upper/lower, full body, bro split…) added with one tap, or your own routines for any day.
- **Set up before you start:** each set shows what you did last time; adjust sets, weight and reps, then press Start and the timer begins.
- **Auto-progression:** double progression suggests the next weight and reps, and a deload after two missed sessions.
- **PR alerts, recovery heat map, streaks** (counted per training day), body measurements, history and charts.
- **38 achievements** and a shareable workout card.
- **Optional AI coach (Gemini):** bring your own free API key for a daily tip, a coach chat that knows your training, and AI-generated weekly plans. Without a key the app is fully offline.
- Three themes: Ember, Volt and Tide.

## Privacy and security

- All data is stored locally in a Room database; Android backup is turned off. You can export and import a JSON backup yourself.
- The Gemini key is stored with EncryptedSharedPreferences, sent only over HTTPS in a request header, and never included in backups.
- Training data is sent to Google only when you use an AI feature and have added your own key.
- The app requests just one permission (internet, needed only for the AI features).

## Build

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleRelease      # APK in app/build/outputs/apk/release/
./gradlew testDebugUnitTest    # unit tests
```

Without a signing key the release build is signed with the debug key. To use your own key, put `keystore.properties` (`storeFile`, `alias`, `password`) and the keystore file in a `keys/` folder **next to** the project folder (outside the repo).

Stack: Kotlin, Jetpack Compose, Room, Navigation, AndroidSVG.

## Tools

- `tools/build_catalog.py` builds `app/src/main/assets/exercises.json` and the photos from `curated.psv`, [free-exercise-db](https://github.com/yuhonas/free-exercise-db) and [wger](https://wger.de). Download those two datasets yourself to rebuild.
- `tools/gen_medals3.py` and `tools/gen_logos.py` generate the rank badges and the app icon.

## Licenses

- App code: MIT, see [LICENSE](LICENSE).
- Exercise data and photos: from free-exercise-db (Unlicense) and wger.de (CC BY-SA 4.0), adapted (resized, merged, deduplicated). The wger-derived parts of `assets/exercises.json` and `assets/img/` stay under CC BY-SA 4.0.
- Fonts: Bricolage Grotesque and Geist, SIL Open Font License 1.1 (see `licenses/`).

Ascend is a training log, not medical advice. Strength standards are estimates.
