# Android quest content and pure tracker

This is a Godot port of the current Android quest system, not an Android save
importer. Android and Godot remain parallel versions with separate persistence.
All 40 definitions across 24 ordered nodes are included now: 16 single quests
and eight sets of three alternatives. There is no replacement starter catalog.

Source snapshot: Android repository `222f6d36f51f31ac1998c833e0fa8d7c38f7ff94`,
read on 2026-10-22:

- `shared/src/commonMain/kotlin/com/example/douqiuqiu/quest/QuestContent.kt`
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/quest/QuestTracker.kt`
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/quest/QuestModels.kt`
- `docs/design/quest-progression.md`
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/growth/GrowthContent.kt`

The standalone parity fixture freezes the Android content independently of the
GDScript catalog: exact IDs, node order, names, hints (`description`), kind,
objective type/target/difficulty/hero/material, reward order/type/quantity/IDs,
and model defaults. Do not rename these IDs to match another Godot catalog.

## API and ownership

Preload `res://scripts/progression/QuestRules.gd` and
`res://scripts/progression/AndroidQuestCatalog.gd`.

```gdscript
var progress: Dictionary = state.quest_progress
progress = QuestRules.record(progress, {"type": "BattleWon", "hero_id": "skywrath", "difficulty": 0})
progress = QuestRules.observe(progress, milestones)
var current: Array[Dictionary] = QuestRules.visible(progress)
var result: Dictionary = QuestRules.claim(progress, "journey.1", {"at_home": true})
```

The four tracker entry points are static:

- `record(progress: Dictionary, event: Dictionary) -> Dictionary`
- `observe(progress: Dictionary, milestones: Dictionary) -> Dictionary`
- `visible(progress: Dictionary) -> Array[Dictionary]`
- `claim(progress: Dictionary, quest_id: String, context: Dictionary) -> Dictionary`

Progress is a dictionary containing `quest_counters: Dictionary` (quest ID to
integer) and `claimed_quests: Array` (stable quest ID strings). Missing fields
mean empty counters/claims. Other progress fields, unknown quest counters and
unknown claimed IDs are retained. Unknown IDs do not complete any known node.
Inputs are never mutated, including nested values and rejected claims; all
returned mutable data is detached from the caller and catalog snapshots.

`AndroidQuestCatalog.all() -> Array[Dictionary]` returns definitions in Android
order. `by_id(id) -> Dictionary` returns an empty dictionary for an unknown ID.
Quest fields use snake case for Android model properties:
`prerequisite_quest_ids`, `min_clears`, `repeatable`, and `cost`, alongside `id`,
`node`, `name`, `description`, `kind`, `objective`, and `rewards`.
Objective fields are `type`, `target`, `difficulty`, `hero_id`, `material_id`.
Kinds, objective types and reward types retain Android enum names as uppercase
strings. Optional hero/material IDs are `null`, not empty strings.

## Event dictionaries

Use the exact case-sensitive Android event names in `type`; payload keys use
snake case. Emit one event **after each successful operation**, as part of that
operation's proposed state transaction. The tracker cannot validate game state
or deduplicate repeated settlement callbacks by itself.

| `type` | Payload | Effect in this catalog |
| --- | --- | --- |
| `Entered` | none | Successful run entry |
| `LootPicked` | none | Successful pickup of new loot, capped at one |
| `BattleWon` | `hero_id: String`, `difficulty: int` | General wins and matching hero wins; win goals do not filter difficulty |
| `RunCleared` | `hero_id: String`, `difficulty: int` | Clear count and each goal whose difficulty is at most this difficulty |
| `Extracted` | none | Successful safe extraction after battle three |
| `EventChosen` | none | Successful event choice |
| `ShopPurchase` | none | Successful purchase |
| `Crafted` | none | Successful equipment craft |
| `HeroLevelChanged` | `hero_id: String`, `level: int` | Fill matching level goals when level reaches target |
| `ConsumableUsed` | none | Android event supported; no matching goal among these 40 quests |
| `GuardedDeath` | none | Android event supported; no matching goal among these 40 quests |
| `MaterialGained` | `material_id: String`, `count: int` | Android event supported; no matching goal among these 40 quests |

Do not emit `Extracted` for an ordinary full clear. Do not count failed/stale
purchases, failed crafts, replayed battle settlement, or carried-in items put
down and picked up again. Unknown event types do nothing. There is no bulk
`amount`/`count` increment for wins, clears, crafting or purchases: one event
means one successful operation. A hero-level event below target does not
partially fill that target, matching Android.

## Milestone dictionaries

Call `observe` after successful inventory/loadout/growth/hero-level changes,
and when reconstructing verifiable state. Then publish its returned progress.
Call it before `claim` if current milestones have not already been recorded;
`claim` has no access to inventory or live state and does not infer milestones.

| Key | Type/default | Meaning |
| --- | --- | --- |
| `equip_item` | `bool`, false | At least one item in an effective equipment slot, index 0 through 5 |
| `slot_card` | `bool`, false | At least one card in the skills container |
| `store_safe` | `bool`, false | At least one item in the safe container |
| `backpack_level` | `int`, 0 | Zero-based growth index: 4×2=0, 4×3=1, 5×3=2, 6×3=3, 6×4=4 |
| `safe_level` | `int`, 0 | Zero-based growth index: 2×2=0, 3×2=1, 3×3=2 |
| `skill_slots` | `int`, 4 | Absolute slot count; observed upgrade level is `max(0, skill_slots - 4)` |
| `clears` | `int`, 0 | Verifiable lifetime clear count |
| `hero_levels` | `Dictionary`, `{}` | Stable hero ID to current integer level; any-hero goals use max level, default 1 |

Observation only increases counters up to targets. Removing equipment, losing
items, consuming materials or later passing an empty milestone dictionary does
not revoke earned progress. Observed hero levels may partially fill level
targets (for example level 4 gives 4/5), as on Android. Observation never
fabricates historical wins, difficulty clears, extractions, events, purchases,
crafts or pickups. Do not pass these history values as inferred milestones.

## Node progression and difficulty

Events and milestones accumulate for **all** unfinished nodes, including future
nodes. Each counter stops at its objective target. Claimed quests and all their
same-node siblings stop advancing; stored sibling counters are retained.
`visible` returns only the first unclaimed node's one or three complete quest
definitions in catalog order, or an empty array after all nodes are claimed.
It does not append UI progress fields; read `quest_counters[quest.id]`, default 0.
Completion requires claiming a reward, not merely filling the objective.

`completed_nodes(progress)` counts distinct known nodes, even if a supplied
dictionary has duplicated claims, multiple siblings, holes or unknown IDs.
Claiming still requires every preceding node to have been claimed.
`max_difficulty(progress)` returns difficulty 0 through 5.
`locked_reason(progress, difficulty)` returns `""` when allowed, the Android
localized lock message otherwise, or `副本难度无效` outside 0 through 5.

| Difficulty | Required claimed nodes |
| --- | ---: |
| 0 简单 | 0 |
| 1 普通 | 6 |
| 2 困难 | 12 |
| 3 精英 | 16 |
| 4 大师 | 20 |
| 5 噩梦 | 23 |

The caller checks entry difficulty before charging tickets or carried gold.
An ongoing run must continue under its original rules. Chest generation and
reroll policy remain the caller's responsibility.

## Claim transaction

The claim result has exactly four keys:

```gdscript
{"accepted": bool, "reason": String, "progress": Dictionary, "rewards": Array}
```

`context.at_home` must be true only when at camp with no active run. Missing
or false rejects. Validation then checks quest existence, previous claim,
same-node mutex, preceding nodes, and objective target, in that order. All
40 definitions have `min_clears=0`, no explicit prerequisite IDs, no costs and
`repeatable=false`; ordered nodes implement their prerequisite policy. This is
a fixed-content port, not an injectable framework for hypothetical conversions.

Rejections preserve input progress and return no rewards. Success returns an
empty reason, proposed progress with the chosen ID appended, and detached reward
descriptors. It does **not** change gold, unlock heroes, instantiate items,
validate warehouse limits, or publish/save the proposed claim.

Each reward descriptor has exactly the Android fields, translated to snake case:

```gdscript
{"type": "HERO_UNLOCK", "amount": 300, "hero_id": "skywrath", "material_id": null}
{"type": "MATERIAL", "amount": 2, "hero_id": null, "material_id": "backpack_fabric"}
{"type": "GOLD", "amount": 200, "hero_id": null, "material_id": null}
```

For `HERO_UNLOCK`, `amount` is compensation gold **only if already owned**:
Skywrath 300, Viper 500, Phantom Assassin 700, Necrophos 900. Existing unlocks
and purchase channels remain available. `ITEM` and `MATERIAL` quantities mean
that many instances, respecting the real definition's binding properties and
warehouse placement. Guard cores in this catalog use `MATERIAL`, exactly as
Android, despite the broader Android model also defining `GUARD_CORE`.

The caller stages every reward and proposed progress on one detached full-state
snapshot, validates definition references, unique instance IDs, bound-on-acquire,
10000-item warehouse limit, Android-compatible gold overflow limits, and all
other relevant invariants. Publish/save the snapshot only after everything
succeeds. On reward failure discard the entire proposal, including its claimed
ID and any newly observed milestones belonging only to the failed transaction;
the original node remains claimable for retry. Never persist the claimed ID
before rewards have been successfully applied.

## Verification and integration boundary

Run using the standalone non-console Godot 4.6.2 executable (the console wrappers
on this machine have crashed):

```powershell
$questLog = Join-Path $env:TEMP 'infinite-strife-quest-green.log'
$questProcess = Start-Process -FilePath 'D:\GODOT\Godot_v4.6.1-stable_mono_win64\Godot_v4.6.2-stable_win64.exe' -ArgumentList @('--headless','--path','D:\GODOT\InfiniteStrife','--script','res://tests/test_quest_rules.gd','--log-file',$questLog) -Wait -PassThru -WindowStyle Hidden
Get-Content $questLog
$questProcess.ExitCode
```

Verification on 2026-10-22: the missing implementation first failed with exit 1;
the completed implementation passes 518 checks with zero failures and exit 0.
Coverage includes all content fields, future accumulation, caps, hero and
difficulty filters, milestone persistence, no fabricated history, detached
snapshots, claim rejection, sequence, mutex, repeat prevention, full progression,
all difficulty boundaries, and a complete simple-only route through 24 nodes.

This task owns only the catalog, pure rules, standalone test, and this document.
PreparationState, Save/SaveCodec, AppSession, UI, reward application and gameplay
event hooks are integrated separately. Their transactional reward rollback,
actual catalog reference availability, save/reload behavior, successful-operation
event wiring and UI must be verified by that integration. No Android persistence
migration, CRC/backups, legacy save fixtures, conversion quests, or changes to
existing runs are introduced by these four files.
