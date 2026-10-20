#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""计算 B 组测试的精确期望值（与 Kotlin Double 运算顺序一致）。"""

def phys_mult(armor):
    return 1.0 - 0.06 * armor / (1.0 + 0.06 * abs(armor))

def magic_mult(base, independents):
    m = 1.0 - base
    for r in independents:
        m *= 1.0 - r
    return m

print("== B01.1 面具攻速 ==")
print("PA+mask interval:", 1.7 * 100 / max(20.0, 100 + 28 + 60))
print("PA plain interval:", 1.7 * 100 / max(20.0, 100 + 28))
print("attacks in 40 ticks: mask -> 1,11,21,31 =4; plain -> 1,15,29 =3")

print()
print("== B01.4 面具减甲（juggernaut armor=1+26/6）==")
armor = 1 + 26 / 6
print("armor:", armor, "mask armor:", armor - 3)
d_plain = 60 * phys_mult(armor)
d_mask = 60 * phys_mult(armor - 3)
print("skywrath hit 60 -> plain:", d_plain, "mask:", d_mask)
print("with-mask run (60 ticks, hits at 1,16,31 masked + 46 plain):", 3 * d_mask + d_plain)
print("plain run (60 ticks, hits at 1,16,31,46):", 4 * d_plain)

print()
print("== B02 ==")
print("maelstrom seed1: 3 attacks x92 + 2 procs x70 =", 3 * 92 + 2 * 70)
print("crystalys+maelstrom seed12: t1 122*1.9=231.8 +70 ; t2 122 ; total", 122 * 1.9 + 70 + 122)
print("  lifesteal 0.10 (morbid): 231.8*0.1 + 122*0.1 =", 231.8 * 0.1 + 12.2)
print("mjollnir seed2: player loss 12*84 + 4*120 =", 12 * 84 + 4 * 120, " enemy loss 2*60 =", 120)
print("upgrade both: 116 + 120 =", 116 + 120, "; 2 attacks:", 116 + 120 + 116)

print()
print("== B03 ==")
print("viper_bite seed1: 3*118 + 2*50 =", 3 * 118 + 2 * 50, "; +3 layer ticks x8 =", 3 * 118 + 2 * 50 + 24)
print("  lifesteal 0.10: 3*118*0.1 =", 3 * 11.8)
print("witch_blade: 4 attacks x100 + 3 ticks x6 =", 4 * 100 + 18)
mr_pa = 0.25 + 15 * 0.001
print("atos: PA dummy MR =", mr_pa, " effective with -0.15:", mr_pa - 0.15)
print("  tick 8 ->", 8 * magic_mult(mr_pa - 0.15, []), " ; witch 6 ->", 6 * magic_mult(mr_pa, []))

print()
print("== B04/05 ==")
print("butterfly evasion remain:", 1 - 0.35)

print()
print("== B07 ==")
print("satanic window lifesteal 1.25: 133*1.25 =", 133 * 1.25, "; passive 0.25: 133*0.25 =", 133 * 0.25)
print("  hp after 60 ticks: 1000 + 3*%.2f + 2*%.2f = %.2f" % (133 * 1.25, 133 * 0.25, 1000 + 3 * 133 * 1.25 + 2 * 133 * 0.25))
regen = 0.5 + (22 + 40) * 0.1 + 10.0
maxhp = 500 + 22 * (22 + 40) + 500
print("heart: regen =", regen, " per tick =", regen * 0.1, " maxHp =", maxhp)
print("  after 10 ticks: 1000 +", 10 * regen * 0.1)
print("butterfly: speed =", (100 + 63 + 30) * 1.3, " interval =", 1.7 * 100 / ((100 + 63 + 30) * 1.3))
print("  attacks in 30 ticks: butterfly -> 1,8,15,22,29 =5; plain -> 1,15,29 =3")
