# Android equipment and inventory snapshot

`data/android/equipment.json` is evaluated from the actual Android shared Kotlin content plus `app/src/main/assets/config/inventory.json`. It is not a hand-transcribed approximation. The current snapshot contains 186 item definitions: 81 EQUIPMENT, 29 SCROLL, 45 CARD, 7 MATERIAL, 4 CONSUMABLE, and 20 LOOT. There are 39 recipes (36 shared Kotlin, 3 legacy inventory), 45 enabled card definitions, 8 schools, and the source synergy tiers.

## Reproduce and verify

Run from `D:\GODOT\InfiniteStrife` in PowerShell 7 with Java 17+:

```powershell
./tools/export_android_content.ps1
./tools/export_android_content.ps1 -Check
```

The default Android root is `D:\android_project\DouQiuQiu`; override with `-AndroidRoot`. The exporter discovers the cached Kotlin compiler and its matching stdlib in `%USERPROFILE%/.gradle/caches/modules-2/files-2.1`. Alternatively supply `-KotlinHome` pointing to a Kotlin distribution containing `lib/kotlin-compiler.jar` and `lib/kotlin-stdlib.jar`. `-SharedJar` overrides the default `<AndroidRoot>/shared/build/libs/shared-jvm.jar`.

No Gradle command runs, no dependencies download, and no Android files are written. A preexisting shared JVM jar supplies dependencies; current EquipmentContent, SchoolEquipmentContent, ControlEquipmentContent, EffectModel, CardContent, Crafting, Inventory, and ValuableLootCatalog sources are compiled into an isolated temporary jar that precedes the shared jar on the runtime classpath. Temporary compilation files are removed on exit. Missing prerequisites or compilation failures abort rather than substituting invented values or silently exporting stale equipment classes.

`-Check` compiles and evaluates again and compares the complete JSON text with the checked-in snapshot without writing it. Sources are hashed before and after evaluation to detect concurrent Android edits. Item/recipe order, object field order, UTF-8 without BOM, LF endings, and absence of timestamps/absolute paths make repeated exports stable. Toolchain fingerprints are part of the snapshot: changing the compiler, stdlib, dependency jar, or exporter intentionally requires regeneration and review.

Run the standalone regression test with a working Godot executable:

```powershell
& 'D:\GODOT\Godot_v4.6.1-stable_mono_win64\Godot_v4.6.2-stable_win64.exe' --headless --path 'D:\GODOT\InfiniteStrife' --log-file "$env:TEMP/android-equipment-test.log" --script res://tests/test_android_equipment_catalog.gd
```

When invoking a Windows GUI executable, wait for its process and inspect both its exit code and log for `SCRIPT ERROR:`/`ERROR:`. The installed 4.7.1 executables crashed before script execution in this environment; 4.6.2 ran this test. The test checks source values, reward IDs, card star parameters, component/active effects, duplicate IDs/recipes, ingredient counts, all recipe references, recipe cycles, copy isolation, and missing-file handling.

## Godot interface

```gdscript
const AndroidCatalog = preload("res://scripts/content/AndroidEquipmentCatalog.gd")

var essence: Dictionary = AndroidCatalog.item("essence") # Lazy default load.
var gauntlet: Dictionary = AndroidCatalog.item("bloodrage_gauntlet")
var error: Error = AndroidCatalog.load_catalog() # Explicit validation/reload.
var by_id: Dictionary = AndroidCatalog.definitions
var scroll: Dictionary = by_id.get("recipe_bracer", {})
```

All methods are static; no scene, autoload, ContentCatalog, or battle/loadout module is required. `item(id)` and `recipe(id)` return `{}` for unknown IDs. `items()`, `recipes()`, and `cards()` return arrays. These accessors return deep copies. `definitions` is the public ID-to-item dictionary populated by a successful load; treat it as read-only and call `item()` or `load_catalog()` before accessing it. Direct `AndroidCatalog.get(id)` is not an item lookup because Godot reserves `Object.get` for property access.

`load_catalog(path = DEFAULT_PATH)` returns `OK`, `ERR_FILE_NOT_FOUND`, `ERR_PARSE_ERROR`, or `ERR_INVALID_DATA`; diagnostics are in `errors`. Failed reloads clear old data. `validate_document(document)` returns a `PackedStringArray` of errors without changing the loaded catalog. `metadata()` provides schema, stat keys, schools, and fingerprints. Calls lazily load the default snapshot once; following a failure, use an explicit reload to retry. The cache is shared across callers.

Item dictionaries preserve Android field names: `id`, `name`, `kind`, `width`, `height`, `quality` (Chinese label), `price`, `stats`, `maxStack`, `combatUsable`, `shopStock`, `shopShelf`, `minimumShopClears`, `minimumDropClears`, `boundOnAcquire`. Also present are `source`, `category`, `qualityTier`, `statsDeclared`, `description`, `requiresReview`, `uniqueGroup`, `recipe` (object or null), `effects`, and `active_effects`. Kotlin equipment additionally retains `contentShopStock` and `contentMinimumShopClears`, before InventoryCatalog applies scroll rules. Legacy-only category and qualityTier are null rather than guessed.

Godot lookups additionally provide snake-case aliases for `bound_on_acquire`, `minimum_drop_clears`, `minimum_shop_clears`, `max_stack`, `combat_usable`, `shop_stock`, `shop_shelf`, and `unique_group`. Raw JSON retains the Android spellings. Stat keys are never renamed.

Every item has exactly these ten numeric `stats` keys, including explicit zeroes:

```text
strength, agility, intelligence, health, healthRegen,
armor, attackSpeed, manaPercent, manaRegen, attack
```

These are raw additive bonuses; derived HP/mana/armor are not included. Examples verified against source: crown strength/agility/intelligence = 4 each; broadsword attack = 15 and size = 2×1; bloodrage_gauntlet attack/attackSpeed = 18 each; frenzy_heart strength = 20 and health = 300; orchid manaRegen = 1. Percentages and milliseconds retain their source units; effect-specific values must not receive a global percent conversion.

Recipes have `id`, `outputId`, and an ingredient-ID-to-count dictionary `ingredients`. They are both embedded in their output item and listed in the root `recipes` array. The exporter rejects duplicate item/recipe IDs, duplicate outputs, unresolved references, invalid quantities, and recipe cycles. The Godot loader validates these again before publishing any definitions.

## Inventory, effect, and card boundaries

All base inventory definitions are included, including essence, backpack_fabric, skill_shard, safe_lock, safe_frame, equipment_guard_core, skill_guard_core, salve, tango, faerie_fire, cheese, and legacy scrolls. Default inventory fields follow Inventory.kt/InventoryCatalog.kt. Base inventory card definitions take precedence over added card item definitions as on Android. Kotlin equipment ID overlaps with inventory cause an explicit failure requiring precedence review, rather than silently discarding rich equipment data. ValuableLootCatalog adds the 20 sellable loot definitions.

FirstRunContent in JourneyModels.kt defines heroes and RNG helpers, not inventory definitions. It was inspected and fingerprinted; no nonexistent inventory API is inferred from it.

Equipment `effects` combines the item's declared effects with the separate `EquipmentCatalog.componentEffects` registry. `active_effects` contains exactly the ACTIVE-trigger subset, including pending-review metadata. Every effect preserves all data-class fields: id, kind, trigger, params, perStar, scaling, damageType, attackClass, shieldKind, dispelClass, uniqueGroup, flags, intervalMs, internalCooldownMs, maxStacks, stackDurationMs, and requiresReview. Effect values are typed objects, e.g. `{"type":"Num","value":0.15}`, `{"type":"Count","value":1200}`, `{"type":"Flag","value":true}`, or `{"type":"Choice","value":"FREEZE"}`. Null values remain null. Glacier freeze retains 900 ms melee / 1200 ms ranged duration and 12000 ms cooldown; cloak retains its separate 0.15 magic-resistance effect.

Root `cards` exports `CardCatalog.confirmedOnly`, the list used by `CardCombat.enabledCards`: IDs, names, quality, original tags, `schools` (sorted school IDs), dimensions, pricing/gates, descriptions, review state, and complete typed effects. `stars` contains three objects `{star, effects}`, each with parameters resolved by source `EffectDefinition.paramsAt(star)` while preserving effect metadata. A1 attackSpeed is 12/24/48. Root `schools` and `synergy_tiers` preserve the source school names and synergy rules. These are definition snapshots, not CardCombat's runtime overrides (for example its P2 timing override).

This migration supplies reproducible definitions and metadata. It does not implement or claim active combat effects parity, proc scheduling, unique-group selection, inventory transactions, or card runtime parity. No ContentCatalog or BattleLoadoutBuilder changes are part of this work. JSON resource packaging for a shipped build remains the integrating export preset's responsibility.

## Source fingerprints

The root `source_fingerprints` object contains the exact lowercase SHA-256 of each source file's bytes, using Android-relative paths. This is the authoritative fingerprint list shipped with the data:

- content/EquipmentContent.kt
- content/SchoolEquipmentContent.kt
- content/ControlEquipmentContent.kt
- content/EffectModel.kt
- content/CardContent.kt
- inventory/Crafting.kt
- inventory/Inventory.kt
- loot/ValuableLootCatalog.kt
- app/src/main/assets/config/inventory.json
- app/src/main/java/com/example/douqiuqiu/inventory/InventoryCatalog.kt
- equipment/EquipmentCombat.kt
- cards/CardCombat.kt
- journey/JourneyModels.kt

The shortened shared paths above are rooted at `shared/src/commonMain/kotlin/com/example/douqiuqiu/`. `exporter_fingerprints` records both exporter files. `toolchain` records filename and SHA-256 for compiler, stdlib, and shared JVM jar. Fingerprints of inspected runtime sources document interpretation boundaries; they do not imply those runtime rules have been ported.
