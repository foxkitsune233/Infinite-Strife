package com.example.douqiuqiu.grid

/**
 * 固定格子自动摆放（纯 Kotlin common，无 Android / java.* / 随机数 / 时钟依赖）。
 *
 * 用于 6×6 宝箱生成与背包整理：按输入顺序做确定性 first-fit 摆放，
 * 支持 90° 旋转与已有占格障碍。不要求回溯最优解——失败仅表示本算法没找到摆法，
 * 不代表一定不存在可行排列。
 *
 * 坐标为格子数，与像素无关；y 向下增长。返回坐标始终表示旋转后的左上角，
 * 实际宽高由 rotated 推导（rotated 时宽高互换）。
 */
object FixedGridPacker {

    /**
     * 把 [items] 逐个摆进 [size] 的网格，[occupied] 为预先保留的障碍格。
     *
     * 扫描顺序：y 从 0 增长，每行 x 从 0 增长；同一坐标先试原方向再试旋转方向。
     * 全部成功时按输入顺序返回位置（failedItemId=null）；
     * 任一物品摆不下时 success=false、placements 为空、failedItemId 为第一个失败 ID（不返回半成品）。
     * 同样输入每次结果相同；不修改任何输入。
     */
    fun pack(
        size: GridSize,
        items: List<PackItem>,
        occupied: List<GridRect> = emptyList()
    ): GridPackResult {
        validate(size, items, occupied)

        val blocked = Array(size.height) { BooleanArray(size.width) }
        for (rect in occupied) {
            for (y in rect.y until rect.y + rect.height) {
                for (x in rect.x until rect.x + rect.width) {
                    blocked[y][x] = true
                }
            }
        }

        val placements = ArrayList<PackedItem>(items.size)
        for (item in items) {
            val spot = findSpot(blocked, size, item) ?: return GridPackResult(false, emptyList(), item.id)
            mark(blocked, spot, item)
            placements.add(spot)
        }
        return GridPackResult(true, placements, null)
    }

    // ---------------- 内部实现 ----------------

    private fun findSpot(blocked: Array<BooleanArray>, size: GridSize, item: PackItem): PackedItem? {
        for (y in 0 until size.height) {
            for (x in 0 until size.width) {
                // 同一坐标原方向优先
                if (fits(blocked, size, x, y, item.width, item.height)) {
                    return PackedItem(item.id, x, y, false)
                }
                // 正方形旋转无意义，只试原方向
                if (item.canRotate && item.width != item.height &&
                    fits(blocked, size, x, y, item.height, item.width)
                ) {
                    return PackedItem(item.id, x, y, true)
                }
            }
        }
        return null
    }

    /** 用减法判边界，避免 x+width 之类的整数溢出 */
    private fun fits(
        blocked: Array<BooleanArray>,
        size: GridSize,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Boolean {
        if (width > size.width - x || height > size.height - y) return false
        for (row in y until y + height) {
            val line = blocked[row]
            for (col in x until x + width) {
                if (line[col]) return false
            }
        }
        return true
    }

    private fun mark(blocked: Array<BooleanArray>, spot: PackedItem, item: PackItem) {
        val (w, h) = if (spot.rotated) item.height to item.width else item.width to item.height
        for (y in spot.y until spot.y + h) {
            for (x in spot.x until spot.x + w) {
                blocked[y][x] = true
            }
        }
    }

    // ---------------- 输入校验 ----------------

    private fun validate(size: GridSize, items: List<PackItem>, occupied: List<GridRect>) {
        require(size.width in 1..64) { "网格宽必须在 1..64，当前 ${size.width}" }
        require(size.height in 1..64) { "网格高必须在 1..64，当前 ${size.height}" }
        require(items.size <= 256) { "物品数不得超过 256，当前 ${items.size}" }

        val seenIds = HashSet<String>(items.size)
        for (item in items) {
            require(item.id.isNotBlank()) { "物品 ID 不能为空或全空白" }
            require(seenIds.add(item.id)) { "物品 ID 重复：${item.id}" }
            require(item.width in 1..64) { "物品 ${item.id} 宽必须在 1..64，当前 ${item.width}" }
            require(item.height in 1..64) { "物品 ${item.id} 高必须在 1..64，当前 ${item.height}" }
        }

        val placed = ArrayList<GridRect>(occupied.size)
        for (rect in occupied) {
            require(rect.width > 0 && rect.height > 0) { "障碍尺寸必须为正：${rect}" }
            require(rect.x >= 0 && rect.y >= 0) { "障碍坐标不能为负：${rect}" }
            require(rect.width <= size.width - rect.x && rect.height <= size.height - rect.y) {
                "障碍越出网格：${rect}"
            }
            for (other in placed) {
                val disjoint = rect.x + rect.width <= other.x ||
                    other.x + other.width <= rect.x ||
                    rect.y + rect.height <= other.y ||
                    other.y + other.height <= rect.y
                require(disjoint) { "障碍互相重叠：${rect} 与 ${other}" }
            }
            placed.add(rect)
        }
    }
}

/** 网格尺寸（格数） */
data class GridSize(val width: Int, val height: Int)

/** 矩形区域（格子坐标，右上角开区间） */
data class GridRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** 待摆放物品；正方形旋转无意义，恒按原方向返回 */
data class PackItem(val id: String, val width: Int, val height: Int, val canRotate: Boolean = true)

/** 摆放结果：左上角坐标 + 是否旋转（rotated 时实际宽高为原宽高互换） */
data class PackedItem(val id: String, val x: Int, val y: Int, val rotated: Boolean)

/** 摆放结果：成功时 placements 按输入顺序；失败时 placements 为空且带第一个失败 ID */
data class GridPackResult(val success: Boolean, val placements: List<PackedItem>, val failedItemId: String?)
