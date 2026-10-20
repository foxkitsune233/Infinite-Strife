package com.example.douqiuqiu.ui

import android.content.Context
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.event.*
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.status.*
import com.example.douqiuqiu.ui.components.*

/** Debug-only starting states. No fixture branches exist in the production engine. */
object AcceptanceFixtures {
    data class Preset(val id: String, val title: String, val instruction: String)
    val presets = listOf(
        Preset("s04_full", "S04 · 双满奖励后放弃", "背包每格已占，安全箱仍有空位。依次点搬锁芯、拆解，两次都应拒绝且不变；最后放弃应推进。"),
        Preset("c02_double", "C02 · 双碎颅高攻速", "两件碎颅，仅一个唯一被动。自然概率用于观察；精确roll边界另跑外部单测。"),
        Preset("c10_force", "C10 · 自然沉默触发推推", "敌人首帧自动紫怨，我方只有推推保护；应当帧后撤，沉默仍在。"),
        Preset("c10_pike", "C10 · 推推与长戟同组", "我方同时带推推、长戟，应仅长戟生效并有攻速增益。"),
        Preset("c11_six", "C11 · 六主动与双来源状态", "六件主动均设手动，双方停止攻击施法。为便于查看，预置30/20秒两来源沉默（仅显示夹具，非紫怨正式时长）。先看来源详情，再点装备；BKB会驱散沉默。")
    )
    fun spec(id: String, seed: Long = 1729) = ScenarioSpec(scene = if (id == "s04_full") TestScene.EVENT else TestScene.BATTLE,
        round = if (id == "s04_full") 4 else 1, hero = "phantom_assassin", enemy = "juggernaut",
        event = EventKind.SAFE_WRECKAGE, seed = seed, healthPercent = 100, fixture = id, startPaused = id == "c11_six")

    fun create(catalog: InventoryCatalog, requested: ScenarioSpec): InventoryState {
        require(presets.any { it.id == requested.fixture }) { "未知专项夹具：${requested.fixture}" }
        val chosen = spec(requested.fixture, requested.seed)
        // Canonical presets ignore stale form knobs; the seed and fixture ID completely describe them.
        var s = ScenarioFixtures.create(catalog, chosen.copy(fixture = "none"))
        val rules = catalog.rules
        val journey = JourneyRules(rules)
        if (requested.fixture == "s04_full") {
            val filler = rules.definitions.getValue("iron_branch")
            require(filler.width == 1 && filler.height == 1)
            val filled = (0 until s.backpackHeight).flatMap { y -> (0 until s.backpackWidth).map { x ->
                StoredItem("fixture:full:$x:$y", filler.id, ItemPlace(Container.BACKPACK, x, y))
            } }
            val r = s.progress.run!!
            val node = r.nodeEvent!!
            val outcomes = mapOf(
                "wreckage.core" to EventOutcome(node.nodeId, "wreckage.core", grantDefinitionIds = listOf("safe_lock")),
                "wreckage.salvage" to EventOutcome(node.nodeId, "wreckage.salvage", grantDefinitionIds = listOf("essence")),
                "wreckage.abandon" to EventOutcome(node.nodeId, "wreckage.abandon"))
            s = s.copy(items = s.items.filter { it.place.container != Container.BACKPACK } + filled,
                progress = s.progress.copy(run = r.copy(nodeEvent = node.copy(outcomes = outcomes))))
        } else {
            val playerIds = when (requested.fixture) {
                "c02_double" -> listOf("skull_basher", "skull_basher", "hyperstone", "hyperstone", "heart_of_tarrasque")
                "c10_force" -> listOf("force_staff", "heart_of_tarrasque")
                "c10_pike" -> listOf("force_staff", "hurricane_pike", "heart_of_tarrasque")
                else -> listOf("black_king_bar", "satanic", "mask_of_madness", "armlet_of_mordiggian", "pipe_of_insight", "crimson_guard")
            }
            val enemyIds = if (requested.fixture == "c11_six") listOf("orchid", "orchid") else listOf("orchid", "heart_of_tarrasque")
            fun gear(ids: List<String>, prefix: String) = ids.mapIndexed { i, id ->
                StoredItem("fixture:$prefix:$i", id, ItemPlace(Container.EQUIPMENT, i))
            }
            val warehouseAndBags = s.items.filter { it.place.container !in setOf(Container.EQUIPMENT, Container.SKILLS) }
            s = s.copy(items = warehouseAndBags + gear(playerIds, "player"))
            var player = journey.fighter(s, chosen.hero).copy(mana = 0.0, manaRegen = 0.0, ultIn = 60.0)
            var enemy = journey.fighter(s.copy(items = gear(enemyIds, "enemy")), chosen.enemy)
                .copy(mana = 0.0, manaRegen = 0.0, ultIn = 60.0, attackIn = 60.0)
            if (requested.fixture == "c02_double") {
                // Do not let the observer's silence hide normal attacks; this fixture has no active control opponent.
                enemy = journey.fighter(s.copy(items = gear(listOf("heart_of_tarrasque"), "enemy")), chosen.enemy)
                    .copy(mana = 0.0, manaRegen = 0.0, ultIn = 60.0, attackIn = 60.0)
            }
            if (requested.fixture == "c11_six") {
                player = player.copy(attackIn = 60.0, equipment = player.equipment.copy(manual = player.equipment.items.map { it.instanceId }.toSet()))
                enemy = enemy.copy(equipment = enemy.equipment.copy(manual = enemy.equipment.items.map { it.instanceId }.toSet()))
                val sources = EquipmentCombat.effects(enemy.equipment).filter { it.effect.id == "orchid.silence" }
                require(sources.size == 2)
                player = player.copy(timedControls = sources.mapIndexed { i, source ->
                    TimedControl(source.key, 1, ControlKind.SILENCE, if (i == 0) 30000 else 20000,
                        DispelClass.BASIC, 0)
                })
            }
            val r = s.progress.run!!
            s = s.copy(progress = s.progress.copy(manualItems = player.equipment.manual,
                run = r.copy(hp = player.hp, maxHp = player.maxHp,
                    fight = DuelState(player, enemy, chosen.seed))))
        }
        require(rules.validate(s) == null) { rules.validate(s) ?: "专项夹具无效" }
        return s
    }

    /** Explicit renderer comparison; does not delete packaged assets to manufacture a missing-file case. */
    fun showIcons(c: Context) {
        val catalog = InventoryCatalog(c)
        val body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        body.addView(GameUi.label(c, "左：正常物品彩绘；右：强制调用开源回退渲染器。此对照不等于已验证自然缺图自动回退。", 13))
        listOf("skull_basher", "orchid", "heavens_halberd", "recipe_skull_basher", "recipe_orchid", "recipe_heavens_halberd").forEach { id ->
            val d = catalog.rules.definitions.getValue(id)
            body.addView(GameUi.label(c, "${d.name} · $id", 14, GameUi.gold))
            body.addView(LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ItemArtwork.view(c, d), LinearLayout.LayoutParams(GameUi.dp(c, 88), GameUi.dp(c, 88)))
                addView(ImageView(c).apply { contentDescription = "${d.name}强制回退预览"; setImageDrawable(OpenItemIcons.drawable(c, d)) },
                    LinearLayout.LayoutParams(GameUi.dp(c, 88), GameUi.dp(c, 88)))
            })
        }
        AlertDialog.Builder(c).setTitle("T06 · 三控制装与卷轴对照").setView(ScrollView(c).apply { addView(body) })
            .setPositiveButton("关闭", null).show()
    }
}
