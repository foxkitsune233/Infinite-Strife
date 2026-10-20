package com.example.douqiuqiu.journey

import com.example.douqiuqiu.inventory.ItemDefinition
import kotlin.math.ln

data class ChestCapacity(val columns: Int, val rows: Int, val attempts: Int)

/** Generation and presentation share one profile. Existing fixed boxes keep all original coordinates. */
object ChestLayout {
    fun capacity(quality: String): ChestCapacity = when (quality) {
        "白" -> ChestCapacity(4, 3, 4)
        "蓝" -> ChestCapacity(5, 4, 7)
        "紫" -> ChestCapacity(6, 5, 10)
        "金" -> ChestCapacity(6, 6, 14)
        else -> ChestCapacity(6, 6, 6)
    }
    fun display(chest: TargetChest, definitions: Map<String, ItemDefinition>): ChestCapacity {
        val p = capacity(chest.quality)
        return p.copy(columns = maxOf(p.columns, chest.items.maxOfOrNull { it.x + definitions.getValue(it.definitionId).width } ?: 0),
            rows = maxOf(p.rows, chest.items.maxOfOrNull { it.y + definitions.getValue(it.definitionId).height } ?: 0))
    }
}

/** Presentation only: price is the fixed catalog price, never a fresh valuation roll. */
object AppraisalTiming {
    fun durationMs(item: ItemDefinition): Long {
        val rank = when (item.quality) { "金" -> 3; "紫" -> 2; "蓝" -> 1; else -> 0 }
        return (500L + item.width * item.height * 65L + rank * 160L + (ln(1.0 + item.price) * 45).toLong()).coerceIn(600L, 2400L)
    }
    /** Three inspection stations; next item uses the first station to become free. */
    fun startsMs(items: List<ItemDefinition>): List<Long> {
        val lanes = LongArray(3)
        return items.map { item ->
            val lane = lanes.indices.minBy { lanes[it] }
            val start = lanes[lane]
            lanes[lane] += durationMs(item)
            start
        }
    }
}
