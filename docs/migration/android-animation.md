# Android battle actor animation

## Integration contract

`BattleActorView` remains a `TextureRect`. Set `actor_id` to the actual Android hero ID;
events must use that same ID in `source_id` and `target_id`. The existing
`motion_for(event, id)` and `play_event(event)` signatures and source-before-target
classification are preserved. Thus a self-targeted `skill_cast` plays a cast, and a
positive hit on another actor overlays damage feedback without resetting its current
attack. BattleScreen, scenes, combat timing, and the Android repository are unchanged.

The caller can supply its initial image using `AndroidArtCatalog.hero_texture(hero_id)`.
At ready time and during playback, valid heroes use the atlas's ready frame as idle.
Unknown actors retain a caller-supplied texture; rebinding clears previously owned hero
art rather than showing another hero. Rebinding also resets the animation, hand cycle,
scale, tint, and active tint tween. Caller-controlled `flip_h` is preserved.

## Source evidence and actual hero mapping

The implementation follows Android `AttackAtlas.kt`, shared `AttackAnimationClock.kt`,
`DuelIllustration.kt`, `HeroPoseLayout.kt`, and `GameArt.kt`. Metadata is read from the
already copied `assets/android/art/heroes/attack-atlas-v3.json` and
`v0.1.5/attack-manifest.json`. Expansion entries must have `runtimeReady: true`.

All ten primary sheets live in `assets/android/art/heroes/atlases/`:

| Actor ID | Primary sheet basename |
| --- | --- |
| juggernaut | juggernaut-attack-atlas-v3.png |
| phantom_assassin | phantom-assassin-attack-atlas-v3.png |
| skywrath | skywrath-attack-atlas-v3.png |
| abaddon | abaddon-attack-atlas-v3.png |
| necrophos | necrophos-attack-atlas-v3.png |
| viper | viper-attack-atlas-v3.png |
| anti_mage / antimage | antimage-attack-atlas-v3.png |
| axe | axe-attack-atlas-v3.png |
| faceless_void | faceless-void-attack-atlas-v3.png |
| legion_commander | legion-commander-attack-atlas-v3.png |

These are real 1024-by-1024 sheets arranged as four 512-by-512 cells: ready, windup,
strike, recovery. Idle uses ready, rather than treating review contact sheets as animation.
The default attack lasts 420 ms. Phase boundaries are 14%, 34%, and 68%, matching
Android's presentation clock. Damage resolution remains entirely with the battle engine.
New attack or cast events restart the pose clock; there is no event queue.

Every second displayed attack for Juggernaut and Phantom Assassin uses windup/strike
from their corresponding `*-offhand-atlas-v4.png` sheet. Those sheets are 1024-by-512;
ready and recovery continue to use the primary sheet. This matches Android's alternate
frame selection without relying on Godot resource filenames to infer hero identity.

Cast presentation uses Abaddon's and Skywrath's actual `*-cast-v1.png` files via
`AndroidArtCatalog.hero_path`. Viper uses `viper-charge-v1.png` during the first two cast
phases, then the primary attack sheet's strike cell for release. Other heroes use their
four actual attack cells as a cast presentation because dedicated cast artwork is not
available in the copied source. Cast presentation lasts 550 ms. Android's separate Axe
spin sheet is not selected: its COUNTER_HELIX cue needs a separate event contract, while
this task preserves the existing attack/cast/hit interface.

## Placement, validation, and bounds

Metadata must provide finite numeric calibration, 512-by-512 cells, in-cell roots and
attack origins, positive body height/content width within a cell, safe local filenames,
and an existing image. Decoded primary sheets must be exactly 1024-by-1024; offhand
sheets must be exactly 1024-by-512. Unusable entries or images fall back to
`AndroidArtCatalog.hero_texture`, which uses the genuine portrait or static idle art.
Unknown heroes return no invented animation mapping.

Only an extracted frame is retained as a texture, reduced to 256-by-256 RGBA8 without
mipmaps. Full sheets are temporary CPU images. A shared LRU holds at most 64 textures,
or 16 MiB of frame pixel payload. Aliases and idle/ready share the same source-cell key.
A separate failure set is bounded to 64 entries and avoids decoding a corrupt sheet on
every display frame. Metadata and failures persist until process restart.

The authored atlas root is (256, 464), which becomes (128, 232) in each output frame.
The actor uses aspect-preserving centered drawing and places its pivot at that exact
foot point after letterboxing. All four cells and both hands share this root. Dedicated
casts are composited onto the same transparent 256-by-256 canvas, bottom-aligned to the
same root, using Android's body fractions: Abaddon .69, Skywrath .86, Viper .94. Actor
scale stays at one, avoiding foot movement during frame changes; hit/cast tints affect
color only. Right-facing source art is mirrored by the caller's `flip_h` property.

The cache bound excludes temporary source decoding, renderer overhead, static fallback
textures held by AndroidArtCatalog's own bounded cache, and textures still referenced by
live views after eviction. First use of a frame decodes its source synchronously. Raw
images and JSON metadata must be retained in packaged exports, as described in
`android-art.md`; default texture import remaps alone do not support raw decoding.

## Verification

`tests/test_android_actor_animation.gd` verifies all ten exact hero-to-sheet mappings,
both source sheet dimensions, correct cell rectangles, visible and distinct frame
pixels, output dimensions, ready/attack/cast transitions, offhand playback, Viper release,
stable foot pivots, caller facing, rebinding, malformed descriptor rejection, genuine
static fallback, and bounded cache eviction with a hot frame retained. It also checks
the `anti_mage`/`antimage` alias and unknown/out-of-range requests.

Run with the actual Godot executable and an explicit writable log path:

```powershell
$ErrorActionPreference = 'Stop'
$engine = 'D:/GODOT/Godot_v4.6.1-stable_mono_win64/Godot_v4.6.2-stable_win64.exe'
$logRoot = Join-Path $env:TEMP 'infinite-strife-actor'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$process = Start-Process -FilePath $engine -WindowStyle Hidden -Wait -PassThru -ArgumentList @(
    '--headless', '--path', 'D:/GODOT/InfiniteStrife',
    '--log-file', (Join-Path $logRoot 'engine.log'),
    '--script', 'res://tests/test_android_actor_animation.gd'
) -RedirectStandardOutput (Join-Path $logRoot 'out.log') -RedirectStandardError (Join-Path $logRoot 'err.log')
Get-Content (Join-Path $logRoot 'out.log')
Get-Content (Join-Path $logRoot 'err.log')
if ($process.ExitCode -ne 0) { throw "Actor test failed: $($process.ExitCode)" }
```

Godot 4.6.2 reported `Android actor animation: heroes=10 failures=0` and exited 0.
The existing `test_battle_actor_view.gd`, `test_battle_screen.gd`, and
`test_battle_presentation.gd` also exited 0 with empty stderr. Verification is headless;
interactive scene composition and packaged exports were not exercised.
