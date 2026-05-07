# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Domino is an Android app for managing multiplayer domino game teams. Players create teams, invite others via real-time requests, and track games. The UI and player names are in Russian; the `com.declination` library handles Russian genitive-case name declination for team captions.

**Stack:** Java 1.8 · Android SDK 34 (min 21) · Firebase Realtime Database · Gradle 8.7.2

## Build Commands

```bat
gradlew assembleDebug          # Build debug APK
gradlew assembleRelease        # Build release APK
gradlew installDebug           # Build and install on connected device/emulator
gradlew test                   # Run unit tests
gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
gradlew clean                  # Clean build outputs
```

Run a single test class:
```bat
gradlew test --tests "com.family.app.ExampleUnitTest"
```

## Architecture

The app follows a layered pattern:

```
UI (Activity + Adapters)
       ↓ calls
GameService  (singleton, in-memory state)
       ↓ delegates persistence to
GameSyncService  (Firebase Realtime Database)
       ↓ async results via
Callbacks  (GameDataCallback / GamesDataCallback)
```

**`GameService`** — singleton that owns all in-memory game and player state. Creates games, manages player lists, lazily initializes `Declinator` for genitive name forms used in team captions.

**`GameSyncService`** — all Firebase I/O lives here. Attaches `ValueEventListener`s for real-time sync, filters out unstarted games, and locates a player's active game by advertising ID.

**`MainActivity`** — single-activity entry point. Drives both the team list (`TeamTableAdapter` / `TeamViewHolder`) and the current-game view (`CurrentGameTableAdapter` / `CurrentGameViewHolder`). Handles player-name input and timed message dismissal for invitation alerts.

**DTOs** (`Game`, `Player`, `Message`) — plain Java beans used for Firebase serialization via Jackson. Keep them free of logic.

**`com.declination`** — self-contained Russian declination library (genitive case). Lives in a separate package under `src/main/java/com/declination/`. Do not modify unless fixing a Russian-language declension bug.

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/family/activity/MainActivity.java` | UI entry point, coordinates all views |
| `app/src/main/java/com/family/service/GameService.java` | In-memory game state, singleton |
| `app/src/main/java/com/family/service/GameSyncService.java` | Firebase read/write, real-time listeners |
| `app/src/main/java/com/family/dto/` | Firebase data model (Game, Player, Message) |
| `app/src/main/res/layout/main_activity.xml` | Main screen layout |
| `app/google-services.json` | Firebase project config (do not commit secrets here) |

## Firebase Data Model

Games are stored in Firebase Realtime Database. `GameSyncService` uses the advertising ID (`UserUtils.getAdvertisingId()`) as the player identifier. Real-time listeners are attached in `GameSyncService`; call `removeEventListeners()` when the activity stops to avoid leaks.

## Gradle / Build Notes

- Java source and target compatibility are set to 1.8 in `app/build.gradle`.
- Release builds have `minifyEnabled false` — ProGuard is not active.
- `gradle.properties` sets `-Xmx2048m` for the Gradle daemon; increase if OOM errors occur during build.
- `local.properties` contains the local `sdk.dir` path — it is gitignored and must be present on each dev machine.

## Development Rules

- Prefer incremental refactoring over full rewrites.
- Keep architecture simple and maintainable.
- Avoid unnecessary abstractions.
- Prefer small reviewable commits.
- Add tests only for stable business logic.
- UI must support phones and tablets.
- Multiplayer logic must tolerate disconnects and stale sessions.
- Avoid scanning build/generated directories unless necessary.

## Future Documentation

If the project grows, additional documentation should be placed under:
- docs/architecture/
- docs/gameplay/
- docs/firebase/
- docs/ui/