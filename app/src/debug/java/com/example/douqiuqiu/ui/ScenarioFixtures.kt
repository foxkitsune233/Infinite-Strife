package com.example.douqiuqiu.ui

import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.event.*
import com.example.douqiuqiu.status.ActionControlState
import org.json.JSONObject

enum class TestScene(val title: String) {
    EVENT("指定事件"), REWARD("战后奖励／第五战通关"), REST("第三战休息"), READY("开战前整备"),
    BATTLE("直接进入战斗"), HOME("营地／商店／成长"), APPRAISAL("宝箱预览与鉴定")
}
enum class TestLoadout(val title: String) {
    BASIC("基础配装"), FAST("高攻速普攻"), POISON("普通毒／剧毒"), SHIELD("护盾构筑"), ACTIVE("主动装备与恢复品"), CONTROL("自然控制与解控")
}
data class ScenarioSpec(
    val scene: TestScene = TestScene.REWARD, val round: Int = 5,
    val hero: String = "juggernaut", val enemy: String = "skywrath",
    val event: EventKind = EventKind.FIELD_CLINIC, val loadout: TestLoadout = TestLoadout.BASIC,
    val seed: Long = 1729, val eventRoll: Int = 0, val runGold: Int = 999,
    val healthPercent: Int = 50, val control: String = "none", val weakEnemy: Boolean = false,
    val expanded: Boolean = false, val fixture: String = "none", val startPaused: Boolean = false
) {
    fun json() = JSONObject().put("scene", scene.name).put("round", round).put("hero", hero).put("enemy", enemy)
        .put("event", event.name).put("loadout", loadout.name).put("seed", seed).put("eventRoll", eventRoll)
        .put("runGold", runGold).put("healthPercent", healthPercent).put("control", control)
        .put("weakEnemy", weakEnemy).put("expanded", expanded).put("fixture", fixture).put("startPaused", startPaused).toString(2)
    companion object {
        fun parse(raw: String): ScenarioSpec {
            val j = JSONObject(raw)
            val fixture = j.optString("fixture", "none")
            if (fixture != "none") {
                val preset = AcceptanceFixtures.spec(fixture, j.optLong("seed", 1729))
                return preset.copy(startPaused = j.optBoolean("startPaused", preset.startPaused))
            }
            return ScenarioSpec(TestScene.valueOf(j.getString("scene")), j.getInt("round"), j.getString("hero"), j.getString("enemy"),
                EventKind.valueOf(j.getString("event")), TestLoadout.valueOf(j.getString("loadout")), j.getLong("seed"),
                j.getInt("eventRoll"), j.getInt("runGold"), j.getInt("healthPercent"), j.getString("control"),
                j.getBoolean("weakEnemy"), j.getBoolean("expanded"), j.optString("fixture", "none"), j.optBoolean("startPaused", false))
        }
    }
}

/** Debug fixtures only: production rules resolve every subsequent click/tick. No instant "claim result" bypass. */
object ScenarioFixtures {
    fun create(catalog: InventoryCatalog, spec: ScenarioSpec): InventoryState {
        if (spec.fixture != "none") return AcceptanceFixtures.create(catalog, spec)
        require(spec.seed in 1..2147483646) { "随机种子必须在1–2147483646之间" }
        require(spec.round in 1..5) { "场号必须在1–5之间" }
        require(spec.eventRoll in 0..99) { "事件roll必须在0–99之间" }
        require(spec.runGold in 0..100000) { "本局金币必须在0–100000之间" }
        require(spec.healthPercent in 1..100) { "初始生命百分比必须在1–100之间" }
        require(spec.control in setOf("none", "stun", "silence", "hex", "disarm")) { "未知控制夹具" }
        if (spec.scene == TestScene.EVENT) require(spec.round <= 4) { "事件请选择第1–4战；第五战直接结算" }
        if (spec.scene == TestScene.REST) require(spec.round == 3) { "休息节点请选择第3战" }
        FirstRunContent.hero(spec.hero); FirstRunContent.hero(spec.enemy)
        val rules = catalog.rules
        val journey = JourneyRules(rules)
        val warehouse = rules.definitions.values.sortedBy { it.id }.flatMap { d ->
            (0 until if (d.kind == ItemKind.MATERIAL) 20 else 2).map { i ->
                StoredItem("test:stock:${d.id}:$i", d.id, ItemPlace(Container.WAREHOUSE), bound = d.boundOnAcquire)
            }
        }
        val gear = when (spec.loadout) {
            TestLoadout.BASIC -> listOf("broadsword", "bracer", "ring_of_health", "force_staff", "black_king_bar", "satanic")
            TestLoadout.FAST -> List(4) { "hyperstone" } + listOf("butterfly", "mask_of_madness")
            TestLoadout.POISON -> listOf("witch_blade", "viper_bite", "rod_of_atos", "black_king_bar", "heart_of_tarrasque", "satanic")
            TestLoadout.SHIELD -> listOf("crimson_guard", "pipe_of_insight", "heart_of_tarrasque", "force_staff", "black_king_bar", "satanic")
            TestLoadout.ACTIVE -> listOf("black_king_bar", "satanic", "mask_of_madness", "armlet_of_mordiggian", "faerie_fire", "cheese")
            TestLoadout.CONTROL -> listOf("skull_basher", "orchid", "heavens_halberd", "force_staff", "black_king_bar", "heart_of_tarrasque")
        }
        val cards = when (spec.loadout) {
            TestLoadout.FAST -> listOf("A1", "A2", "A3", "A4", "U1", "U3", "C1", "C2")
            TestLoadout.POISON -> listOf("P1", "P2", "P3", "P4", "D1", "D5", "U1", "R1")
            TestLoadout.SHIELD -> listOf("S1", "S2", "S3", "S4", "R1", "R2", "U1", "U3")
            else -> listOf("A1", "U1", "C1", "R1", "S1", "P1", "U3", "D5")
        }.take(if (spec.expanded) 8 else 4)
        require((gear + cards).all { it in rules.definitions }) { "测试配装引用了不存在的内容，请检查当前目录版本" }
        val equipped = gear.mapIndexed { i, id -> StoredItem("test:gear:$i", id, ItemPlace(Container.EQUIPMENT, i)) }
        val slotted = cards.mapIndexed { i, id -> StoredItem("test:card:$i", id, ItemPlace(Container.SKILLS, i), star = if (spec.loadout == TestLoadout.FAST) 3 else 1) }
        var state = InventoryState(gold = 100000, runGold = 0,
            backpackWidth = if (spec.expanded) 6 else 4, backpackHeight = if (spec.expanded) 4 else 2,
            safeWidth = if (spec.expanded) 3 else 2, safeHeight = if (spec.expanded) 3 else 2, skillSlots = if (spec.expanded) 8 else 4,
            items = warehouse + equipped + slotted + listOf(
                StoredItem("test:carried:salve", "salve", ItemPlace(Container.BACKPACK)),
                StoredItem("test:safe:essence", "essence", ItemPlace(Container.SAFE))),
            progress = CampaignProgress(seed = spec.seed, clears = 10, extracted = true,
                unlockedHeroes = FirstRunContent.heroes.map { it.id }.toSet()))
        val clues = listOf("iron_branch", "gauntlets", "slippers", "circlet", "essence", "salve")
        require(clues.all { rules.definitions.getValue(it).let { d -> d.width == 1 && d.height == 1 } }) { "测试宝箱布局需更新" }
        val chest = TargetChest("test-target-${spec.seed}", "金", 0, 30,
            clues.mapIndexed { i, id -> ChestItem(id, i % 3, i / 3) })
        if (spec.scene == TestScene.HOME || spec.scene == TestScene.APPRAISAL) {
            return state.copy(progress = state.progress.copy(chests = listOf(chest))).also { require(rules.validate(it) == null) { rules.validate(it) ?: "" } }
        }
        val fighter = journey.fighter(state, spec.hero)
        val hp = fighter.maxHp * spec.healthPercent / 100.0
        val run = JourneyState("test-run-${spec.seed}", spec.hero, chest, spec.seed, hp, fighter.maxHp,
            round = spec.round, pendingItem = "sage_mask")
        state = state.copy(phase = InventoryPhase.PREPARATION, runGold = spec.runGold,
            progress = state.progress.copy(run = run))
        state = when (spec.scene) {
            TestScene.EVENT -> state.copy(progress = state.progress.copy(run = run.copy(stage = JourneyStage.EVENT,
                nodeEvent = NodeEventInstance("${run.id}:event:${run.round}", spec.event, spec.eventRoll, "essence", 30))))
            TestScene.REST -> state.copy(progress = state.progress.copy(run = run.copy(stage = JourneyStage.REST)))
            TestScene.REWARD -> {
                val enemy = journey.fighter(state.copy(items = warehouse), spec.enemy).copy(hp = 0.0)
                state.copy(progress = state.progress.copy(run = run.copy(stage = JourneyStage.REWARD,
                    fight = DuelState(fighter.copy(hp = hp), enemy, spec.seed, won = true))))
            }
            TestScene.BATTLE -> {
                val started = journey.startBattle(state)
                require(started.ok) { started.error ?: "无法创建战斗" }
                val r = started.state.progress.run!!
                val fight = r.fight!!
                val enemyItems = if (spec.loadout == TestLoadout.CONTROL) listOf("skull_basher", "orchid", "heavens_halberd", "force_staff", "black_king_bar", "heart_of_tarrasque")
                    .mapIndexed { index, id -> StoredItem("test:enemy:$index", id, ItemPlace(Container.EQUIPMENT, index)) } else warehouse
                val enemy = journey.fighter(state.copy(items = enemyItems), spec.enemy).let {
                    if (spec.weakEnemy) it.copy(hp = 1.0, maxHp = 1.0, attack = 0.0, regen = 0.0, mana = 0.0, ultIn = 999.0) else it
                }
                val player = fight.player.copy(control = ActionControlState(stunned = spec.control == "stun",
                    silenced = spec.control == "silence", hexed = spec.control == "hex", disarmed = spec.control == "disarm"))
                started.state.copy(progress = started.state.progress.copy(run = r.copy(fight = fight.copy(player = player, enemy = enemy))))
            }
            else -> state
        }
        require(rules.validate(state) == null) { rules.validate(state) ?: "测试状态无效" }
        return state
    }
}
