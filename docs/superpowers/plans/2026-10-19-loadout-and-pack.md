# 英雄配装与背包整理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在仓库中支持英雄切换自动套用方案、三套配装保存和背包/安全箱原子整理。

**Architecture:** 方案和当前英雄存入 `CampaignProgress`，共享层提供纯不可变的方案应用及格子整理规则。Android 仓库页只调用规则、持久化成功状态并展示操作入口。

**Tech Stack:** Kotlin shared commonMain、Android Views、JUnit 4、现有二进制存档适配器。

## Global Constraints

- 当前对外版本维持 0.1.3；只有完成分发时才变更随机构建索引与 Android `versionCode`。
- 所有拒绝操作返回原始 `InventoryState`，不允许半提交。
- 战斗阶段与挑战中整备阶段禁止局外英雄/方案操作。

---

### Task 1: 方案与整理共享规则

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/douqiuqiu/inventory/Inventory.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/inventory/LoadoutAndPackingTest.kt`

- [x] Write failing tests for a saved hero scheme that replaces equipment and cards atomically, and a full-grid packing rollback.
- [x] Implement `LoadoutPreset`, `LoadoutRules.save/apply`, and `InventoryRules.organize`.
- [x] Compile the targeted test class; execution remains external by project convention.

### Task 2: Persist current hero and presets

**Files:**
- Modify: `app/src/main/java/com/example/douqiuqiu/inventory/CampaignBinaryCodec.kt`
- Modify: `app/src/main/java/com/example/douqiuqiu/inventory/InventoryStore.kt`
- Test: `app/src/test/java/com/example/douqiuqiu/inventory/LoadoutAndPackingTest.kt`

- [x] Add a current hero and bounded preset map to the campaign codec.
- [x] Bump the binary profile version and retain old profile defaults.
- [x] Add round-trip coverage; test execution remains external by project convention.

### Task 3: Warehouse controls and concise copy

**Files:**
- Modify: `app/src/main/java/com/example/douqiuqiu/ui/InventoryActivity.kt`

- [x] Add avatar hero selector, preset chips, save-on-long-press, and automatic preset application after hero selection.
- [x] Add compact organize actions for backpack and safe areas; preserve secondary detail sheets.
- [x] Make card-only star language explicit and add equipment recipe wording.
- [x] Compile the Android and shared modules.
