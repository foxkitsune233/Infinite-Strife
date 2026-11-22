# Temporary Android art reuse

The Android repository is read-only. This migration copies packaged art and attribution
only; it does not import Android profiles, saves, configuration, or gameplay definitions.
All authored changes are confined to this document, `scripts/ui/AndroidArtCatalog.gd`,
`scripts/ui/ItemIconFactory.gd`, `tools/sync_android_art.ps1`, and `assets/android/`.

## Reproduce

Run from any working directory in PowerShell:

```powershell
& D:\GODOT\InfiniteStrife\tools\sync_android_art.ps1
# Optional alternate Android checkout:
& D:\GODOT\InfiniteStrife\tools\sync_android_art.ps1 -AndroidRoot D:\android_project\DouQiuQiu
```

The destination is always resolved relative to the script's Godot project. Source and
destination must not overlap. Reparse points in the source tree and destination ancestry
are rejected. Files retain their relative directories and original bytes. Existing files
are compared using SHA-256, and only missing or different files are copied; each copy is
verified. No files are deleted, including stale destination files. A source change updates
its corresponding destination file. Run with the source checkout stable; this is not a
transactional snapshot or an atomic multi-file update. Failures terminate with an error.

`assets/android/manifest.json` records every copied file's Android-repository-relative
source, Godot-project-relative destination, byte count, and SHA-256. It contains no machine
paths or run timestamps. `catalog.json` is the generated ID-to-resource-path index. Both
JSON files are rewritten only when their contents differ. The runtime reads the catalog
once per process; restart after syncing changed mappings.

## Inventory and mapping evidence

Source: `D:\android_project\DouQiuQiu\app\src\main\assets`.

| Copied subtree | Files | Bytes |
| --- | ---: | ---: |
| art/branding | 1 | 2,502,105 |
| art/cards | 47 | 90,297,193 |
| art/chests | 2 | 381,539 |
| art/events | 25 | 2,463,058 |
| art/heroes | 47 | 47,071,523 |
| art/items | 167 | 12,420,957 |
| art/scenes | 14 | 3,232,491 |
| licenses | 4 | 26,744 |
| **Total** | **307** | **158,395,610** |

The art tree has 295 raster images, seven JSON metadata files, and one Markdown review
note. The copied inventory intentionally includes review sheets and attack atlases, but
they are not treated as individual runtime hero poses.

The loader rules were derived from these Android files under
`app/src/main/java/com/example/douqiuqiu/ui/components/`:

- `GameArt.kt`: original cards, per-definition item images, hero poses and portraits.
- `ExpansionArtwork.kt`: expansion manifest has priority, matching both `id` and `artKey`.
- `PaintedItemIcons.kt` and `ItemIconCatalog.kt`: specific images precede explicit glyphs;
  `CROSS` uses the `BOW` bitmap.

Cards have 60 index keys for 45 unique pictures: 30 legacy `A1`/`U1`/`C1`/`S1`/`R1`/`P1`
through rank 4 and `D1` through `D6`, plus 15 expansion definitions (`D7` through `D13`,
`F1` through `F4`, `RG1` through `RG4`) with their 15 descriptive art-key aliases.
The expansion manifest supplies the exact `v0.1.5/*.webp` paths.

Equipment has 173 index keys: 167 direct PNG basenames plus six explicit Android glyph
fallbacks derived from `ItemIconCatalog.kt`. This index also includes consumables, loot,
materials, recipes, and uppercase generic glyphs already shipped alongside equipment.
Direct images always win. New same-ID PNG files are picked up by rerunning the script.

The nine demo aliases `equipment_0` through `equipment_8`, in order, use:
`iron_branch`, `broadsword`, `slippers`, `chainmail`, `gloves_of_haste`, `ring_of_health`,
`black_king_bar`, `mjollnir`, `satanic`.
The eight `card_0` through `card_7` aliases use `A1`, `A2`, `A3`, `A4`, `U1`, `U2`, `U3`, `U4`.
These are temporary visual assignments, not changes to the demo's gameplay definitions.
Unknown IDs retain the original generated 72-by-72 icon. Negative, noncanonical, or
out-of-range numeric demo aliases do not wrap into valid assignments.

## Runtime APIs and memory

`AndroidArtCatalog.card_path(id)` and `equipment_path(id)` return an existing `res://`
path or an empty string. `hero_path(id, pose = "idle", portrait = false)` follows Android's
pose rules, including `phantom_assassin` to `phantom-assassin` and `anti_mage` to `antimage`.
The six legacy heroes use `art/heroes/<name>-<pose>-v1.png` with idle fallback; the four
expansion heroes use `art/heroes/v0.1.5/<name>-idle.png`, regardless of requested pose.

BattleScreen can preload the catalog and call:

```gdscript
const AndroidArt = preload("res://scripts/ui/AndroidArtCatalog.gd")
var texture: Texture2D = AndroidArt.hero_texture(hero_id)
```

`hero_texture(hero_id: String) -> Texture2D` prefers the four expansion portraits and
otherwise uses static idle art. It supports ten genuine heroes plus the `antimage` alias.
Missing portrait files fall back to idle; unknown IDs return `null`, allowing the caller's
own fallback. This task does not modify BattleScreen or wire other scenes to copied art.

Item icons preserve aspect ratio, never upscale, and have a maximum edge of 160 pixels.
Their LRU cache holds at most 256 textures, including generated fallbacks. Aliases share
the same texture cache key as the genuine picture. `ItemIconFactory.clear_cache()` frees
cache references. Hero textures are separately limited to a 512-pixel edge and 16 LRU
entries; `AndroidArtCatalog.clear_hero_cache()` releases those cache references.

Textures use RGBA8 with no mipmaps: retained pixel payload is at most 25 MiB for item
cache plus 16 MiB for hero cache. This is not a bound on total application memory: UI
consumers can retain evicted textures, decoding temporarily holds full-resolution source
images, and renderer/driver allocations add overhead. The catalog stores paths only.

## Verification

The owned `assets/android/verify_art.gd` checks all 233 catalog mappings and loads their
textures, all 17 demo aliases, expansion aliases, hero paths and static textures, unknown
IDs, aspect-size bounds, fallback generation, cache eviction and clearing. Godot 4.6.2
reported `mapped=233 decoded=233 failures=0` and exited 0.
The existing `tests/test_preparation_screen.gd` also exited 0 under the same engine,
with no stderr output; its logs use the `assets/android/regression*` names.

```powershell
$engine = 'D:\GODOT\Godot_v4.6.1-stable_mono_win64\Godot_v4.6.2-stable_win64.exe'
$process = Start-Process -FilePath $engine -WindowStyle Hidden -Wait -PassThru -ArgumentList @(
    '--headless', '--path', 'D:/GODOT/InfiniteStrife',
    '--log-file', 'D:/GODOT/InfiniteStrife/assets/android/engine.log',
    '--script', 'res://assets/android/verify_art.gd'
)
if ($process.ExitCode -ne 0) { throw "Godot verification failed: $($process.ExitCode)" }
```

An explicit writable log path is necessary in this sandbox: without it the real engine
crashed before script startup; `--version` still worked. Console wrappers were not used.
Successful logs are retained in `assets/android/engine.log` and `verification.log`.

The repeated sync reported `files=307 copied=0 unchanged=307 cards=60 equipment=173`.
All 307 source and destination hashes matched the manifest, and all 309 copied/generated
files retained their timestamps; the manifest hash also remained identical.

## Limits and integration notes

- Current tests run against the project source tree. These loaders use raw file decoding
  to avoid retaining full-sized imported textures. Packaged exports must retain raw
  images and `assets/android/catalog.json` (for example, configure Keep File export mode
  for these files). Default imported texture remaps alone are insufficient. Export presets
  and import settings are outside this task's ownership and have not been changed or tested.
- Android XML/vector drawables, launcher resources under `res/`, audio, and configuration
  are not copied or converted. Native scroll composition and procedural Android drawable
  behavior are not reproduced; same-ID recipe bitmaps and explicit generic glyphs are reused.
- Godot's original fictional hero, item, and school IDs are not reinterpreted as Android
  definitions. Only the requested numeric demo aliases receive temporary assignments.
- Scene, chest, event, branding, and atlas files are available for subsequent integration.
  Animated atlas playback and hero UI layout/anchors remain the integrating caller's work.
- Attribution files are preserved. Reuse is temporary per the user's authorization; this
  task does not establish new distribution rights or replace existing attribution.
