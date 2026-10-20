package com.example.douqiuqiu.journey

import com.example.douqiuqiu.inventory.ItemDefinition

object JourneyValidation {
    fun check(p: CampaignProgress, defs: Map<String, ItemDefinition>): String? {
        if (p.lockedItems.size > 10000 || p.lockedItems.any { it.isBlank() || it.length > 128 }) return "物品锁定资料无效"
        val invalidPity = p.materialPity.counters.any { (category, counter) ->
            val threshold = MaterialProgression.policy.thresholds[category]
            threshold == null || counter.misses !in 0..threshold || counter.pending != (counter.misses == threshold)
        }
        if (invalidPity) return "材料保底资料无效"
        val e = p.economy
        if (!p.unlockedHeroes.containsAll(setOf("juggernaut", "abaddon")) || p.unlockedHeroes.any { id -> FirstRunContent.heroes.none { it.id == id } }) return "英雄解锁资料无效"
        p.shop?.let { shop ->
            if (shop.revision <= 0 || shop.nextSeed !in 1..2147483646 || shop.offers.size > 1000 ||
                shop.offers.map { it.definitionId }.distinct().size != shop.offers.size ||
                shop.offers.count { it.shelf == com.example.douqiuqiu.shop.ShopShelf.ROTATING } > 6 ||
                shop.offers.count { it.shelf == com.example.douqiuqiu.shop.ShopShelf.RARE } > 1 ||
                shop.offers.any { it.definitionId !in defs || it.price < 0 || it.stock !in 1..100 } ||
                shop.purchases.any { (id, n) -> id !in defs || n !in 0..100 }) return "商店货架资料无效"
            if (p.shopStock.any { (id, n) -> shop.offers.none { it.definitionId == id && n <= it.stock } }) return "货架库存资料无效"
        }
        if (e.refreshCharges !in 0..3 || e.reliefCharges !in 0..2 || e.refreshAnchorMs < 0 || e.reliefAnchorMs < 0 ||
            e.reliefClaims < 0 || e.vouchers !in 0..e.reliefClaims) return "补给次数资料无效"
        fun validBox(c: TargetChest): Boolean {
            if (c.id.isBlank() || c.ticket !in 0..100000000 || c.appraisals !in 0..100 || c.difficulty !in ChallengeBalance.difficulties || c.items.size !in 1..36) return false
            if (c.guaranteedCategories.size > 1 || c.guaranteedCategories.any { category ->
                category !in MaterialProgression.policy.thresholds || MaterialProgression.theme(category) != c.theme ||
                    c.items.none { MaterialProgression.category(it.definitionId) == category }
            }) return false
            val occupied = mutableSetOf<Pair<Int, Int>>()
            return c.items.all { i ->
                val d = defs[i.definitionId] ?: return@all false
                i.reveal in 0..2 && i.x in 0..(6 - d.width) && i.y in 0..(6 - d.height) &&
                    (i.x until i.x + d.width).all { x -> (i.y until i.y + d.height).all { y -> occupied.add(x to y) } }
            }
        }
        fun validFighter(f: Fighter): Boolean = FirstRunContent.heroes.any { it.id == f.heroId } &&
            listOf(f.hp, f.maxHp, f.mana, f.maxMana, f.attack, f.speed, f.bat, f.armor, f.magicResist, f.regen, f.manaRegen,
                f.shield, f.magicShield, f.skyShield, f.attackIn, f.ultIn, f.invulnerable, f.ghost, f.slashIn,
                f.criticalReady, f.broken, f.slowTime, f.slow, f.skinIn, f.flareIn, f.skinSlowTime, f.physicalShield).all { it.isFinite() } &&
            f.hp in 0.0..f.maxHp && f.maxHp > 0 && f.mana in 0.0..f.maxMana && f.maxMana > 0 && f.bat > 0 &&
            listOf(f.shield, f.physicalShield, f.magicShield, f.skyShield).all { it >= 0 } &&
            runCatching { com.example.douqiuqiu.combat.ShieldLedger.total(FighterEffects.shields(f));
                FighterEffects.withShields(f, FighterEffects.shields(f)).let { it.shield == f.shield && it.physicalShield == f.physicalShield && it.magicShield == f.magicShield && it.skyShield == f.skyShield }
            }.getOrDefault(false) && runCatching { com.example.douqiuqiu.cards.CardCombat.validate(f.cardState); true }.getOrDefault(false) &&
            f.flareLeft in 0..8 && f.poisons.size <= 1000 && f.poisons.all { it.source.isNotBlank() && it.dps.isFinite() && it.dps >= 0 && it.remaining.isFinite() && it.remaining > 0 && it.remaining <= 3600 }
        if (p.seed !in 1..2147483646 || p.batch < 0 || p.clears < 0 || p.shopCycle < 0 || p.shopSeen < 0 ||
            p.shopStock.any { (id, n) -> id !in defs || n !in 0..100 } || p.chests.size > 8 || p.chests.map { it.id }.distinct().size != p.chests.size || p.chests.any { !validBox(it) }) return "挑战资料无效"
        p.run?.let { r ->
            r.nodeLoot?.let { loot ->
                if (r.stage != JourneyStage.REWARD || loot.id != "${r.id}:${loot.source.name.lowercase()}:${r.round}" ||
                    loot.items.size !in 1..10 || loot.items.map { it.instanceId }.toSet().size != loot.items.size ||
                    !loot.items.map { it.instanceId }.toSet().containsAll(loot.picked) || loot.goldAwarded < 0 ||
                    loot.source == NodeLootSource.EVENT && r.round >= 5 ||
                    loot.items.withIndex().any { (i, item) ->
                        val d = defs[item.definitionId]
                        d == null || item.instanceId != "${loot.id}:drop:$i" || item.star !in 1..3 ||
                            d.kind != com.example.douqiuqiu.inventory.ItemKind.CARD && item.star != 1 || item.count !in 1..(d?.maxStack ?: 0) ||
                            item.bound != d.boundOnAcquire || item.place != com.example.douqiuqiu.inventory.ItemPlace(com.example.douqiuqiu.inventory.Container.WAREHOUSE)
                    }) return "节点掉落资料无效"
                if (loot.inspectionMs !in 0..NodeLootRules.duration(loot, defs) ||
                    loot.picked.any { !NodeLootRules.revealed(loot, it, defs) }) return "节点鉴定进度无效"
                if (loot.putDown.size > 64 || (loot.items + loot.putDown).map { it.instanceId }.distinct().size != loot.items.size + loot.putDown.size ||
                    loot.putDown.any { item ->
                        val d = defs[item.definitionId]
                        d == null || item.instanceId.isBlank() || item.instanceId.length > 128 || item.star !in 1..3 ||
                            d.kind != com.example.douqiuqiu.inventory.ItemKind.CARD && item.star != 1 || item.count !in 1..(d?.maxStack ?: 0) ||
                            item.place != com.example.douqiuqiu.inventory.ItemPlace(com.example.douqiuqiu.inventory.Container.WAREHOUSE)
                    }) return "放下的物品资料无效"
            }
            if (r.equipmentCooldowns.lastPreparedBattle !in 0..5 || r.equipmentCooldowns.remainingByGroup.size > 100 ||
                r.equipmentCooldowns.remainingByGroup.any { (key, ms) -> key.isBlank() || key.length > 256 || ms !in 0..3600000 }) return "跨战装备冷却无效"
            r.nodeEvent?.let { node ->
                if (r.stage != JourneyStage.EVENT || node.nodeId != "${r.id}:event:${r.round}" || node.priceGold !in 0..100000 ||
                    node.requiredItemDefinitionId.isNotEmpty() && node.requiredItemDefinitionId !in defs ||
                    node.resolvedOptionId != null && com.example.douqiuqiu.event.EventCatalog.definition(node.kind).options.none { it.id == node.resolvedOptionId }) return "事件快照无效"
                if (node.outcomes.size > 3 || node.outcomes.any { (id, result) ->
                    com.example.douqiuqiu.event.EventCatalog.definition(node.kind).options.none { it.id == id } ||
                        result.nodeId != node.nodeId || result.optionId != id || result.currency != com.example.douqiuqiu.event.EventCurrency.RUN_GOLD ||
                        result.goldDelta !in -100000..0 || result.hpDeltaPercentOfMax !in -100.0..100.0 ||
                        result.grantDefinitionIds.size > 10 || result.grantDefinitionIds.any { it !in defs } || result.consumeInstanceIds.isNotEmpty() ||
                        result.applyNormalPoisonLayers !in 0..10 || result.poisonDamagePerTick !in 0.0..100.0
                }) return "事件结果快照无效"
            }
            if (r.target.difficulty >= 3 && r.rewardVersion < 4) return "高阶挑战版本无效"
            if (r.rewardVersion !in 1..EconomyBalance.VERSION || r.id.isBlank() || FirstRunContent.heroes.none { it.id == r.heroId } || !validBox(r.target) ||
                r.rng !in 1..2147483646 || r.round !in 1..5 || !r.maxHp.isFinite() || !r.hp.isFinite() || r.maxHp <= 0 || r.hp !in 0.0..r.maxHp ||
                (r.pendingItem.isNotEmpty() && r.pendingItem !in defs) || r.eventRoll !in 0..99 || p.chests.isNotEmpty()) return "本局资料无效"
            if (r.stage == JourneyStage.BATTLE && r.fight == null) return "战斗快照缺失"
            r.fight?.let {
                if (it.rng !in 1..2147483646 || it.tick !in 0..600 || !validFighter(it.player) || !validFighter(it.enemy)) return "战斗快照无效"
                if (!runCatching {
                    com.example.douqiuqiu.cards.CardCombat.validateAt(it.player.cardState, it.enemy.cardState, it.tick, 1)
                    com.example.douqiuqiu.cards.CardCombat.validateAt(it.enemy.cardState, it.player.cardState, it.tick, 0)
                    com.example.douqiuqiu.combat.PoisonCombat.validate(it.player, it.tick)
                    com.example.douqiuqiu.combat.PoisonCombat.validate(it.enemy, it.tick)
                    com.example.douqiuqiu.equipment.EquipmentCombat.validate(it.player.equipment)
                    com.example.douqiuqiu.equipment.EquipmentCombat.validate(it.enemy.equipment)
                    com.example.douqiuqiu.status.TimedControls.validate(it.player.timedControls, it.tick)
                    com.example.douqiuqiu.status.TimedControls.validate(it.enemy.timedControls, it.tick)
                    com.example.douqiuqiu.status.TimedControls.validateSources(it.player, it.enemy, 0)
                    com.example.douqiuqiu.status.TimedControls.validateSources(it.enemy, it.player, 1)
                    require(it.player.heroId == "juggernaut" || "hero:blade-dance" !in it.player.cardState.criticalFailures)
                    require(it.enemy.heroId == "juggernaut" || "hero:blade-dance" !in it.enemy.cardState.criticalFailures)
                }.isSuccess) return "卡牌运行快照无效"
            }
        }
        return null
    }
}
