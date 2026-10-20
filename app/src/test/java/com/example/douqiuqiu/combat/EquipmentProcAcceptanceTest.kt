package com.example.douqiuqiu.combat

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.content.EffectKind
import com.example.douqiuqiu.content.EquipmentCategory
import com.example.douqiuqiu.content.EquipmentCatalog
import com.example.douqiuqiu.equipment.EquipmentBattleState
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.inventory.Container
import com.example.douqiuqiu.inventory.EquipmentStats
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.ItemKind
import com.example.douqiuqiu.inventory.ItemPlace
import com.example.douqiuqiu.inventory.StoredItem
import com.example.douqiuqiu.journey.DuelEngine
import com.example.douqiuqiu.journey.DuelState
import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.journey.JourneyRules
import org.junit.Assert.*
import org.junit.Test

/**
 * 28号 B 组：装备战斗行为验收（B01～B07）。
 *
 * 全部用例都用**实际装备/技能栏构造的角色**推进 [DuelEngine]（100ms 战斗 tick），
 * 不直接调用纯工具判定通过。自然回血/回蓝、敌人普攻与英雄被动已隔离；
 * 需要这些机制的用例（面具减甲、撒旦低血、电锤反击）另开测试并显式说明。
 * 概率效果用固定 seed，注释里给出可解释的触发轨迹（Lehmer 序列，
 * 与 `FirstRunContent.next` 一致；暴击阈值经 `CriticalPrd` 校准）。
 */
class EquipmentProcAcceptanceTest {
    private val defs = (EquipmentCatalog.all.map { d ->
        ItemDefinition(d.id, d.name,
            if (d.category == EquipmentCategory.SCROLL) ItemKind.SCROLL else ItemKind.EQUIPMENT,
            d.width, d.height, d.quality.label, stats = d.stats ?: EquipmentStats(), price = d.price)
    } + CardCombat.enabledCards.map {
        ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height, it.quality.label)
    }).associateBy { it.id }
    private val rules = InventoryRules(defs)
    private val journey = JourneyRules(rules)

    private fun idle(f: Fighter) = f.copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 999.0)
    private fun advance(s: DuelState, count: Int): DuelState {
        var current = s
        repeat(count) { current = DuelEngine.step(current) }
        return current
    }
    private fun duel(player: Fighter, enemy: Fighter = victim(), seed: Long = 1729) = DuelState(player, enemy, seed)

    /** 不行动、不吃治疗的木桩；magicResist/armor 归零以保证伤害数字可精确断言。 */
    private fun victim(hero: String = "juggernaut", hp: Double = 500.0) =
        idle(journey.fighter(InventoryState(), hero)).copy(hp = hp, maxHp = hp, magicResist = 0.0, armor = 0.0)

    /** 生效装备栏持装角色；默认停放攻击，护甲/魔抗归零；需要真实防御时传 zeroDefense = false。 */
    private fun gearFighter(hero: String, vararg gearIds: String,
                            hp: Double = 5000.0, attackIn: Double = 999.0, ultIn: Double = 999.0,
                            zeroDefense: Boolean = true, maxHp: Double = hp, keepRegen: Boolean = false): Fighter {
        val items = gearIds.mapIndexed { i, id -> StoredItem("g$i", id, ItemPlace(Container.EQUIPMENT, i)) }
        val base = journey.fighter(InventoryState(items = items), hero)
        // keepRegen：保留英雄/装备自然恢复（idle 默认清零），供龙心类用例使用
        val parked = if (keepRegen) base.copy(mana = 0.0, manaRegen = 0.0) else idle(base)
        return parked.copy(hp = hp, maxHp = maxHp, attackIn = attackIn, ultIn = ultIn,
            armor = if (zeroDefense) 0.0 else base.armor,
            magicResist = if (zeroDefense) 0.0 else base.magicResist)
    }

    /** 技能栏持卡 + 生效装备栏持装。 */
    private fun cardAndGear(hero: String, cards: List<Pair<String, Int>>, gearIds: List<String>): Fighter {
        val items = cards.mapIndexed { i, (id, star) -> StoredItem("c$i", id, ItemPlace(Container.SKILLS, i), star = star) } +
            gearIds.mapIndexed { i, id -> StoredItem("g$i", id, ItemPlace(Container.EQUIPMENT, i)) }
        return idle(journey.fighter(InventoryState(skillSlots = 8, items = items), hero))
    }

    /** 推进一次玩家普攻（强制 attackIn=0），随后停放攻击以隔离后续 tick。 */
    private fun playerAttackTurn(s: DuelState): DuelState {
        val stepped = DuelEngine.step(s.copy(player = s.player.copy(attackIn = 0.0)))
        return stepped.copy(player = stepped.player.copy(attackIn = 999.0))
    }

    /** 推进一次敌人普攻（强制敌方 attackIn=0），随后停放敌方攻击。 */
    private fun enemyAttackTurn(s: DuelState): DuelState {
        val stepped = DuelEngine.step(s.copy(enemy = s.enemy.copy(attackIn = 0.0)))
        return stepped.copy(enemy = stepped.enemy.copy(attackIn = 999.0))
    }

    // ---------------- B01：疯狂面具 ----------------

    @Test fun maskOfMadnessAutoActivatesAndGrantsRealAttackSpeedForItsWindow() {
        // 面具在 tick 1 由 EquipmentEffectRuntime.automatic 自动开启（非 manual）：
        // activeGroups=4000ms、唯一组 CD=8000ms；攻速 +60 进入真实 interval 计算
        var masked = duel(gearFighter("phantom_assassin", "mask_of_madness", attackIn = 0.0), victim(hp = 100000.0))
        masked = DuelEngine.step(masked)
        assertEquals(4000L, masked.player.equipment.activeGroups["mask_madness"])
        assertEquals(8000L, masked.player.equipment.cooldowns.remainingByGroup["mask_madness"])
        // PA: bat 1.7 / speed (100+28+60)=188 -> 170/188
        assertEquals(0.9042553191489362, masked.player.attackIn, 1e-9)
        assertEquals(1, masked.player.cardState.attackCount)

        var plain = duel(gearFighter("phantom_assassin", attackIn = 0.0), victim(hp = 100000.0))
        plain = DuelEngine.step(plain)
        // 无面具: speed 128 -> 170/128
        assertEquals(1.328125, plain.player.attackIn, 1e-9)

        // 40 tick 内真实攻击次数：面具 1/11/21/31 共 4 次，无面具 1/15/29 共 3 次
        masked = advance(masked, 39)
        plain = advance(plain, 39)
        assertEquals(4, masked.player.cardState.attackCount)
        assertEquals(3, plain.player.cardState.attackCount)

        // tick 41 持续时间结束：activeGroups 掉出、攻速恢复（下次攻击按 1.328125 排）
        masked = DuelEngine.step(masked)
        assertFalse(masked.player.equipment.activeGroups.containsKey("mask_madness"))
        assertEquals(1.328125, masked.player.attackIn, 1e-9)
    }

    @Test fun maskSelfSilenceBlocksTheUltUntilTheWindowEnds() {
        // 自我沉默经 FighterEffects.permission 表达：面具 active 期间 HERO_SKILL 被禁
        val player = gearFighter("juggernaut", "mask_of_madness", attackIn = 0.0, ultIn = 0.0)
            .copy(mana = 100.0, maxMana = 100.0)
        var s = duel(player, victim(hp = 100000.0))
        s = advance(s, 40)
        assertEquals("沉默窗口内大招放不出", 100.0, s.player.mana, 1e-9)
        assertEquals(0.0, s.player.ultIn, 1e-9)
        assertEquals(0.0, s.player.invulnerable, 1e-9)
        assertTrue(s.player.equipment.activeGroups.containsKey("mask_madness"))
        // tick 41 窗口结束，同 tick 大招立即放开（消耗 80、CD 10、无敌 1 秒）
        s = DuelEngine.step(s)
        assertEquals(20.0, s.player.mana, 1e-9)
        assertEquals(10.0, s.player.ultIn, 1e-9)
        assertEquals(1.0, s.player.invulnerable, 1e-9)
        assertFalse(s.player.equipment.activeGroups.containsKey("mask_madness"))
        // CD 仍在走（8000 - 40*100），窗口结束后不会立刻续上
        assertEquals(4000L, s.player.equipment.cooldowns.remainingByGroup["mask_madness"])
    }

    @Test fun maskManualActivationFollowsTheSameWindowAndCooldown() {
        // manual 集合内的装备跳过 automatic，只能由 useItem 手动开启。
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
    }

    @Test fun maskArmorDownAppliesOnlyInsideTheWindowAndRecoversAfter() {
        // 减甲是面具佩戴者作为受击者时的 -3 护甲；敌人天怒自然普攻（间隔 170/118，
        // 命中 tick 1/16/31/46），玩家主宰停放攻击隔离反击
        val enemy = gearFighter("skywrath", hp = 5000.0, attackIn = 0.0, zeroDefense = false)
        var masked = duel(gearFighter("juggernaut", "mask_of_madness", zeroDefense = false), enemy)
        var plain = duel(gearFighter("juggernaut", zeroDefense = false), enemy)
        masked = advance(masked, 60)
        plain = advance(plain, 60)
        // 主宰护甲 1+26/6=5.3333；面具期内 2.3333：60*(1-0.14/1.14)=52.6316，
        // 窗口外 60*(1-0.32/1.32)=45.4545。面具场前三击减甲、第四击已恢复
        assertEquals(5000.0 - 3 * 52.631578947368425 - 45.45454545454545, masked.player.hp, 1e-9)
        assertEquals(5000.0 - 4 * 45.45454545454545, plain.player.hp, 1e-9)
        assertEquals(4, masked.enemy.cardState.attackCount)
        assertFalse(masked.player.equipment.activeGroups.containsKey("mask_madness"))
    }

    // ---------------- B02：漩涡 / 电锤 ----------------

    @Test fun maelstromChainLightningEntersTheDamagePipelineOnProc() {
        // seed 1：每 tick 顺序 roll + chain chance(0.25)。轨迹：
        // t1 chain=0.0850 触发 / t2 chain=0.8916 未触发 / t3 chain=0.1897 触发
        var s = duel(gearFighter("phantom_assassin", "maelstrom"), victim(hp = 100000.0), seed = 1)
        s = playerAttackTurn(s)
        assertEquals("普攻 92 + 闪电 70", 100000.0 - 92.0 - 70.0, s.enemy.hp, 1e-9)
        s = playerAttackTurn(s)
        assertEquals("本次未触发，只结算普攻", 100000.0 - 2 * 92.0 - 70.0, s.enemy.hp, 1e-9)
        s = playerAttackTurn(s)
        assertEquals("两次触发共 140 魔法伤害进管线", 100000.0 - 3 * 92.0 - 2 * 70.0, s.enemy.hp, 1e-9)
        assertEquals(3, s.player.cardState.attackCount)
    }

    @Test fun chainLightningSegmentNeitherCritsNorLifestealsNorCountsAsAttack() {
        // seed 12：每 tick 顺序 + 水晶剑暴击(PRD 0.20, 首阈 0.0557) + chain(0.25)。轨迹：
        // t1 crit roll=0.0204<0.0557 暴击(1.60+PA0.30=1.90) 且 chain=0.2162 触发；
        // t2 crit roll=0.6155 未暴击 且 chain=0.2763 未触发
        // 玩家不满血（4000/5000）：满血时吸血增量被 min(maxHp, …) 钳掉
        val player = gearFighter("phantom_assassin", "crystalys", "maelstrom", "morbid_mask", hp = 4000.0, maxHp = 5000.0)
        var s = duel(player, victim(hp = 100000.0), seed = 12)
        s = playerAttackTurn(s)
        assertEquals("暴击普攻 122*1.90=231.8 + 闪电 70（不二次暴击）", 100000.0 - 231.8 - 70.0, s.enemy.hp, 1e-9)
        assertEquals(0, s.player.equipment.criticalFailures["gear:g0:crystalys.crit"])
        s = playerAttackTurn(s)
        assertEquals("普通普攻 122，闪电未触发", 100000.0 - 231.8 - 70.0 - 122.0, s.enemy.hp, 1e-9)
        assertEquals(1, s.player.equipment.criticalFailures["gear:g0:crystalys.crit"])
        // 吸血只来自普攻段：231.8*0.10 + 122*0.10；若闪电也吸血会是 42.38
        assertEquals(4000.0 + 23.18 + 12.2, s.player.hp, 1e-9)
        assertEquals("追加段不计入普攻次数", 2, s.player.cardState.attackCount)
    }

    @Test fun mjollnirStaticChargeCountersWithoutRecursionOrDuplicateTargets() {
        // seed 2：敌人（天怒+电锤）每 tick 被强制普攻；玩家（PA+电锤）停放攻击。
        // 每 tick 顺序 roll；普攻命中 -> 玩家 static(0.20, CD 500ms)；
        // 随后敌人 chain(0.25) -> 命中玩家 -> static 再判（CD 内不消耗 roll）。
        // 轨迹：t1 static=0.1701 触发 & chain=0.2027 触发；t3/t5 chain 触发但 static 在 CD；
        // t6 static=0.1645 再次触发；t7 chain 触发但 static 在 CD。12 tick 合计 static×2、chain×4
        val player = gearFighter("phantom_assassin", "mjollnir")
        val enemy = gearFighter("skywrath", "mjollnir", hp = 5000.0, attackIn = 0.0)
        var s = duel(player, enemy, seed = 2)
        repeat(12) { s = enemyAttackTurn(s) }
        // 玩家掉血：12 次普攻 84 + 4 次连锁 120 = 1488（魔抗已归零）
        assertEquals(5000.0 - 12 * 84.0 - 4 * 120.0, s.player.hp, 1e-9)
        // 敌人掉血：t1 反击 60 实扣；t6 第二次反击的 60 被天怒 hero:sky 法术盾全额吸收
        // （天怒被动：其法术伤害 30% 转化为持久法术盾；连锁命中 t1/t3/t5 累计 3x36=108 ≥ 60）。
        // 若反击递归或重复主目标，数值会继续放大
        assertEquals(5000.0 - 60.0, s.enemy.hp, 1e-9)
        assertEquals("玩家从未攻击", 0, s.player.cardState.attackCount)
        assertEquals(12, s.enemy.cardState.attackCount)
    }

    @Test fun maelstromAndMjollnirShareOneUpgradeGroup() {
        // 同持漩涡与雷神之锤：唯一组 maelstrom 择强，只留雷神之锤的 120 段
        // seed 1：t1 chain=0.0850 触发、t2 chain=0.8916 未触发
        var s = duel(gearFighter("phantom_assassin", "maelstrom", "mjollnir"), victim(hp = 100000.0), seed = 1)
        s = playerAttackTurn(s)
        assertEquals("普攻 116 + 雷神闪电 120（漩涡 70 段被升级组剔除）", 100000.0 - 116.0 - 120.0, s.enemy.hp, 1e-9)
        s = playerAttackTurn(s)
        assertEquals(100000.0 - 2 * 116.0 - 120.0, s.enemy.hp, 1e-9)
    }

    // ---------------- B03：怪蛇之息 / 巫师之刃 / 圣斧 ----------------

    @Test fun viperBiteAppliesOnePoisonLayerPerSuccessfulAttack() {
        // seed 1：每 tick 顺序 + 追加物理 chance(0.35)。轨迹：t1=0.0850 触发50 / t2=0.8916 未触发 / t3=0.1897 触发
        // 玩家不满血（4000/5000）：满血时 min(maxHp, …) 会把吸血增量钳掉
        val player = gearFighter("phantom_assassin", "viper_bite", "morbid_mask", hp = 4000.0, maxHp = 5000.0)
        var s = duel(player, victim(hp = 100000.0), seed = 1)
        repeat(3) { s = playerAttackTurn(s) }
        assertEquals("3 次普攻 118 + 2 次追加 50", 100000.0 - 3 * 118.0 - 2 * 50.0, s.enemy.hp, 1e-9)
        val layers = s.enemy.poisonPool!!.normal
        assertEquals("每次有效普攻 1 层普通毒", 3, layers.size)
        assertTrue(layers.all { it.sourceId == "gear:g0:viper.poison" && it.damagePerTick == 8.0 && it.ticksRemaining == 3 })
        // 吸血只来自普攻段：3*118*0.10；追加段不吸血
        assertEquals(4000.0 + 3 * 11.8, s.player.hp, 1e-9)
        // 三层分别在 tick 1/2/3 施加，首跳 tick 11/12/13，各 8 伤害
        s = advance(s, 10)
        assertEquals(100000.0 - 3 * 118.0 - 2 * 50.0 - 3 * 8.0, s.enemy.hp, 1e-9)
    }

    @Test fun witchBladePoisonRespectsItsFourSecondInternalCooldown() {
        // 无概率效果（附毒 chance 缺省 1.0，不消耗 roll），完全确定
        var s = duel(gearFighter("phantom_assassin", "witch_blade"), victim(hp = 100000.0))
        s = playerAttackTurn(s)
        assertEquals(1, s.enemy.poisonPool!!.normal.size)
        assertEquals(4000L, s.player.equipment.cooldowns.remainingByGroup["poison_blade"])
        s = playerAttackTurn(s)
        assertEquals("CD 内不再附毒", 1, s.enemy.poisonPool!!.normal.size)
        // 推进到 tick 31：首层在 11/21/31 各跳 6 后耗尽
        s = advance(s, 29)
        assertNull(s.enemy.poisonPool)
        assertEquals(100000.0 - 2 * 88.0 - 3 * 6.0, s.enemy.hp, 1e-9)
        // tick 41 CD 归零，再次攻击重新附毒并重启 CD；首层 3 跳耗尽已移除，重附后为 1 层
        s = advance(s, 10)
        s = playerAttackTurn(s)
        assertEquals(1, s.enemy.poisonPool!!.normal.size)
        assertEquals(4000L, s.player.equipment.cooldowns.remainingByGroup["poison_blade"])
        s = playerAttackTurn(s)
        assertEquals("CD 重启后当日攻击不再附毒", 1, s.enemy.poisonPool!!.normal.size)
        assertEquals(100000.0 - 4 * 88.0 - 3 * 6.0, s.enemy.hp, 1e-9)
    }

    @Test fun rodOfAtosAppliesVirulentAndIndependentlyShredsMagicResist() {
        // 敌人保留真实魔抗（PA 0.25+15*0.001=0.265）但护甲归零；玩家停放攻击外的普攻强制触发
        val dummy = idle(journey.fighter(InventoryState(), "phantom_assassin"))
            .copy(hp = 100000.0, maxHp = 100000.0, armor = 0.0)
        var atos = duel(gearFighter("phantom_assassin", "rod_of_atos"), dummy)
        atos = playerAttackTurn(atos)
        assertEquals(1, atos.enemy.poisonPool!!.virulent.size)
        assertEquals("gear:g0:atos.virulent", atos.enemy.poisonPool!!.virulent.single().sourceId)
        assertEquals(8.0, atos.enemy.poisonPool!!.virulent.single().damagePerTick, 1e-9)
        atos = playerAttackTurn(atos)
        assertEquals("4 秒 CD 内不重复附毒", 1, atos.enemy.poisonPool!!.virulent.size)
        // 剧毒每 1000ms 产一层：tick 11 产第 1 层（tick 21 首跳），tick 21 产第 2 层
        // 首跳伤害 8*(1-(0.265-0.15))=7.08
        atos = advance(atos, 19)
        val spawned = atos.enemy.poisonPool!!.normal
        assertEquals(2, spawned.size)
        assertTrue(spawned.all { it.sourceId == "gear:g0:atos.virulent" && it.damagePerTick == 8.0 })
        assertEquals("第 1 层已跳 1 次剩 2，第 2 层尚未起跳", listOf(2, 3), spawned.map { it.ticksRemaining }.sorted())
        assertEquals(100000.0 - 2 * 93.0 - 7.08, atos.enemy.hp, 1e-9)
        // 减魔抗是独立被动，与附毒 CD 无关
        assertEquals(-0.15, EquipmentCombat.passive(atos.player.equipment, EffectKind.MAGIC_RESIST, "percent"), 1e-9)

        // 对照组：巫师之刃普通毒 6，无减魔抗 -> 6*(1-0.265)=4.41
        var witch = duel(gearFighter("phantom_assassin", "witch_blade"), dummy)
        witch = playerAttackTurn(witch)
        witch = advance(witch, 10)
        assertEquals(100000.0 - 88.0 - 4.41, witch.enemy.hp, 1e-9)
        // 7.08/8=0.885 与 4.41/6=0.735 相差 15 个百分点
        assertEquals(0.15, (1 - 4.41 / 6.0) - (1 - 7.08 / 8.0), 1e-9)
    }

    @Test fun poisonBladeUpgradeGroupKeepsOnlyTheStrongerEffect() {
        // 巫师之刃与圣斧同属唯一组 poison_blade：只保留圣斧的剧毒 + 减魔抗
        val dummy = idle(journey.fighter(InventoryState(), "phantom_assassin"))
            .copy(hp = 100000.0, maxHp = 100000.0, armor = 0.0)
        var s = duel(gearFighter("phantom_assassin", "witch_blade", "rod_of_atos"), dummy)
        s = playerAttackTurn(s)
        assertEquals("巫师普通毒被剔除，只附圣斧剧毒", 0, s.enemy.poisonPool!!.normal.size)
        assertEquals(1, s.enemy.poisonPool!!.virulent.size)
        s = playerAttackTurn(s)
        assertEquals(1, s.enemy.poisonPool!!.virulent.size)
        s = advance(s, 19)
        assertEquals("减魔抗对剧毒产毒同样生效", 100000.0 - 2 * 113.0 - 7.08, s.enemy.hp, 1e-9)
    }

    // ---------------- B04：固定格挡与整次无效 ----------------

    @Test fun fixedBlockZeroesTheDamageButKeepsTheHitSuccessful() {
        // 当前内容最低自然攻击也高于最大固定格挡，为观察「伤害为 0」分支，
        // 夹具将攻击力显式设为 25；先锋盾 30 格挡、装备与推进链路均为真实
        val player = gearFighter("phantom_assassin", "witch_blade").copy(attack = 25.0)
        var s = duel(player, gearFighter("juggernaut", "vanguard_shield", hp = 100000.0))
        s = playerAttackTurn(s)
        assertEquals("格挡后事件伤害为 0，不掉血", 100000.0, s.enemy.hp, 1e-9)
        assertEquals("仍是成功命中：附毒继续", 1, s.enemy.poisonPool!!.normal.size)
        assertEquals("普攻计数继续", 1, s.player.cardState.attackCount)
        s = advance(s, 10)
        assertEquals(100000.0 - 6.0, s.enemy.hp, 1e-9)
    }

    @Test fun evasionRejectsTheWholeAttackBeforeAnyOnHitEffect() {
        // seed 8：每 tick 顺序 + 闪避 roll。蝴蝶 35% 闪避 -> 保留 0.65。
        // 轨迹：t1 roll=0.6803>=0.65 闪避 / t2 roll=0.1329<0.65 命中
        var s = duel(gearFighter("phantom_assassin", "witch_blade"),
            gearFighter("juggernaut", "butterfly", hp = 100000.0), seed = 8)
        s = playerAttackTurn(s)
        assertNull("整次被闪避：不附毒", s.enemy.poisonPool)
        assertEquals(0, s.player.cardState.attackCount)
        assertEquals(100000.0, s.enemy.hp, 1e-9)
        s = playerAttackTurn(s)
        assertEquals(1, s.enemy.poisonPool!!.normal.size)
        assertEquals(1, s.player.cardState.attackCount)
        assertEquals(100000.0 - 88.0, s.enemy.hp, 1e-9)
    }

    @Test fun attackProtectionRejectsTheWholeAttack() {
        // 推开保护（force_staff/hurricane_pike 家族，此处直接置 protectionMs）
        // 在 attack() 入口整次拒绝，与格挡的「命中但 0 伤害」区分
        val enemy = gearFighter("juggernaut", hp = 100000.0)
            .copy(equipment = EquipmentBattleState(protectionMs = 2000))
        var s = duel(gearFighter("phantom_assassin", "witch_blade"), enemy)
        s = playerAttackTurn(s)
        s = playerAttackTurn(s)
        assertNull("保护期内不附毒", s.enemy.poisonPool)
        assertEquals(0, s.player.cardState.attackCount)
        assertEquals(100000.0, s.enemy.hp, 1e-9)
    }

    // ---------------- B05：闪避 / 保护 / 装备暴击 ----------------

    @Test fun invalidAttackTriggersNeitherPoisonNorLifestealNorCount() {
        // seed 8：t1 闪避 / t2 命中。附毒（巫师之刃）与吸血（吸血面具 10%）都只跟命中走
        // 玩家不满血（4000/5000）：满血时吸血增量被 min(maxHp, …) 钳掉
        var s = duel(gearFighter("phantom_assassin", "witch_blade", "morbid_mask", hp = 4000.0, maxHp = 5000.0),
            gearFighter("juggernaut", "butterfly", hp = 100000.0), seed = 8)
        s = playerAttackTurn(s)
        assertEquals("闪避不吸血", 4000.0, s.player.hp, 1e-9)
        assertEquals(0, s.player.cardState.attackCount)
        s = playerAttackTurn(s)
        assertEquals("命中才吸血 88*0.10", 4000.0 + 8.8, s.player.hp, 1e-9)
        assertEquals(1, s.player.cardState.attackCount)
        s = advance(s, 10)
        assertEquals(100000.0 - 88.0 - 6.0, s.enemy.hp, 1e-9)
    }

    @Test fun equipmentCriticalsEvaluateInOrderAndOnlyAdvanceEvaluatedPrd() {
        // seed 12：t1 顺序 + 代达罗斯(2.20, PRD 0.25 首阈 0.0847) roll=0.0204 暴击，
        // 倍率择强止步，水晶剑不再评估；t2 代达罗斯 roll=0.6993 未暴击
        var s = duel(gearFighter("phantom_assassin", "daedalus", "crystalys"), victim(hp = 100000.0), seed = 12)
        s = playerAttackTurn(s)
        // 158*(2.20+PA 0.30)=395
        assertEquals(100000.0 - 395.0, s.enemy.hp, 1e-9)
        assertEquals("未评估来源不进 PRD 表", setOf("gear:g0:daedalus.crit"), s.player.equipment.criticalFailures.keys)
        assertEquals(0, s.player.equipment.criticalFailures["gear:g0:daedalus.crit"])
        s = playerAttackTurn(s)
        assertEquals(1, s.player.equipment.criticalFailures["gear:g0:daedalus.crit"])
        assertEquals("第二次才评估到水晶剑", 1, s.player.equipment.criticalFailures["gear:g1:crystalys.crit"])
    }

    @Test fun bothCritSourcesAdvanceTheirOwnPrdWhenEvaluated() {
        // seed 5：t1 代达罗斯 roll=0.4252 未暴击(失败 0->1) -> 水晶剑 roll=0.0068<0.0557 暴击；
        // t2/t3/t4 两源依次评估且均未暴击，各自失败计数递增
        var s = duel(gearFighter("phantom_assassin", "daedalus", "crystalys"), victim(hp = 100000.0), seed = 5)
        repeat(4) { s = playerAttackTurn(s) }
        // t1 水晶剑暴击 158*(1.60+0.30)=300.2，其余 3 次普通 158
        assertEquals(100000.0 - 300.2 - 3 * 158.0, s.enemy.hp, 1e-9)
        assertEquals(4, s.player.equipment.criticalFailures["gear:g0:daedalus.crit"])
        assertEquals(3, s.player.equipment.criticalFailures["gear:g1:crystalys.crit"])
    }

    // ---------------- B06：玲珑心 ----------------

    @Test fun octarineCoreShortensTheRealUltCooldownAndTheNextCast() {
        val withOct = gearFighter("phantom_assassin", "octarine_core")
            .copy(mana = 1000.0, maxMana = 1000.0, ultIn = 0.0)
        val plain = gearFighter("phantom_assassin")
            .copy(mana = 1000.0, maxMana = 1000.0, ultIn = 0.0)
        var a = duel(withOct, victim(hp = 100000.0))
        var b = duel(plain, victim(hp = 100000.0))
        a = DuelEngine.step(a)
        b = DuelEngine.step(b)
        // 10s*(1-0.25)=7.5；蓝耗 70
        assertEquals(7.5, a.player.ultIn, 1e-9)
        assertEquals(930.0, a.player.mana, 1e-9)
        assertEquals(10.0, b.player.ultIn, 1e-9)
        // 玲珑心场第二次大招在 tick 76（7.5s）落地；无玲珑心要到 tick 101
        a = advance(a, 75)
        b = advance(b, 75)
        assertEquals(860.0, a.player.mana, 1e-9)
        assertEquals(7.5, a.player.ultIn, 1e-9)
        assertEquals(930.0, b.player.mana, 1e-9)
        assertEquals(2.5, b.player.ultIn, 1e-9)
    }

    @Test fun octarineCoreShortensTheRealIntervalCardPeriod() {
        val withOct = cardAndGear("phantom_assassin", listOf("P3" to 1), listOf("octarine_core"))
        val plain = cardAndGear("phantom_assassin", listOf("P3" to 1), emptyList())
        var a = duel(withOct, victim(hp = 100000.0))
        a = advance(a, 37)
        assertNull("玲珑心场 5s*(1-0.25)=3.75s -> 38 tick 才首次触发", a.enemy.poisonPool)
        a = advance(a, 1)
        assertEquals(3, a.enemy.poisonPool!!.normal.size)
        assertTrue(a.enemy.poisonPool!!.normal.all { it.damagePerTick == 5.0 })
        a = advance(a, 38)
        // tick 76 第二次触发：首批 3 层已各跳 3 次耗尽移除，场上只有新施加的 3 层
        assertEquals("下次触发在 tick 76，旧层已耗尽，新一批 3 层", 3, a.enemy.poisonPool!!.normal.size)
        assertEquals("首批三层各跳 3 次共 45", 100000.0 - 45.0, a.enemy.hp, 1e-9)

        var b = duel(plain, victim(hp = 100000.0))
        b = advance(b, 49)
        assertNull("无玲珑心 5s -> 50 tick 首次触发", b.enemy.poisonPool)
        b = advance(b, 1)
        assertEquals(3, b.enemy.poisonPool!!.normal.size)
    }

    @Test fun octarineCoreShortensTheRealActiveItemCooldown() {
        var a = duel(gearFighter("phantom_assassin", "octarine_core", "black_king_bar"), victim(hp = 100000.0))
        a = DuelEngine.useItem(a, "g1")
        assertEquals(3000L, a.player.equipment.immunityMs)
        // BKB 内部 CD 12000ms -> 12000*(1-0.25)=9000
        assertEquals(9000L, a.player.equipment.cooldowns.remainingByGroup["bkb"])
        var b = duel(gearFighter("phantom_assassin", "black_king_bar"), victim(hp = 100000.0))
        b = DuelEngine.useItem(b, "g0")
        assertEquals(12000L, b.player.equipment.cooldowns.remainingByGroup["bkb"])
    }

    @Test fun duplicateCooldownReductionGroupDoesNotStack() {
        // 两件玲珑心同属唯一组 cooldown_reduction，只取一个 25%
        var s = duel(gearFighter("phantom_assassin", "octarine_core", "octarine_core")
            .copy(mana = 1000.0, maxMana = 1000.0, ultIn = 0.0), victim(hp = 100000.0))
        s = DuelEngine.step(s)
        assertEquals("不是 10*(1-0.4375)=5.625", 7.5, s.player.ultIn, 1e-9)
    }

    @Test fun flatSecondsAreSubtractedBeforeThePercentageReduction() {
        // U3 三星固定减 2 秒先扣，再走玲珑心 25%：(10-2)*0.75=6.0；
        // 若顺序相反会是 10*0.75-2=5.5
        val player = cardAndGear("phantom_assassin", listOf("U3" to 3), listOf("octarine_core"))
            .copy(mana = 1000.0, maxMana = 1000.0, ultIn = 0.0)
        var s = duel(player, victim(hp = 100000.0))
        s = DuelEngine.step(s)
        assertEquals(6.0, s.player.ultIn, 1e-9)
        // U3 大招开始追加 40*4=160 魔法伤害
        assertEquals(100000.0 - 160.0, s.enemy.hp, 1e-9)
    }

    @Test fun freeUltCastAppliesTheFirstCastPenaltyAfterThePercentage() {
        // U4 开场免费大招：CD 先按 25% 缩减（10*0.75=7.5），再乘首次惩罚 1.5 -> 11.25
        val player = cardAndGear("phantom_assassin", listOf("U4" to 1), listOf("octarine_core"))
            .copy(mana = 1000.0, maxMana = 1000.0)
        var s = duel(player, victim(hp = 100000.0))
        s = DuelEngine.step(s)
        assertEquals(11.25, s.player.ultIn, 1e-9)
        assertEquals("免费大招不耗蓝", 1000.0, s.player.mana, 1e-9)
        assertEquals(4.0, s.player.criticalReady, 1e-9)
    }

    // ---------------- B07：撒旦 / 龙心 / 蝴蝶 ----------------

    @Test fun satanicActivatesOnLowHealthWithWeakDispelAndALifestealWindow() {
        // 20% 生命触发低血自动开启；夹具预置一层敌方普通毒（ nowTick=0 ）用于观察弱驱散
        val player = gearFighter("phantom_assassin", "satanic", hp = 1000.0, attackIn = 0.0, maxHp = 5000.0)
        val poisoned = PoisonCombat.apply(player, sourceUnit = 1, sourceId = "enemy-poison",
            serial = 0, nowTick = 0, virulent = false, layers = 1, damage = 10.0)
        var s = duel(poisoned, victim(hp = 100000.0))
        s = DuelEngine.step(s)
        assertNull("开启时弱驱散清掉已附普通毒", s.player.poisonPool)
        assertEquals(3000L, s.player.equipment.activeGroups["satanic"])
        assertEquals(12000L, s.player.equipment.cooldowns.remainingByGroup["satanic"])
        // 窗口内吸血 0.25 被动 + 1.00 开启 = 1.25；窗口外只剩 0.25。
        // PA+撒旦攻击 108，间隔 1.328125 -> 命中 tick 1/15/29（窗口内）/43/57（窗口外）
        s = advance(s, 59)
        assertEquals(1000.0 + 3 * 108.0 * 1.25 + 2 * 108.0 * 0.25, s.player.hp, 1e-9)
        assertEquals(100000.0 - 5 * 108.0, s.enemy.hp, 1e-9)
    }

    @Test fun heartOfTarrasqueRegeneratesPerTickAndNeverExceedsMaxHealth() {
        // 恢复 = 英雄 0.5 + 力量(22+40)*0.1 + 装备 10 = 16.7，每 tick 结算 1.67
        // keepRegen：gearFighter 默认停放自然恢复，龙心用例需保留 16.7/s
        var s = duel(gearFighter("phantom_assassin", "heart_of_tarrasque", hp = 1000.0, maxHp = 2364.0, keepRegen = true), victim(hp = 5000.0))
        s = advance(s, 10)
        assertEquals(1000.0 + 10 * 1.67, s.player.hp, 1e-9)
        // 贴近上限时恢复被钳在 maxHp，不虚增
        var capped = duel(gearFighter("phantom_assassin", "heart_of_tarrasque", hp = 2363.0, maxHp = 2364.0, keepRegen = true), victim(hp = 5000.0))
        capped = DuelEngine.step(capped)
        assertEquals(2364.0, capped.player.hp, 1e-9)
    }

    @Test fun butterflyAttackSpeedIsAMultiplicativeZone() {
        // 蝴蝶 30% 是乘区：(100 + 敏捷63 + 固定30) * 1.3 = 250.9 -> 170/250.9；
        // 若按加法会是 100+63+30+30=223 -> 170/223≈0.7623
        var withBf = duel(gearFighter("phantom_assassin", "butterfly", attackIn = 0.0), victim(hp = 100000.0))
        var plain = duel(gearFighter("phantom_assassin", attackIn = 0.0), victim(hp = 100000.0))
        withBf = DuelEngine.step(withBf)
        plain = DuelEngine.step(plain)
        assertEquals(0.6775607811877242, withBf.player.attackIn, 1e-9)
        assertEquals(1.328125, plain.player.attackIn, 1e-9)
        // 30 tick 内：蝴蝶 1/8/15/22/29 共 5 次，无蝴蝶 1/15/29 共 3 次
        withBf = advance(withBf, 29)
        plain = advance(plain, 29)
        assertEquals(5, withBf.player.cardState.attackCount)
        assertEquals(3, plain.player.cardState.attackCount)
    }
}
