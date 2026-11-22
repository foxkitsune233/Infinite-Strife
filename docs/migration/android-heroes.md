# Android hero roster and base-stat migration

## Scope and sources

This migration imports the current shared Android roster, not the placeholder
`app/src/main/assets/config/heroes.json` or the original Godot fantasy roster.
Android sources under `D:/android_project/DouQiuQiu` were read without edits:

- `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt`:
  `HeroSpec` and the ten ordered `FirstRunContent.heroes` records.
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyRules.kt`:
  `fighter`, including starting mana and attribute-derived stats.
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/DuelEngine.kt`:
  attack interval conversion with the speed floor of 20.
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/content/HeroContent.kt`:
  populated legacy and new hero skill constants, retained as metadata.
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/content/HeroSkillInfo.kt`:
  Chinese skill names and behavior descriptions.
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/hero/HeroLevel.kt`:
  executable level formulas; the level-ten examples in its comments are wrong.

Only five files belong to this change: new `scripts/content/AndroidHeroCatalog.gd`,
updated `scripts/battle/BattleLoadoutBuilder.gd` and
`scripts/ui/PreparationScreen.gd`, new `tests/test_android_hero_catalog.gd`, and
this document. `HeroLevelRules` and `PreparationState.hero_levels` are supplied
by the parallel progression migration. `ContentCatalog`, `AppSession`, Android
files, and existing tests are not edited by this change.

## Catalog and exact formulas

`AndroidHeroCatalog` is a standalone `RefCounted` script without scene or catalog
dependencies. Static `ids() -> Array[String]`, `heroes() -> Array[Dictionary]`,
`hero(id) -> Dictionary`, and `base_stats(id) -> Dictionary` return fresh data.
Unknown IDs return empty hero/stat dictionaries. `hero` retains every raw
`HeroSpec` field plus skill name, adapter ID, populated `HeroContent` constants,
source, `ability_parity = false`, and an explicit per-hero ability gap.

The following table gives naked level-one stats; armor is shown as exact fractions.
The script retains double precision rather than truncating these values.

| Android ID | Exact name | STR/AGI/INT | HP | Max mana | Attack | Armor | BAT (s) | Ultimate mana / cooldown (s) |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| juggernaut | 主宰 | 24/26/16 | 1028 | 267 | 61 | 16/3 | 1.5 | 80 / 16 |
| skywrath | 天怒法师 | 18/18/30 | 896 | 435 | 60 | 3 | 1.7 | 90 / 8 |
| phantom_assassin | 幻影刺客 | 22/28/15 | 984 | 255 | 68 | 17/3 | 1.7 | 70 / 10 |
| abaddon | 亚巴顿 | 28/18/22 | 1116 | 339 | 52.6 | 5 | 1.7 | 140 / 9 |
| necrophos | 瘟疫法师 | 24/16/27 | 1028 | 399 | 57 | 11/3 | 1.7 | 85 / 18 |
| viper | 冥界亚龙 | 24/24/20 | 1028 | 315 | 58 | 5 | 1.7 | 75 / 9 |
| anti_mage | 敌法师 | 20/30/18 | 940 | 291 | 66 | 6 | 1.45 | 75 / 12 |
| axe | 斧王 | 32/20/16 | 1204 | 267 | 50 | 19/3 | 1.70 | 90 / 14 |
| faceless_void | 虚空假面 | 26/28/18 | 1072 | 291 | 62 | 20/3 | 1.60 | 85 / 16 |
| legion_commander | 军团指挥官 | 30/24/18 | 1160 | 291 | 57 | 6 | 1.55 | 80 / 15 |

For naked heroes with attributes S/A/I:

- HP = `500 + 22*S`; max mana = `75 + 12*I`; initial mana = `min(max_mana, 30)`.
- Main attribute = `(S+A+I)*0.45` for Abaddon, I for Skywrath/Necrophos,
  A for every other hero, including Axe and Legion Commander.
- Attack = `baseAttack + main`; armor = `baseArmor + A/6.0`.
- Attack speed = `100 + A`; interval milliseconds = `BAT*100/max(20,speed)*1000`.
- Magic resistance fraction = `0.25 + I*0.001`.
- Health regeneration per second = `spec.regen + S*0.1`.
- Mana regeneration per second = `spec.manaRegen + I*0.05`.

The builder preloads `res://scripts/progression/HeroLevelRules.gd`, reads
`level(state, id)`, and consumes `bonuses(level)` after its existing additive
gear/card adjustments. Health and attack use `health_multiplier` and
`attack_multiplier`; armor adds `armor_bonus`; resistance adds
`magic_resist_bonus / 100` (the API returns percentage points). The catalog
itself always reports naked level-one stats. With `m = floor(level/5)`:

- Attack multiplier = `1 + 0.015*(level-1) + 0.01*m`.
- Health multiplier = `1 + 0.020*(level-1) + 0.01*m`.
- Armor bonus = `0.4*(level-1)`, rounded to one decimal by progression rules.
- Magic resistance bonus = `0.5*(level-1) + m` percentage points.

At level 10 these are **1.155 attack, 1.20 HP, +3.6 armor, +6.5 percentage points
magic resistance**. Level-one Juggernaut becomes 1234 HP, 70 attack, 9 armor,
and 0.331 magic resistance at level 10 after integer projection.

## Godot integration and compatibility

The preparation button cycles through all ten IDs in Android source order,
wraps to Juggernaut, and uses `LoadoutRules.activate_hero` to restore each hero's
selected preset. An original or unknown active ID enters the Android cycle at
Juggernaut. Hero labels resolve Android first, then the original catalog, then
the raw unknown ID. The button tooltip explicitly identifies skills as placeholders.

The builder resolves Android heroes first, then preserves all ten original
`ContentCatalog.heroes()` profiles and their normalized skill behavior. Original
heroes keep full starting mana, zero base magic resistance, and no Android level
bonuses. Unrecognized IDs use the Android Juggernaut profile while preserving
the requested fighter identity, matching the previous fallback identity policy.

HP, attack, mana, armor, and interval are rounded with `roundi` only when creating
the integer-based `FighterState`; Abaddon's 52.6 attack therefore becomes 53 at
level one. Resistance is passed as a fraction to `FighterState`, which clamps
it to its supported range. Android starting mana is set after construction.
The build result retains `base_stats`, `hero_metadata`, and `hero_level` in
addition to its existing fighter, skill, active-items and display-name fields.

## Explicit parity gaps

**This is roster/base-stat migration, not hero ability parity.** The unchanged
generic engine only executes a single magic-damage packet and optional stun for
a skill. All Android skill dictionaries identify `runtime_mode =
"generic_placeholder"` and `ability_parity = false`. Mana costs and cooldowns
come from Android. Adapter skill IDs are Godot labels, not Android persisted IDs.
For compatibility, the former two Godot placeholders retain 180 magic damage
for Juggernaut and 155 for Phantom Assassin; the other heroes use a generic 110
damage packet. These values have no claim to Android balance or mechanics.

| Hero | Missing Android mechanics |
| --- | --- |
| 主宰 | Strong self-dispel, invulnerability, boosted full attacks and on-hit triggers |
| 天怒法师 | Eight timed hits at 0.25-second intervals and passives |
| 幻影刺客 | Four-second critical-attack window and passives |
| 亚巴顿 | Strong self-dispel, shield ledger, reflection, curse passive |
| 瘟疫法师 | Physical immunity, reduced magic damage, passives |
| 冥界亚龙 | Android poison stacking, break, slow, passives |
| 敌法师 | Passive mana burn, accumulated burn scaling, source-aware stun |
| 斧王 | Taunt, temporary armor, every-four-hits pure-damage spin |
| 虚空假面 | Undispellable time stop, time-lock procs, enemy cooldown extension |
| 军团指挥官 | Forced mutual attacks, duel speed, nonrecursive extra attacks/healing |

The exact health/mana regeneration values remain available in `base_stats` but
cannot run in the current `FighterState`/`BattleEngine`. Since Android heroes
start at 30 mana and every ultimate costs more, they cannot cast even a
placeholder without another mana source (such as an existing mana active item).
Increasing starting mana to disguise this gap would diverge from Android.
Android initial-mana card bonuses, unlock restrictions, attribute-bearing gear,
card/synergy formulas, passive abilities, Android damage/control semantics and
the Android tick scheduler are outside this change. Existing Godot gear/card
bonuses are retained, not asserted to match Android equipment/card behavior.
The generic engine also schedules the first attack at 1000 ms regardless of
base interval; subsequent attacks use the projected interval. Integer combat
stats and millisecond rounding preclude complete numerical battle parity.

## Verification

Verified with the real non-Mono Godot **4.6.2.stable.official.71f334935** at
`D:/GODOT/Godot_v4.6.1-stable_mono_win64/Godot_v4.6.2-stable_win64.exe`.
The containing directory's name does not identify the executable version.
The GUI executable must be launched through `Start-Process -WindowStyle Hidden`
and explicitly waited on; direct invocation can return before Godot completes.
The shared runner checks both process exit code and logs for script/errors.

The new test was first run against the old implementation and failed with
Juggernaut HP `expected=1028 actual=1000` and missing catalog. After migration:

```powershell
./tools/verify_migration.ps1 -TestNames test_android_hero_catalog.gd,test_battle_loadout_builder.gd,test_preparation_screen.gd,test_content_catalog.gd,test_journey_battle_bridge.gd,test_battle_screen.gd
```

All six selected tests passed with exit 0 and no logged engine/script errors.
The new test covers independent naked-stat fixtures for every hero, exact
fractional values, initial mana, skill costs/cooldowns, metadata copy isolation,
level-ten scaling and its order relative to gear, all original hero fallbacks,
unknown IDs, the full preparation cycle and per-hero preset retention.
This is headless verification; no visual gameplay or Android ability-parity
acceptance test is claimed.
