# -*- coding: utf-8 -*-
"""临时插桩二：打印 attack 吸血治疗的入参。"""
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
"""            heal(i, hit.lifestealBase(listOf(CardCombat.passive(f[i].cardState, EffectKind.LIFESTEAL, "percent") + EquipmentCombat.lifesteal(f[i].equipment))))""",
"""            run { val rate = CardCombat.passive(f[i].cardState, EffectKind.LIFESTEAL, "percent") + EquipmentCombat.lifesteal(f[i].equipment); val base = hit.lifestealBase(listOf(rate)); println("DBGENG lifesteal i=$i rate=$rate canLifesteal=${hit.canLifesteal} lifeDamage=${hit.lifeDamage} base=$base hp=${f[i].hp}"); heal(i, base) }""",
"attack-heal")
