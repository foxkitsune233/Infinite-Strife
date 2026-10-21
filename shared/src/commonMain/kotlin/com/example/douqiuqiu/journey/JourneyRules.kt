package com.example.douqiuqiu.journey

import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.combat.CombatMath
import com.example.douqiuqiu.status.CombatAction
import com.example.douqiuqiu.cards.SynergyCard
import com.example.douqiuqiu.cards.SynergyCounter
import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.cards.CardBattleState
import com.example.douqiuqiu.cards.CombatCard
import com.example.douqiuqiu.content.EffectKind
import com.example.douqiuqiu.equipment.*
import kotlin.math.min

class JourneyRules(val inventory: InventoryRules) {
    private val defs get() = inventory.definitions
    private fun loot(s: InventoryState) = defs.values.filter { it.minimumDropClears <= s.progress.clears }.map { it.id }
    fun syncLoadout(s: InventoryState): InventoryState {
        val r = s.progress.run ?: return s
        if (r.stage == JourneyStage.BATTLE) return s
        val max = fighter(s, r.heroId).maxHp
        return s.copy(progress = s.progress.copy(run = r.copy(hp = CombatMath.rescaleHealth(r.hp, r.maxHp, max), maxHp = max)))
    }
    fun ensureBatch(state: InventoryState): InventoryState = ChestFactory(inventory).batch(state)

    fun appraise(s: InventoryState, id: String): InventoryResult {
        fun fail(e: String) = InventoryResult(s, e)
        if (s.phase != InventoryPhase.HOME || s.progress.run != null) return fail("挑战中不能鉴定")
        val box = s.progress.chests.find { it.id == id } ?: return fail("宝箱不存在")
        val fee = EconomyBalance.appraisalFee(box.appraisals)
        if (s.gold < fee) return fail("金币不足")
        val unknown = box.items.indices.filter { box.items[it].reveal < 2 }.toMutableList()
        if (unknown.isEmpty()) return fail("已全部公开")
        var seed = s.progress.seed
        fun rand(n: Int): Int { seed = FirstRunContent.next(seed); return (seed % n).toInt() }
        val items = box.items.toMutableList()
        var extra = 0
        repeat(min(unknown.size, 1 + rand(3))) {
            val index = unknown.removeAt(rand(unknown.size))
            val old = items[index]
            val def = defs.getValue(old.definitionId)
            val reveal = if (old.reveal == 1 || rand(100) < 30) 2 else 1
            if (old.reveal == 0) extra += when (def.quality) { "金" -> 120; "紫" -> 60; "蓝" -> 25; else -> 10 }
            if (reveal == 2) extra += (def.price * .4 * 1.1).toInt()
            items[index] = old.copy(reveal = reveal)
        }
        val changed = box.copy(items = items, ticket = box.ticket + extra, appraisals = box.appraisals + 1)
        return InventoryResult(s.copy(gold = s.gold - fee, progress = s.progress.copy(seed = seed,
            chests = s.progress.chests.map { if (it.id == id) changed else it })))
    }

    /** Creates a genuinely new fixed-content box without touching the other offers. */
    fun replaceChest(s: InventoryState, id: String, rescue: Boolean = false): InventoryResult = ChestFactory(inventory).replace(s, id, rescue)

    fun fighter(state: InventoryState, heroId: String): Fighter {
        val hero = FirstRunContent.hero(heroId)
        // 0.1.6 英雄等级：局外提升的永久属性在此一次性写入，战斗内不再读等级（回放可复现）。
        val level = com.example.douqiuqiu.hero.HeroLevelRules.level(state, heroId)
        val equipped = state.items.filter { it.place.container == Container.EQUIPMENT && it.place.x < 6 }.map { defs.getValue(it.definitionId).stats }
        val cards = state.items.filter { it.place.container == Container.SKILLS }
        val gear = EquipmentBattleState(items = state.items.filter { it.place.container == Container.EQUIPMENT && it.place.x < 6 && it.definitionId in EquipmentCombat.definitions }
            .map { EquippedGear(it.instanceId, it.definitionId) }, started = false)
        val combatCards = cards.mapNotNull {
            if (it.definitionId in CardCombat.definitions) CombatCard(it.instanceId, it.definitionId, it.star) else null
        }
        val cardState = CardBattleState(combatCards, started = combatCards.isEmpty())
        fun stat(name: String) = CardCombat.passive(cardState, EffectKind.STAT_BONUS, name) + EquipmentCombat.passive(gear, EffectKind.STAT_BONUS, name)
        val str = hero.strength + equipped.sumOf { it.strength } + stat("strength")
        val agi = hero.agility + equipped.sumOf { it.agility } + stat("agility")
        val int = hero.intelligence + equipped.sumOf { it.intelligence } + stat("intelligence")
        val hp = (500 + 22 * str + equipped.sumOf { it.health } + stat("health")) *
            com.example.douqiuqiu.hero.HeroLevelRules.healthMultiplier(level)
        val mana = (75 + 12 * int + stat("manaFlat")) * (1 + equipped.sumOf { it.manaPercent } / 100)
        val main = when (heroId) { "abaddon" -> (str + agi + int) * .45; "skywrath", "necrophos" -> int; else -> agi }
        val fighter = Fighter(heroId, hp, hp, minOf(mana, 30.0 + CardCombat.initialManaBonus(cardState)), mana,
            (hero.baseAttack + main + equipped.sumOf { it.attack } + stat("attack")) *
                com.example.douqiuqiu.hero.HeroLevelRules.attackMultiplier(level),
            (100 + agi + equipped.sumOf { it.attackSpeed } + stat("attackSpeed")) * (1 + stat("attackSpeedPercent")),
            hero.bat, hero.baseArmor + agi / 6 + equipped.sumOf { it.armor } + stat("armor") +
                com.example.douqiuqiu.hero.HeroLevelRules.armorBonus(level),
            .25 + int * .001 + com.example.douqiuqiu.hero.HeroLevelRules.magicResistBonus(level) / 100,
            hero.regen + str * .1 + equipped.sumOf { it.healthRegen } + stat("healthRegen"),
            hero.manaRegen + int * .05 + equipped.sumOf { it.manaRegen } + CardCombat.passive(cardState, EffectKind.MANA_REGEN, "amount") + EquipmentCombat.passive(gear, EffectKind.MANA_REGEN, "amount"), cardState = cardState, equipment = gear)
        val shields = CardCombat.initialShields(cardState)
        return if (shields.entries.isEmpty()) fighter else FighterEffects.withShields(fighter, shields)
    }

    fun enter(s: InventoryState, chestId: String, heroId: String, carryGold: Int = 0): InventoryResult {
        if (s.phase != InventoryPhase.HOME || s.progress.run != null) return InventoryResult(s, "已有挑战")
        if (heroId !in s.progress.unlockedHeroes) return InventoryResult(s, "英雄尚未解锁")
        val box = s.progress.chests.find { it.id == chestId } ?: return InventoryResult(s, "宝箱不存在")
        val subsidized = EconomyBalance.subsidized(box) && s.progress.economy.vouchers > 0
        val price = box.ticket - if (subsidized) min(EconomyBalance.RELIEF_SUBSIDY, box.ticket) else 0
        if (carryGold < 0 || s.gold.toLong() < price.toLong() + carryGold) return InventoryResult(s, "金币不足以支付门票和携入资金")
        val fighter = fighter(s, heroId)
        val run = JourneyState("run-${box.id}", heroId, box, s.progress.seed, fighter.maxHp, fighter.maxHp)
        return InventoryResult(s.copy(gold = s.gold - price - carryGold, runGold = carryGold, phase = InventoryPhase.PREPARATION,
            progress = s.progress.copy(chests = emptyList(), run = run, lastEnding = "",
                economy = s.progress.economy.copy(vouchers = s.progress.economy.vouchers - if (subsidized) 1 else 0))))
    }

    fun startBattle(s: InventoryState): InventoryResult {
        val r = s.progress.run ?: return InventoryResult(s, "没有挑战")
        if (r.stage != JourneyStage.READY) return InventoryResult(s, "当前不能开战")
        val seed = FirstRunContent.next(r.rng)
        val build = EnemyBuilds.preview(r, defs)
        val enemyHero = FirstRunContent.hero(build.heroId)
        val prepared = fighter(s, r.heroId)
        val carried = if (r.equipmentCooldowns.lastPreparedBattle == 0 && r.round > 1 && r.equipmentCooldowns.remainingByGroup.isEmpty())
            r.equipmentCooldowns.copy(lastPreparedBattle = r.round - 1) else r.equipmentCooldowns
        val player = prepared.copy(hp = CombatMath.rescaleHealth(r.hp, r.maxHp, prepared.maxHp), equipment = prepared.equipment.copy(
            cooldowns = EquipmentCooldownTracker.prepareBattle(carried, r.round), manual = s.progress.manualItems.intersect(prepared.equipment.items.map { it.instanceId }.toSet())))
        val baseEnemy = fighter(build.inventory, enemyHero.id)
        val scale = ChallengeBalance.scale(r)
        val enemy = baseEnemy.copy(hp = baseEnemy.maxHp * scale.health, maxHp = baseEnemy.maxHp * scale.health,
            attack = baseEnemy.attack * scale.attack, speed = baseEnemy.speed + scale.attackSpeed,
            armor = baseEnemy.armor + scale.armor)
        val next = FirstRunContent.next(seed)
        val loot = loot(s)
        val school = com.example.douqiuqiu.content.School.byId(build.schoolId).label
        val fight = DuelState(player, enemy, next, log = listOf("${enemyHero.name} · $school · 第${r.round}战"))
        return InventoryResult(s.copy(phase = InventoryPhase.BATTLE, progress = s.progress.copy(run = r.copy(
            stage = JourneyStage.BATTLE, maxHp = player.maxHp, hp = player.hp, fight = fight,
            rng = next, equipmentCooldowns = player.equipment.cooldowns, pendingItem = loot[(next % loot.size).toInt()]))))
    }

    fun tick(s: InventoryState, onFrame: ((DuelFrame) -> Unit)? = null): InventoryState {
        val r = s.progress.run ?: return s
        if (r.stage != JourneyStage.BATTLE || r.fight == null) return s
        var current = s
        for (kind in listOf("faerie_fire", "cheese")) {
            val item = current.items.firstOrNull { it.definitionId == kind && it.place.container == Container.EQUIPMENT && it.place.x < 6 && it.instanceId !in current.progress.manualItems }
            val player = current.progress.run!!.fight!!.player
            if (item != null && player.hp / player.maxHp < if (kind == "faerie_fire") .25 else .20) {
                current = useConsumable(current, item.instanceId).state
            }
        }
        val active = current.progress.run!!
        val snap = active.fight!!
        val inherited = if (snap.player.equipment.cooldowns.lastPreparedBattle == 0 && active.equipmentCooldowns.lastPreparedBattle > 0)
            active.equipmentCooldowns else snap.player.equipment.cooldowns
        val frame = DuelEngine.stepWithCues(snap.copy(player = snap.player.copy(equipment = snap.player.equipment.copy(cooldowns = inherited,
            manual = current.progress.manualItems.intersect(snap.player.equipment.items.map { it.instanceId }.toSet())))))
        val fight = frame.state
        onFrame?.invoke(frame)
        val next = current.copy(progress = current.progress.copy(run = active.copy(fight = fight, hp = fight.player.hp, maxHp = fight.player.maxHp, rng = fight.rng, equipmentCooldowns = fight.player.equipment.cooldowns)))
        if (fight.won == false) return abandon(next).state
        if (fight.won == true) return battleLoot(next.copy(phase = InventoryPhase.PREPARATION,
            progress = next.progress.copy(run = next.progress.run!!.copy(stage = JourneyStage.REWARD))))
        return next
    }

    fun reward(s: InventoryState, choice: RewardChoice): InventoryResult {
        return InventoryResult(s, "战后三选一已取消，请整理掉落后继续")
    }

    /** Also upgrades an old saved REWARD node once; persisted nodeLoot prevents duplicate currency. */
    fun battleLoot(s: InventoryState): InventoryState {
        val r = s.progress.run ?: return s
        if (r.stage != JourneyStage.REWARD || r.nodeLoot != null) return s
        val (loot, random) = NodeLootRules.generate(s, defs, NodeLootSource.BATTLE, r.rng)
        val gold = minOf(EconomyBalance.goldReward(r.target.difficulty, r.round, r.rewardVersion),
            (Int.MAX_VALUE.toLong() - s.gold - s.runGold).coerceAtLeast(0).toInt())
        return s.copy(runGold = s.runGold + gold, progress = s.progress.copy(run = r.copy(rng = random, nodeLoot = loot.copy(goldAwarded = gold))))
    }

    fun finishLoot(s: InventoryState, discardRemaining: Boolean = false): InventoryResult {
        inventory.validate(s)?.let { return InventoryResult(s, it) }
        val r = s.progress.run ?: return InventoryResult(s, "没有挑战")
        val loot = r.nodeLoot ?: return InventoryResult(s, "节点掉落尚未生成")
        if (r.stage != JourneyStage.REWARD || s.phase != InventoryPhase.PREPARATION) return InventoryResult(s, "当前不能离开掉落节点")
        if (loot.inspectionMs < NodeLootRules.duration(loot, defs)) return InventoryResult(s, "请等待鉴定完成")
        if (loot.remaining.isNotEmpty() && !discardRemaining) return InventoryResult(s, "尚有未拾取物品，需要确认丢弃")
        val clean = r.copy(nodeLoot = null, fight = null)
        if (loot.source == NodeLootSource.EVENT) return InventoryResult(s.copy(progress = s.progress.copy(run = clean.copy(
            round = r.round + 1, stage = JourneyStage.READY, nodeEvent = null))))
        if (r.round == 5) return complete(s)
        val seed = FirstRunContent.next(r.rng)
        var updated = clean.copy(stage = if (r.round == 3) JourneyStage.REST else JourneyStage.EVENT,
            rng = seed, eventRoll = (seed % 100).toInt())
        if (updated.stage == JourneyStage.EVENT) {
            val node = JourneyEvents.generate(s, updated, defs)
            updated = updated.copy(nodeEvent = node,
                recentEventKinds = (updated.recentEventKinds + node.kind).takeLast(2))
        }
        return InventoryResult(s.copy(progress = s.progress.copy(run = updated)))
    }

    fun event(s: InventoryState, optionId: String, selection: com.example.douqiuqiu.event.EventItemSelection? = null): InventoryResult {
        val result = JourneyEvents.resolve(s, inventory, optionId, selection)
        if (!result.ok) return result
        if ((result.state.progress.run?.hp ?: 1.0) <= 0) return abandon(result.state)
        return InventoryResult(syncLoadout(result.state))
    }

    fun useEquipment(s: InventoryState, instanceId: String): InventoryResult {
        val r = s.progress.run ?: return InventoryResult(s, "没有挑战")
        val fight = r.fight ?: return InventoryResult(s, "当前不是战斗")
        if (r.stage != JourneyStage.BATTLE) return InventoryResult(s, "只能战斗时使用装备")
        val next = DuelEngine.useItem(fight, instanceId)
        if (next == fight) return InventoryResult(s, "装备未就绪或当前不能使用")
        return InventoryResult(s.copy(progress = s.progress.copy(run = r.copy(fight = next, rng = next.rng,
            hp = next.player.hp, maxHp = next.player.maxHp, equipmentCooldowns = next.player.equipment.cooldowns))))
    }

    fun event(s: InventoryState, search: Boolean): InventoryResult {
        val r = s.progress.run ?: return InventoryResult(s, "没有挑战")
        if (r.stage != JourneyStage.EVENT) return InventoryResult(s, "当前没有事件")
        if (r.nodeEvent != null) return InventoryResult(s, "请按当前事件的具体选项结算")
        var next = s
        var hp = r.hp
        if (search) {
            if (r.eventRoll < 70) {
                val (drops, seed) = NodeLootRules.generate(s, defs, NodeLootSource.EVENT, r.rng, listOf(r.pendingItem))
                return InventoryResult(s.copy(progress = s.progress.copy(run = r.copy(stage = JourneyStage.REWARD,
                    fight = null, nodeLoot = drops, rng = seed))))
            } else hp -= r.maxHp * .1
        }
        if (hp <= 0) return abandon(next)
        return InventoryResult(next.copy(progress = next.progress.copy(run = r.copy(hp = hp, round = r.round + 1, stage = JourneyStage.READY))))
    }

    fun rest(s: InventoryState): InventoryResult {
        val r = s.progress.run ?: return InventoryResult(s, "没有挑战")
        if (r.stage != JourneyStage.REST) return InventoryResult(s, "不是休息节点")
        return InventoryResult(s.copy(progress = s.progress.copy(run = r.copy(round = 4, stage = JourneyStage.READY, hp = min(r.maxHp, r.hp + r.maxHp * .35)))))
    }
    fun useConsumable(s: InventoryState, instanceId: String): InventoryResult {
        val r = s.progress.run ?: return InventoryResult(s, "没有挑战，无需恢复")
        val item = s.items.singleOrNull { it.instanceId == instanceId } ?: return InventoryResult(s, "物品不存在")
        if (item.place.container == Container.WAREHOUSE) return InventoryResult(s, "挑战中不能使用仓库物资")
        if (item.definitionId !in setOf("salve", "tango", "faerie_fire", "cheese")) return InventoryResult(s, "此物品不能直接使用")
        val fighting = r.stage == JourneyStage.BATTLE
        if (fighting && (item.definitionId !in setOf("faerie_fire", "cheese") || item.place.container != Container.EQUIPMENT || item.place.x >= 6))
            return InventoryResult(s, "战斗中只能使用生效装备栏的仙灵之火或奶酪")
        val actor = if (fighting) r.fight!!.player else fighter(s, r.heroId).let { it.copy(hp = CombatMath.rescaleHealth(r.hp, r.maxHp, it.maxHp)) }
        if (fighting && !FighterEffects.permission(actor, CombatAction.CONSUMABLE)) return InventoryResult(s, "受控状态下不能使用此物品")
        if (actor.hp <= 0) return InventoryResult(s, "死亡后不能使用恢复品")
        val amount = when (item.definitionId) { "salve" -> 180.0; "tango" -> 70.0; "faerie_fire" -> 100.0; else -> actor.maxHp * .4 }
        val healed = min(actor.maxHp, actor.hp + amount * EquipmentCombat.healingMultiplier(actor))
        val updated = actor.copy(hp = healed,
            mana = if (item.definitionId == "cheese") min(actor.maxMana, actor.mana + actor.maxMana * .3 * if (actor.ghost > 0) 1.5 else 1.0) else actor.mana,
            attack = actor.attack - inventory.definitions.getValue(item.definitionId).stats.attack)
        val items = s.items.mapNotNull { if (it.instanceId != instanceId) it else if (it.count > 1) it.copy(count = it.count - 1) else null }
        val fight = if (fighting) r.fight!!.copy(player = com.example.douqiuqiu.cards.CardEffectRuntime.recordHealing(updated, healed - actor.hp), log = (r.fight.log + "使用${inventory.definitions.getValue(item.definitionId).name}").takeLast(LOG_CAP)) else r.fight
        return InventoryResult(s.copy(items = items, progress = s.progress.copy(run = r.copy(hp = healed, maxHp = actor.maxHp, fight = fight))))
    }
    fun extract(s: InventoryState): InventoryResult {
        if (s.progress.run?.stage != JourneyStage.REST) return InventoryResult(s, "只能在第三战后的节点撤离")
        return finish(s, true, "成功撤离，物资和本局金币已入库")
    }
    fun abandon(s: InventoryState): InventoryResult {
        if (s.progress.run == null) return InventoryResult(s, "没有挑战")
        return finish(s.copy(phase = InventoryPhase.PREPARATION), false, "挑战失败，仅安全箱物品带回")
    }
    private fun complete(s: InventoryState): InventoryResult {
        val r = s.progress.run!!
        var next = s
        r.target.items.forEachIndexed { i, item ->
            next = next.copy(items = next.items + StoredItem("${r.id}:chest:$i", item.definitionId, ItemPlace(Container.WAREHOUSE), bound = defs.getValue(item.definitionId).boundOnAcquire))
        }
        return finish(next, true, "全部通关，目标宝箱和本局物资已入库", cleared = true)
    }
    private fun finish(s: InventoryState, survived: Boolean, text: String, cleared: Boolean = false): InventoryResult {
        val result = inventory.settle(s, survived)
        if (!result.ok) return result
        val pity = MaterialProgression.record(s, result.state, defs, cleared)
        return InventoryResult(result.state.copy(progress = s.progress.copy(run = null, seed = FirstRunContent.next(s.progress.run!!.rng), materialPity = pity,
            clears = s.progress.clears + if (cleared) 1 else 0, extracted = s.progress.extracted || survived, lastEnding = text)))
    }
    private fun grant(s: InventoryState, defId: String, id: String, direct: Boolean): InventoryResult {
        if (direct) return InventoryResult(s.copy(items = s.items + StoredItem(id, defId, ItemPlace(Container.WAREHOUSE), bound = defs.getValue(defId).boundOnAcquire)))
        val loose = StoredItem(id, defId, ItemPlace(Container.BACKPACK), bound = defs.getValue(defId).boundOnAcquire)
        for (y in 0 until s.backpackHeight) for (x in 0 until s.backpackWidth) for (rotated in listOf(false, true)) {
            val candidate = s.copy(items = s.items + loose.copy(place = ItemPlace(Container.BACKPACK, x, y, rotated)))
            if (inventory.validate(candidate) == null) return InventoryResult(candidate)
        }
        return InventoryResult(s, "背包放不下，请先整理，或选择其他奖励/放弃搜索")
    }
}
