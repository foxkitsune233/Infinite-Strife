package com.example.douqiuqiu.shop

/**
 * 商店货架选择器（纯 Kotlin common，无系统随机数 / 时钟 / 库存 / 存档依赖）。
 *
 * 一次货架生成时调用：按累计通关数筛选候选，常备全上、轮换抽 6 件不重复、
 * 珍品栏按 30% 概率决定是否有货（有货时抽 1 件）。随机数完全由调用方传入的
 * seed 经文档约定的 Lehmer 发生器推进，相同输入（含重排）结果完全一致。
 *
 * 本工具只做「选哪些商品上架」：不猜物品类型、不生成商品、不解锁候选、
 * 不扣库存也不存 seed（nextSeed 交由上层保存）。
 */
enum class ShopShelf { STAPLE, ROTATING, RARE }

/** 货架候选。stock 由上层按卡牌 / 卷轴 / 成装的库存差异传入，此处不推断。 */
data class ShopCandidate(
    val definitionId: String,
    val shelf: ShopShelf,
    val minimumClears: Int = 0,
    val stock: Int = 1
)

/** 上架结果：一个货架位。 */
data class SelectedShopOffer(
    val definitionId: String,
    val shelf: ShopShelf,
    val stock: Int
)

data class ShopShelfSelection(
    val offers: List<SelectedShopOffer>,
    val nextSeed: Long
)

object ShopShelfSelector {

    /** 轮换栏最多上架件数。 */
    const val ROTATING_SLOTS = 6

    /** 珍品栏有货概率（百分比，掷出 0..99 小于该值即有货）。 */
    const val RARE_STOCK_PERCENT = 30

    private const val SEED_MIN = 1L
    private const val SEED_MAX = 2147483646L
    private const val MODULUS = 2147483647L
    private const val MULTIPLIER = 48271L
    private const val STOCK_MIN = 1
    private const val STOCK_MAX = 100

    /**
     * 选出本次货架。返回按「常备 → 轮换 → 珍品」分组、每组 definitionId 字典序的
     * 独立快照，以及推进后的 seed（无珍品空池时也消费珍品判定那一次抽取）。
     */
    fun select(candidates: List<ShopCandidate>, clears: Int, seed: Long): ShopShelfSelection {
        require(clears >= 0) { "clears 必须非负：$clears" }
        require(seed in SEED_MIN..SEED_MAX) { "seed 必须位于 $SEED_MIN..$SEED_MAX：$seed" }
        validateCandidates(candidates)

        // 三类候选分别按 ID 排序（未开放的也参与排序与校验，保证确定性）
        val staple = candidates.filter { it.shelf == ShopShelf.STAPLE }.sortedBy { it.definitionId }
        val rotating = candidates.filter { it.shelf == ShopShelf.ROTATING }.sortedBy { it.definitionId }
        val rare = candidates.filter { it.shelf == ShopShelf.RARE }.sortedBy { it.definitionId }

        val eligibleStaple = staple.filter { it.minimumClears <= clears }
        val eligibleRotating = rotating.filter { it.minimumClears <= clears }
        val eligibleRare = rare.filter { it.minimumClears <= clears }

        val draw = DrawState(seed)

        // 常备：合格候选全部上架，不消耗随机数
        val stapleOffers = eligibleStaple.map {
            SelectedShopOffer(it.definitionId, ShopShelf.STAPLE, it.stock)
        }

        // 轮换：从剩余列表反复抽一件并移除，直到满 6 件或池空，每抽消费一次
        val pool = ArrayList(eligibleRotating)
        val rotatingPicked = ArrayList<ShopCandidate>()
        while (rotatingPicked.size < ROTATING_SLOTS && pool.isNotEmpty()) {
            rotatingPicked.add(pool.removeAt(draw.index(pool.size)))
        }
        val rotatingOffers = rotatingPicked
            .map { SelectedShopOffer(it.definitionId, ShopShelf.ROTATING, it.stock) }
            .sortedBy { it.definitionId }

        // 珍品：无论 RARE 是否为空都消费一次随机数；有货且池非空再抽一次定物品
        var rareOffer: SelectedShopOffer? = null
        if (draw.index(100) < RARE_STOCK_PERCENT && eligibleRare.isNotEmpty()) {
            val pick = eligibleRare[draw.index(eligibleRare.size)]
            rareOffer = SelectedShopOffer(pick.definitionId, ShopShelf.RARE, pick.stock)
        }

        val offers = ArrayList<SelectedShopOffer>(
            stapleOffers.size + rotatingOffers.size + if (rareOffer == null) 0 else 1
        )
        offers.addAll(stapleOffers)
        offers.addAll(rotatingOffers)
        rareOffer?.let { offers.add(it) }

        return ShopShelfSelection(offers, draw.seed)
    }

    // ---------------- 内部 ----------------

    /** Lehmer 发生器状态：每次抽取先推进 seed，再取 seed % n。 */
    private class DrawState(var seed: Long) {
        fun index(n: Int): Int {
            seed = (seed * MULTIPLIER) % MODULUS
            return (seed % n).toInt()
        }
    }

    private fun validateCandidates(candidates: List<ShopCandidate>) {
        val seen = HashSet<String>()
        for (c in candidates) {
            require(c.definitionId.isNotBlank()) { "候选 definitionId 不能为空白" }
            require(seen.add(c.definitionId)) { "候选 definitionId 重复：${c.definitionId}" }
            require(c.minimumClears >= 0) { "minimumClears 必须非负：${c.definitionId}=${c.minimumClears}" }
            require(c.stock in STOCK_MIN..STOCK_MAX) {
                "stock 必须位于 $STOCK_MIN..$STOCK_MAX：${c.definitionId}=${c.stock}"
            }
        }
    }
}
