# Mobile 0.1.5 Playable-Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an original, portrait-first Godot game that reaches the Android 0.1.5 core loop: choose a hero and build, complete a five-node expedition, collect and appraise loot, manage inventory and synthesis, then save and continue.

**Architecture:** Godot owns all runtime data in original JSON-like GDScript content records. The deterministic domain stays free of scene nodes; screens read immutable snapshots and submit named commands. Android 0.1.5 is only a scope and balance-reference product: Godot retains no Android source, Dota-derived names, assets, silhouettes or direct data tables.

**Tech Stack:** Godot 4.6.2 Stable, GDScript, Control UI, JSON save files, Compatibility renderer, Windows debug export and Android debug export.

## Global Constraints

- Primary canvas is portrait `720×1280`; all touch targets must be at least 72 logical pixels tall or wide.
- Windows runs emulate the portrait mobile canvas and are not a separate desktop layout.
- All Godot public data, art, audio, names and descriptions are original and registered in `assets/ASSET_REGISTER.md`.
- Fixed seed plus identical commands must reproduce the same battle and expedition log.
- A content release requires engine tests, save round-trip tests, a portrait runtime smoke test and an Android device checklist.
- Android 0.1.5 remains independent and unchanged by this work.

---

### Task 1: Portrait application shell and mobile navigation

**Files:**
- Modify: `project.godot`
- Modify: `scenes/MainMenu.tscn`
- Modify: `scenes/Battle.tscn`
- Modify: `scripts/app.gd`
- Create: `scripts/ui/ScreenRouter.gd`
- Create: `tests/test_portrait_shell.gd`

**Interfaces:**
- `ScreenRouter.open(screen_path: String) -> Error` changes scenes only to registered paths.
- `ScreenRouter.is_portrait_viewport() -> bool` returns true when height is greater than width.
- Main menu exposes `open_loadout()` and `start_default_match()`.

- [x] **Step 1: Write the failing portrait test**

```gdscript
func _test_portrait_viewport() -> void:
    var settings := ProjectSettings
    _assert_equal(settings.get_setting("display/window/size/viewport_width"), 720)
    _assert_equal(settings.get_setting("display/window/size/viewport_height"), 1280)
```

- [x] **Step 2: Run the test before configuration**

Run: `godot --headless --path . --script res://tests/test_portrait_shell.gd`

Expected: FAIL because the existing viewport is `1280×720`.

- [x] **Step 3: Implement portrait settings and router**

```gdscript
class_name ScreenRouter
extends RefCounted

const SCREENS := {
    "home": "res://scenes/MainMenu.tscn",
    "loadout": "res://scenes/Loadout.tscn",
    "battle": "res://scenes/Battle.tscn",
}

func open(tree: SceneTree, screen_id: String) -> Error:
    return tree.change_scene_to_file(SCREENS.get(screen_id, ""))
```

- [x] **Step 4: Re-run test and open both scenes in headless mode**

Run: `godot --headless --path . --script res://tests/test_portrait_shell.gd`

Expected: PASS; menu and battle scenes parse without missing resources.

### Task 2: Original content catalog and stable identifiers

**Files:**
- Create: `scripts/content/ContentCatalog.gd`
- Create: `scripts/content/HeroDefinition.gd`
- Create: `scripts/content/ItemDefinition.gd`
- Create: `scripts/content/CardDefinition.gd`
- Create: `scripts/content/EventDefinition.gd`
- Create: `tests/test_content_catalog.gd`
- Modify: `assets/ASSET_REGISTER.md`

**Interfaces:**
- `ContentCatalog.hero(id: String) -> HeroDefinition`
- `ContentCatalog.item(id: String) -> ItemDefinition`
- `ContentCatalog.cards_for_school(school_id: String) -> Array[CardDefinition]`
- `ContentCatalog.event(id: String) -> EventDefinition`

- [x] **Step 1: Write catalog contract tests**

```gdscript
func _test_catalog_has_original_mobile_launch_content() -> void:
    _assert_equal(ContentCatalog.heroes().size(), 10)
    _assert_equal(ContentCatalog.schools().size(), 8)
    _assert_equal(ContentCatalog.cards().size(), 45)
    _assert_equal(ContentCatalog.events().size(), 16)
```

- [x] **Step 2: Run before creating catalog**

Expected: FAIL because `ContentCatalog` does not exist.

- [x] **Step 3: Implement data-only original records**

```gdscript
class_name HeroDefinition
extends RefCounted
var id: String
var display_name: String
var max_hp: int
var max_mp: int
var skill: Dictionary
```

Use neutral original identifiers such as `cinder`, `gale`, `bastion`, `vex`, `rime`, `thorn`, `lumen`, `shade`, `emberwing`, and `ironveil`; no names are imported from Android.

- [x] **Step 4: Register every non-procedural placeholder asset and validate IDs**

`tests/test_content_catalog.gd` must reject duplicate IDs, absent card schools, absent item recipes and missing asset-register rows.

- [x] **Step 5: Run catalog tests**

Expected: PASS with 10 heroes, 8 schools, 45 cards, 32 items and 16 events.

### Task 3: Deterministic combat parity and battle-presentation foundation

**Files:**
- Modify: `scripts/battle/FighterState.gd`
- Modify: `scripts/battle/BattleEngine.gd`
- Modify: `scripts/battle/BattleLog.gd`
- Create: `scripts/battle/StatusState.gd`
- Create: `scripts/battle/SkillRuntime.gd`
- Create: `tests/test_combat_rules.gd`
- Create: `docs/porting/godot-rule-vectors.json`

**Interfaces:**
- `BattleEngine.step(delta_ms: int) -> Array[Dictionary]`
- `BattleEngine.command(command: Dictionary) -> Dictionary`
- `BattleEngine.snapshot() -> Dictionary` includes hp, mp, cooldowns, shields, statuses, timer and result.
- Events include `tick_ms`, `kind`, `source_id`, `target_id`, `amount`, `school_id` and `remaining_hp`.

- [ ] **Step 1: Write rule-vector tests**

```gdscript
func _test_shield_absorbs_before_health() -> void:
    var engine := fixture_engine({"right_shield": 80})
    engine.step(1000)
    _assert_equal(engine.snapshot().right.shield, 22)
    _assert_equal(engine.snapshot().right.hp, 500)
```

Cover physical armor, magic resistance, shield, stun, silence, mana gating, cooldown, critical strike, life steal, damage-over-time and 60-second timeout.

- [ ] **Step 2: Run before adding status and skill runtimes**

Expected: FAIL because snapshot has no shield, resource, cooldown or status keys.

- [ ] **Step 3: Add explicit state and command processing**

```gdscript
func command(command: Dictionary) -> Dictionary:
    if command.get("kind") != "cast_skill": return {"accepted": false, "reason": "unknown_command"}
    if fighter.mp < skill.mana_cost: return {"accepted": false, "reason": "insufficient_mana"}
    if fighter.cooldowns.get(skill.id, 0) > 0: return {"accepted": false, "reason": "cooldown"}
    return _cast_skill(fighter, target, skill)
```

- [ ] **Step 4: Add fixed seed repeatability test after every rule family**

Run: `godot --headless --path . --script res://tests/test_combat_rules.gd`

Expected: PASS and two simulations with same seed and commands produce equal logs.

- [ ] **Step 5: Add original battle assets and presentation anchors**

Integrate original, distributable hero and enemy battle art, a portrait battlefield background, skill effects, status icons, and damage-number styling. Register each imported resource's source and license in `assets/ASSET_REGISTER.md`. Define stable foot, weapon, and effect anchors for each character. The first pass covers idle, attack, hit, and cast presentation; detailed frame-by-frame combos remain a later art iteration.

### Task 4: Mobile loadout, cards and build explanation

**Files:**
- Create: `scenes/Loadout.tscn`
- Create: `scripts/ui/LoadoutScreen.gd`
- Create: `scripts/build/LoadoutState.gd`
- Create: `scripts/build/BuildRules.gd`
- Create: `tests/test_loadout_rules.gd`

**Interfaces:**
- `LoadoutState` has one `hero_id`, six `item_ids`, six `card_ids`, and `revision`.
- `BuildRules.equip(loadout, item_id, slot) -> Dictionary`
- `BuildRules.set_card(loadout, card_id, slot) -> Dictionary`
- `BuildRules.synergy_summary(loadout) -> Array[Dictionary]`

- [ ] **Step 1: Write capacity, replacement and synergy tests**

```gdscript
func _test_equipping_to_an_occupied_slot_returns_previous_item() -> void:
    var result := BuildRules.equip(loadout_with("ward_charm"), "storm_lens", 0)
    _assert_equal(result.replaced_item_id, "ward_charm")
    _assert_equal(result.loadout.item_ids[0], "storm_lens")
```

- [ ] **Step 2: Run test before `BuildRules` exists**

Expected: FAIL with missing class.

- [ ] **Step 3: Implement portrait loadout UI**

The upper 40% shows hero portrait placeholder, six item slots and six card slots. The lower 60% uses filter tabs and an item grid. Tapping opens a detail sheet; long press opens replacement or lock controls. No desktop-only hover interaction.

- [ ] **Step 4: Re-run domain tests and headless scene navigation**

Expected: PASS; switching heroes applies their last saved loadout.

### Task 5: Five-node expedition, difficulty, events and loot

**Files:**
- Create: `scripts/run/RunState.gd`
- Create: `scripts/run/RunRules.gd`
- Create: `scripts/run/LootRules.gd`
- Create: `scenes/Expedition.tscn`
- Create: `scripts/ui/ExpeditionScreen.gd`
- Create: `tests/test_run_rules.gd`

**Interfaces:**
- `RunRules.start(seed, loadout, difficulty) -> RunState`
- `RunRules.resolve_battle(run, battle_result) -> Dictionary`
- `RunRules.choose_event(run, option_id) -> Dictionary`
- `LootRules.appraise(run, loot_id) -> Dictionary`

- [ ] **Step 1: Write deterministic five-node test**

```gdscript
func _test_same_seed_builds_same_five_node_run() -> void:
    _assert_equal(RunRules.start(420, loadout, 2).nodes, RunRules.start(420, loadout, 2).nodes)
```

- [ ] **Step 2: Verify expected failure**

Expected: FAIL because `RunRules` does not exist.

- [ ] **Step 3: Implement battle/event nodes and six difficulty tiers**

The run contains five nodes, never repeats the same event category in the two immediately preceding event nodes, and applies escalating enemy budget at difficulty 1–6. Higher tiers reduce enemy-loadout disclosure and gate higher item rarities; low-tier items remain available as components.

- [ ] **Step 4: Implement loot ground, appraise duration and explicit departure**

Unclaimed loot stays on the node until the player presses `返回远征`; then a confirmation discards it. The fifth-node target chest bypasses backpack capacity and goes to camp storage. Each item has grid footprint, value, rarity and a non-skippable appraisal duration derived from them.

- [ ] **Step 5: Run `tests/test_run_rules.gd`**

Expected: PASS for battle loot, event reward, partial pickup, discard confirmation, target chest and each difficulty tier.

### Task 6: Inventory, packing, synthesis and shop

**Files:**
- Create: `scripts/inventory/GridInventory.gd`
- Create: `scripts/inventory/InventoryRules.gd`
- Create: `scripts/inventory/CraftingRules.gd`
- Create: `scripts/shop/ShopRules.gd`
- Create: `scenes/Inventory.tscn`
- Create: `scripts/ui/InventoryScreen.gd`
- Create: `scenes/Crafting.tscn`
- Create: `tests/test_inventory_rules.gd`

**Interfaces:**
- `GridInventory.place(item_instance, x, y, rotated) -> Dictionary`
- `InventoryRules.collect_all(run) -> Dictionary`
- `InventoryRules.quick_sell(item_id) -> Dictionary`
- `CraftingRules.available_recipes(state) -> Array[Dictionary]`
- `ShopRules.buy(state, offer_id, revision) -> Dictionary`

- [ ] **Step 1: Write grid and stale-offer tests**

```gdscript
func _test_rotated_two_by_one_item_fits_one_by_two_gap() -> void:
    var result := inventory.place(item("storm_lens", Vector2i(2, 1)), 0, 0, true)
    _assert_equal(result.accepted, true)

func _test_stale_shop_revision_returns_fresh_snapshot() -> void:
    var result := ShopRules.buy(state, "offer_a", 0)
    _assert_equal(result.accepted, false)
    _assert_equal(result.reason, "stale_offer")
    _assert_equal(result.refreshed.revision, state.shop.revision)
```

- [ ] **Step 2: Verify failure before implementation**

Expected: missing classes and methods.

- [ ] **Step 3: Implement touch-first item placement**

Tap selects; tap a valid cell places; long press starts drag; one visible rotation control rotates the selected item. Occupied equipment/card slots replace and return the prior entry to inventory. A `整理` action is stable and deterministic; `快速出售` ignores locked items.

- [ ] **Step 4: Implement synthesis and shop sheets**

Synthesis shows craftable recipes first, unavailable recipes after a dim separator, and names each missing ingredient source. Shop purchases are atomic and stale revisions return the current offer list.

- [ ] **Step 5: Run inventory suite**

Expected: PASS for placement, rotate, replace, organize, lock, bulk pickup, sell, craft and stale purchase.

### Task 7: Versioned saves, return flow and release evidence

**Files:**
- Create: `scripts/save/SaveCodec.gd`
- Create: `scripts/save/SaveRepository.gd`
- Create: `tests/test_save_codec.gd`
- Modify: `scenes/MainMenu.tscn`
- Modify: `scripts/app.gd`
- Modify: `tools/verify_phase_a.ps1`
- Create: `tools/verify_mobile_loop.ps1`
- Create: `docs/testing/godot-mobile-parity.md`

**Interfaces:**
- `SaveCodec.encode(state: Dictionary) -> String`
- `SaveCodec.decode(text: String) -> Dictionary`
- `SaveRepository.save(slot_id: String, state: Dictionary) -> Error`
- `SaveRepository.load(slot_id: String) -> Dictionary`

- [ ] **Step 1: Write round-trip and migration tests**

```gdscript
func _test_v1_save_round_trips_mid_run_and_loadout() -> void:
    var restored := SaveCodec.decode(SaveCodec.encode(fixture_run()))
    _assert_equal(restored.run.seed, 7001)
    _assert_equal(restored.loadouts.cinder.item_ids.size(), 6)
```

- [ ] **Step 2: Run before codec exists**

Expected: FAIL with missing `SaveCodec`.

- [ ] **Step 3: Implement version envelope and safe default migration**

```gdscript
const CURRENT_VERSION := 1
func encode(state: Dictionary) -> String:
    return JSON.stringify({"version": CURRENT_VERSION, "payload": state})
```

Reject future versions with a readable error; migrate missing optional fields to defaults; never overwrite a failed decode.

- [ ] **Step 4: Add home actions and verification script**

Main menu exposes `继续远征`, `新远征`, `整备`, and `设置`. `verify_mobile_loop.ps1` runs every headless suite, verifies portrait dimensions, starts the Windows debug executable, checks save round-trip output and hashes Android/Windows exports when present.

- [ ] **Step 5: Run complete suite and record device checklist**

Expected: all GDScript tests pass; documentation includes portrait touch, pause/resume, low-memory restore, event loot, appraisal, crafting, shop and save checks.

## Scope Coverage

- Android 0.1.5’s combat, six difficulties, four new-hero-scale roster, eight schools, 45-card-scale pool, 16-event-scale pool, active effects, loot, appraisal, inventory, crafting, shop, version log behavior and persistence are represented by Tasks 2–7.
- Original-content and mobile portrait constraints are enforced by Tasks 1–2.
- Android runtime, iOS work, web export and third-party content are outside this plan.

## Execution Handoff

Plan saved as `docs/superpowers/plans/2026-10-21-mobile-015-parity.md`. Execute inline with `executing-plans`, completing one task and verification gate at a time.
