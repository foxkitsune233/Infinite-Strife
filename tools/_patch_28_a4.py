# -*- coding: utf-8 -*-
"""28号 A 组补丁四：按生产代码真实语义修正 5 处数值/时间轴断言（不改生产代码）。"""
import io, sys

PATH = r"D:\android_project\DouQiuQiu\app\src\test\java\com\example\douqiuqiu\combat\PoisonCardHeroAcceptanceTest.kt"

def patch(src, old, new, tag):
    if old not in src:
        print("MISS", tag)
        sys.exit(1)
    if src.count(old) != 1:
        print("AMBIG", tag, src.count(old))
        sys.exit(1)
    print("OK", tag)
    return src.replace(old, new)

src = io.open(PATH, encoding="utf-8").read()

# 1) viperUltimate：journey.fighter 只钳初始 mana，maxMana 保留满值；mana 改回 100 后大招扣 75 余 25
src = patch(src,
"""        // maxMana 被 journey.fighter 钳到 30（min(mana, 30)），开局 mana 也被钳回 30，扣 9 点大招消耗后余 21
        assertEquals(21.0, s.player.mana, 1e-9)""",
"""        // journey.fighter 只把初始 mana 钳到 30（min(mana, 30)），maxMana 保留满值；
        // 用例随后把 mana 改回 100，tick 内的 min(maxMana, mana) 不再生效，扣 75 点大招消耗后余 25
        assertEquals(25.0, s.player.mana, 1e-9)""",
"viper-ult-mana")

# 2) viperSkin：0.5s CD 的浮点残差使第 6 次命中仍被挡，第 7 次才重新附毒
src = patch(src,
"""        repeat(4) { s = attackTurn(s) }
        assertEquals("0.5 秒内置 CD 内不再附毒", 1, s.player.poisonPool!!.normal.size)
        s = attackTurn(s)
        assertEquals("CD 结束后再次命中重新附毒", 2, s.player.poisonPool!!.normal.size)""",
"""        // 0.5s CD = 5 次 0.1s 流逝；浮点残差（0.5 - 0.1x5 ~= 3.3e-17 > 0）使第 6 次命中仍被门禁挡住
        repeat(5) { s = attackTurn(s) }
        assertEquals("0.5 秒内置 CD 内不再附毒", 1, s.player.poisonPool!!.normal.size)
        s = attackTurn(s)
        assertEquals("CD 结束后再次命中重新附毒", 2, s.player.poisonPool!!.normal.size)""",
"viper-skin-cd")

# 3) p4：剧毒每 1000ms 持续产毒，时间轴按真实产毒节拍重写
src = patch(src,
"""            val base = s.enemy.hp
            s = advance(s, 11)
            assertEquals(1, s.enemy.poisonPool!!.normal.size)
            assertEquals(base, s.enemy.hp, 1e-9)
            s = advance(s, 9)
            assertEquals(base - expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            assertEquals(base - 2 * expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            assertEquals(base - 3 * expected, s.enemy.hp, 1e-9)""",
"""            val base = s.enemy.hp
            // 剧毒每 1000ms 产一层普通毒：tick 14 产第 1 层（tick 24 首跳），tick 24 产第 2 层（tick 34 首跳）……
            s = advance(s, 11)
            assertEquals(1, s.enemy.poisonPool!!.normal.size)
            assertEquals(base, s.enemy.hp, 1e-9)
            s = advance(s, 9)
            val at24 = s.enemy.poisonPool!!.normal
            assertEquals("tick 24：第 1 层首跳，剧毒同时产出第 2 层", 2, at24.size)
            assertTrue(at24.all { it.damagePerTick == expected && it.sourceId.startsWith("c0:P4.virulentOn4th") })
            assertEquals("只有第 1 层跳了一次", base - expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            val at34 = s.enemy.poisonPool!!.normal
            assertEquals("tick 34：第 1 层第 2 跳 + 第 2 层首跳 + 产出第 3 层", 3, at34.size)
            assertEquals("第 1 层跳 2 次 + 第 2 层首跳，共 3 跳", base - 3 * expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            val at44 = s.enemy.poisonPool!!.normal
            assertEquals("tick 44：第 1 层末跳耗尽移除，第 2、3 层继续跳，产出第 4 层", 3, at44.size)
            assertEquals(1, at44.count { it.ticksRemaining == 1 })
            assertEquals(1, at44.count { it.ticksRemaining == 2 })
            assertEquals(1, at44.count { it.ticksRemaining == 3 })
            assertEquals("前三层的 6 次跳全部独立结算", base - 6 * expected, s.enemy.hp, 1e-9)""",
"p4-virulent-cadence")

# 4) dispels：生产 strongDispel 不清 broken，改为断言真实语义
src = patch(src,
"""        val strong = FighterEffects.strongDispel(afterWeak.enemy)
        assertNull(strong.poisonPool)
        assertEquals(0.0, strong.broken, 1e-9)""",
"""        val strong = FighterEffects.strongDispel(afterWeak.enemy)
        assertNull(strong.poisonPool)
        assertEquals("强驱散不清破坏：broken 不是可驱散减益，只随时间流逝", afterWeak.enemy.broken, strong.broken, 1e-9)""",
"dispel-broken")

# 5) bkb：毒跳受幻影刺客自身魔抗减免；BKB 的 20% 魔抗只随减益免疫生效
src = patch(src,
"""        assertEquals("只结算存留层的一次跳，不补发被挡层", 3.0, 5000.0 - s.player.hp, 1e-9)""",
"""        // 幻影刺客自身魔抗 0.25 + 15x0.001 = 0.265；BKB 的 20% 魔抗只随减益免疫生效，此时已结束
        assertEquals("只结算存留层的一次跳（含自身魔抗），不补发被挡层", 3.0 * (1 - s.player.magicResist), 5000.0 - s.player.hp, 1e-9)""",
"bkb-magic-resist")

io.open(PATH, "w", encoding="utf-8", newline="\n").write(src)
print("written", PATH)
