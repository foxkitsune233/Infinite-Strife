# 强化战斗演出 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Godot combat screen into an Android-inspired duel stage with deterministic-event-driven enhanced presentation.

**Architecture:** Keep `BattleEngine` authoritative and attach a presentation-only controller to `BattleScreen`. The controller converts semantic engine events into short-lived visual directives consumed by the stage, actors and effect layer. No visual state is persisted or fed back into simulation.

**Tech Stack:** Godot 4.6.2, GDScript, existing Android atlas PNG assets, headless scene-script tests.

## Global Constraints

- Preserve fixed 100ms engine simulation and current save payloads.
- Reuse Android art already under `assets/android/`; do not add generated art.
- Support portrait Android layouts and animation-disabled fallback.
- Every behavior change is introduced by a failing Godot test first.

---

### Task 1: Event-to-presentation controller

**Files:**
- Create: `scripts/ui/BattlePresentationController.gd`
- Create: `tests/test_battle_presentation_controller.gd`

**Interfaces:**
- Produces: `push_event(event: Dictionary) -> Dictionary`, `advance(delta: float) -> void`, `side_state(side: String) -> Dictionary`, `camera_offset() -> Vector2`.

- [ ] Write mapping tests for normal hit, critical hit, skill, dot and animations-disabled states.
- [ ] Run the test and confirm missing-controller failure.
- [ ] Implement bounded, presentation-only directives and decay.
- [ ] Run the controller test to green.

### Task 2: Duel-stage scene structure

**Files:**
- Modify: `scenes/Battle.tscn`
- Modify: `scripts/ui/BattleScreen.gd`
- Test: `tests/test_battle_screen.gd`

**Interfaces:**
- Consumes controller `side_state` and `camera_offset`.
- Produces stage anchor methods used by the effect layer.

- [ ] Add a failing scene assertion for shared left/right arena anchors.
- [ ] Run the scene test and confirm the pre-stage structure fails.
- [ ] Replace vertical actor-card layout with shared stage plus compact HUD.
- [ ] Run the scene test to green.

### Task 3: Actors, effects and regression verification

**Files:**
- Modify: `scripts/ui/BattleActorView.gd`
- Modify: `scripts/ui/BattleEffectView.gd`
- Modify: `scripts/ui/BattleScreen.gd`
- Test: `tests/test_battle_actor_view.gd`, `tests/test_battle_presentation.gd`

**Interfaces:**
- Consumes controller directives and stage anchor positions.
- Produces visual-only actor transforms, damage labels and projectile/ring nodes.

- [ ] Add failing tests for hit flash/knockback and anchor-based effect placement.
- [ ] Run focused tests and confirm failure.
- [ ] Implement effects with automatic node cleanup and animation-disabled fallback.
- [ ] Run focused plus full migration verification.
