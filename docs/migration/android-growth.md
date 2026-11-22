# Android growth upgrades

`scripts/progression/AndroidGrowthRules.gd` ports the Android growth catalog and
purchase logic into deterministic in-memory Godot rules. It performs no save,
UI, session, network or `user://` operations. Android and Godot remain parallel
versions; this does not import Android saves.

Sources read directly on 2026-10-22:

- `shared/src/commonMain/kotlin/com/example/douqiuqiu/growth/GrowthContent.kt`
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/growth/GrowthMaterialPlanner.kt`
- `app/src/main/java/com/example/douqiuqiu/inventory/GrowthRules.kt`
- `app/src/main/java/com/example/douqiuqiu/system/GrowthRequirementChecker.kt`
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/inventory/Inventory.kt`
- Android growth integration and material planner tests

## Calling the rules

```gdscript
const Growth = preload("res://scripts/progression/AndroidGrowthRules.gd")

var offers: Array[Dictionary] = Growth.entries()
var next: Dictionary = Growth.next_entry(state, "backpack")
var result: Dictionary = Growth.upgrade(state, "backpack")
if result.accepted:
    # The existing state has already been updated; the caller may now save it.
    print(result.id, result.consumed_instance_ids)
else:
    print(result.reason)
```

`entries() -> Array[Dictionary]` returns all 21 Android entries in source order:
four hero unlocks, four skill-slot upgrades, four backpack upgrades, two safe
upgrades, three equipment protection upgrades and four skill protection upgrades.
It returns detached dictionaries on every call, including nested materials and
requirements. Entry fields are:

```gdscript
{
    "id": "safe:2",             # Exact Android upgrade ID
    "name": "安全箱3×2→3×3",     # Exact Android localized name
    "type": "safe",             # Category, see below
    "target": 2,                # Android growth tier, not a size or slot count
    "cost_gold": 1200,
    "materials": {"safe_frame": 1},
    "requirements": {"min_clears": 5, "extraction": false, "backpack_level": 1},
    "hero_id": null
}
```

`upgrade(state: PreparationState, kind: String) -> Dictionary` accepts the
category `backpack`, `safe`, `skills`, `equipment_guard`, or `skill_guard` to
purchase its next tier. It also accepts any exact Android entry ID. Explicit
IDs reject repeated tiers and skipped tiers. Heroes require an explicit ID such
as `hero:skywrath`; the ambiguous category `hero` rejects. Equipment protection
has category `equipment_guard` but stable entry IDs `equip_guard:1` through
`equip_guard:3`, matching Android.

Success returns `accepted: true`, a localized `reason`, the chosen `id`, `type`,
`target`, and `consumed_instance_ids` in deterministic planner order. Rejection
returns `accepted: false` and a nonempty `reason`, leaving the entire supplied
state unchanged. All missing resource/qualification reasons are reported
together after phase, entry and tier checks succeed.

`next_entry(state: PreparationState, kind: String) -> Dictionary` is the public
read-only wrapper over the same resolver used by purchases. For the five growth
categories it returns only the next tier's full entry, or `{}` at the cap or for
an unknown category. It returns the offer even when gold, materials or
qualifications are insufficient, so the UI can show costs and requirements.
It does not validate purchase eligibility or mutate state. Returned nested
metadata is detached. Exact IDs resolve as-is, including previous tiers; use
category names when rendering the next-upgrade UI. The session wrapper owns
saving and full-state rollback if saving fails after an accepted purchase.

## Exact tracks

The backpack size ladder is 4×2 → 4×3 → 5×3 → 6×3 → 6×4, with Android tiers
0 through 4. Godot currently starts at 4×3, so it starts at tier 1. Calling
`upgrade(state, "backpack")` therefore selects `backpack:2`, costing 400 gold
and three fabric, and requires one clear. Calling `backpack:1` on that state
rejects without payment. A 4×2 state can still purchase the original tier 1.

The safe size ladder is 2×2 → 3×2 → 3×3, with tiers 0 through 2.

| Upgrade | Gold | Materials | Qualification |
| --- | ---: | --- | --- |
| `skills:1` (4→5 enabled) | 250 | essence 3 | extracted flag |
| `skills:2` (5→6) | 500 | essence 5 | 1 clear |
| `skills:3` (6→7) | 900 | essence 8, skill_shard 2 | 3 clears |
| `skills:4` (7→8) | 1500 | essence 12, skill_shard 4 | 6 clears |
| `backpack:1` (4×2→4×3) | 200 | backpack_fabric 2 | extracted flag |
| `backpack:2` (4×3→5×3) | 400 | backpack_fabric 3 | 1 clear |
| `backpack:3` (5×3→6×3) | 700 | backpack_fabric 5 | 3 clears |
| `backpack:4` (6×3→6×4) | 1000 | backpack_fabric 8 | 6 clears |
| `safe:1` (2×2→3×2) | 600 | safe_lock 1 | 1 clear |
| `safe:2` (3×2→3×3) | 1200 | safe_frame 1 | 5 clears; backpack tier ≥1 |
| `equip_guard:1` | 300 | equipment_guard_core 1 | 2 clears |
| `equip_guard:2` | 700 | equipment_guard_core 2 | 4 clears |
| `equip_guard:3` | 1200 | equipment_guard_core 3 | 6 clears |
| `skill_guard:1` | 300 | skill_guard_core 1 | 2 clears |
| `skill_guard:2` | 700 | skill_guard_core 2 | 4 clears |
| `skill_guard:3` | 1200 | skill_guard_core 3 | 6 clears |
| `skill_guard:4` | 1800 | skill_guard_core 4 | 8 clears |

Guard tracks require their previous tier and the listed clears; they do not
require a maxed safe. The source comment about a maxed safe concerns a possible
material conversion path, not the eligibility rules for buying guard levels.

The four original hero purchase channels also remain available:

| ID | Gold | Essence | Qualification |
| --- | ---: | ---: | --- |
| `hero:skywrath` | 300 | 3 | extracted flag |
| `hero:viper` | 500 | 5 | 1 clear |
| `hero:phantom_assassin` | 700 | 7 | 3 clears |
| `hero:necrophos` | 900 | 9 | 5 clears |

An already owned hero rejects without payment; this is a purchase, so it does
not grant quest-style duplicate-hero compensation. No current Android growth
entry sets `questId`, so there is no task-claim requirement or waived price in
this snapshot. Changing a future source table to use quest-gated purchases
would need a deliberate port of that new behavior.

## State representation

All physical slot arrays remain fixed: `state.equipment_slots.size() == 9`
and `state.card_slots.size() == 8`. Growth never resets or resizes them and
never edits equipment/card contents, presets, hero levels or item placements.

| Value | Godot storage | Default/range |
| --- | --- | --- |
| Backpack tier | Index of `state.backpack_size` in the size ladder | Current default 4×3 is tier 1 |
| Safe tier | Index of `state.safe_size` in the size ladder | Default 2×2 is tier 0 |
| Enabled skill slots | `state.quest_progress.enabled_card_slots` | Default 4; range 4–8 |
| Equipment protection tier | `state.quest_progress.equipment_guard_level` | Default 0; range 0–3 |
| Skill protection tier | `state.quest_progress.skill_guard_level` | Default 0; range 0–4 |
| Lifetime clears | `state.quest_progress.clears` | Default 0 |
| Extraction qualification | `state.quest_progress.extracted` | Default false |
| Purchased hero unlock | `state.unlocked_heroes` | Existing unlocks preserved |

For the current main-task death integration, the exact keys are
`quest_progress["equipment_guard_level"]` and
`quest_progress["skill_guard_level"]`, each defaulting to 0. Preserving occupied
equipment/card slot indices `0 <= index < level` is the chosen main-task policy.
These rules only buy the capacity metadata; Android's selectable guarded-item
whitelist is a separate selection mechanism and is not implemented here.

Unknown progress keys, future quest counters, existing claims and qualifications
are preserved. Growth creates no item instances and does not change
`state.progression_serial` or `state.craft_serial`.

The Android requirement checker explicitly uses the provided extraction flag;
even 99 clears do not substitute for a false flag. The session settlement layer
must maintain `quest_progress.extracted` when the qualifying extraction or full
clear succeeds. At inspection time `ProgressionRules.record` incremented
`clears` on `RunCleared` but did not set `extracted`. This integration hook is
outside these three files. Growth purchases do not invent that history.

## Transactions and material selection

Purchases require `PreparationState.Phase.HOME` and no non-null legacy/optional
`quest_progress.run`. They validate container sizes, fixed slot array counts,
enabled skill slots, guard tier ranges, nonnegative clear counts, Android gold
range, the 10000-instance limit, instance IDs/dictionary identity and material
definition availability before any deduction. Cost definitions must exist as
`MATERIAL` in `AndroidEquipmentCatalog`.

Each material instance contributes exactly one unit. Only instances with a
matching `definition_id`, `ItemInstance.Kind.MATERIAL`, warehouse location and
`locked == false` are eligible. Bound materials are allowed and preferred.
The planner sorts material definition IDs, then bound before unbound, then
instance IDs lexicographically. It selects exactly the required quantity.
Insufficient resources across any material abort the entire plan; partial
plans cannot consume the materials that happened to be available.

The explicit locked-item exclusion implements the migration request. The
inspected Android `GrowthMaterialInstance` model itself has no locked field;
its planner supplies the warehouse/bound/ID ordering. This port preserves that
ordering while honoring Godot's additional instance lock protection.

After every check succeeds, the rule deducts gold, erases only planned warehouse
instance IDs, updates the selected growth field, and calls
`ProgressionRules.observe(state)`. That observer is a safe dependency in the
inspected tree: it depends on quests/hero levels, not growth, and performs no
persistence. Do not add a reverse preload from `ProgressionRules` to growth.
Use the session layer as the owner of both APIs if it needs a facade.

Observation immediately acknowledges size/skills/hero milestones, including
future quest nodes, without claiming rewards or fabricating event history.
Rejected upgrades never call the observer, so they do not update unrelated
milestones as a side effect. Retained item objects keep their identity; even
consumed objects are simply removed from the state's item dictionary rather
than modified through external references.

## Verification and remaining integration

`tests/test_android_growth.gd` uses independently written fixtures for all
21 source entries. It exercises every exact purchase threshold, one-gold and
one-material shortages, clear/extraction near misses, sequential tiers, caps,
all five full tracks, default backpack compatibility, safe prerequisites,
bound-first selection, lock/location/type exclusions, multi-material rollback,
hero unlocks/repeats, malformed state, preservation of unrelated data and quest
milestone observation. It neither reads nor writes `user://` fixtures.

The initial test failed because the implementation was absent (exit 1).
Verification on 2026-10-22 with standalone Godot 4.6.2: **491 checks, zero
failures, exit 0**.

This includes public next-entry lookup across every intermediate tier, all five
caps, insufficient-resource display, detached metadata and no state mutation.

```powershell
$growthLog = Join-Path $env:TEMP 'infinite-strife-growth-green.log'
$growthProcess = Start-Process -FilePath 'D:\GODOT\Godot_v4.6.1-stable_mono_win64\Godot_v4.6.2-stable_win64.exe' -ArgumentList @('--headless','--path','D:\GODOT\InfiniteStrife','--script','res://tests/test_android_growth.gd','--log-file',$growthLog) -Wait -PassThru -WindowStyle Hidden
Get-Content $growthLog
$growthProcess.ExitCode
```

Only the growth rule, its standalone test and this document are changed.
State/save/UI/session integration remains with the main task: expose purchases,
save the resulting metadata, maintain extraction qualification, enforce enabled
card slots in loadout/combat flows, and apply guard protection during death
settlement. Guard upgrades here grant permanent protection capacity metadata;
they do not choose guarded items, implement protection toggles, or alter death
loss rules. Existing inventory/loadout validation remains responsible for full
grid overlap, placement legality and catalog consistency of unrelated items;
this module does not duplicate the entire Android inventory validator.
