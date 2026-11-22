# Android loadout effect adapter

`scripts/battle/AndroidEffectCatalog.gd` is a pure adapter over the existing `data/android/equipment.json` snapshot. It selects card/gear/synergy effects and prepares combat stat inputs. It does not change PreparationState, run triggers, read a clock, roll RNG, or apply base item stats. Engine, builder, state, AndroidLoadoutStats, and exporter integration are outside these three owned files.

## Public interface

```gdscript
const Effects = preload("res://scripts/battle/AndroidEffectCatalog.gd")
var inputs: Dictionary = Effects.for_loadout(state) # state: PreparationState
```

The result has exactly five root keys:

| Key | Type | Meaning |
| --- | --- | --- |
| `effects` | `Array[Dictionary]` | Selected, star-resolved effects available to the runtime; excludes starting card shields/mana already included below. |
| `schools` | `Dictionary[String, int]` | Distinct equipped card IDs per school; only represented schools appear. |
| `initial_mana` | `float` | `30 + CardCombat.initialManaBonus`; consumer caps this at calculated max mana. This is an absolute starting input, not a bonus to add to 30 again. |
| `initial_shields` | `Array[Dictionary]` | Starting pools after same-card copy aggregation and shield-synergy amplification. |
| `passive` | `Dictionary` | Effect-only stat sums and independent modifier inputs described below. |

Each `effects` entry retains the raw effect fields (`id`, `kind`, `trigger`, `params`, `perStar`, `scaling`, flags, nullable timing, review/group metadata, and damage/control classifications). `params` contains resolved typed values, not bare numbers. `number(effect, parameter, fallback = 0.0)` reads only `Num`/`Count`; it does not convert a `Flag` or `Choice` into a number.

Each effect additionally has:

| Field | Meaning |
| --- | --- |
| `source_type` | `card`, `gear`, or `synergy`. |
| `instance_id` | Original item instance ID; for a synergy it is the synthetic effect ID. |
| `definition_id` | Android card/equipment ID; for a synergy it is the effect ID. |
| `star` | Card's 1..3 star; gear/synergy always 1. |
| `source_key` | Card: `<instance>:<effect.id>`; gear: `gear:<instance>:<effect.id>`; synergy: `<effect.id>`. Matches Android bound keys used for proc/PRD bookkeeping. |
| `shield_source` | Card: `card:<definition>:<effect.id>`; synergy: `<effect.id>`; gear: its `source_key`. This intentionally differs from the per-instance proc key. |

Cards and synergies are ordered by effect ID, then source key, matching CardCombat. Gear follows, ordered by source key, matching EquipmentCombat. Critical candidates have their own priority order; the combined effects array does not imply trigger execution order across systems.

Starting shield entries are `{source_key: String, kind: String, amount: float, duration_ms: int|null}`. Kind is `UNIVERSAL`, `PHYSICAL`, or `MAGICAL`; Android content's `GENERAL` maps to `UNIVERSAL`. Null duration means no expiry this battle. Pools are ordered by source key. Same-card instances sum shield amounts before amplification and become one pool; different cards retain separate pools. For S1 stars 1 and 2 with shield tier 3, `(60 + 120) * 1.15 = 207`, and the separate shield tier-2 pool is `80 * 1.15 = 92`.

## Passive contract for AndroidLoadoutStats

`passive` contains all PASSIVE `STAT_BONUS` parameter sums directly under their exact Android keys. These twelve keys always exist and default to zero:

```text
strength, agility, intelligence, health, healthRegen, armor,
attackSpeed, manaPercent, manaRegen, attack, manaFlat, attackSpeedPercent
```

Additional numeric STAT_BONUS parameter keys, if introduced by a snapshot, are included as well. These values exclude the item's base `stats`. Base stats must be accumulated separately even when the item's special effects lose an upgrade/unique-group contest. In particular, `passive.manaRegen` is a STAT_BONUS parameter, whereas `passive.mana_regen` below is an effect amount; they are distinct and should not be counted twice.

| Additional passive key | Type / exact semantics |
| --- | --- |
| `mana_regen` | `float`: selected PASSIVE `MANA_REGEN.amount` sum. |
| `magic_resistance_values` | `Array[float]`: PASSIVE `MAGIC_RESIST.percent` independent values, including negatives. Apply `Π(1-r)` alongside base resistance; do not add them into one resistance. |
| `status_resistance_values` | `Array[float]`: PASSIVE `STATUS_RESIST.percent`. |
| `healing_amplification_values` | `Array[float]`: PASSIVE `HEAL_AMP.percent`. |
| `evasion_values` | `Array[float]`: PASSIVE `EVASION.percent`. |
| `lifesteal` | `float`: PASSIVE `LIFESTEAL.percent` sum. Active satanic/etc. are not applied. |
| `critical_multiplier_bonus` | `float`: PASSIVE `CRIT_MULTIPLIER.points` sum. Hero bonuses are outside this adapter. |
| `critical_sources` | `Array[Dictionary]`: `{source_key, instance_id, source_type, chance, multiplier}`, sorted by multiplier descending then source key ascending. Chance is configured probability, not a calibrated PRD threshold. Hero crits are not included. |
| `cooldown_flat_seconds` | `float`: card/synergy PASSIVE `COOLDOWN_REDUCTION.flatSeconds` sum, following DuelEngine's ultimate path. Gear flatSeconds are not used by that path. |
| `cooldown_sources` | `Array[Dictionary]`: all fraction candidates `{source_key, fraction, unique_group: String|null, active: true}` sorted by source key. Gear includes passive zero candidates, while cards/synergies contribute positive percent candidates, matching Android. |
| `cooldown_reduction` | `float`: selected fraction `min(0.6, 1 - Π(1-fraction))`. |
| `cooldown_applied_source_ids` | `Array[String]`: selected source keys, including selected zero candidates, in multiplication order. |

STAT_BONUS and MANA_REGEN card/synergy effects sum independently, even when duplicate copies share the same card ID. Only gear passives apply unique-group selection: for each `(kind, parameter, uniqueGroup)`, select the greatest nonnegative value, tie-breaking on item instance ID. Independently retain the minimum negative value from the same group. Ungrouped gear values all remain. Grouping never suppresses unrelated parameters from an item. `passive_values(effects: Array[Dictionary], kind: String, parameter: String) -> Array[float]` exposes this calculation for other consumers; it filters pending-review effects.

Cooldown grouping is separate from generic passives. `cooldown_reduction(sources: Array[Dictionary]) -> Dictionary` returns `{fraction: float, applied_source_ids: Array[String]}`. It ignores `active: false`, chooses the greatest fraction per non-null group (ties by source key), retains ungrouped duplicates, sorts sources by key, multiplies their remaining proportions, and caps at 60%. Inputs must have distinct nonempty source keys and finite fractions in `[0, 1]`, as produced by the adapter. The helper is not a general save-data validator.

The consuming ultimate cooldown code should subtract `cooldown_flat_seconds` from base seconds, clamp to positive, apply the fraction, and then apply its action-specific minimum. Android's ultimate path uses 0.5 seconds normally and 10 seconds for Necrophos, with an additional free-cast penalty; that timing policy is not performed by this adapter. Card periodic/internal cooldowns use *gear-only* cooldown sources in CardEffectRuntime, not the merged hero/ultimate fraction. The source_type field allows the runtime to select the gear subset.

## Selection and runtime overrides

Only the first six equipment slot references and first eight card slot references participate. Warehouse/backpack/safe items and reserve equipment do not contribute. Unknown/dangling references, duplicate references to the same instance, and invalid card stars are skipped defensively. The adapter follows slot references and does not validate location flags or unlocked slot limits; preparation validation remains the caller's responsibility. Distinct copies of a card each produce their own effects. School counts deduplicate by card ID, not instance ID or star. A dual-school card contributes once to each school. Reached synergy tiers 2, 3, and 4 are cumulative and resolve at star 1.

Cards must have `review == CONFIRMED`; pending-review card definitions and effects are excluded. Pending-review gear definitions/effects are also excluded. Gear comes only from EquipmentCatalog definitions, so legacy inventory components/consumables have no invented combat effects. Their raw base stat bonuses belong to the separate stats accumulator. Component effects are already merged into equipment snapshot entries and are not appended twice.

The explicit current CardCombat override is `P2.virulent.intervalMs = [8000, 6000, 4000][star - 1]`. Its poison spawn damage remains 3 at every star because source PER_STAR values explicitly say so. `paramsAt` resolution uses source perStar maps with base fallback, and scales only `Num` values in EffectDefinition's whitelist for LINEAR_1_2_4. Counts, probability values expressed through explicit PER_STAR, durations, and cooldowns are not blanket multiplied. The snapshot is cached for the process; returned dictionaries are detached from it.

The exact EquipmentCombat upgrade priorities are:

| Effect unique group | Priority, strongest first |
| --- | --- |
| `poison_blade` | rod_of_atos, witch_blade |
| `force_staff` | hurricane_pike, force_staff |
| `maelstrom` | mjollnir, maelstrom |
| `expert_array` | viper_bite, expert_array |
| `basher` | skull_basher |

The winner is chosen from equipped items by priority index, then smallest instance ID. Only effects bearing that group are suppressed on losing items. For example duplicate mjollnirs retain both `mjollnir.static` effects but only the winning item's `mjollnir.chain` effect; ordinary item base stats remain the stats helper's responsibility.

Card/synergy BATTLE_START `MANA_RESTORE` and `GRANT_SHIELD` effects are consumed into initial inputs and omitted from the returned runtime list, so a runtime may invoke remaining START triggers once without regranting them. U4.freeCast stays in `effects`. `synergy.ultimate.4` is ULT_START, not BATTLE_START: it stays available and adds no initial mana. A star-3 U2 starts with `30 + 60 = 90` before max-mana capping, regardless of that tier-4 synergy.

## Verification and gaps

Run `tests/test_android_effect_catalog.gd` with Godot 4.6.2 headlessly; wait for the Windows process and inspect its exit code and log. The tests cover duplicate card IDs versus duplicate references, dual tags, cumulative tiers, LINEAR/PER_STAR values, P2 intervals, START consumption, shield grouping, upgrade priorities/ties, independent secondary effects, base-stat exclusion, negative/positive group selection, separate resistances, cooldown grouping/product/cap, pending-review passive exclusion, critical ordering/keys, and snapshot/input isolation.

This is effect selection and initial combat input preparation, not combat parity. Trigger scheduling, proc rolls, CriticalPrd calibration/failure advancement, hero-specific crit sources, active gear execution and timers, low-HP gates (including conditional lifesteal), shield refresh/expiry/absorption, damage, controls, DOTs, and legacy saved stack compatibility are not implemented here. Conditional passives are exposed as Android's passive query currently exposes them; they are not newly conditioned on HP. Consumers must use preserved effect metadata when running their trigger logic. No RNG or PRD counters are changed.

Rules were checked against Android CardCombat.kt, EquipmentCombat.kt, SynergyCounter.kt, CooldownReduction.kt, UniqueBonusSelector.kt, EffectModel.kt, JourneyRules.kt, HeroStats.kt, DuelEngine.kt, CardEffectRuntime.kt, CriticalPrd.kt, and ShieldLedger.kt. The snapshot's existing source_fingerprints cover the exported definitions and CardCombat/EquipmentCombat runtime interpretation. Re-exporting content alone does not update adapter-specific runtime rules; changes to those sources require reviewing this adapter and its literal regression expectations.
