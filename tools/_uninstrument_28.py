# -*- coding: utf-8 -*-
"""还原 _instrument_28/b/c 的全部插桩，恢复生产代码原样。"""
import io, sys, shutil

RUNTIME = r"D:\android_project\DouQiuQiu\shared\src\commonMain\kotlin\com\example\douqiuqiu\equipment\EquipmentEffectRuntime.kt"
ENGINE = r"D:\android_project\DouQiuQiu\shared\src\commonMain\kotlin\com\example\douqiuqiu\journey\DuelEngine.kt"
BAK = r"D:\android_project\DouQiuQiu\tools\_EquipmentEffectRuntime.kt.bak"

def rev(path, new, old, tag):
    src = io.open(path, encoding="utf-8").read()
    if new not in src:
        print("MISS", tag); sys.exit(1)
    if src.count(new) != 1:
        print("AMBIG", tag, src.count(new)); sys.exit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(src.replace(new, old))
    print("OK", tag)

# 1) EquipmentEffectRuntime 整文件还原
shutil.copyfile(BAK, RUNTIME)
print("OK runtime-restore")

# 2) DuelEngine: damage 早退打印
rev(ENGINE,
"""            val c = f[target]
            println("DBGENG damage target=$target raw=$raw spell=$spell tHp=${c.hp} inv=${c.invulnerable} ghost=${c.ghost}")
            if (raw <= 0 || c.hp <= 0 || c.invulnerable > 0 || (!spell && c.ghost > 0)) { println("DBGENG damage EARLY-RETURN target=$target"); return null }""",
"""            val c = f[target]
            if (raw <= 0 || c.hp <= 0 || c.invulnerable > 0 || (!spell && c.ghost > 0)) return null""",
"damage-early")

# 3) DuelEngine: attack 吸血治疗行
rev(ENGINE,
"""            run { val rate = CardCombat.passive(f[i].cardState, EffectKind.LIFESTEAL, "percent") + EquipmentCombat.lifesteal(f[i].equipment); val base = hit.lifestealBase(listOf(rate)); println("DBGENG lifesteal i=$i rate=$rate canLifesteal=${hit.canLifesteal} lifeDamage=${hit.lifeDamage} base=$base hp=${f[i].hp}"); heal(i, base) }""",
"""            heal(i, hit.lifestealBase(listOf(CardCombat.passive(f[i].cardState, EffectKind.LIFESTEAL, "percent") + EquipmentCombat.lifesteal(f[i].equipment))))""",
"attack-heal")

# 4) DuelEngine: heal-enter
rev(ENGINE,
"""        fun heal(i: Int, amount: Double, ghost: Boolean = f[i].ghost > 0) {
            println("DBGENG heal-enter i=$i amount=$amount hp=${f[i].hp}")
            if (f[i].hp > 0 && amount > 0) {""",
"""        fun heal(i: Int, amount: Double, ghost: Boolean = f[i].ghost > 0) {
            if (f[i].hp > 0 && amount > 0) {""",
"heal-enter")

# 5) DuelEngine: heal-set
rev(ENGINE,
"""                f[i] = CardEffectRuntime.recordHealing(old.copy(hp = hp), hp - old.hp)
                println("DBGENG heal-set i=$i newHp=${f[i].hp}")
                CardEffectRuntime.fire(this, i, EffectTrigger.HEAL_ACCUMULATED)""",
"""                f[i] = CardEffectRuntime.recordHealing(old.copy(hp = hp), hp - old.hp)
                CardEffectRuntime.fire(this, i, EffectTrigger.HEAL_ACCUMULATED)""",
"heal-set")

# 6) DuelEngine: poisonCard
rev(ENGINE,
"""        override fun poisonCard(source: Int, target: Int, sourceId: String, virulent: Boolean, layers: Int, damage: Double) {
            println("DBGENG poisonCard src=$source srcHp=${f[source].hp}")
            val actor = f[source]""",
"""        override fun poisonCard(source: Int, target: Int, sourceId: String, virulent: Boolean, layers: Int, damage: Double) {
            val actor = f[source]""",
"poisoncard")

# 7) DuelEngine: tick-end
rev(ENGINE,
"""            println("DBGENG tick-end p0=${f[0].hp} p1=${f[1].hp}")
            var next = DuelState(f[0], f[1], seed, original.tick + 1, log = messages.takeLast(6))""",
"""            var next = DuelState(f[0], f[1], seed, original.tick + 1, log = messages.takeLast(6))""",
"tick-end")

# 校验：不再有任何插桩残留
for p in (RUNTIME, ENGINE):
    src = io.open(p, encoding="utf-8").read()
    if "DBGENG" in src:
        print("RESIDUE", p); sys.exit(1)
print("clean")
