# 0.1.7 转生爬塔与资源显示收口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the complete Android 0.1.7 tower release: a 20-floor continuous-health tower, inferno difficulty, hero augments, rebirth rewards, UI entry points, resource audit, and release validation.

**Architecture:** Keep all gameplay, persistence models, combat effects, and progression rules in `shared`. Android activities render the shared snapshots and commit immutable transactions through `PlayerRepository`. Add tower-only state to `TowerRunState`; it is serialized with the profile and is cleared only by tower death or floor-20 victory.

**Tech Stack:** Kotlin Multiplatform common code, Android Views, JUnit 4, Gradle Android plugin.

## Global Constraints

- Floor 1–5 use nightmare (difficulty 5) enemy budgets; floor 6–20 use new inferno (difficulty 6) budgets.
- Tower HP persists across floors; mana, cooldowns, shields and ordinary combat statuses reset each battle.
- Rest restores 30% max HP; supply grants only next-battle 60-second 1% max-HP-per-second regeneration; forge grants stackable +8 main attribute for this tower run only.
- Shard and Aghanim enter pending pools, are granted manually in camp, and remain permanent after grant.
- New artwork must have explicit, testable mappings or a documented deterministic vector fallback; do not add unlicensed artwork.
- Preserve unrelated untracked patch scripts and logs in the repository root.

---

### Task 1: Add inferno difficulty and persist full tower-run state

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/ChallengeBalance.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt`
- Modify: `app/src/main/java/com/example/douqiuqiu/inventory/CampaignBinaryCodec.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/inventory/Inventory.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/tower/TowerPersistenceTest.kt`

**Interfaces:**
- Produces `TowerRunState.currentHp`, `pendingSupplyRegen`, `towerPrimaryBonus`, and `fight`.
- Produces `ChallengeBalance.difficulties == 0..6` with name `炼狱` at index 6.

- [ ] **Step 1: Write failing persistence and difficulty tests**

```kotlin
@Test fun infernoIsTheSeventhDifficulty() {
    assertEquals("炼狱", ChallengeBalance.name(6))
    assertTrue(6 in ChallengeBalance.difficulties)
}

@Test fun v19RoundTripPreservesContinuousTowerState() {
    val source = state(towerRun = TowerRunState(floor = 8, heroId = "viper", rng = 99,
        clearedFloors = 7, currentHp = 412.0, pendingSupplyRegen = true,
        towerPrimaryBonus = 16))
    assertEquals(source.progress.towerRun, decode(encode(source)).progress.towerRun)
}
```

- [ ] **Step 2: Run the new test and verify it fails because the new state and difficulty are absent**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.TowerPersistenceTest" --rerun-tasks --offline`

Expected: compilation failure for `currentHp`, `pendingSupplyRegen`, `towerPrimaryBonus`, and/or failed difficulty assertion.

- [ ] **Step 3: Implement the smallest serializable state expansion**

```kotlin
data class TowerRunState(
    val floor: Int = 1,
    val heroId: String = "juggernaut",
    val rng: Long = 1729,
    val clearedFloors: Int = 0,
    val loot: List<TowerLoot> = emptyList(),
    val currentHp: Double? = null,
    val pendingSupplyRegen: Boolean = false,
    val towerPrimaryBonus: Int = 0,
    val fight: DuelState? = null
)
```

Append a v19 codec section after v18 fields; read v18 and older profiles with `null`, `false`, `0`, and `null` defaults. Reject invalid floor, HP, bonus, or duplicate persisted state through `InventoryRules.validate`.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.TowerPersistenceTest" --tests "com.example.douqiuqiu.inventory.QuestPersistenceTest" --rerun-tasks --offline`

Expected: PASS; legacy profile migration tests continue to read defaults.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/ChallengeBalance.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerModels.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/inventory/Inventory.kt app/src/main/java/com/example/douqiuqiu/inventory/CampaignBinaryCodec.kt app/src/test/java/com/example/douqiuqiu/tower/TowerPersistenceTest.kt
git commit -m "feat(0.1.7): persist inferno tower state"
```

### Task 2: Implement continuous tower combat and node choices

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerRules.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerModels.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/tower/TowerCampaignTest.kt`

**Interfaces:**
- Produces `TowerFlow.startBattle(state)`, `tick(state)`, `chooseNode(state, TowerNodeChoice)` and `TowerNodeChoice.REST`, `SUPPLY`, `FORGE`.
- Consumes `TowerRunState.currentHp`, `pendingSupplyRegen`, and `towerPrimaryBonus` from Task 1.

- [ ] **Step 1: Write failing health and node-choice tests**

```kotlin
@Test fun winnerCarriesRemainingHealthButStartsNextBattleWithOpeningMana() {
    val won = resolveAtHp(run(floor = 1, hp = 400.0), won = true)
    val next = start(ok(won))
    assertEquals(416.0, next.fight!!.player.hp, 0.01) // 4% victory recovery
    assertEquals(openingMana(next.fight!!.player), next.fight!!.player.mana, 0.01)
}

@Test fun supplyOnlyAppliesRegenerationToNextBattleForSixtySeconds() {
    val supplied = chooseNode(nodeState(), TowerNodeChoice.SUPPLY)
    assertTrue(ok(supplied).progress.towerRun!!.pendingSupplyRegen)
    val battle = start(ok(supplied))
    assertEquals(0.01, battle.fight!!.player.towerRegenPerSecond, 1e-9)
    assertFalse(finish(battle).progress.towerRun!!.pendingSupplyRegen)
}

@Test fun forgeBonusIsTowerOnlyAndClearsOnDeath() {
    val forged = chooseNode(nodeState(), TowerNodeChoice.FORGE)
    assertEquals(8, ok(forged).progress.towerRun!!.towerPrimaryBonus)
    assertEquals(0, ok(resolve(ok(forged), false)).progress.towerRun?.towerPrimaryBonus ?: 0)
}
```

- [ ] **Step 2: Run the new test and verify it fails because the tower starts fresh every floor**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.TowerCampaignTest" --rerun-tasks --offline`

Expected: compile or assertion failure at missing API and reset HP behavior.

- [ ] **Step 3: Implement the node state machine and continuous HP**

Introduce a `TowerStage` (`READY`, `BATTLE`, `NODE`, `COMPLETE`) and `TowerNodeChoice`. Build the next player from normal `JourneyRules.fighter`, add the tower main-stat bonus before derived combat values, restore `currentHp` through `CombatMath.rescaleHealth`, and preserve normal opening mana. Apply `+4% max HP` only after a won floor. Set node state after floors 2, 5, 8, 11, 14, and 17; `REST` heals 30%, `SUPPLY` only arms next-battle 60-second 1% max-HP regen, and `FORGE` adds 8 primary attribute.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.TowerCampaignTest" --tests "com.example.douqiuqiu.tower.TowerRulesTest" --rerun-tasks --offline`

Expected: PASS; no node choice can apply an implicit strong dispel.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerRules.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerModels.kt app/src/test/java/com/example/douqiuqiu/tower/TowerCampaignTest.kt
git commit -m "feat(0.1.7): add continuous tower health and waypoints"
```

### Task 3: Use nightmare/inferno builds and fixed tower bosses

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/EnemyBuilds.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerRules.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/tower/TowerDifficultyTest.kt`

**Interfaces:**
- Produces `TowerRules.enemyBuild(floor, seed)` and `TowerRules.bossRule(floor)`.
- Uses `ChallengeBalance` difficulty 5 for floors 1–5 and 6 for floors 6–20.

- [ ] **Step 1: Write failing baseline and boss tests**

```kotlin
@Test fun firstFiveFloorsUseNightmareAndLaterFloorsUseInferno() {
    assertEquals(5, TowerRules.enemyBuild(1, 1729).difficulty)
    assertEquals(5, TowerRules.enemyBuild(5, 1729).difficulty)
    assertEquals(6, TowerRules.enemyBuild(6, 1729).difficulty)
    assertEquals(6, TowerRules.enemyBuild(20, 1729).difficulty)
}

@Test fun bossFloorsAreExactlyFiveTenFifteenAndTwenty() {
    assertEquals(setOf(5, 10, 15, 20), (1..20).filter { TowerRules.bossRule(it) != null }.toSet())
}
```

- [ ] **Step 2: Run the test and verify it fails because tower enemies are unarmed base heroes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.TowerDifficultyTest" --rerun-tasks --offline`

Expected: failed assertion or missing `enemyBuild` API.

- [ ] **Step 3: Implement deterministic enemy builds and four bounded boss rules**

Use the existing `EnemyBuilds` budget path with synthetic `JourneyState` difficulty 5 or 6 rather than reading player inventory. Give floor 5 a shield rule, floor 10 control resistance, floor 15 skill reflection window, and floor 20 one phase transition at 50% HP. Keep boss rules as a serializable `TowerBossRule` enum; do not add random per-run modifiers.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.TowerDifficultyTest" --tests "com.example.douqiuqiu.tower.TowerCampaignTest" --rerun-tasks --offline`

Expected: PASS; floor 6 changes the difficulty baseline exactly once.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/EnemyBuilds.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerRules.kt app/src/test/java/com/example/douqiuqiu/tower/TowerDifficultyTest.kt
git commit -m "feat(0.1.7): build nightmare and inferno tower encounters"
```

### Task 4: Add shared augmentation state and Rapier/rebirth transactions

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyRules.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/inventory/Inventory.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerRules.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/tower/TowerRewardUseTest.kt`

**Interfaces:**
- Produces `InventoryRules.useRebirthItem(state, itemIndex, cardInstanceId)`.
- Produces immutable fighter augmentation state `shard`, `aghsUsed`, `towerRegenRemaining`, and `rapierLethalUsed`.

- [ ] **Step 1: Write failing reward transaction tests**

```kotlin
@Test fun rebirthItemRaisesExactlyOneOwnedCardToThreeStars() {
    val result = rules.useRebirthItem(withPendingRebirthAndCard(star = 1), 0, "card-1")
    assertEquals(3, ok(result).items.single { it.instanceId == "card-1" }.star)
    assertTrue(ok(result).progress.pendingRebirthItems.isEmpty())
}

@Test fun rapierAppliesItsBonusesAndOnlyOneLethalShieldPerBattle() {
    val f = fighter(withRapier())
    assertEquals(base.attack * 1.45, f.attack, 0.01)
    assertTrue(firstLethalHitIsPrevented(f))
    assertFalse(secondLethalHitIsPrevented(f))
}
```

- [ ] **Step 2: Run the test and verify it fails because neither transaction nor combat state exists**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.TowerRewardUseTest" --rerun-tasks --offline`

Expected: compilation failure for `useRebirthItem` or failed Rapier assertions.

- [ ] **Step 3: Implement atomic reward use and global equipment effects**

Validate camp phase, pending item existence, card ownership, and target card type before consuming one `rebirth_item`; then upgrade only the selected stored card to star 3. Apply Rapier attack ×1.45, spell-damage ×1.35, attack speed +30, and per-battle first-lethal one-second invulnerability from immutable progress. Persist every fighter field that survives an app restart during battle.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.TowerRewardUseTest" --tests "com.example.douqiuqiu.inventory.QuestPersistenceTest" --rerun-tasks --offline`

Expected: PASS; invalid card selection leaves state byte-for-byte unchanged.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyRules.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/inventory/Inventory.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/tower/TowerRules.kt app/src/test/java/com/example/douqiuqiu/tower/TowerRewardUseTest.kt
git commit -m "feat(0.1.7): add rebirth and Rapier reward use"
```

### Task 5: Implement all ten shard effects

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/DuelEngine.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/status/ActionPermission.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/tower/ShardAugmentTest.kt`

**Interfaces:**
- Consumes `CampaignProgress.towerShard` at fighter construction.
- Produces testable shard behaviors for all ten `FirstRunContent.heroes` IDs.

- [ ] **Step 1: Write one failing test per shard behavior**

```kotlin
@Test fun shardCatalogCoversEveryHeroExactlyOnce() {
    assertEquals(FirstRunContent.heroes.map { it.id }.toSet(), ShardAugments.ids)
}

@Test fun viperShardAppliesArmorReductionWithPoison() {
    assertTrue(hitWithShardViper().enemy.armorBuff < hitWithoutShardViper().enemy.armorBuff)
}
```

Add dedicated assertions for Juggernaut critical lifesteal, Skywrath mana-drain ultimate, PA evasion, Necrophos damage healing, Abaddon frost, Anti-Mage resistance/reflection, Faceless Void dilation, Axe hunger, and Legion periodic dispel.

- [ ] **Step 2: Run the test and verify it fails because the augment catalog is absent**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.ShardAugmentTest" --rerun-tasks --offline`

Expected: compilation failure for `ShardAugments` or behavior assertions fail.

- [ ] **Step 3: Implement a typed shard catalog and connect each effect at its existing combat hook**

Create `ShardAugments` in shared combat code, with one explicit `when(heroId)` branch per approved effect. Apply shard state at fighter creation and preserve necessary timers/counters in `Fighter`; do not infer ownership from hero name or UI text. Use current poison, shield, control, cooldown, and damage paths rather than duplicating combat calculations.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.ShardAugmentTest" --tests "com.example.douqiuqiu.combat.PoisonCardHeroAcceptanceTest" --rerun-tasks --offline`

Expected: PASS; ungranted heroes retain exact pre-augment behavior.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/DuelEngine.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/status/ActionPermission.kt app/src/test/java/com/example/douqiuqiu/tower/ShardAugmentTest.kt
git commit -m "feat(0.1.7): implement hero shard augments"
```

### Task 6: Implement ten Aghanim active skills and the battle button

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/DuelEngine.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyRules.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt`
- Modify: `app/src/main/java/com/example/douqiuqiu/ui/JourneyActivity.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/tower/AghanimAugmentTest.kt`

**Interfaces:**
- Produces `JourneyRules.useAghanim(state): InventoryResult`.
- `JourneyActivity` renders a button only for a granted active hero and dispatches this transaction.

- [ ] **Step 1: Write failing global-rule and hero-effect tests**

```kotlin
@Test fun aghanimIsFreeOncePerBattleAndControlDoesNotConsumeIt() {
    val controlled = useAghanim(controlledAghanimFight())
    assertFalse(controlled.ok)
    assertFalse(controlled.state.progress.run!!.fight!!.player.aghsUsed)
    assertTrue(useAghanim(readyAghanimFight()).ok)
    assertFalse(useAghanim(ok(useAghanim(readyAghanimFight()))).ok)
}
```

Add effect assertions for all ten approved actives: Juggernaut, Skywrath, Abaddon, PA, Viper, Necrophos, Anti-Mage, Faceless Void, Axe, and Legion Commander.

- [ ] **Step 2: Run the test and verify it fails because the active transaction and state are absent**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.AghanimAugmentTest" --rerun-tasks --offline`

Expected: compilation failure or failed one-use/control assertions.

- [ ] **Step 3: Implement the shared action and Android control**

Gate by `towerAghs`, battle stage, `ActionPermission`, and `aghsUsed`; do not read mana or cooldown. Apply the existing combat primitives for silence, invulnerability, damage, healing, reflect, rewind, dispel, and timed speed. Render an accessible `神杖` button next to existing battle actions, disable it while controlled or used, and refresh from the committed snapshot.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.tower.AghanimAugmentTest" --tests "com.example.douqiuqiu.ui.UiIconCatalogTest" --rerun-tasks --offline`

Expected: PASS; button state remains a UI projection of shared state.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/DuelEngine.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyRules.kt shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt app/src/main/java/com/example/douqiuqiu/ui/JourneyActivity.kt app/src/test/java/com/example/douqiuqiu/tower/AghanimAugmentTest.kt
git commit -m "feat(0.1.7): add Aghanim battle actives"
```

### Task 7: Add tower activity, camp entry, and reward-use UI

**Files:**
- Create: `app/src/main/java/com/example/douqiuqiu/ui/TowerActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/douqiuqiu/MainActivity.kt`
- Modify: `app/src/main/java/com/example/douqiuqiu/ui/InventoryActivity.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/ui/TowerUiContractTest.kt`

**Interfaces:**
- `TowerActivity` loads/saves through `PlayerRepository` and only invokes `TowerFlow` transactions.
- `InventoryActivity.grants()` invokes `useRebirthItem` after a card instance selection.

- [ ] **Step 1: Write failing UI contract tests**

```kotlin
@Test fun towerEntryShowsCompletedTaskGateBeforeLaunching() {
    assertEquals("转生爬塔需完成全部 24 个任务节点（当前 0/24）", TowerRules.lockedReason(fresh()))
}

@Test fun towerRewardAndCardSelectionHaveAccessibleDescriptions() {
    assertTrue(source("TowerActivity.kt").contains("下一层"))
    assertTrue(source("InventoryActivity.kt").contains("使用转生道具"))
}
```

- [ ] **Step 2: Run the test and verify it fails because TowerActivity and reward selection are absent**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.ui.TowerUiContractTest" --rerun-tasks --offline`

Expected: missing source or failed string contract.

- [ ] **Step 3: Implement the activity and camp entry**

Add a camp “转生爬塔” action with locked progress text. `TowerActivity` shows floor, current HP, current tower bonus, cumulative loot, enemy preview, boss marker, fight control, and node-choice controls. Persist after every accepted action and render the exact restored snapshot after process recreation. Add a card-selection sheet in `grants()` for each pending rebirth item; only owned skill cards can be selected.

- [ ] **Step 4: Run the focused tests and assemble the debug APK**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.ui.TowerUiContractTest" assembleDebug --rerun-tasks --offline`

Expected: PASS and `app-debug.apk` generated.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/douqiuqiu/ui/TowerActivity.kt app/src/main/AndroidManifest.xml app/src/main/java/com/example/douqiuqiu/MainActivity.kt app/src/main/java/com/example/douqiuqiu/ui/InventoryActivity.kt app/src/test/java/com/example/douqiuqiu/ui/TowerUiContractTest.kt
git commit -m "feat(0.1.7): add tower and rebirth reward UI"
```

### Task 8: Audit and repair skill, card, item, and tower artwork mappings

**Files:**
- Modify: `app/src/main/java/com/example/douqiuqiu/ui/components/ItemIconCatalog.kt`
- Modify: `app/src/main/java/com/example/douqiuqiu/ui/components/OpenIconResources.kt`
- Modify: `app/src/main/java/com/example/douqiuqiu/ui/components/ExpansionArtwork.kt`
- Modify: `app/src/main/java/com/example/douqiuqiu/ui/components/HeroSkillHud.kt`
- Modify: `app/src/main/java/com/example/douqiuqiu/ui/IconGalleryActivity.kt`
- Create: `app/src/test/java/com/example/douqiuqiu/ui/ArtworkCoverageTest.kt`

**Interfaces:**
- Produces `ArtworkCoverage.missingDefinitions()` for deterministic JUnit assertions.
- Every item definition, card definition, hero skill, tower reward, and status presentation has an explicit resource ID or vector fallback.

- [ ] **Step 1: Write failing coverage tests**

```kotlin
@Test fun everyVisibleItemAndTowerRewardHasLoadableArtwork() {
    assertEquals(emptyList<String>(), ArtworkCoverage.missingDefinitions(context))
}

@Test fun everyHeroSkillAndCardUsesAnExplicitVisualSpec() {
    assertEquals(emptySet<String>(), ArtworkCoverage.missingHeroSkills())
    assertEquals(emptySet<String>(), ArtworkCoverage.missingCards())
}
```

- [ ] **Step 2: Run the test and record the exact missing or incorrect mappings**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.ui.ArtworkCoverageTest" --rerun-tasks --offline`

Expected: FAIL listing only concrete unmapped IDs; copy the list into the release validation record.

- [ ] **Step 3: Add explicit mappings and safe fallbacks**

Map Rapier, shard, Aghanim, rebirth item, all card schools, all hero skills, and tower statuses to existing licensed open-icon vectors or existing deterministic painted glyphs. Correct any mapping whose glyph category conflicts with the definition category. Show the resolved resource in `IconGalleryActivity` for visual review.

- [ ] **Step 4: Run the artwork suite and verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.ui.ArtworkCoverageTest" --tests "com.example.douqiuqiu.ui.UiIconCatalogTest" --tests "com.example.douqiuqiu.ui.OpenItemIconTest" --rerun-tasks --offline`

Expected: PASS with no missing IDs and no resource-load exception.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/douqiuqiu/ui/components/ItemIconCatalog.kt app/src/main/java/com/example/douqiuqiu/ui/components/OpenIconResources.kt app/src/main/java/com/example/douqiuqiu/ui/components/ExpansionArtwork.kt app/src/main/java/com/example/douqiuqiu/ui/components/HeroSkillHud.kt app/src/main/java/com/example/douqiuqiu/ui/IconGalleryActivity.kt app/src/test/java/com/example/douqiuqiu/ui/ArtworkCoverageTest.kt
git commit -m "fix(0.1.7): audit and repair game artwork mappings"
```

### Task 9: Release notes, full validation, and device record

**Files:**
- Modify: `app/src/main/java/com/example/douqiuqiu/ui/VersionLog.kt`
- Modify: `docs/ROADMAP.md`
- Create: `docs/testing/2026-10-23-v017-tower-release.md`
- Modify: `docs/releases/MODIFICATION_INDEX.md`

- [ ] **Step 1: Write failing version-log coverage test**

```kotlin
@Test fun versionLogDescribesThe017TowerRelease() {
    assertTrue(VersionLog.entries.any { it.version == "0.1.7" && it.notes.contains("转生爬塔") })
}
```

- [ ] **Step 2: Run the test and verify it fails before the release note is added**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.ui.VersionLogTest" --rerun-tasks --offline`

Expected: failed assertion for absent 0.1.7 release entry.

- [ ] **Step 3: Add final release notes and acceptance record**

Document the 20-floor gate, nightmare/inferno split, continuous health, node choices, permanent augment lifecycle, Rapier, rebirth card upgrade, resource-audit result, migration version, APK filename/checksum, and device scenarios. Update `ROADMAP.md` only for requirements demonstrably delivered.

- [ ] **Step 4: Run full verification**

Run: `./gradlew.bat --offline cleanTestDebugUnitTest testDebugUnitTest assembleDebug :shared:compileKotlinJs`

Expected: exit 0 with no new failures. If the designated `SingleRunIntegrationTest` balance red remains, record its exact test name and failure independently; do not mark the full suite green.

- [ ] **Step 5: Perform device acceptance and commit**

Verify task gate, tower resume after process death, HP carry, each node choice, inferno floor 6, pending grant, all ten active buttons, rebirth card selection, icon gallery, 1.3× font, and final victory on an Android emulator/device. Then commit only the release-note and validation files:

```bash
git add app/src/main/java/com/example/douqiuqiu/ui/VersionLog.kt docs/ROADMAP.md docs/testing/2026-10-23-v017-tower-release.md docs/releases/MODIFICATION_INDEX.md app/src/test/java/com/example/douqiuqiu/ui/VersionLogTest.kt
git commit -m "docs(0.1.7): record tower release validation"
```
