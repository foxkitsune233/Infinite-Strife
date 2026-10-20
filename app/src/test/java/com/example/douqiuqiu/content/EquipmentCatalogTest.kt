package com.example.douqiuqiu.content

import org.junit.Assert.*
import org.junit.Test

/** 装备/组件/配方内容表的行为测试（H01-C）。 */
class EquipmentCatalogTest {

    // ---------------- 规模 ----------------

    @Test fun e01_thirtyOneMainsAndFiveIntermediates() {
        assertEquals(31, EquipmentCatalog.mains.size)
        assertTrue(EquipmentCatalog.mains.map { it.id }.containsAll(listOf("skull_basher", "orchid", "heavens_halberd")))
        assertEquals(5, EquipmentCatalog.intermediates.size)
    }

    @Test fun e02_noDuplicateEquipmentIds() {
        val ids = EquipmentCatalog.all.map { it.id }
        assertEquals("重复 ID: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}",
            ids.size, ids.toSet().size)
    }

    @Test fun e03_noConflictWithExistingInventoryIds() {
        val conflicts = EquipmentCatalog.all.map { it.id }.filter { it in CardCatalogTest.EXISTING_ITEM_IDS }
        assertTrue("与既有物品冲突: $conflicts", conflicts.isEmpty())
    }

    @Test fun e04_componentsAndScrollsHaveNoRecipe() {
        for (eq in EquipmentCatalog.components + EquipmentCatalog.scrolls) {
            assertNull("${eq.id} 不应带配方", eq.recipe)
        }
    }

    @Test fun e05_mainsAndIntermediatesAllHaveRecipes() {
        for (eq in EquipmentCatalog.mains + EquipmentCatalog.intermediates) {
            assertNotNull("${eq.id} 必须有配方", eq.recipe)
        }
    }

    // ---------------- 配方图结构（不等于原版核验） ----------------

    @Test fun e06_recipeGraphHasNoMissingPartsOrCycles() {
        val result = ContentValidator.validate(
            CardCatalog.all, EquipmentCatalog.all, CardCatalogTest.EXISTING_ITEM_IDS
        )
        val recipeIssues = result.issues.filter { it.kind == ContentIssueKind.RECIPE_GRAPH }
        assertTrue("配方图问题: $recipeIssues", recipeIssues.isEmpty())
    }

    @Test fun e07_everyIngredientResolvesToADefinition() {
        val known = EquipmentCatalog.all.map { it.id }.toSet() + CardCatalogTest.EXISTING_ITEM_IDS
        for (eq in EquipmentCatalog.mains + EquipmentCatalog.intermediates) {
            for (ingredient in eq.recipe!!.ingredients.keys) {
                assertTrue("${eq.id} 的组件 $ingredient 未定义", ingredient in known)
            }
        }
    }

    @Test fun e08_noDuplicateRecipeOutput() {
        val outputs = EquipmentCatalog.recipes.map { it.outputId }
        assertEquals(outputs.size, outputs.toSet().size)
    }

    @Test fun e09_recipeOutputMatchesOwnerId() {
        for (eq in EquipmentCatalog.mains + EquipmentCatalog.intermediates) {
            assertEquals("${eq.id} 配方产物应等于自身", eq.id, eq.recipe!!.outputId)
        }
    }

    // ---------------- 唯一组 ----------------

    @Test fun e10_witchBladeAndRodOfAtosSharePoisonGroup() {
        val witch = EquipmentCatalog.mains.single { it.id == "witch_blade" }
        val atos = EquipmentCatalog.mains.single { it.id == "rod_of_atos" }
        val witchGroups = witch.effects.mapNotNull { it.uniqueGroup }.toSet()
        val atosGroups = atos.effects.mapNotNull { it.uniqueGroup }.toSet()
        assertTrue("巫师之刃与圣斧应同组: $witchGroups / $atosGroups",
            witchGroups.intersect(atosGroups).contains("poison_blade"))
    }

    @Test fun e11_forceStaffAndHurricanePikeShareGroup() {
        val force = EquipmentCatalog.mains.single { it.id == "force_staff" }
        val pike = EquipmentCatalog.mains.single { it.id == "hurricane_pike" }
        assertEquals("force_staff", force.effects.single().uniqueGroup)
        assertTrue(pike.effects.all { it.uniqueGroup == "force_staff" })
    }

    @Test fun e12_threeSingleBladeFamiliesUseTheirOwnGroups() {
        val sange = EquipmentCatalog.mains.single { it.id == "sange" }
        val yasha = EquipmentCatalog.mains.single { it.id == "yasha" }
        val kaya = EquipmentCatalog.mains.single { it.id == "kaya" }
        assertTrue(sange.effects.all { it.uniqueGroup == "sange_family" })
        assertTrue(yasha.effects.all { it.uniqueGroup == "yasha_family" })
        assertTrue(kaya.effects.all { it.uniqueGroup == "kaya_family" })
    }

    @Test fun e13_dualBladesInheritBothFamilies() {
        val snY = EquipmentCatalog.mains.single { it.id == "sange_and_yasha" }
        val groups = snY.effects.mapNotNull { it.uniqueGroup }.toSet()
        assertEquals(setOf("sange_family", "yasha_family"), groups)
    }

    @Test fun e14_blockSharesOneGroupBetweenVanguardAndCrimson() {
        val vanguard = EquipmentCatalog.intermediates.single { it.id == "vanguard_shield" }
        val crimson = EquipmentCatalog.mains.single { it.id == "crimson_guard" }
        val vg = vanguard.effects.single { it.kind == EffectKind.PHYSICAL_BLOCK }.uniqueGroup
        val cg = crimson.effects.single { it.kind == EffectKind.PHYSICAL_BLOCK }.uniqueGroup
        assertEquals(vg, cg)
        // 赤红甲主动盾是独立效果，不并入格挡组
        val active = crimson.effects.single { it.kind == EffectKind.GRANT_SHIELD }
        assertNotEquals(vg, active.uniqueGroup)
    }

    // ---------------- 效果建模正确性 ----------------

    @Test fun e15_butterflyBaseAttackSpeedUsesMultiplicativeParam() {
        val butterfly = EquipmentCatalog.mains.single { it.id == "butterfly" }
        val amp = butterfly.effects.single { it.id == "butterfly.baseSpeedAmp" }
        assertEquals(EffectKind.STAT_BONUS, amp.kind)
        assertTrue("基础攻速增幅必须是乘区参数", amp.params.containsKey("attackSpeedPercent"))
        assertFalse(amp.params.containsKey("attackSpeed"))
    }

    @Test fun e16_maelstromChainLightningCannotCritOrLifesteal() {
        val chain = EquipmentCatalog.mains.single { it.id == "maelstrom" }
            .effects.single { it.kind == EffectKind.ADD_SPELL_DAMAGE }
        assertFalse(chain.flags.canCrit)
        assertFalse(chain.flags.canLifesteal)
        assertFalse(chain.flags.triggersOnHit)
        assertEquals(DamageType.MAGICAL, chain.damageType)
    }

    @Test fun e17_mjollnirStaticCounterIsMarkedReflect() {
        val static = EquipmentCatalog.mains.single { it.id == "mjollnir" }
            .effects.single { it.id == "mjollnir.static" }
        assertTrue("静电反击必须标反射，防止递归", static.flags.isReflect)
        assertTrue("反击需有内部 CD", (static.internalCooldownMs ?: 0) > 0)
    }

    @Test fun e18_armletSelfDamageIsSelfDamageAndCannotKill() {
        val self = EquipmentCatalog.mains.single { it.id == "armlet_of_mordiggian" }
            .effects.single { it.id == "armlet.selfDamage" }
        assertEquals(AttackClass.SELF_DAMAGE, self.attackClass)
        assertTrue(self.flags.isSelfDamage)
        assertFalse(self.flags.canLifesteal)
    }

    @Test fun e19_forceStaffProtectionIsControlledTriggerNotActive() {
        val protection = EquipmentCatalog.mains.single { it.id == "force_staff" }
            .effects.single()
        assertEquals(EffectTrigger.ON_CONTROLLED, protection.trigger)
        assertEquals(EffectKind.ATTACK_PROTECTION, protection.kind)
        assertFalse("普攻保护不得被必中穿透", protection.flags.piercesAttackProtection)
    }

    @Test fun e20_bkbUsesBasicDispelAndImmunity() {
        val bkb = EquipmentCatalog.mains.single { it.id == "black_king_bar" }
        val dispel = bkb.effects.single { it.kind == EffectKind.DISPEL }
        assertEquals("BASIC", (dispel.params["level"] as EffectValue.Choice).value)
        assertTrue(bkb.effects.any { it.kind == EffectKind.IMMUNITY })
    }

    // ---------------- 待审与尺寸 ----------------

    @Test fun e21_poisonEquipmentParametersWereApproved() {
        val pending = EquipmentCatalog.pendingReview.map { it.id }.toSet()
        assertFalse("巫师之刃已批准", "witch_blade" in pending)
        assertFalse("圣斧已批准", "rod_of_atos" in pending)
        assertFalse("怪蛇已批准", "viper_bite" in pending)
    }

    @Test fun e22_allItemsWithinSizeLimit() {
        for (eq in EquipmentCatalog.all) {
            assertTrue("${eq.id} 尺寸超限", eq.width <= 6 && eq.height <= 4)
        }
    }

    @Test fun e23_missingComponentsFromDesignDocArePresent() {
        val required = setOf(
            "crown", "diadem", "belt_of_strength", "band_of_elvenskin", "robe_of_magi",
            "vitality_booster", "energy_booster", "point_booster", "perseverance"
        )
        val ids = EquipmentCatalog.all.map { it.id }.toSet()
        for (id in required) assertTrue("缺组件 $id", id in ids)
    }

    @Test fun e24_componentEffectsAreRegisteredSeparately() {
        assertTrue(EquipmentCatalog.componentEffects.containsKey("cloak"))
        assertTrue(EquipmentCatalog.componentEffects.containsKey("talisman_of_evasion"))
        assertTrue(EquipmentCatalog.componentEffects.containsKey("morbid_mask"))
    }

    // ---------------- 辅助 ----------------

    @Test fun e25_fullEquipmentCatalogPassesValidator() {
        val result = ContentValidator.validate(
            CardCatalog.all, EquipmentCatalog.all, CardCatalogTest.EXISTING_ITEM_IDS
        )
        val equipmentIssues = result.issues.filter { issue ->
            EquipmentCatalog.all.any { it.id == issue.source.substringBefore("/") }
        }
        assertTrue("装备表问题: $equipmentIssues", equipmentIssues.isEmpty())
    }
}
