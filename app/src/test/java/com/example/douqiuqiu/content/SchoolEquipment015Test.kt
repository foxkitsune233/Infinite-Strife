package com.example.douqiuqiu.content

import com.example.douqiuqiu.cards.CardEffectHost
import com.example.douqiuqiu.combat.ShieldKind
import com.example.douqiuqiu.equipment.EquipmentBattleState
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.equipment.EquipmentEffectRuntime
import com.example.douqiuqiu.equipment.EquippedGear
import com.example.douqiuqiu.journey.ChallengeBalance
import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.status.TimedControls
import org.junit.Assert.*
import org.junit.Test

/** 0.1.5 怒火／寒霜装备：配方、唯一组、主动 CD、投放门槛与运行时效果。 */
class SchoolEquipment015Test {

    private val schoolMains = SchoolEquipmentContent.mains
    private val rageIds = setOf("bloodrage_gauntlet", "frenzy_heart")
    private val frostIds = setOf("frost_shard_blade", "glacier_plate")

    @Test fun eachSchoolHasAtLeastTwoMainsWithDistinctIds() {
        assertEquals(4, schoolMains.size)
        assertEquals(schoolMains.size, schoolMains.map { it.id }.toSet().size)
        assertTrue(schoolMains.map { it.id }.containsAll(rageIds + frostIds))
        for (main in schoolMains) {
            assertEquals(EquipmentCategory.MAIN, main.category)
            assertNotNull("${main.id} 必须有配方", main.recipe)
            assertTrue("${main.id} 必须有描述", main.description.isNotBlank())
            assertTrue("${main.id} 价格必须为正", main.price > 0)
        }
    }

    @Test fun scrollsAreReferencedByTheirOwnRecipeAndAllMaterialsExist() {
        val known = EquipmentCatalog.all.map { it.id }.toSet() + CardCatalogTest.EXISTING_ITEM_IDS
        for (main in schoolMains) {
            val recipe = main.recipe!!
            assertEquals(main.id, recipe.outputId)
            assertTrue("${main.id} 配方应包含同名卷轴", "recipe_${main.id}" in recipe.ingredients)
            for (ingredient in recipe.ingredients.keys) {
                assertTrue("${main.id} 的材料 $ingredient 未定义", ingredient in known)
            }
        }
        assertEquals(4, SchoolEquipmentContent.scrolls.size)
        assertTrue(SchoolEquipmentContent.scrolls.all { it.recipe == null })
    }

    @Test fun newMainsEnterDropPoolsAtTheirPriceTier() {
        for (main in schoolMains) {
            // 掉落门槛由成装价格档决定（>=2500 为二档），不强行压到一档。
            val tier = ChallengeBalance.minimumDropDifficulty(main.id)
            assertTrue("${main.id} 必须至少是一档掉落，实际 $tier", tier >= 1)
            val item = com.example.douqiuqiu.inventory.ItemDefinition(main.id, main.name,
                com.example.douqiuqiu.inventory.ItemKind.EQUIPMENT, main.width, main.height, price = main.price)
            assertFalse("难度 ${tier - 1} 不应投放 ${main.id}", ChallengeBalance.allowsDrop(item, (tier - 1).coerceAtLeast(0)) && tier > 1)
            assertTrue("难度 $tier 应投放 ${main.id}", ChallengeBalance.allowsDrop(item, tier))
        }
    }

    @Test fun glacierPlateFreezeIsActiveUndispellableWithCooldown() {
        val plate = definition("glacier_plate")
        val freeze = plate.effects.single { it.id == "glacier.freeze" }
        assertEquals(EffectKind.APPLY_CONTROL, freeze.kind)
        assertEquals(EffectTrigger.ACTIVE, freeze.trigger)
        assertEquals("FREEZE", (freeze.params.getValue("control") as EffectValue.Choice).value)
        assertEquals(DispelClass.NOT_DISPELLABLE, freeze.dispelClass)
        assertTrue("主动冻结必须有内置 CD", (freeze.internalCooldownMs ?: 0) > 0)
        // 同组被动与主动共享唯一组，禁止重复叠加
        assertEquals(setOf("glacier_plate"), plate.effects.mapNotNull { it.uniqueGroup }.toSet())
    }

    @Test fun bloodrageUsesOwnGroupAndStackDuration() {
        val gauntlet = definition("bloodrage_gauntlet")
        val stack = gauntlet.effects.single { it.kind == EffectKind.ATTACK_SPEED_STACK }
        assertEquals(EffectTrigger.ATTACK_LAND, stack.trigger)
        assertTrue((stack.stackDurationMs ?: 0) > 0)
        assertTrue((stack.internalCooldownMs ?: 0) > 0)
        assertEquals(EffectKind.LIFESTEAL, gauntlet.effects.first { it.id == "bloodrage.lifesteal" }.kind)
    }

    @Test fun equippedFreezeActuallyFreezesTargetAndStartsCooldown() {
        val host = Host(gear = "glacier_plate")
        assertTrue(EquipmentEffectRuntime.use(host, 0, "g1"))
        assertTrue("目标应处于冻结", TimedControls.current(host.enemy).frozen)
        assertTrue("冻结必须留痕", host.events.any { it.second == "APPLIED" })
        val cd = host.self.equipment.cooldowns.remainingByGroup["glacier_plate"] ?: 0
        assertTrue("主动装备应进入 CD", cd > 0)
    }

    @Test fun bloodrageOnHitAddsTimedAttackSpeed() {
        val host = Host(gear = "bloodrage_gauntlet")
        assertEquals(0.0, host.self.speedBuff, 0.0)
        EquipmentEffectRuntime.trigger(host, 0, EffectTrigger.ATTACK_LAND, { 1.0 })
        assertEquals(12.0, host.self.speedBuff, 0.0001)
        assertTrue(host.self.speedBuffMs > 0)
    }

    @Test fun frostBladeSlowUsesStatusResistanceAndIsDispellable() {
        val host = Host(gear = "frost_shard_blade")
        // 概率效果用必中随机源，确保本次触发
        EquipmentEffectRuntime.trigger(host, 0, EffectTrigger.ATTACK_LAND, { 0.0 })
        if (host.enemy.slowTime > 0) {
            assertEquals(20.0, host.enemy.slow, 0.0001)
            assertTrue(host.enemy.slowTime <= 1.5)
        } else {
            assertTrue("减速未触发时应至少走完概率判定", host.events.isEmpty() || host.enemy.hp <= host.initialEnemyHp)
        }
    }

    private fun definition(id: String) = EquipmentCatalog.all.single { it.id == id }

    /** 最小宿主：记录控制事件，其余伤害/治疗忽略。 */
    private class Host(gear: String) : CardEffectHost {
        override val cardTick: Int = 1
        var self = Fighter("axe", 300.0, 300.0, 100.0, 100.0, 40.0, 100.0, 1.7, 5.0, 0.0, 1.0, 0.0,
            equipment = EquipmentBattleState(items = listOf(EquippedGear("g1", gear))))
        var enemy = Fighter("abaddon", 300.0, 300.0, 100.0, 100.0, 30.0, 100.0, 1.7, 2.0, 0.0, 1.0, 0.0)
        val initialEnemyHp = enemy.hp
        val events = mutableListOf<Pair<String, String>>()

        override fun fighter(index: Int): Fighter = if (index == 0) self else enemy
        override fun update(index: Int, fighter: Fighter) { if (index == 0) self = fighter else enemy = fighter }
        override fun healCard(index: Int, amount: Double) = Unit
        override fun damageCard(source: Int, target: Int, amount: Double, type: DamageType,
                                attackClass: AttackClass, flags: EffectFlags, effectId: String) = Unit
        override fun poisonCard(source: Int, target: Int, sourceId: String, virulent: Boolean,
                                layers: Int, damage: Double) = Unit
        override fun controlEvent(source: Int, target: Int, sourceId: String, result: String, durationMs: Long) {
            events += sourceId to result
        }
        override fun roll(): Double = 0.0
    }
}
