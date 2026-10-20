# -*- coding: utf-8 -*-
"""临时插桩三：heal 前后 + poisonCard + tick 收尾打印 hp。"""
import io, sys

ENGINE = r"D:\android_project\DouQiuQiu\shared\src\commonMain\kotlin\com\example\douqiuqiu\journey\DuelEngine.kt"

def patch(path, old, new, tag):
    src = io.open(path, encoding="utf-8").read()
    if old not in src:
        print("MISS", tag); sys.exit(1)
    if src.count(old) != 1:
        print("AMBIG", tag, src.count(old)); sys.exit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(src.replace(old, new))
    print("OK", tag)

patch(ENGINE,
"""        fun heal(i: Int, amount: Double, ghost: Boolean = f[i].ghost > 0) {
            if (f[i].hp > 0 && amount > 0) {""",
"""        fun heal(i: Int, amount: Double, ghost: Boolean = f[i].ghost > 0) {
            println("DBGENG heal-enter i=$i amount=$amount hp=${f[i].hp}")
            if (f[i].hp > 0 && amount > 0) {""",
"heal-enter")

patch(ENGINE,
"""                f[i] = CardEffectRuntime.recordHealing(old.copy(hp = hp), hp - old.hp)
                CardEffectRuntime.fire(this, i, EffectTrigger.HEAL_ACCUMULATED)""",
"""                f[i] = CardEffectRuntime.recordHealing(old.copy(hp = hp), hp - old.hp)
                println("DBGENG heal-set i=$i newHp=${f[i].hp}")
                CardEffectRuntime.fire(this, i, EffectTrigger.HEAL_ACCUMULATED)""",
"heal-set")

patch(ENGINE,
"""        override fun poisonCard(source: Int, target: Int, sourceId: String, virulent: Boolean, layers: Int, damage: Double) {
            val actor = f[source]""",
"""        override fun poisonCard(source: Int, target: Int, sourceId: String, virulent: Boolean, layers: Int, damage: Double) {
            println("DBGENG poisonCard src=$source srcHp=${f[source].hp}")
            val actor = f[source]""",
"poisoncard")

patch(ENGINE,
"""            var next = DuelState(f[0], f[1], seed, original.tick + 1, log = messages.takeLast(6))""",
"""            println("DBGENG tick-end p0=${f[0].hp} p1=${f[1].hp}")
            var next = DuelState(f[0], f[1], seed, original.tick + 1, log = messages.takeLast(6))""",
"tick-end")
