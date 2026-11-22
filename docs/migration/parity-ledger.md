# Android → Godot parity ledger

This is a live implementation ledger, not a claim of release parity.  Android
remains the production build while the Godot build catches up.

## Android 1.0.0 target (2026-11-16)

- Reference: Android tag `v1.0.0`, commit `1a79fe3`, version code 1019.
- Equipment data refreshed: 217 item definitions, 53 recipes and 45 cards. Recipe links and typed effect metadata have regression coverage. Runtime behavior and new item art still need verification.
- Next: implement and compare active equipment outcomes on controlled fixtures. Start with Nullifier versus Necrophos and Abaddon shields, then Silver Edge and Blade Mail. Check manual ally input and automatic enemy actions in the same battle path.
- Then port the 20-floor tower loop, persistent rewards and hero growth, followed by quest dialogue and ending subtitles. Validate an exported build from camp through tower completion before replacing Android.
- Windows editor tests run with the installed Godot 4.7.1 console executable outside the command sandbox. The battle screen smoke test reports raw image loading warnings that require export verification.

## 2026-11-20 catch-up batch (event / auxiliary pages / warehouse)

- **Event module** now compiles and matches Android `JourneyEvents`: implemented the missing
  `_event_pools` / `_event_available` helpers (the compile blocker), added reward-pool filtering
  by clears/difficulty, deterministic RNG cursor (`event_rng`), last-two event de-duplication
  (`recent_event_kinds`), and frozen `grant_ids` per option. All fields roundtrip through
  `SaveCodec` v2. Covered by `test_event_delivery_parity`, `test_journey_loot_rules`,
  `test_event_dedup_rng`.
- **Auxiliary pages** added: `AppSettings` (presentation prefs, ConfigFile), `BackupRules`
  (SHA256-checked export/restore with prior-save backup), and a three-tab `Settings` scene
  (gameplay / storage / gallery+sources) reachable from camp. Covered by `test_settings_backup`,
  `test_settings_screen`.
- **Warehouse parity**: sale value now 40% of base price with 3/9 copies for 2/3-star cards
  (Android `SupplyRules.saleValue`); added a standalone item detail sheet with rotation preview.
  `test_preparation_screen` updated for the dedicated skill-card panel layout.
- Remaining for full parity: visual/device acceptance, Android save import, full mobile polish.

## 2026-11-20 fix batch (backup reliability / live settings / visual preview)

Player-reported regressions from the catch-up batch, all fixed with regression coverage:

- **Backup restore wrote nothing when no save existed** (`BackupRules.restore`): the
  `SaveRepository.write` call was nested inside the `if FileAccess.file_exists(path)` branch,
  so a first-ever restore returned success without creating a file. Write is now unconditional;
  the `.bak` of a previous save is still kept only when one existed, and a failed write rolls
  the `.bak` back. Covered by `test_settings_backup` (restore to absent path creates the file).
- **Restored state was overwritten by stale in-memory state** (`SettingsScreen._on_restore`):
  restore only touched the disk, so the live `AppSession` still held the old gold/items and the
  next save reverted them. Added `AppSession.restore_from_backup(text)` which writes the backup,
  re-reads it, and adopts the decoded states into the *existing* state objects via the shared
  `_adopt_decoded` helper (resource identity preserved for active screens), and clears the
  save-block flags. `SettingsScreen` now calls it and reports "restored and immediately active".
  Covered by `test_settings_backup` (in-memory gold 1 → 777, resave keeps 777).
- **Presentation settings were saved but never applied**: wired `AppSettings` into consumers —
  `sound` gates the procedural `BattleAudioBus` cues (with immediate `refresh_settings()` on
  toggle); `animations` gates `BattlePresentationController`, `BattleActorView`, `BattleEffectView`
  and the story-page fade-in (数值结算不受影响); `textLevel` scales fonts in story, battle HUD and
  settings via `AppSettings.scaled_font_size`; `battleLog` toggles the battle log button;
  `statusIcons` toggles the buff icon chips (text summaries always remain); `haptics` calls
  `Input.vibrate_handheld` on mobile only. Covered by `test_settings_effects`.
  **Note:** the Godot migration ships no licensed audio assets, so `music` and `jingles` remain
  persisted preferences with no playable source; the settings UI states this explicitly. This is
  an intentional gap, not a silent no-op claim.
- **"Rotation preview" performed a real rotation** (`ItemDetailSheet.show`): the button called the
  supplied `rotate_handler`, which invoked `PackingRules.rotate` — rejected for warehouse items
  ("only backpack/safe") and mutated the item for grid items. Preview is now a purely visual,
  in-dialog flip (swapped dimension text + icon rotation) that never writes to state; square
  items omit the button; warehouse items can preview. Covered by `test_item_detail`.

## Implemented in Godot

- 10 Android heroes: base data, level growth, portraits, sprite-atlas combat
  animation, abilities/passives, regeneration, control effects and lossless
  in-battle resume.
- 217 exported Android items, 45 cards, recipes, schools and confirmed
  equipment/card passive selection (star values, unique groups and upgrades).
- Android art copied without modification from the Android project, with source
  hashes in `assets/android/manifest.json`.
- 24 route nodes, 40 quests, claim rewards, hero upgrade and account-growth
  rules, save backup/future-schema protection, and a 9-node five-battle route.
- Windows debug export and Android export presets include raw Android JSON and
  image assets.

## Deliberately not yet at Android parity

- Android's complete enemy roster, encounter formation data, 16 event choices,
  rewards/drops, shop stock/gating, crafting UX and economy balancing.
- Active equipment timing, trigger execution, PRD/RNG and the full card-effect
  network.  The current adapter supplies exact selected inputs but does not
  execute every active effect.
- Final mobile UI/animation polish, audio, localization, analytics, Android
  platform services and release signing.
- Existing Android save files are intentionally not imported; the Godot build
  begins a separate local save.

## Catch-up rule

For every Android release, re-run `tools/capture_android_baseline.ps1`, export
content with `tools/export_android_content.ps1`, update this ledger, and add a
regression test before declaring that feature caught up.  Android remains the
source of truth until every release-critical row is closed.
