# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Domino is an Android multiplayer domino game. Two screens:

- **Lobby** (`MainActivity`): create a team, invite players, see incoming requests, start a game.
- **In-game** (`GameActivity`): play a full 28-tile domino round on a shared Firebase-synced board.

UI and player names are in Russian. The `com.declination` library handles Russian genitive-case declination for team captions.

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

### Lobby flow (MainActivity)

```
MainActivity
   ↓ calls
GameService  (singleton, in-memory team/player state)
   ↓ delegates persistence to
GameSyncService  (Firebase Realtime Database)
   ↓ async results via
Callbacks  (GameDataCallback / GamesDataCallback)
```

- **`GameService`** — singleton that owns in-memory team and player state. Lazily initialises `Declinator` for genitive name forms used in team captions.
- **`GameSyncService`** — all Firebase I/O for the lobby lives here. Attaches `ValueEventListener`s for real-time sync, filters out unstarted games, locates a player's active game by user id. Remember to `removeEventListeners()` when the activity stops to avoid leaks.

### In-game flow (GameActivity)

Different shape — `GameActivity` talks to Firebase directly via `runTransaction` for atomic moves, no service layer in between:

```
GameActivity ⇄ Firebase (runTransaction)        ← every move is one atomic write
        ↓
DominoLogic  (pure rules, mutates GameState)
        ↓
DominoBoardView  (custom ViewGroup, snake layout)
SoundManager     (SFX for plays and round wins)
```

- **`DominoLogic`** — pure static rules: 28-tile deal, opening rule (lowest double by a house rank order — see `DominoLogic.DOUBLE_RANK_ORDER`), legal-move check, scoring, fish detection. Always operates on a passed `GameState`.
- **`GameState`** — runtime DTO holding `board`, `hands` per player, `bazaar`, `scores`, `playerOrder`, `currentTurnIndex`, `roundFinished`, `roundWinnerId`, `fish`, `finished`, `loserId`, `targetScore` (default 101).
- **`Tile`** — DTO with orientation-independent `equals`/`hashCode` so the board chain can match `(3|5) == (5|3)`. `@Exclude` on computed methods (`points`, `matches`, `otherSide`) keeps Firebase serialization clean.
- **`DominoBoardView`** — custom `FrameLayout` that lays the chain out as a snake. Anchor in the centre, two branches growing outward and curling at the canvas edge. Supports pinch-to-zoom and pan (wired in `GameActivity.setupBoardZoom`).
- **`SoundManager`** — `SoundPool` wrapper. Loads `res/raw/tile_play.ogg` / `round_win.ogg` if present; falls back to `AudioManager.FX_KEYPRESS_*` so the game compiles and runs without real audio assets.

### DTOs

Plain Java beans used for Firebase serialization via Jackson — keep them logic-free.

- Lobby: `Game`, `Player`, `Message`.
- In-game: `Tile`, `GameState` (nested inside `Game.state`).

### `com.declination`

Self-contained Russian declination library (genitive case). Lives under `src/main/java/com/declination/`. Do not modify unless fixing a Russian-language declension bug.

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/family/activity/MainActivity.java` | Lobby entry point; team list + invitations |
| `app/src/main/java/com/family/activity/GameActivity.java` | In-game screen; renders board, hand, controls, banner |
| `app/src/main/java/com/family/activity/DominoBoardView.java` | Custom snake-layout view for the board chain |
| `app/src/main/java/com/family/activity/DominoApp.java` | Application class (Firebase init, persistence config) |
| `app/src/main/java/com/family/service/GameService.java` | In-memory lobby state, singleton |
| `app/src/main/java/com/family/service/GameSyncService.java` | Firebase real-time listeners for the lobby |
| `app/src/main/java/com/family/service/DominoLogic.java` | Pure domino rules (deal, validate, score, fish) |
| `app/src/main/java/com/family/service/SoundManager.java` | SFX wrapper around `SoundPool` |
| `app/src/main/java/com/family/dto/` | Firebase data model (`Game`, `Player`, `Message`, `GameState`, `Tile`) |
| `app/src/main/java/com/family/utils/UserUtils.java` | Per-install UUID + saved player name in `SharedPreferences` |
| `app/src/main/res/layout/main_activity.xml` | Lobby screen layout |
| `app/src/main/res/layout/game_activity.xml` | In-game screen layout |
| `app/google-services.json` | Firebase project config (do not commit secrets here) |

## Firebase Data Model

Games are stored under `/games/{gameId}` in Firebase Realtime Database. The root `Game` object holds the lobby data (`players`, `bossId`, `messages`, `createdAt`) plus an embedded `state: GameState` once the round starts.

### Player identity

The player id is a **per-install UUID** generated on first launch and persisted in `SharedPreferences` under key `user_uuid` — see `UserUtils.resolveUserId(Context)`. **Do not use Google Advertising ID** — under "Limit Ad Tracking" it collapses to all-zeros across devices, which caused real identity collisions in this project. The UUID has no Google Play policy implications (no AD_ID permission required, not listed in Data Safety as an identifier).

The UUID keys every per-player Firebase field: `players[].id`, `bossId`, `state.hands[uid]`, `state.scores[uid]`, `state.playerOrder[*]`. Stable across app restarts; resets only on clear-app-data or reinstall.

### Atomic moves

`GameActivity.applyMove(GameTransform)` runs every state mutation inside `ref.runTransaction(...)`. The handler re-reads server state, applies the transform, commits-or-aborts. This prevents lost updates when two devices play simultaneously and naturally enforces server-side validation: the transform can return `false` (turn isn't theirs, tile not in hand, …) to abort with no write.

### Server time

`GameSyncService.currentServerTimeMillis()` uses Firebase's `.info/serverTimeOffset` to keep timestamps consistent across devices with clock skew (which broke the TTL filter on emulators).

## Game Rules (house variant)

- 28 tiles (`0|0` … `6|6`), 7 dealt to each player, leftover to the bazaar (2-3 players; 4 players deal out the whole set).
- Opening tile: the lowest double anyone holds by `DominoLogic.DOUBLE_RANK_ORDER` (`0|0` is the strongest, played last). Only the holder of that double may open.
- Must play if able; otherwise draw from bazaar; if bazaar is empty, pass. All-pass round = fish; the lowest hand-sum wins.
- Losers add their remaining pip sum to their score. First to reach `targetScore` (default 101) **loses** the game.

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
