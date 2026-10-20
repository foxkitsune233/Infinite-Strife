package com.example.douqiuqiu.loot

import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.ItemKind

/** Sellable collectibles, without combat effects, recipes or shop stock. Prices are v1 tuning data. */
object ValuableLootCatalog {
    data class Entry(val definition: ItemDefinition, val description: String)

    private fun entry(id: String, name: String, w: Int, h: Int, quality: String, sell: Int,
                      description: String, clears: Int = 0) = Entry(
        ItemDefinition("loot_$id", name, ItemKind.LOOT, w, h, quality,
            price = sell * 5 / 2, minimumDropClears = clears), description)

    val entries = listOf(
        entry("copper_coin", "磨损铜币", 1, 1, "白", 10, "磨损的旧币，只值少量零钱。"),
        entry("tin_cup", "旧锡杯", 1, 2, "白", 20, "杯身凹陷，仍可按金属回收。"),
        entry("wood_box", "空木匣", 2, 2, "白", 36, "没有暗格的旧木匣，体积大于身价。"),
        entry("faded_painting", "褪色风景画", 2, 3, "白", 60, "普通摹本，画框比画本身更值钱。"),
        entry("old_carpet", "旧挂毯", 3, 3, "白", 90, "占地很大，但不是每件大物品都是珍品。"),
        entry("silver_coin", "纪念银币", 1, 1, "蓝", 70, "小巧的纪念币，便于装进安全箱。"),
        entry("pocket_watch", "黄铜怀表", 1, 2, "蓝", 100, "还能走动的老式怀表。"),
        entry("porcelain_vase", "青釉花瓶", 2, 2, "蓝", 160, "釉面完整的装饰瓷瓶。"),
        entry("silver_cups", "银器套组", 3, 2, "蓝", 240, "成套保存的银质餐具。"),
        entry("tapestry", "织锦壁毯", 4, 2, "蓝", 320, "工艺精细，搬运时需要腾出连续空间。"),
        entry("ruby", "切割红宝石", 1, 1, "紫", 360, "稀少的小件珍品，单位格价值很高。", 1),
        entry("gold_seal", "古代金印", 1, 2, "紫", 420, "带有旧王朝纹样的金印。", 1),
        entry("jade_statue", "玉雕摆件", 2, 2, "紫", 520, "完整的玉雕陈设，只有收藏价值。", 1),
        entry("oil_painting", "收藏油画", 3, 2, "紫", 680, "有收藏价值的画作，无法作为装备使用。", 1),
        entry("bronze_clock", "雕花座钟", 3, 3, "紫", 900, "复杂的机械结构与雕花抬高了总价。", 1),
        entry("star_diamond", "星芒钻石", 1, 1, "金", 1000, "极少见的精品，体积小却价值不菲。", 3),
        entry("royal_pendant", "王室珠链", 1, 2, "金", 1200, "珠宝收藏品，不能装备，也不提供属性。", 3),
        entry("jeweled_egg", "宝石彩蛋", 2, 2, "金", 1500, "宝石镶嵌的精巧工艺品。", 3),
        entry("golden_statue", "鎏金雕像", 2, 3, "金", 1900, "贵重的大件陈设，携带时需要作出取舍。", 3),
        entry("royal_screen", "王室屏风", 4, 3, "金", 2600, "首批最昂贵的大件，需要较大的背包空间。", 3)
    )
    val definitions: List<ItemDefinition> = entries.map { it.definition }
    private val byId = entries.associateBy { it.definition.id }
    fun find(id: String): Entry? = byId[id]

    /** Weight per eligible collectible, not an absolute drop rate. Unknown IDs never join the pool. */
    fun weight(id: String, chestQuality: String, difficulty: Int, clears: Int): Int {
        require(difficulty in com.example.douqiuqiu.journey.ChallengeBalance.difficulties && clears >= 0)
        val quality = listOf("白", "蓝", "紫", "金")
        val boxRank = quality.indexOf(chestQuality)
        require(boxRank >= 0)
        val item = byId[id]?.definition ?: return 0
        val rank = quality.indexOf(item.quality)
        if (item.minimumDropClears > clears || rank > minOf(3, boxRank + difficulty)) return 0
        return listOf(12, 6, 2, 1)[rank] * if (rank >= 2) difficulty + 1 else 1
    }

    /** Chance to try the collectible pool in each non-guaranteed chest slot; packing may reject a draw. */
    fun attemptPercent(chestQuality: String, difficulty: Int): Int {
        val rank = listOf("白", "蓝", "紫", "金").indexOf(chestQuality)
        require(rank >= 0 && difficulty in com.example.douqiuqiu.journey.ChallengeBalance.difficulties)
        return 20 + rank * 5 + difficulty * 5
    }
}
