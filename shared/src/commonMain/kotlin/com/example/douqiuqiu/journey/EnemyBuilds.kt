package com.example.douqiuqiu.journey

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.content.CardDefinition
import com.example.douqiuqiu.content.QualityTier
import com.example.douqiuqiu.content.School
import com.example.douqiuqiu.inventory.*

data class EnemyBuild(val heroId: String, val schoolId: String, val inventory: InventoryState)

/** Pure preview: no global randomness and no consumption of the journey's persisted RNG. */
object EnemyBuilds {
    private data class Budget(val cards: Int, val twoStars: Int, val goldCards: Int, val gear: Int, val gearValue: Int)

    // Difficulty rows, then rounds 1..5. First-pass balance values, documented alongside H09.
    private val budgets = listOf(
        listOf(Budget(1, 0, 0, 1, 60), Budget(2, 0, 0, 1, 100), Budget(3, 0, 0, 2, 230), Budget(4, 0, 1, 2, 360), Budget(4, 1, 1, 3, 540)),
        listOf(Budget(2, 0, 0, 1, 80), Budget(3, 0, 0, 2, 160), Budget(4, 0, 0, 2, 360), Budget(4, 1, 1, 3, 460), Budget(5, 2, 2, 3, 540)),
        listOf(Budget(2, 0, 0, 1, 80), Budget(3, 0, 0, 2, 230), Budget(4, 1, 1, 3, 410), Budget(5, 2, 1, 3, 540), Budget(6, 3, 2, 4, 720))
    )

    // Base items plus the integrated H05/H06 catalog, constrained by round and difficulty.
    private val liveGear = setOf("iron_branch", "gauntlets", "slippers", "mantle", "circlet",
        "broadsword", "bracer", "wraith_band", "null_talisman")

    private val heroSchools = mapOf(
        "juggernaut" to listOf("basic", "crit", "ultimate"),
        "phantom_assassin" to listOf("crit", "basic"),
        "skywrath" to listOf("ultimate", "shield"),
        "abaddon" to listOf("shield", "restore", "basic"),
        "necrophos" to listOf("restore", "ultimate", "poison"),
        "viper" to listOf("poison", "basic"),
        "anti_mage" to listOf("basic", "crit", "ultimate"),
        "axe" to listOf("basic", "shield", "ultimate"),
        "faceless_void" to listOf("basic", "ultimate", "crit"),
        "legion_commander" to listOf("basic", "crit", "shield")
    )

    fun preview(run: JourneyState, definitions: Map<String, ItemDefinition>): EnemyBuild {
        val round = run.round.coerceIn(1, 5)
        val difficulty = run.target.difficulty.also { require(it in ChallengeBalance.difficulties) }
        val hard = ChallengeBalance.enhanced(run)
        val budget = if (hard) ChallengeBalance.budget(run).let {
            Budget(it.cards, it.twoStars, it.goldCards, it.gear, it.gearValue)
        } else budgets[difficulty][round - 1]
        var seed = FirstRunContent.next(run.rng)
        fun pick(size: Int): Int {
            seed = FirstRunContent.next(seed)
            return (seed % size).toInt()
        }
        val heroes = FirstRunContent.heroes.filter { it.id != run.heroId }.sortedBy { it.id }
        val heroId = heroes[(seed % heroes.size).toInt()].id
        // Salt the local stream without changing the persisted combat/reward stream.
        repeat(round + difficulty * 5) { seed = FirstRunContent.next(seed) }
        val eligible = CardCombat.enabledCards.filter {
            definitions[it.id]?.kind == ItemKind.CARD &&
            (round > 1 || it.quality == QualityTier.BLUE) &&
                (budget.goldCards > 0 || it.quality != QualityTier.GOLD)
        }.sortedBy { it.id }
        val schools = School.entries.map { it.id }.filter { id -> eligible.any { id in it.tags } }.sorted()
        val preferred = heroSchools.getValue(heroId).filter { it in schools }
        val schoolPool = preferred.ifEmpty { schools }
        val schoolId = if (schoolPool.isEmpty()) heroSchools.getValue(heroId).first() else schoolPool[pick(schoolPool.size)]
        val chosen = mutableListOf<CardDefinition>()
        fun addCards(pool: List<CardDefinition>, targetSize: Int) {
            while (chosen.size < targetSize) {
                val remaining = pool.filter { card -> chosen.none { it.id == card.id } &&
                    (card.quality != QualityTier.GOLD || chosen.count { it.quality == QualityTier.GOLD } < budget.goldCards) }
                if (remaining.isEmpty()) break
                chosen += remaining[pick(remaining.size)]
            }
        }
        // Establish a major school before adding supplementary cards on later rounds.
        addCards(eligible.filter { schoolId in it.tags }, minOf(4, budget.cards))
        addCards(eligible.filter { schoolId in it.tags }, budget.cards)
        addCards(eligible, budget.cards)
        val items = chosen.mapIndexed { slot, card ->
            StoredItem("enemy-card-$slot", card.id, ItemPlace(Container.SKILLS, slot),
                star = if (slot < budget.twoStars) 2 else 1)
        }.toMutableList()

        val allowed = liveGear + com.example.douqiuqiu.equipment.EquipmentCombat.definitions.keys
        val controlIds = if (run.rewardVersion >= 4) com.example.douqiuqiu.equipment.EquipmentCombat.definitions.values.filter { gear ->
            gear.effects.any { !it.requiresReview && it.kind == com.example.douqiuqiu.content.EffectKind.APPLY_CONTROL }
        }.map { it.id }.toSet() else com.example.douqiuqiu.content.ControlEquipmentContent.mains.map { it.id }.toSet()
        // Legacy runs retain their old control limits; v4 introduces earlier high-tier threats.
        val maxControls = if (run.rewardVersion >= 4) when {
            difficulty == 0 -> 0
            difficulty == 1 -> if (round >= 4) 1 else 0
            else -> (difficulty - 1 + (round - 1) / 2).coerceAtMost(3)
        } else when {
            difficulty == 0 || round <= 3 -> 0
            hard -> if (round < 5) 1 else 2
            difficulty == 1 -> 1
            else -> 2
        }
        val gearPool = definitions.values.filter { it.id in allowed && it.kind == ItemKind.EQUIPMENT &&
            (maxControls > 0 || it.id !in controlIds) &&
            (run.rewardVersion >= 4 && difficulty > 0 || round > 1 || it.quality == "白" || hard && it.quality == "蓝") &&
            (it.quality != "金" || run.rewardVersion >= 4 && difficulty > 0 || difficulty == 2 && (round == 5 || hard && round >= 3)) &&
            it.minimumShopClears <= if (run.rewardVersion >= 4 && difficulty > 0) maxOf(difficulty * 3, listOf(0, 1, 3, 6, 10)[round - 1]) else if (hard) listOf(0, 1, 3, 6, 10)[round - 1] else if (round < 3) 0 else if (round < 5) 1 else 2 }.sortedBy { it.id }.toMutableList()
        var remainingValue = if (hard) budget.gearValue else budget.gearValue * if (round < 3 || difficulty == 0) 1 else if (difficulty == 1) 4 else 7
        // The old 2160 ceiling made every new control item impossible on normal round 5.
        if (run.rewardVersion < 4 && difficulty == 1 && round == 5) remainingValue = maxOf(remainingValue, 3000)
        val activeQuota = ChallengeBalance.activeQuota(run)
        fun activeCount(entries: List<StoredItem>): Int = com.example.douqiuqiu.equipment.EquipmentCombat.activeItems(
            com.example.douqiuqiu.equipment.EquipmentBattleState(items = entries.filter { it.place.container == Container.EQUIPMENT }.map {
                com.example.douqiuqiu.equipment.EquippedGear(it.instanceId, it.definitionId)
            })).size
        repeat(budget.gear) { slot ->
            val missingActives = (activeQuota - activeCount(items)).coerceAtLeast(0)
            val spendingCap = if (hard && slot < budget.gear - 1) maxOf(1, remainingValue * 55 / 100) else remainingValue
            val affordable = gearPool.filter { it.price <= spendingCap &&
                (it.id !in controlIds || items.count { entry -> entry.definitionId in controlIds } < maxControls) }
            if (affordable.isNotEmpty()) {
                // Higher rounds spend their budget on real upgrades instead of always
                // taking a cheap stat band with a larger legacy affinity score.
                val upgrades = if (hard) affordable.filter { it.price >= affordable.maxOf { d -> d.price } * .60 } else affordable
                val bestAffinity = upgrades.maxOf { gearAffinity(it.id, schoolId) }
                val matching = upgrades.filter { gearAffinity(it.id, schoolId) == bestAffinity }
                val controls = affordable.filter { it.id in controlIds }
                // Reserve an equal share of the remaining budget for each required active.
                // Check the real runtime selector so shared/overridden actives do not count twice.
                val guaranteed = if (missingActives > 0) gearPool.filter { gear ->
                    gear.price <= remainingValue / missingActives &&
                    (gear.id !in controlIds || items.count { it.definitionId in controlIds } < maxControls) &&
                    activeCount(items + StoredItem("enemy-gear-$slot", gear.id, ItemPlace(Container.EQUIPMENT, slot))) > activeCount(items)
                } else emptyList()
                val candidate = if (guaranteed.isNotEmpty()) guaranteed else if (controls.isNotEmpty() && pick(100) < 25) controls else matching
                val gear = candidate[pick(candidate.size)]
                items += StoredItem("enemy-gear-$slot", gear.id, ItemPlace(Container.EQUIPMENT, slot))
                remainingValue -= gear.price
                gearPool.remove(gear)
            }
        }
        return EnemyBuild(heroId, schoolId, InventoryState(gold = 0,
            skillSlots = maxOf(4, chosen.size), phase = InventoryPhase.PREPARATION, items = items))
    }

    private fun gearAffinity(id: String, schoolId: String): Int = when (schoolId) {
        "basic", "crit", "poison" -> when (id) {
            "wraith_band", "broadsword" -> 3
            "slippers", "circlet" -> 2
            else -> 1
        }
        "ultimate" -> when (id) {
            "null_talisman" -> 3
            "mantle", "circlet" -> 2
            else -> 1
        }
        else -> when (id) {
            "bracer" -> 3
            "gauntlets", "circlet" -> 2
            else -> 1
        }
    }
}
