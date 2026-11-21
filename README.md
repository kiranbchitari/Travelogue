# Travelogue

A personal, offline-first Android travel diary and expense manager. Everything is entered by hand and
stays on the phone: there's no account, no cloud, no ads, no AI and no network calls. Each trip can be
exported to a ZIP and restored.

## Build & run

Requirements: Android Studio (2026.1+) or JDK 17+, plus the Android SDK with platform 37.

```bash
./gradlew :app:assembleDebug          # debug APK  → app/build/outputs/apk/debug/
./gradlew :app:installDebug           # install on a connected device/emulator
./gradlew :app:assembleRelease        # minified release APK (signed with the debug key for personal use)
./gradlew :app:testDebugUnitTest      # finance/split unit tests
./gradlew :app:connectedDebugAndroidTest  # on-device export → import round-trip tests
```

Open the folder in Android Studio to run it the usual way. To try the app with content, go to
**Settings → Load sample trips**. This creates three demo trips with generated artwork, and
**Remove sample trips** deletes them again without touching your own trips.

## Stack

Kotlin · Jetpack Compose · Material 3 (heavily themed) · MVVM · Room · DataStore · Navigation Compose
(type-safe routes) · Coroutines/Flow · kotlinx.serialization · Coil 3 · Android Photo Picker · Storage
Access Framework. Dependencies are wired by hand in `di/AppContainer.kt`; no DI framework is used.
minSdk 26, targetSdk 36, compileSdk 37.

## Project layout (`app/src/main/java/com/travelogue/app`)

| Package | What lives there |
|---|---|
| `core/designsystem` | Theme (warm paper palette, Fraunces + Plus Jakarta Sans), spacing/shapes, reusable components, hand-drawn interactive charts |
| `core/storage` | `MediaStorage`: copies picked photos and documents into app-private storage (downscaled JPEG), checks free space, reports usage |
| `core/util` | Formatting (money, dates) and Android intents (call, SMS, WhatsApp, maps links, open file) |
| `data/db` | Room entities, DAOs, converters, `AppDatabase` (explicit migrations only) |
| `data/repository` | Trip, expense, journal (hotels/itinerary/places/bookings/contacts), diary and photo repositories |
| `data/backup` | ZIP export/import (`BackupManager`), archive format, demo data seeder |
| `domain` | Pure-Kotlin money logic: manual currency conversion, splits, balances, who-owes-whom, trip analytics |
| `ui/*` | One package per feature: home, trips, expenses, itinerary, hotels, places, bookings, contacts, diary, memories, settings, backup |

## Data

- Normalised Room schema: trips, travellers (shared across trips) linked through `trip_travellers`;
  expenses, splits and settlements; hotels with an embedded review; itinerary days and activities;
  places; bookings; contacts; diary entries with tags; photos; documents; per-trip currency rates.
  Foreign keys use indices and cascade (or SET NULL where a record should survive its parent).
- Photos and documents are **copied** into `filesDir/media/<trip-uuid>/<folder>/`. The database stores
  only paths relative to that folder; no image bytes and no temporary picker URIs.
- Currency: the original amount and currency are always kept. Totals convert using rates **you enter**
  per trip (Financial summary → Currencies). Amounts without a rate are shown separately and left out
  of totals, never guessed.
- Upgrades: the schema is exported to `app/schemas/`. Add a `Migration` to `AppDatabase.MIGRATIONS` for
  each version bump. Destructive migration is deliberately never enabled.

## Export / import format

```
Travelogue_<Trip_Name>.zip
├── manifest.json        format, version, trips, every file with its size
├── trip.json            the trip and every related record (ids preserve relationships)
├── images/cover|hotels|places|diary|receipts|activities|gallery/
└── documents/           booking tickets/PDFs
```

A full backup (Settings → Export all data) contains `manifest.json`, `settings.json` (travellers,
categories, default currency, theme) and one `trips/<uuid>/` folder per trip with the layout above.

Before anything is written, an import:
- checks the ZIP and its `manifest.json`;
- rejects unsafe paths, unsupported file types, oversized entries and archives that don't fit in free space;
- validates the JSON (dates, required fields, amounts, file references);
- detects trips already on the device by uuid, then asks whether to **replace** them, **keep both** or **skip**;
- extracts files to a staging folder, then rebuilds every relationship with remapped ids inside a single
  database transaction per trip. If anything fails, nothing is half-imported.

## Testing done

- Unit tests (`FinanceTest`): equal-split rounding, manual conversion, balances, settlement suggestions,
  trip analytics totals and groupings.
- Instrumented tests (`BackupRoundTripTest`, run on an API 37 emulator):
  - exports a demo trip, confirms the documented ZIP layout, and re-imports it as a copy, checking that
    every record count, split, settlement, tag and photo file matches;
  - repeats the import with replace and with skip;
  - previews a full backup;
  - feeds in seven kinds of malformed archive and checks that each is rejected.
- Manual pass on the emulator: Home, Trips, trip detail and its tabs, expense analytics, splits and
  settlement, trip story, photo viewer, settings, trip creation with validation, light and dark themes,
  the minified release build, and exporting a trip through the system file picker.

## Known limitations

- Activities are reordered with up/down actions rather than drag and drop.
- In the photo viewer, a zoomed-in photo has to be zoomed back out before you can swipe to the next one.
- Global charts on the Expenses tab only include trips in your default currency. Each trip's own
  screens convert everything to that trip's currency.
- No in-app camera screen. The camera buttons open the installed camera app, so the app needs no
  camera permission.
