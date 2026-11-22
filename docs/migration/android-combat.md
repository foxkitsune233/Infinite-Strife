# Android hero combat runtime

## Integration contract

This change owns only `scripts/battle/BattleEngine.gd`,
`scripts/battle/FighterState.gd`, new `scripts/battle/AndroidHeroRuntime.gd`,
new `tests/test_android_hero_runtime.gd`, and this document. The main migration
owns fighter construction, initial mana, cast scheduling, session state and save
storage; none of those files are edited here.

Before constructing the engine, the builder must set:

```gdscript
fighter.hero_id = android_hero_id # Empty string preserves generic behavior.
fighter.health_regen = health_per_second
fighter.mana_regen = mana_per_second
# Set fighter.mp to the desired initial mana separately.
# Pass fractional armor to the constructor, or assign fighter.armor directly.
```

Maximum HP and base attack remain integers. Current HP, current mana, universal
shield, magical shield, and armor support fractional values. Generic damage APIs
still return rounded integers; Android combat uses `receive_android_damage` and
retains fractional actual loss. Logs carry both integer `amount` for existing
presentation and floating `exact_amount` for Android damage events.

Use the existing command shape, including source aliases `left` and `right`:

```gdscript
engine.command({"kind": "cast_skill", "source_id": "left", "skill": skill})
```

For a supported `fighter.hero_id`, `skill.runtime_mode` of `generic_placeholder`
or `android_hero` selects the actual Android runtime. Its hero ID, mana cost,
cooldown and constants come from `AndroidHeroCatalog`, not a UI skill ID or the
placeholder damage packet. Arbitrary UI skill IDs cannot bypass the cooldown.
The shared key is `hero:<hero_id>:ultimate`; it is also recorded in
`fighter.hero_state.ultimate_key`. The returned cast event has
`runtime_mode = "android_hero"`. Automatic cast decisions remain with the caller;
the engine executes automatic attacks and ongoing hero effects.

If the adapter changes attack speed, it can set
`fighter.hero_state["base_speed"]` to the effective Android attack-speed points
before engine construction. Otherwise the runtime initializes that value from
the naked catalog. `attack_interval_ms` must describe the same base speed/BAT.
Hero buffs and debuffs apply relative to those values.

## Save and restore

```gdscript
journey.battle_snapshot = engine.export_state()
var resumed: BattleEngine = BattleEngine.from_state(journey.battle_snapshot)
if resumed == null:
    # Report an invalid/incompatible snapshot; do not silently reroll the battle.
    return
engine = resumed
```

`export_state() -> Dictionary` returns a version-one envelope with `encoding =
"godot_variant_base64"` and a `payload` string. It deliberately uses a lossless
Variant payload: default JSON serialization rounds floating values and converts
integers, which changed resumed outcomes in regression testing. The envelope can
itself be stored in ordinary JSON without losing precision. Objects are not
deserialized (`bytes_to_var`, not `bytes_to_var_with_objects`). This is a Godot
save format, not an Android save format or a cross-engine interchange format.

The payload includes both fighters, all combat fields, regeneration rates,
fractional current values, hero counters, all timers, ordinary/virulent poisons,
source-aware controls, generic DOTs/statuses, shields, cooldowns, both RNG states,
next attacks, tie order, elapsed time, a pending partial 100ms tick, result, and
full event log. The RNG state is restored without drawing again. Finished games
remain finished. `snapshot()` is still the readable display dictionary;
`FighterState.to_data()` / static `from_data(data)` expose fighter serialization.
Invalid version/shape returns null rather than constructing a new seeded fight.

## Rules read from Android

Sources are under `D:/android_project/DouQiuQiu/shared/src/commonMain/kotlin/`
`com/example/douqiuqiu/`:

- `journey/DuelEngine.kt`: frame order, attacks, hero passives, ultimates,
  heartstopper, mana burn, counter helix, regeneration and speed adjustments.
- `content/HeroContent.kt` and `journey/JourneyModels.kt`: constants and costs.
- `journey/FighterEffects.kt`, `status/ActionPermission.kt` and
  `status/TimedControls.kt`: action gates, Abaddon's stunned-cast exception,
  strong/basic dispel, control rounding and time stop.
- `combat/CombatMath.kt`, `combat/DamagePipeline.kt`,
  `combat/CriticalPrd.kt`, `combat/CriticalStrikeResolver.kt`: signed armor,
  damage categories, ghost vulnerability, actual life loss, critical priority
  and Juggernaut's calibrated PRD.
- `combat/PoisonCombat.kt` and `status/PoisonEngine.kt`: normal poison has three
  one-second ticks; persistent virulent poison creates ordinary layers each
  second, with no immediate damage on layer creation.

Executable rules take precedence over conflicting comments or skill-info text.
In particular, Ghost Shroud has **20% magic vulnerability**, not half incoming
magic damage; Anti-Mage burns **current** mana, not maximum mana; Phantom
Assassin's four-second readiness is consumed by the next eligible attack.

## Implemented behavior

| Hero | Automatic/passive behavior | Ultimate |
| --- | --- | --- |
| 主宰 | 1.8x critical with 25% calibrated rising-failure PRD | Strong self-dispel, 2s invulnerability; full attacks at boosted speed plus Android slash interval divisor 1.4 |
| 天怒法师 | Ordinary magic spell life damage creates magical shield equal to 30%, capped at max HP | Eight separate 60-damage hits on the 100ms tick grid |
| 幻影刺客 | +0.3 to successful critical multiplier while unbroken | Four-second next-attack readiness, base 3x critical, giving 3.3x with passive |
| 亚巴顿 | Landed attacks refresh a 2s, 12 magic damage/s curse; +20 speed while curse is active | Cast allowed while stunned; strong self-dispel, 180 universal shield, immediate magic damage equal to 30% total shield |
| 瘟疫法师 | 0.8% target max HP/s heartstopper through the actual HP_REMOVAL pipeline | 3s ghost, physical immunity, +20% incoming magic, +50% healing and mana regeneration |
| 冥界亚龙 | Independent 15% magic resistance; damaging hits trigger 3-tick normal poison of 6 per tick and 15-point speed slow, gated by 0.5s | One persistent virulent layer generating normal poison of 3 per tick; 3s break and 30-point speed slow |
| 敌法师 | On damaging attacks, burn 2% current mana, at most once/s; accumulate actual burn | 100 + cumulative burn × 0.6 magic damage; 0.2s stun |
| 斧王 | Every fourth damaging basic attack received causes 55 + effective armor × 1.2 pure damage | 2.5s TAUNT and +12 armor if control applies |
| 虚空假面 | 18% landed-attack time lock: stun rounded from 0.35s to 0.4s, extend an already-running ultimate by 1s | 3s undispellable TIME_STOP |
| 军团指挥官 | 25% landed-attack chance to heal 22 and perform one nonrecursive extra attack | 4s TAUNT and +60 speed points if control applies |

Break gates follow the actual branches: it suppresses Juggernaut critical,
Phantom Assassin's multiplier bonus, Abaddon curse, heartstopper, Viper skin/
resistance and Void time lock. Anti-Mage burn and Legion extra attacks are not
break-gated in the source and remain enabled here.

Android battles run on fixed 100ms ticks, with pending smaller deltas retained.
Supported heroes have their first ordinary attack ready at 100ms; generic
fighters keep their existing initial 1000ms schedule. Android turn order rolls
each tick using the Park–Miller RNG; generic-only fights keep the old engine
RNG and schedule. Skywrath's signed countdown gives hit times 100, 300, 500,
800, 1000, 1300, 1500 and 1800ms after a tick-boundary cast. Full battles resume
with the same Godot events; cross-platform seeded replay parity is not claimed.

TAUNT is a distinct source-aware status, not stun. In Android's single-target
`ActionPermission`, it does not prevent attacks, skills or items. TIME_STOP
prevents all actions and pauses attack scheduling, hero/item cooldowns and flare
progression. Regeneration, poison and control expiration continue. Dead units
cannot act or regenerate. Stun/hex/freeze, silence for skills, mute for items,
and disarm/ghost for attacks gate the corresponding actions. Abaddon bypasses
stun only for his hero skill. Controls reject invulnerable, banished, dead or
debuff-immune targets, round up to 100ms, retain source/caster/dispel class,
and refresh matching sources. Strong dispel does not remove TIME_STOP.

Physical mitigation uses `1 - 0.06*A/(1 + 0.06*abs(A))`, including negative and
fractional armor. Magic applies base resistance, Viper's independent resistance,
and ghost vulnerability. Pure damage bypasses these multipliers. As in the
actual Android pipeline, HP_REMOVAL still uses magic mitigation and shields but
does not trigger ordinary damage responses. Android marks counter helix as
SELF_DAMAGE: it does not trigger responses and cannot reduce its target below
one HP. This surprising behavior is retained and tested rather than inferred
from the skill name.

## Remaining limits

This is meaningful hero combat, not complete Android battle-system parity.

- Base/max HP and base attack are still integer projections. Fractional derived
  attack values lost by the builder cannot be recovered by the runtime.
- Card/equipment proc networks, multiple independent critical sources and their
  per-source PRD, damage amplification, on-hit cards, equipment controls,
  evasion rules, status resistance, BKB suppression/resumption and free opening
  ultimates are not ported here. Generic scalar critical/lifesteal fields work;
  they do not replace those systems.
- Shields are universal and magical aggregates, not Android's full typed,
  ordered source ledger. Aphotic tracks its remaining contribution assuming it
  absorbs first. Refresh/absorption with several simultaneous shield sources can
  therefore differ. Physical-only shields and shield expiration are absent.
- The generic opponent in mixed fights retains its first-attack schedule.
  Subsequent Android attack intervals derive from the existing projected integer
  interval; Omnislash does not preserve an independent paused normal-attack
  countdown. Tick/death ordering and per-frame action-order consumption are a
  local adaptation, not a verified byte-for-byte port of all `Tick.run` branches.
- Source/caster/remaining time of hero controls are preserved; full Android
  status-source validation, non-hero dispel taxonomy and save compatibility are
  not implemented. Generic `apply_status` remains a simple timed status command.
- Viper/Abaddon hero poison is implemented locally, separately from generic DOTs.
  The full poison engine's validation, immunity piercing, deterministic layer
  instance ordering and card poison-tick hooks are absent. Damage-response
  ordering between two poison-producing heroes can differ from Android.
- Generic spell/DOT commands in mixed battles use Android mitigation and hero
  damage responses, but externally applied generic DOT tick damage remains an
  integer payload. No visual skill animations are added by this change.
- The engine does not choose when to cast or apply Android cooldown reduction
  sources; the caller sends cast commands. Runtime cooldowns use unmodified
  catalog values. The builder's previous `ability_parity = false` remains honest;
  this patch does not rewrite metadata outside its ownership boundary.

## Verification

The test was run before implementation and failed on negative armor
(`expected=138 actual=250`) and missing `hero_id`. The persistence test failed
before the export interface existed. A cadence regression also caught and fixed
an incorrect alternating flare timer.

Verified with real non-Mono Godot 4.6.2 through the waited, hidden-process runner:

```powershell
./tools/verify_migration.ps1 -TestNames test_android_hero_runtime.gd,test_battle_engine.gd,test_combat_rules.gd,test_combat_effects.gd,test_battle_screen.gd,test_battle_presentation.gd,test_skill_runtime.gd
```

All seven selected tests passed with process exit 0 and no logged errors. The new
test covers every hero's cast, concrete damage/regen/control/passive outcomes,
fractional mana burn and mitigation, poison timing, break, immunity, TAUNT versus
TIME_STOP, cooldown freezing/extension, strong dispel, seeded proc execution,
chunking, JSON save envelopes, both RNG continuations, all ten heroes' resumed
timers/controls/poisons, and finished-result restoration. Existing generic tests
are unchanged. This is headless verification; no Android replay comparison or
visual acceptance result is claimed.
