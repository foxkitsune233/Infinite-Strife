package com.example.douqiuqiu.quest

import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.journey.ChallengeBalance

/** Issue #1：16 个单任务节点 + 8 个三选一节点。数值与设计说明见 docs/design/quest-progression.md。 */
object QuestContent {
    private fun gold(n: Int) = QuestReward(RewardType.GOLD, n)
    private fun item(id: String, n: Int = 1) = QuestReward(RewardType.ITEM, n, materialId = id)
    private fun material(id: String, n: Int = 1) = QuestReward(RewardType.MATERIAL, n, materialId = id)
    private fun hero(id: String, compensation: Int) = QuestReward(RewardType.HERO_UNLOCK, compensation, heroId = id)
    private fun goal(type: ObjectiveType, n: Int, difficulty: Int = 0, hero: String? = null) =
        QuestObjective(type, n, difficulty = difficulty, heroId = hero)
    private fun single(node: Int, name: String, hint: String, goal: QuestObjective, vararg rewards: QuestReward) =
        listOf(Quest("journey.$node", name, hint, QuestKind.GUIDE, goal, rewards.toList(), node = node))
    private fun choice(node: Int, goals: List<Pair<String, QuestObjective>>, vararg rewards: QuestReward) =
        goals.mapIndexed { i, (name, objective) ->
            Quest("journey.$node.${i + 1}", name, "三选一：$name。累计进度；领取后本节点其他选项关闭。",
                QuestKind.CHALLENGE, objective, rewards.toList(), node = node)
        }

    val all: List<Quest> = listOf(
        single(1, "整装出发", "在整备页把一件装备放入前六个装备栏位。", goal(ObjectiveType.EQUIP_ITEM, 1), gold(100)),
        single(2, "技能入阵", "把一张技能卡放入技能栏，战斗中自动生效。", goal(ObjectiveType.SLOT_CARD, 1), material("essence")),
        single(3, "留好退路", "把一件物品放进安全箱；挑战失败时仍能保留。", goal(ObjectiveType.STORE_SAFE, 1), gold(100)),
        single(4, "初次远征", "选择简单宝箱并成功入场；先确认门票与携入金币。", goal(ObjectiveType.ENTER_RUN, 1), item("salve", 2)),
        single(5, "初试锋芒", "累计赢得一场战斗，战后整理掉落。", goal(ObjectiveType.WIN_BATTLES, 1), item("chainmail")),
        single(6, "战地拾取", "鉴定完成后成功拾取一件新掉落；本节点只需拾取一件。", goal(ObjectiveType.PICKUP_LOOT, 1), gold(150)),
        single(7, "稳妥撤离", "第三战后的休息节点选择撤离，带回物资。", goal(ObjectiveType.EXTRACT_COUNT, 1),
            hero("skywrath", 300), material("backpack_fabric", 2), gold(200)),
        single(8, "扩充行囊", "营地成长购买背包4×3（200金币、背包布料×2、撤离资格）。", goal(ObjectiveType.BACKPACK_LEVEL, 1),
            material("essence", 3), gold(250), item("gauntlets"), item("circlet"), item("recipe_bracer")),
        single(9, "工坊入门", "在合成页用仓库中的力量手套、贵族圆环与护腕卷轴合成护腕。", goal(ObjectiveType.CRAFT_COUNT, 1), gold(200), material("safe_lock")),
        single(10, "完整归航", "累计通关一次五战远征；简单难度也可完成。", goal(ObjectiveType.CLEAR_COUNT, 1), hero("viper", 500), gold(600)),
        single(11, "加固安全箱", "营地成长购买安全箱3×2（600金币、锁芯×1、通关1次）。", goal(ObjectiveType.SAFE_LEVEL, 1), material("skill_shard", 2)),
        single(12, "英雄成长", "英雄等级页将任意英雄升至2级（1→2级花费100金币）。", goal(ObjectiveType.HERO_LEVEL, 2), gold(250)),
        choice(13, listOf("困难通关1次" to goal(ObjectiveType.CLEAR_DIFFICULTY, 1, 2),
            "累计战斗胜利20场" to goal(ObjectiveType.WIN_BATTLES, 20), "累计选择事件10次" to goal(ObjectiveType.EVENT_CHOICES, 10)),
            hero("phantom_assassin", 700), material("backpack_fabric", 3), gold(400)),
        single(14, "丰富技能", "营地成长将技能栏扩至5格（250金币、精华×3、撤离资格）。", goal(ObjectiveType.SKILL_LEVEL, 1), material("skill_shard", 2), gold(300)),
        choice(15, listOf("困难通关2次" to goal(ObjectiveType.CLEAR_DIFFICULTY, 2, 2),
            "累计合成3件装备" to goal(ObjectiveType.CRAFT_COUNT, 3), "累计通关5次" to goal(ObjectiveType.CLEAR_COUNT, 5)),
            material("equipment_guard_core"), gold(300)),
        single(16, "远行准备", "营地成长将背包扩至5×3（400金币、布料×3、通关1次）。", goal(ObjectiveType.BACKPACK_LEVEL, 2), material("backpack_fabric", 5), gold(700)),
        choice(17, listOf("精英通关1次" to goal(ObjectiveType.CLEAR_DIFFICULTY, 1, 3),
            "天怒法师战斗胜利15场" to goal(ObjectiveType.HERO_WINS, 15, hero = "skywrath"), "累计战斗胜利40场" to goal(ObjectiveType.WIN_BATTLES, 40)),
            hero("necrophos", 900), gold(500)),
        single(18, "远征常客", "累计通关6次；任意难度均计入。", goal(ObjectiveType.CLEAR_COUNT, 6), material("essence", 8), gold(900)),
        choice(19, listOf("精英通关2次" to goal(ObjectiveType.CLEAR_DIFFICULTY, 2, 3),
            "累计合成6件装备" to goal(ObjectiveType.CRAFT_COUNT, 6), "累计选择事件25次" to goal(ObjectiveType.EVENT_CHOICES, 25)),
            material("safe_frame"), gold(1200)),
        single(20, "稳固后方", "营地成长将安全箱扩至3×3（1200金币、框架×1、通关5次、背包至少4×3）。", goal(ObjectiveType.SAFE_LEVEL, 2), material("skill_shard", 4), gold(1500)),
        choice(21, listOf("大师通关1次" to goal(ObjectiveType.CLEAR_DIFFICULTY, 1, 4),
            "累计战斗胜利70场" to goal(ObjectiveType.WIN_BATTLES, 70), "累计通关12次" to goal(ObjectiveType.CLEAR_COUNT, 12)),
            item("broadsword"), material("equipment_guard_core", 2), gold(700)),
        choice(22, listOf("大师通关2次" to goal(ObjectiveType.CLEAR_DIFFICULTY, 2, 4),
            "任意英雄达到5级" to goal(ObjectiveType.HERO_LEVEL, 5), "累计商店购买15次" to goal(ObjectiveType.SHOP_PURCHASE, 15)),
            material("skill_guard_core", 2), material("essence", 5), gold(800)),
        choice(23, listOf("大师通关3次" to goal(ObjectiveType.CLEAR_DIFFICULTY, 3, 4),
            "累计战斗胜利100场" to goal(ObjectiveType.WIN_BATTLES, 100), "累计通关18次" to goal(ObjectiveType.CLEAR_COUNT, 18)),
            material("equipment_guard_core", 3), material("skill_shard", 4), gold(1000)),
        choice(24, listOf("噩梦通关1次" to goal(ObjectiveType.CLEAR_DIFFICULTY, 1, 5),
            "累计战斗胜利150场" to goal(ObjectiveType.WIN_BATTLES, 150), "累计通关25次" to goal(ObjectiveType.CLEAR_COUNT, 25)),
            material("skill_guard_core", 4), material("essence", 12), item("black_king_bar"), gold(1500))
    ).flatten()

    fun byId(id: String): Quest? = all.firstOrNull { it.id == id }
}

/** 只在新入场时校验；进行中的旧版远征不受新门槛影响。 */
object QuestProgression {
    val requiredNodes = listOf(0, 6, 12, 16, 20, 23)
    fun completedNodes(s: InventoryState): Int = QuestContent.all.filter { it.id in s.progress.claimedQuests }.map { it.node }.distinct().size
    fun maxDifficulty(s: InventoryState): Int = requiredNodes.indexOfLast { completedNodes(s) >= it }
    fun lockedReason(s: InventoryState, difficulty: Int): String? {
        val required = requiredNodes.getOrNull(difficulty) ?: return "副本难度无效"
        val completed = completedNodes(s)
        return if (completed >= required) null else "${ChallengeBalance.name(difficulty)}需领取${required}个任务节点奖励（当前$completed/24）"
    }
}
