# -*- coding: utf-8 -*-
"""临时插桩：在 EquipmentEffectRuntime.trigger 与 DuelEngine.damage 里打印轨迹，跑完用 _unpatch 还原。"""
import io, sys

RUNTIME = r"D:\android_project\DouQiuQiu\shared\src\commonMain\kotlin\com\example\douqiuqiu\equipment\EquipmentEffectRuntime.kt"
ENGINE = r"D:\android_project\DouQiuQiu\shared\src\commonMain\kotlin\com\example\douqiuqiu\journey\DuelEngine.kt"

def patch(path, old, new, tag):
    src = io.open(path, encoding="utf-8").read()
    if old not in src:
        print("MISS", tag); sys.exit(1)
    if src.count(old) != 1:
        print("AMBIG", tag, src.count(old)); sys.exit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(src.replace(old, new))
    print("OK", tag)

patch(RUNTIME,
"""                EffectKind.ADD_PHYSICAL_DAMAGE, EffectKind.ADD_SPELL_DAMAGE -> EquipmentTargeting.select(1 - actor,
                    host.enemyTargets(actor), bound.number("maxTargets", 1.0).toInt()).forEach { target ->
                    host.damageCard(actor, target, bound.number("amount"), bound.effect.damageType!!, bound.effect.attackClass!!, bound.effect.flags, bound.key)
                }""",
"""                EffectKind.ADD_PHYSICAL_DAMAGE, EffectKind.ADD_SPELL_DAMAGE -> EquipmentTargeting.select(1 - actor,
                    host.enemyTargets(actor), bound.number("maxTargets", 1.0).toInt()).forEach { target ->
                    println("DBGENG proc actor=$actor key=${bound.key} amount=${bound.number("amount")} selTarget=$target selfHp=${host.fighter(actor).hp} foeHp=${host.fighter(1 - actor).hp}")
                    host.damageCard(actor, target, bound.number("amount"), bound.effect.damageType!!, bound.effect.attackClass!!, bound.effect.flags, bound.key)
                    println("DBGENG after key=${bound.key} target=$target nowHp=${host.fighter(target).hp}")
                }""",
"runtime-trigger")

patch(ENGINE,
"""            val c = f[target]
            if (raw <= 0 || c.hp <= 0 || c.invulnerable > 0 || (!spell && c.ghost > 0)) return null""",
"""            val c = f[target]
            println("DBGENG damage target=$target raw=$raw spell=$spell tHp=${c.hp} inv=${c.invulnerable} ghost=${c.ghost}")
            if (raw <= 0 || c.hp <= 0 || c.invulnerable > 0 || (!spell && c.ghost > 0)) { println("DBGENG damage EARLY-RETURN target=$target"); return null }""",
"engine-damage")
