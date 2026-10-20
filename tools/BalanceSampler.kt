import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.loot.ValuableLootCatalog
import com.example.douqiuqiu.shop.ShopShelf
import java.io.File
import java.security.MessageDigest

/** External JVM tool, not an Android screen or a test. Calls the actual shared rules. */
object BalanceSampler {
    @JvmStatic fun main(args: Array<String>) {
        val mode = args.getOrElse(0) { "boxes" }
        require(mode in setOf("boxes", "runs")) { "mode: boxes or runs" }
        val samples = args.getOrElse(1) { "100" }.toInt().also { require(it in 1..10000) }
        val onlyCase = args.getOrNull(3)?.takeIf { it.isNotBlank() }
        onlyCase?.split('/')?.let { parts ->
            require(mode == "runs" && parts.size == 6 && FirstRunContent.heroes.any { it.id == parts[0] } &&
                parts[1] in School.IDS && parts[2] in setOf("four", "eight") && parts[3] in setOf("LEAVE_LOOT", "PICK_LOOT") &&
                parts[4].toIntOrNull() in ChallengeBalance.difficulties && parts[5].toIntOrNull() in 1..samples) { "Case format: hero/school/four-or-eight/LEAVE_LOOT-or-PICK_LOOT/difficulty/seed; seed must be <= Samples" }
        }
        val root = File(args.getOrElse(2) { "." }).canonicalFile
        val baseFile = File(root, "tools/balance-base.tsv")
        val defs = catalog(baseFile)
        val rules = InventoryRules(defs)
        val fingerprintFiles = listOf(baseFile, File(root, "tools/BalanceSampler.kt")) + File(root, "shared/src/commonMain/kotlin").walkTopDown().filter { it.extension == "kt" }.sortedBy { it.path }.toList()
        val digest = MessageDigest.getInstance("SHA-256")
        fingerprintFiles.forEach { digest.update(it.readBytes()) }
        val config = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }.take(16)
        val out = File(root, "docs/testing/results/sampling").apply { mkdirs() }
        val suffix = onlyCase?.replace('/', '-')?.let { "-$it" } ?: ""
        val file = File(out, "$mode-v${EconomyBalance.VERSION}-$config$suffix.csv")
        val partial = File(file.path + ".partial")
        require(!file.exists() && !partial.exists()) { "Output already exists; preserve/move the previous CSV or partial before sampling again: $file" }
        var context = "initializing"
        var completed = 0
        var records = 0
        val expected = if (onlyCase != null) 1 else if (mode == "boxes") 120 * ChallengeBalance.COUNT * samples else FirstRunContent.heroes.size * School.entries.size * 4 * ChallengeBalance.COUNT * samples
        val decisions = if (mode == "runs") File(file.path + ".events.csv").bufferedWriter() else null
        decisions?.appendLine("config,case,round,event,attempt,option,result,reason,fallback")
        try {
        partial.bufferedWriter().use { writer ->
            fun row(vararg fields: Any?) { writer.appendLine(fields.joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" }); records++ }
            if (mode == "boxes") {
                row("config", "seed", "clears", "quality", "difficulty", "theme", "columns", "rows", "ticket", "actualItems", "sellableValue", "mainItems", "materials", "collectibles", "maxItemPrice", "netIfClearNoRewards")
                val mains = EquipmentCatalog.mains.map { it.id }.toSet()
                for (clears in listOf(0, 1, 3, 6, 10)) for (quality in listOf("白", "蓝", "紫", "金")) for (difficulty in ChallengeBalance.difficulties) for (theme in ChestTheme.entries) for (seed in 1..samples) {
                    context = "boxes seed=$seed clears=$clears quality=$quality difficulty=$difficulty theme=$theme"
                    val s = InventoryState(progress = CampaignProgress(seed = seed.toLong(), clears = clears))
                    val box = ChestFactory(rules).sample(s, theme, difficulty, quality).second
                    val items = box.items.map { defs.getValue(it.definitionId) }
                    val sell = items.filterNot { it.boundOnAcquire }.sumOf { it.price.toLong() * 40 / 100 }
                    val grid = ChestLayout.display(box, defs)
                    row(config, seed, clears, quality, difficulty, theme, grid.columns, grid.rows, box.ticket, items.size, sell,
                        items.count { it.id in mains }, items.count { it.kind == ItemKind.MATERIAL }, items.count { it.kind == ItemKind.LOOT },
                        items.maxOfOrNull { it.price } ?: 0, sell - box.ticket)
                    completed++
                }
            } else {
                row("config", "seed", "hero", "school", "profile", "strategy", "difficulty", "round", "won", "ticks", "hpRatio", "controlTicks", "playerGearValue", "enemyGearValue", "walletDelta", "sellableDelta", "netAssetsDelta", "ending")
                val journey = JourneyRules(rules)
                fun sellable(s: InventoryState) = s.items.filterNot { it.bound }.sumOf { d -> defs.getValue(d.definitionId).price.toLong() * d.count * (if (d.star == 3) 9 else if (d.star == 2) 3 else 1) * 40 / 100 }
                for (hero in FirstRunContent.heroes) for (school in School.entries) for (profile in listOf("four", "eight")) for (strategy in listOf("LEAVE_LOOT", "PICK_LOOT")) for (difficulty in ChallengeBalance.difficulties) for (seed in 1..samples) {
                    if (onlyCase != null && onlyCase != "${hero.id}/${school.id}/$profile/$strategy/$difficulty/$seed") continue
                    val case = "runs seed=$seed hero=${hero.id} school=${school.id} profile=$profile strategy=$strategy difficulty=$difficulty"
                    context = case
                    val capacity = if (profile == "four") 4 else 8
                    val cards = CardCombat.enabledCards.sortedWith(compareByDescending<CardDefinition> { school.id in it.tags }.thenBy { it.id }).take(capacity)
                    // Equal neutral gear across schools. This is a reference build, not a new-player inventory.
                    val gear = if (profile == "four") listOf("iron_branch", "iron_branch", "gauntlets", "slippers", "mantle")
                        else listOf("broadsword", "bracer", "wraith_band", "null_talisman", "force_staff", "black_king_bar")
                    val items = gear.mapIndexed { i, id -> StoredItem("sample:g$i", id, ItemPlace(Container.EQUIPMENT, i)) } +
                        cards.mapIndexed { i, card -> StoredItem("sample:c$i", card.id, ItemPlace(Container.SKILLS, i), star = if (profile == "four") 1 else 2) }
                    var s = InventoryState(gold = 100000, skillSlots = capacity, items = items,
                        progress = CampaignProgress(seed = seed.toLong(), clears = if (profile == "four") 0 else 6,
                            unlockedHeroes = FirstRunContent.heroes.map { it.id }.toSet()))
                    val quality = listOf("白", "紫", "金", "金", "金", "金")[difficulty]
                    val box = ChestFactory(rules).sample(s, ChestTheme.SUPPLIES, difficulty, quality).second
                    s = s.copy(progress = s.progress.copy(chests = listOf(box)))
                    val before = s
                    val entered = journey.enter(s, box.id, hero.id)
                    require(entered.ok) { entered.error.orEmpty() }; s = entered.state
                    val rows = mutableListOf<List<Any?>>()
                    val enemyValues = mutableMapOf<Int, Int>()
                    var guard = 0
                    while (s.progress.run != null && guard++ < 4000) {
                        val run = s.progress.run!!
                        context = "$case stage=${run.stage} round=${run.round} tick=${run.fight?.tick} event=${run.nodeEvent?.kind}"
                        s = when (run.stage) {
                            JourneyStage.READY -> {
                                enemyValues[run.round] = EnemyBuilds.preview(run, defs).inventory.items.filter { it.place.container == Container.EQUIPMENT }.sumOf { defs.getValue(it.definitionId).price }
                                journey.startBattle(s).also { require(it.ok) { it.error.orEmpty() } }.state
                            }
                            JourneyStage.BATTLE -> {
                                val start = run.fight!!
                                var frame = start
                                var controlled = 0
                                var steps = 0
                                var next = s
                                do {
                                    require(steps++ <= 600) { "Battle stalled: seed=$seed hero=${hero.id} round=${run.round}" }
                                    next = journey.tick(next) { result -> frame = result.state }
                                    val c = com.example.douqiuqiu.status.TimedControls.current(frame.player)
                                    if (c.stunned || c.hexed || c.silenced || c.disarmed) controlled++
                                } while (next.progress.run?.stage == JourneyStage.BATTLE)
                                rows += listOf(config, seed, hero.id, school.id, profile, strategy, difficulty, run.round,
                                    frame.won, frame.tick, frame.player.hp / frame.player.maxHp, controlled,
                                    gear.sumOf { defs.getValue(it).price }, enemyValues.getValue(run.round))
                                next
                            }
                            JourneyStage.REWARD -> {
                                // Three-choice rewards no longer exist; use explicit leave/pick policies.
                                var inspected = journey.battleLoot(s)
                                while (inspected.progress.run!!.nodeLoot!!.let { it.inspectionMs < NodeLootRules.duration(it, defs) }) {
                                    inspected = NodeLootRules.inspect(inspected, rules, 500).also { require(it.ok) { it.error.orEmpty() } }.state
                                }
                                if (strategy == "PICK_LOOT") {
                                    for (item in inspected.progress.run!!.nodeLoot!!.remaining.sortedByDescending { defs.getValue(it.definitionId).price }) {
                                        var result: InventoryResult? = null
                                        placement@ for (y in 0 until inspected.backpackHeight) for (x in 0 until inspected.backpackWidth) for (rotated in listOf(false, true)) {
                                            val attempt = NodeLootRules.collect(inspected, rules, item.instanceId, ItemPlace(Container.BACKPACK, x, y, rotated))
                                            if (attempt.ok) { result = attempt; break@placement }
                                        }
                                        if (result != null) inspected = result.state
                                    }
                                }
                                journey.finishLoot(inspected, discardRemaining = true).also { require(it.ok) { it.error.orEmpty() } }.state
                            }
                            JourneyStage.REST -> journey.rest(s).state
                            JourneyStage.EVENT -> {
                                val options = JourneyEvents.views(s, rules).filter { it.enabled && !it.option.requiresExplicitItemChoice }
                                require(options.isNotEmpty()) { "No non-interactive event option: $context" }
                                val option = options.firstOrNull { it.option.risk == com.example.douqiuqiu.event.EventRisk.NONE } ?: options.first()
                                // A full bag is an ordinary player decision, not a broken run.
                                // Try other free/no-risk options in catalog order (salvage, then leave).
                                // Never trade items, pay money, discard holdings or reroll the event here.
                                val alternatives = options.filter { it.option.id != option.option.id &&
                                    it.option.risk == com.example.douqiuqiu.event.EventRisk.NONE && it.option.cost == null }
                                var resolved: InventoryState? = null
                                for ((attempt, candidate) in (listOf(option) + alternatives).withIndex()) {
                                    context = "$case stage=EVENT round=${run.round} event=${run.nodeEvent?.kind} option=${candidate.option.id} beforeValidation=${rules.validate(s)}"
                                    val result = journey.event(s, candidate.option.id)
                                    decisions?.appendLine(csv(config, case, run.round, run.nodeEvent?.kind, attempt + 1,
                                        candidate.option.id, if (result.ok) "ACCEPTED" else "REJECTED", result.error.orEmpty(), attempt > 0))
                                    if (result.ok) { resolved = result.state; break }
                                    require(result.state == s) { "Rejected event mutated state: $context" }
                                    require(result.error == "背包放不下，请先整理再重试，或选择其他事件选项") {
                                        "Event transaction rejected: ${result.error}; $context"
                                    }
                                }
                                requireNotNull(resolved) { "No legal capacity fallback; $context" }
                            }
                        }
                    }
                    require(s.progress.run == null) { "Journey did not terminate" }
                    val wallet = s.gold - before.gold
                    val goods = sellable(s) - sellable(before)
                    rows.forEachIndexed { index, result ->
                        val ending = if (index == rows.lastIndex) listOf(wallet, goods, wallet + goods, s.progress.lastEnding) else listOf("", "", "", "")
                        row(*(result + ending).toTypedArray())
                    }
                    completed++
                }
            }
        }
        require(completed == expected) { "Incomplete matrix: $completed/$expected" }
        check(partial.renameTo(file)) { "Cannot finalize $partial" }
        File(file.path + ".status.txt").writeText("status=complete\nconfig=$config\ncases=$completed\nexpected=$expected\ndataRows=${records - 1}\n")
        } catch (e: Exception) {
            val evidence = File(file.path + ".failure.txt")
            evidence.writeText("status=failed\nconfig=$config\ncompleted=$completed\nexpected=$expected\ncontext=$context\n\n${e.stackTraceToString()}")
            throw IllegalStateException("Sampling failed; context=$context; evidence=$evidence", e)
        } finally {
            decisions?.close()
        }
        println("Saved $file (samples per matrix cell=$samples, config=$config)")
    }

    private fun csv(vararg fields: Any?) = fields.joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" }

    private fun catalog(file: File): Map<String, ItemDefinition> {
        val base = file.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }.map { line ->
            val v = line.split('\t')
            ItemDefinition(v[0], v[1], ItemKind.valueOf(v[2]), v[3].toInt(), v[4].toInt(), v[5], v[6].toInt(), v[7].toBoolean(),
                EquipmentStats(v[8].toDouble(), v[9].toDouble(), v[10].toDouble(), v[11].toDouble(), v[12].toDouble(),
                    v[13].toDouble(), v[14].toDouble(), v[15].toDouble(), v[16].toDouble(), v[17].toDouble()),
                v[18].toInt(), v[19].toInt(), ShopShelf.valueOf(v[20]), v[21].toInt(), v[22].toBoolean(), v[23].toInt())
        }
        val ids = base.map { it.id }.toSet()
        val cards = CardCombat.enabledCards.filter { it.id !in ids }.map {
            ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height, it.quality.label, price = it.price, shopStock = it.shopStock,
                shopShelf = ShopShelf.valueOf(it.shopShelf), minimumShopClears = it.minimumShopClears, minimumDropClears = it.minimumDropClears)
        }
        val gear = EquipmentCatalog.all.filter { it.id !in ids }.map {
            val scroll = it.category == EquipmentCategory.SCROLL
            val gate = if (scroll) EquipmentCatalog.all.filter { d -> d.recipe?.ingredients?.containsKey(it.id) == true }.minOfOrNull { d -> d.minimumShopClears } ?: it.minimumShopClears else it.minimumShopClears
            ItemDefinition(it.id, it.name, if (scroll) ItemKind.SCROLL else ItemKind.EQUIPMENT, it.width, it.height, it.quality.label,
                stats = it.stats ?: EquipmentStats(), price = it.price, shopStock = if (scroll) 1 else it.shopStock,
                shopShelf = ShopShelf.valueOf(it.shopShelf), minimumShopClears = gate, minimumDropClears = gate)
        }
        return (base + cards + gear + ValuableLootCatalog.definitions).also { require(it.map { d -> d.id }.distinct().size == it.size) }.associateBy { it.id }
    }
}
