# Android 1.0 Equipment Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the selected Android 1.0 equipment set with usable synthesis, deterministic single-target adaptations, runtime safety, and regression coverage.

**Architecture:** Keep item definitions declarative in the content catalog. Add only the five missing combat primitives to the shared equipment runtime and fighter snapshot; item-specific behavior is routed by stable equipment group/definition IDs. Android inventory consumes the same catalog, so it needs no separate combat implementation.

**Tech Stack:** Kotlin Multiplatform shared module, Android AppCompat, JUnit4, Gradle.

## Global Constraints

- Preserve existing stable item IDs, inventory instances, save compatibility, and six active equipment slots.
- Do not add dependencies or implement movement, invisibility, multiplayer targeting, summons, or item cooldown reset.
- `nullifier` removes only dispellable positive combat state; `silver_edge` applies hero-passive break.
- Damage reflected by `blade_mail` uses `AttackClass.REFLECT`, cannot lifesteal, proc, crit, or reflect again.
- Run only targeted tests while the worktree contains unrelated uncommitted narrative/UI changes; do not commit mixed work.

---

### Task 1: Establish the content and recipe contract

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/content/EquipmentContent.kt`
- Modify: `app/src/main/assets/config/inventory.json`
- Test: `app/src/test/java/com/example/douqiuqiu/equipment/EquipmentBattleIntegrationTest.kt`

**Interfaces:**
- Produces `EquipmentCatalog.all` entries for all new IDs and their `CraftingRecipe` objects.
- Consumes `EquipmentDefinition`, `EffectDefinition`, `EffectKind`, and existing catalog registration in `InventoryCatalog`.

- [ ] **Step 1: Write failing catalog tests**

```kotlin
@Test fun selectedExpansionItemsHaveCraftableDefinitions() {
    val ids = setOf("blade_mail", "bloodthorn", "abyssal_blade", "scythe_of_vyse",
        "ethereal_blade", "shivas_guard", "dagon", "monkey_king_bar", "aeon_disk",
        "nullifier", "euls_scepter", "phantom_cloak", "silver_edge")
    assertEquals(ids, EquipmentCatalog.all.filter { it.id in ids }.map { it.id }.toSet())
    assertTrue(EquipmentCatalog.all.filter { it.id in ids }.all { it.recipe != null })
}
```

- [ ] **Step 2: Run the test and observe missing definitions**

Run: `./gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.douqiuqiu.equipment.EquipmentBattleIntegrationTest.selectedExpansionItemsHaveCraftableDefinitions`

Expected: FAIL because the selected IDs do not exist.

- [ ] **Step 3: Add components, scrolls, intermediate items, definitions, and inventory metadata**

Add `plate_mail`, `ultimate_orb`, `sacred_relic`, `ghost_scepter`, and `phantom_cloak`; add all selected recipes from the approved design. Ensure every definition has non-empty Chinese name, positive price, fixed footprint, quality, minimum shop gate, and a description that states the single-target adaptation when needed. Register any catalog-only entries in `inventory.json` using the existing item icon fallback pattern.

- [ ] **Step 4: Run catalog and recipe integration tests**

Run: `./gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.douqiuqiu.equipment.EquipmentBattleIntegrationTest`

Expected: PASS; old recipe-count expectations are updated to derive from the live catalog rather than a stale constant.

### Task 2: Add minimum combat primitives and persistent state

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/content/EffectModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/DuelEngine.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/equipment/EquipmentCombat.kt`
- Modify: `app/src/main/java/com/example/douqiuqiu/inventory/CampaignBinaryCodec.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/combat/EquipmentProcAcceptanceTest.kt`

**Interfaces:**
- Produces typed effects `TRUE_STRIKE`, `REFLECT_DAMAGE`, `BANISH`, and `BLOCK_POSITIVE_EFFECTS` plus Fighter snapshot fields for their timed state.
- Consumes the existing 100 ms duel tick, `damageCard`, `FighterEffects.weakDispel/strongDispel`, and `EquipmentCooldownTracker`.

- [ ] **Step 1: Write failing behavior tests**

```kotlin
@Test fun trueStrikeBypassesEvasionButNotAttackProtection() { /* MKB versus Butterfly and protection */ }
@Test fun reflectedDamageDoesNotRecurseOrLifesteal() { /* Blade Mail one reflected hit only */ }
@Test fun banishedTargetCannotActOrTakeDamageUntilExpiry() { /* Eul duration boundary */ }
@Test fun positiveBuffBlockPreventsNewDispellableShieldOnly() { /* Nullifier boundary */ }
```

- [ ] **Step 2: Run the focused tests and observe compilation/behavior failures**

Run: `./gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.douqiuqiu.combat.EquipmentProcAcceptanceTest`

Expected: FAIL because the effect kinds and state transitions are absent.

- [ ] **Step 3: Implement the typed contract and tick behavior**

Add explicit `EffectContract` entries. Store only bounded millisecond/double state required for the four effects, decrement them in `DuelEngine` alongside `broken` and `invulnerable`, and include them in codec read/write with defaults for pre-expansion profiles. Make attack evasion consult `EquipmentCombat.trueStrike`; route reflected damage through a non-recursive damage path; have banish reject actions and damage; and have positive-buff block reject only new dispellable positive shields/buffs.

- [ ] **Step 4: Run focused runtime and save tests**

Run: `./gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.douqiuqiu.combat.EquipmentProcAcceptanceTest --tests com.example.douqiuqiu.status.PoisonCoreBattleTest`

Expected: PASS with existing poison/control serialization unchanged.

### Task 3: Implement active-item resolution and counter equipment

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/equipment/EquipmentEffectRuntime.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/FighterEffects.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/combat/EquipmentProcAcceptanceTest.kt`

**Interfaces:**
- Consumes Task 2 effect kinds and Fighter state.
- Produces active handling for Abyssal Blade, Scythe of Vyse, Ethereal Blade, Shiva's Guard, Dagon, Nullifier, Eul's Scepter, Silver Edge, and Aeon Disk.

- [ ] **Step 1: Add failing, item-specific tests**

```kotlin
@Test fun nullifierRemovesNecrophosShieldWithoutRemovingPoisonOrControl() { /* active use */ }
@Test fun silverEdgeBreaksHeroPassivesButKeepsEquipmentEffects() { /* nec passive and gear */ }
@Test fun aeonDiskTriggersOnceAtLowHealthThenStartsCooldown() { /* one battle */ }
@Test fun abyssalAndScytheRespectImmunityAndStatusResistance() { /* active control */ }
```

- [ ] **Step 2: Run the new tests and observe failures**

Run: `./gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.douqiuqiu.combat.EquipmentProcAcceptanceTest`

Expected: FAIL because active groups do not dispatch these item IDs.

- [ ] **Step 3: Extend item dispatch without stringly-typed descriptions**

Use definition IDs only at the final item-specific boundary. Apply direct magical/pure damage through `host.damageCard`, use `TimedControls` for Hex/Stun, invoke the typed banish/positive-buff-break/break helpers, and start cooldown only after a legal application. Add automatic predicates: defensive counter items require relevant enemy buffs or low life; offensive controls require a vulnerable, living target; Aeon is automatic-only at its threshold.

- [ ] **Step 4: Run acceptance tests**

Run: `./gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.douqiuqiu.combat.EquipmentProcAcceptanceTest --tests com.example.douqiuqiu.equipment.EquipmentBattleIntegrationTest`

Expected: PASS.

### Task 4: Integrate passives, enemy builds, UI metadata, and final regression

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/EnemyBuilds.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/EnemyIntel.kt`
- Modify: `docs/design/equipment.md`
- Modify: `docs/design/equipment-recipes-verified.md`
- Create: `docs/testing/2026-11-04-equipment-expansion.md`
- Test: `app/src/test/java/com/example/douqiuqiu/equipment/EquipmentBattleIntegrationTest.kt`

**Interfaces:**
- Consumes catalog and runtime support from Tasks 1–3.
- Produces stable enemy build selection, player-visible counter descriptions, and an evidence record.

- [ ] **Step 1: Write failing assertions for enemy legality and item explanations**

```kotlin
@Test fun enemyBuildsOnlySelectImplementedActiveItems() { /* live catalog definition check */ }
@Test fun selectedCounterItemsExposeTheirSingleTargetRule() { /* description check */ }
```

- [ ] **Step 2: Run the focused test class**

Run: `./gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.douqiuqiu.equipment.EquipmentBattleIntegrationTest`

Expected: FAIL until the new catalog metadata and enemy filters are integrated.

- [ ] **Step 3: Complete integration and documentation**

Allow enemy builds to select only definitions whose effects are fully runtime-supported. Document each local adaptation, especially no-invisibility Silver Edge and single-target Shiva/Eul. Record passed tests and explicitly mark device visual validation as pending if no device is attached.

- [ ] **Step 4: Run final targeted suite and build**

Run: `./gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.douqiuqiu.equipment.EquipmentBattleIntegrationTest --tests com.example.douqiuqiu.combat.EquipmentProcAcceptanceTest --tests com.example.douqiuqiu.status.PoisonCoreBattleTest :app:assembleDebug`

Expected: BUILD SUCCESSFUL.
