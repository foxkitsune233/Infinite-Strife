# Godot Original Transition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an original-setting Godot vertical slice while retaining the Android application as a maintained reference product.

**Architecture:** The existing Android app remains independent. A new `godot/` project owns its scenes, GDScript, original assets and local save format. A small deterministic battle domain is separated from Godot nodes so battle logs can be compared across repeated runs; Android’s existing rules serve as design input, not a runtime dependency.

**Tech Stack:** Godot 4 stable, GDScript, Godot Compatibility renderer, JSON resources, native Godot unit tests or headless test runner, Windows and Android exports. Web export is optional after the native Godot version is established.

## Global Constraints

- Preserve the Android application id, source tree and course-submission behavior.
- Use original names, visuals, icons, audio and lore in every Godot public build.
- Do not copy Dota-derived art, names, silhouettes or data into `godot/`.
- Every Godot imported resource must have a source and license record.
- Web is optional and does not block Godot Android migration.
- Native iOS work remains deferred until a real device and compliant Xcode environment exist.

---

### Task 1: Create the Godot project boundary and provenance register

**Files:**
- Create: `godot/project.godot`
- Create: `godot/README.md`
- Create: `godot/assets/ASSET_REGISTER.md`
- Create: `godot/scenes/MainMenu.tscn`
- Create: `godot/scripts/app.gd`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `res://scenes/MainMenu.tscn` as the startup scene.
- Produces: `res://assets/ASSET_REGISTER.md` with columns `path`, `source`, `license`, `author`, `public_build_allowed`, `replacement_status`.

- [ ] **Step 1: Write a failing startup check**

Create `godot/tests/test_project_boot.gd`:

```gdscript
extends RefCounted

func test_start_scene_exists() -> void:
    assert(FileAccess.file_exists("res://scenes/MainMenu.tscn"))
```

- [ ] **Step 2: Run the check before creating the scene**

Run: `godot --headless --path godot --script tests/test_project_boot.gd`

Expected: failure because `MainMenu.tscn` is absent.

- [ ] **Step 3: Create the minimal project**

Set `run/main_scene="res://scenes/MainMenu.tscn"`, `application/config/name="Infinite Strife"`, and `rendering/renderer/rendering_method="gl_compatibility"` in `project.godot`. Create a `Control`-based menu scene with a New Run button and an original placeholder title. Add `app.gd` as an autoload only after it has a concrete responsibility.

- [ ] **Step 4: Add asset provenance policy**

Populate the register with the title placeholder and every first-phase source asset. Reject a row with blank source or blank license in a small validation script.

- [ ] **Step 5: Re-run startup and provenance checks**

Run: `godot --headless --path godot --script tests/test_project_boot.gd`

Expected: success and no missing-resource errors.

- [ ] **Step 6: Commit**

```bash
git add godot .gitignore
git commit -m "feat(godot): create original project boundary"
```

### Task 2: Implement deterministic 1v1 battle domain

**Files:**
- Create: `godot/scripts/battle/BattleSeed.gd`
- Create: `godot/scripts/battle/FighterState.gd`
- Create: `godot/scripts/battle/BattleEngine.gd`
- Create: `godot/scripts/battle/BattleLog.gd`
- Create: `godot/tests/test_battle_engine.gd`

**Interfaces:**
- `BattleEngine.new(seed: int, left: FighterState, right: FighterState)` creates a deterministic match.
- `BattleEngine.step(delta_ms: int) -> Array[Dictionary]` returns newly emitted events.
- `BattleEngine.finished() -> bool` and `BattleEngine.result() -> Dictionary` expose the terminal state.
- Each event has `tick_ms`, `source_id`, `target_id`, `kind`, `amount`, and `remaining_hp`.

- [ ] **Step 1: Write repeatability test**

```gdscript
func test_same_seed_produces_same_log() -> void:
    var first := simulate(7001)
    var second := simulate(7001)
    assert(first == second)
```

- [ ] **Step 2: Run it before implementation**

Run: `godot --headless --path godot --script tests/test_battle_engine.gd`

Expected: failure because `BattleEngine` is absent.

- [ ] **Step 3: Implement minimal combat**

Implement two original fighter presets, attack interval, physical damage, armor reduction, life loss, 60-second timeout and deterministic RNG. Serialize all events through `BattleLog`; do not read scene state inside the engine.

- [ ] **Step 4: Add rule-vector tests**

Test armor 0, armor 12, an attack that kills, an attack reduced to zero, and timeout. Store expected values as explicit constants in `test_battle_engine.gd`.

- [ ] **Step 5: Run test suite**

Run: `godot --headless --path godot --script tests/test_battle_engine.gd`

Expected: all repeatability and rule-vector tests pass.

- [ ] **Step 6: Commit**

```bash
git add godot/scripts/battle godot/tests/test_battle_engine.gd
git commit -m "feat(godot): add deterministic 1v1 battle domain"
```

### Task 3: Present the battle as an original playable scene

**Files:**
- Create: `godot/scenes/Battle.tscn`
- Create: `godot/scripts/ui/BattleScreen.gd`
- Create: `godot/assets/placeholders/` original placeholder assets
- Modify: `godot/scenes/MainMenu.tscn`
- Test: `godot/tests/test_battle_screen.gd`

**Interfaces:**
- `BattleScreen.start_match(seed: int, left_preset: String, right_preset: String) -> void`.
- `BattleScreen` consumes only `BattleEngine.step()` events and renders health, attacks, timer and terminal result.

- [ ] **Step 1: Write navigation test**

```gdscript
func test_menu_starts_battle_scene() -> void:
    var menu := preload("res://scenes/MainMenu.tscn").instantiate()
    menu.start_default_match()
    assert(menu.get_tree().current_scene.scene_file_path == "res://scenes/Battle.tscn")
```

- [ ] **Step 2: Run it before wiring navigation**

Run: `godot --headless --path godot --script tests/test_battle_screen.gd`

Expected: failure because the scene and method do not exist.

- [ ] **Step 3: Create the battle UI**

Use `Control`, `ProgressBar`, labels and original placeholder textures. Advance the engine in fixed 100 ms intervals. Display the last six battle events but retain the full structured log in memory.

- [ ] **Step 4: Verify first playable loop**

Run: `godot --path godot`

Expected: New Run starts a battle, both health bars update, the 60-second timer advances, and the result appears.

- [ ] **Step 5: Commit**

```bash
git add godot/scenes godot/scripts/ui godot/assets/placeholders godot/tests/test_battle_screen.gd
git commit -m "feat(godot): add original playable battle scene"
```

### Task 4: Export and acceptance evidence for phase A

**Files:**
- Create: `godot/export_presets.cfg`
- Create: `docs/testing/godot-phase-a.md`
- Create: `tools/godot/verify_phase_a.ps1`
- Modify: `godot/README.md`

**Interfaces:**
- `tools/godot/verify_phase_a.ps1` takes no secrets and writes logs under ignored `tools/build/`.
- Export targets: Windows Debug and Android Debug.

- [ ] **Step 1: Write verification script assertions**

The script must run headless battle tests, verify `godot/project.godot` has the main scene, inspect both exported files exist, and record Godot version plus SHA-256 values.

- [ ] **Step 2: Execute before exports exist**

Run: `powershell -ExecutionPolicy Bypass -File tools/godot/verify_phase_a.ps1`

Expected: fail with explicit missing Windows and Android artifact paths.

- [ ] **Step 3: Add export presets and export artifacts**

Create Compatibility-renderer Windows and Android Debug presets. Do not add an iOS preset. Export locally, keeping generated binaries out of Git.

- [ ] **Step 4: Run final phase-A verification**

Run: `powershell -ExecutionPolicy Bypass -File tools/godot/verify_phase_a.ps1`

Expected: headless tests pass; artifact metadata and hashes are recorded; no restricted asset is detected in `godot/assets/`.

- [ ] **Step 5: Commit documentation and scripts**

```bash
git add godot/export_presets.cfg godot/README.md docs/testing/godot-phase-a.md tools/godot/verify_phase_a.ps1
git commit -m "docs: add Godot phase A acceptance"
```

### Task 5: Maintain Android and prepare shared acceptance vectors

**Files:**
- Create: `docs/porting/android-rule-vectors.json`
- Create: `docs/testing/android-godot-parity.md`
- Modify: `docs/ROADMAP.md`

**Interfaces:**
- `android-rule-vectors.json` has `id`, `seed`, `inputs`, `expected` and `source_version` per case.
- First cases cover physical multiplier, armor 12, one shield absorption, a 60-second timeout and one fixed RNG roll.

- [ ] **Step 1: Capture existing Android values as immutable vectors**

Record the exact value and current Android version for every first-phase vector. Do not silently change expected output when Android balancing changes; append a new source version.

- [ ] **Step 2: Review vector coverage**

Check that each Godot phase-A battle feature maps to one vector. Add the mapping table to `android-godot-parity.md`.

- [ ] **Step 3: Commit**

```bash
git add docs/porting/android-rule-vectors.json docs/testing/android-godot-parity.md docs/ROADMAP.md
git commit -m "docs: establish Android Godot rule vectors"
```

## Self-review

- Scope is limited to Godot phase A plus Android reference vectors; inventory, events, synthesis, Web and iOS remain later phases.
- All public Godot resources must be original and recorded before use.
- Each task has an explicit artifact, test or visual run condition.
- No task changes existing Android runtime behavior.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-10-21-godot-original-transition.md`.

Two execution options:

1. Subagent-Driven — dispatch a fresh subagent per task with review gates.
2. Inline Execution — implement phase A in this session with checkpoints.
