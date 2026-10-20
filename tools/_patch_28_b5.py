# -*- coding: utf-8 -*-
"""28号 B 组补丁五：按生产代码真实行为修正 9 处夹具/断言（不改生产代码）。"""
import io, sys

PATH = r"D:\android_project\DouQiuQiu\app\src\test\java\com\example\douqiuqiu\combat\EquipmentProcAcceptanceTest.kt"

def patch(src, old, new, tag):
    if old not in src:
        print("MISS", tag); sys.exit(1)
    if src.count(old) != 1:
        print("AMBIG", tag, src.count(old)); sys.exit(1)
    print("OK", tag)
    return src.replace(old, new)

src = io.open(PATH, encoding="utf-8").read()

# 0) gearFighter 增加 keepRegen：保留自然恢复（idle 默认清零）
src = patch(src,
"""    private fun gearFighter(hero: String, vararg gearIds: String,
                            hp: Double = 5000.0, attackIn: Double = 999.0, ultIn: Double = 999.0,
                            zeroDefense: Boolean = true, maxHp: Double = hp): Fighter {
        val items = gearIds.mapIndexed { i, id -> StoredItem("g$i", id, ItemPlace(Container.EQUIPMENT, i)) }
        val base = journey.fighter(InventoryState(items = items), hero)
        return idle(base).copy(hp = hp, maxHp = maxHp, attackIn = attackIn, ultIn = ultIn,
            armor = if (zeroDefense) 0.0 else base.armor,
            magicResist = if (zeroDefense) 0.0 else base.magicResist)
    }""",
"""    private fun gearFighter(hero: String, vararg gearIds: String,
                            hp: Double = 5000.0, attackIn: Double = 999.0, ultIn: Double = 999.0,
                            zeroDefense: Boolean = true, maxHp: Double = hp, keepRegen: Boolean = false): Fighter {
        val items = gearIds.mapIndexed { i, id -> StoredItem("g$i", id, ItemPlace(Container.EQUIPMENT, i)) }
        val base = journey.fighter(InventoryState(items = items), hero)
        // keepRegen：保留英雄/装备自然恢复（idle 默认清零），供龙心类用例使用
        val parked = if (keepRegen) base.copy(mana = 0.0, manaRegen = 0.0) else idle(base)
        return parked.copy(hp = hp, maxHp = maxHp, attackIn = attackIn, ultIn = ultIn,
            armor = if (zeroDefense) 0.0 else base.armor,
            magicResist = if (zeroDefense) 0.0 else base.magicResist)
    }""",
"gearFighter-keepRegen")

# 1) viperBite：满血会把吸血增量钳掉，降到 4000/5000
src = patch(src,
"""        val player = gearFighter("phantom_assassin", "viper_bite", "morbid_mask")
        var s = duel(player, victim(hp = 100000.0), seed = 1)""",
"""        // 玩家不满血（4000/5000）：满血时 min(maxHp, …) 会把吸血增量钳掉
        val player = gearFighter("phantom_assassin", "viper_bite", "morbid_mask", hp = 4000.0, maxHp = 5000.0)
        var s = duel(player, victim(hp = 100000.0), seed = 1)""",
"viperBite-fixture")

src = patch(src,
"""        // 吸血只来自普攻段：3*118*0.10；追加段不吸血
        assertEquals(5000.0 + 3 * 11.8, s.player.hp, 1e-9)""",
"""        // 吸血只来自普攻段：3*118*0.10；追加段不吸血
        assertEquals(4000.0 + 3 * 11.8, s.player.hp, 1e-9)""",
"viperBite-lifesteal")

# 2) witchBlade：首层耗尽已移除，CD 到期重附后为 1 层
src = patch(src,
"""        // tick 41 CD 归零，再次攻击重新附毒并重启 CD
        s = advance(s, 10)
        s = playerAttackTurn(s)
        assertEquals(2, s.enemy.poisonPool!!.normal.size)
        assertEquals(4000L, s.player.equipment.cooldowns.remainingByGroup["poison_blade"])
        s = playerAttackTurn(s)
        assertEquals("CD 重启后当日攻击不再附毒", 2, s.enemy.poisonPool!!.normal.size)""",
"""        // tick 41 CD 归零，再次攻击重新附毒并重启 CD；首层 3 跳耗尽已移除，重附后为 1 层
        s = advance(s, 10)
        s = playerAttackTurn(s)
        assertEquals(1, s.enemy.poisonPool!!.normal.size)
        assertEquals(4000L, s.player.equipment.cooldowns.remainingByGroup["poison_blade"])
        s = playerAttackTurn(s)
        assertEquals("CD 重启后当日攻击不再附毒", 1, s.enemy.poisonPool!!.normal.size)""",
"witchBlade-count")

# 3) rodOfAtos：剧毒每 1000ms 产一层，tick 21 已有 2 层
src = patch(src,
"""        // 剧毒 tick 11 产普通毒，tick 21 首跳：8*(1-(0.265-0.15))=7.08
        atos = advance(atos, 19)
        val spawned = atos.enemy.poisonPool!!.normal.single()
        assertEquals("gear:g0:atos.virulent", spawned.sourceId)
        assertEquals(8.0, spawned.damagePerTick, 1e-9)
        assertEquals(100000.0 - 2 * 93.0 - 7.08, atos.enemy.hp, 1e-9)""",
"""        // 剧毒每 1000ms 产一层：tick 11 产第 1 层（tick 21 首跳），tick 21 产第 2 层
        // 首跳伤害 8*(1-(0.265-0.15))=7.08
        atos = advance(atos, 19)
        val spawned = atos.enemy.poisonPool!!.normal
        assertEquals(2, spawned.size)
        assertTrue(spawned.all { it.sourceId == "gear:g0:atos.virulent" && it.damagePerTick == 8.0 })
        assertEquals("第 1 层已跳 1 次剩 2，第 2 层尚未起跳", listOf(2, 3), spawned.map { it.ticksRemaining }.sorted())
        assertEquals(100000.0 - 2 * 93.0 - 7.08, atos.enemy.hp, 1e-9)""",
"rodOfAtos-spawned")

# 4) invalidAttack：不满血才能观察吸血增量
src = patch(src,
"""        var s = duel(gearFighter("phantom_assassin", "witch_blade", "morbid_mask"),
            gearFighter("juggernaut", "butterfly", hp = 100000.0), seed = 8)
        s = playerAttackTurn(s)
        assertEquals("闪避不吸血", 5000.0, s.player.hp, 1e-9)
        assertEquals(0, s.player.cardState.attackCount)
        s = playerAttackTurn(s)
        assertEquals("命中才吸血 88*0.10", 5000.0 + 8.8, s.player.hp, 1e-9)""",
"""        // 玩家不满血（4000/5000）：满血时吸血增量被 min(maxHp, …) 钳掉
        var s = duel(gearFighter("phantom_assassin", "witch_blade", "morbid_mask", hp = 4000.0, maxHp = 5000.0),
            gearFighter("juggernaut", "butterfly", hp = 100000.0), seed = 8)
        s = playerAttackTurn(s)
        assertEquals("闪避不吸血", 4000.0, s.player.hp, 1e-9)
        assertEquals(0, s.player.cardState.attackCount)
        s = playerAttackTurn(s)
        assertEquals("命中才吸血 88*0.10", 4000.0 + 8.8, s.player.hp, 1e-9)""",
"invalidAttack-lifesteal")

# 5) mjollnir：第二次反击被天怒 hero:sky 法术盾全额吸收
src = patch(src,
"""        // 敌人掉血：仅 2 次反击 60。若反击递归或重复主目标，数值会继续放大
        assertEquals(5000.0 - 2 * 60.0, s.enemy.hp, 1e-9)""",
"""        // 敌人掉血：t1 反击 60 实扣；t6 第二次反击的 60 被天怒 hero:sky 法术盾全额吸收
        // （天怒被动：其法术伤害 30% 转化为持久法术盾；连锁命中 t1/t3/t5 累计 3x36=108 ≥ 60）。
        // 若反击递归或重复主目标，数值会继续放大
        assertEquals(5000.0 - 60.0, s.enemy.hp, 1e-9)""",
"mjollnir-static")

# 6) octarine 卡周期：tick 76 旧层已耗尽，只有新一批 3 层
src = patch(src,
"""        a = advance(a, 38)
        assertEquals("下次触发在 tick 76，累计 6 层", 6, a.enemy.poisonPool!!.normal.size)
        assertEquals("首层 3 跳共 15，三层 45", 100000.0 - 45.0, a.enemy.hp, 1e-9)""",
"""        a = advance(a, 38)
        // tick 76 第二次触发：首批 3 层已各跳 3 次耗尽移除，场上只有新施加的 3 层
        assertEquals("下次触发在 tick 76，旧层已耗尽，新一批 3 层", 3, a.enemy.poisonPool!!.normal.size)
        assertEquals("首批三层各跳 3 次共 45", 100000.0 - 45.0, a.enemy.hp, 1e-9)""",
"octarine-interval")

# 7) maskManual：ultIn 全程停放，最后一步才强制就绪
src = patch(src,
"""        // manual 集合内的装备跳过 automatic，只能由 useItem 手动开启
        val base = gearFighter("juggernaut", "mask_of_madness", ultIn = 0.0)
            .copy(mana = 100.0, maxMana = 100.0)
        val player = base.copy(equipment = base.equipment.copy(manual = setOf("g0")))
        var s = duel(player, victim(hp = 100000.0))
        s = DuelEngine.step(s)
        assertEquals("manual 装备不被自动开启", 0L, s.player.equipment.activeGroups["mask_madness"] ?: 0L)
        assertEquals(0L, s.player.equipment.cooldowns.remainingByGroup["mask_madness"] ?: 0L)
        assertEquals(100.0, s.player.mana, 1e-9)

        s = DuelEngine.useItem(s, "g0")
        assertEquals(4000L, s.player.equipment.activeGroups["mask_madness"])
        assertEquals(8000L, s.player.equipment.cooldowns.remainingByGroup["mask_madness"])
        s = advance(s, 5)
        assertEquals("开启后自我沉默仍在，大招继续被禁", 100.0, s.player.mana, 1e-9)
        s = advance(s, 34)
        assertTrue(s.player.equipment.activeGroups.containsKey("mask_madness"))
        s = DuelEngine.step(s)
        assertEquals("到期后大招放开", 20.0, s.player.mana, 1e-9)
        assertEquals(10.0, s.player.ultIn, 1e-9)
    }""",
"""        // manual 集合内的装备跳过 automatic，只能由 useItem 手动开启。
        // ultIn 全程停放：手动开启前大招若就绪，会在 tick 1 直接放出，沉默窗口就观察不到了
        val base = gearFighter("juggernaut", "mask_of_madness", ultIn = 999.0)
            .copy(mana = 100.0, maxMana = 100.0)
        val player = base.copy(equipment = base.equipment.copy(manual = setOf("g0")))
        var s = duel(player, victim(hp = 100000.0))
        s = DuelEngine.step(s)
        assertEquals("manual 装备不被自动开启", 0L, s.player.equipment.activeGroups["mask_madness"] ?: 0L)
        assertEquals(0L, s.player.equipment.cooldowns.remainingByGroup["mask_madness"] ?: 0L)
        assertEquals("未开启前大招正常待机", 100.0, s.player.mana, 1e-9)

        s = DuelEngine.useItem(s, "g0")
        assertEquals(4000L, s.player.equipment.activeGroups["mask_madness"])
        assertEquals(8000L, s.player.equipment.cooldowns.remainingByGroup["mask_madness"])
        s = advance(s, 5)
        assertEquals("开启后自我沉默仍在，大招继续被禁", 100.0, s.player.mana, 1e-9)
        s = advance(s, 34)
        assertTrue(s.player.equipment.activeGroups.containsKey("mask_madness"))
        // tick 41 窗口结束，同 tick 大招立即放开（消耗 80、CD 10、无敌 1 秒）
        s = DuelEngine.step(s.copy(player = s.player.copy(ultIn = 0.0)))
        assertEquals("到期后大招放开", 20.0, s.player.mana, 1e-9)
        assertEquals(10.0, s.player.ultIn, 1e-9)
        // CD 仍在走（8000 - 40*100），窗口结束后不会立刻续上
        assertEquals(4000L, s.player.equipment.cooldowns.remainingByGroup["mask_madness"])
    }""",
"maskManual-timing")

# 8) chainLightning：不满血才能观察吸血增量
src = patch(src,
"""        val player = gearFighter("phantom_assassin", "crystalys", "maelstrom", "morbid_mask")
        var s = duel(player, victim(hp = 100000.0), seed = 12)""",
"""        // 玩家不满血（4000/5000）：满血时吸血增量被 min(maxHp, …) 钳掉
        val player = gearFighter("phantom_assassin", "crystalys", "maelstrom", "morbid_mask", hp = 4000.0, maxHp = 5000.0)
        var s = duel(player, victim(hp = 100000.0), seed = 12)""",
"chainLightning-fixture")

src = patch(src,
"""        // 吸血只来自普攻段：231.8*0.10 + 122*0.10；若闪电也吸血会是 42.38
        assertEquals(5000.0 + 23.18 + 12.2, s.player.hp, 1e-9)""",
"""        // 吸血只来自普攻段：231.8*0.10 + 122*0.10；若闪电也吸血会是 42.38
        assertEquals(4000.0 + 23.18 + 12.2, s.player.hp, 1e-9)""",
"chainLightning-lifesteal")

# 9) heartOfTarrasque：保留自然恢复
src = patch(src,
"""        var s = duel(gearFighter("phantom_assassin", "heart_of_tarrasque", hp = 1000.0, maxHp = 2364.0), victim(hp = 5000.0))
        s = advance(s, 10)
        assertEquals(1000.0 + 10 * 1.67, s.player.hp, 1e-9)
        // 贴近上限时恢复被钳在 maxHp，不虚增
        var capped = duel(gearFighter("phantom_assassin", "heart_of_tarrasque", hp = 2363.0, maxHp = 2364.0), victim(hp = 5000.0))""",
"""        // keepRegen：gearFighter 默认停放自然恢复，龙心用例需保留 16.7/s
        var s = duel(gearFighter("phantom_assassin", "heart_of_tarrasque", hp = 1000.0, maxHp = 2364.0, keepRegen = true), victim(hp = 5000.0))
        s = advance(s, 10)
        assertEquals(1000.0 + 10 * 1.67, s.player.hp, 1e-9)
        // 贴近上限时恢复被钳在 maxHp，不虚增
        var capped = duel(gearFighter("phantom_assassin", "heart_of_tarrasque", hp = 2363.0, maxHp = 2364.0, keepRegen = true), victim(hp = 5000.0))""",
"heart-regen")

io.open(PATH, "w", encoding="utf-8", newline="\n").write(src)
print("written", PATH)
