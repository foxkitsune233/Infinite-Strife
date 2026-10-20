package com.example.douqiuqiu.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FixedGridPacker 验收用例，对应 docs/tasks/04-fixed-grid-packer.md。
 */
class FixedGridPackerTest {

    private fun expectIllegal(name: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("$name 未抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test
    fun `空清单成功`() {
        val r = FixedGridPacker.pack(GridSize(4, 2), emptyList())
        assertTrue(r.success)
        assertTrue(r.placements.isEmpty())
        assertNull(r.failedItemId)
    }

    @Test
    fun `4x2放满8个1x1`() {
        val items = (1..8).map { PackItem("i$it", 1, 1) }
        val r = FixedGridPacker.pack(GridSize(4, 2), items)
        assertTrue(r.success)
        assertEquals(8, r.placements.size)
        // 逐行扫描：第一行 x=0..3，第二行 x=0..3
        assertEquals(PackedItem("i1", 0, 0, false), r.placements[0])
        assertEquals(PackedItem("i4", 3, 0, false), r.placements[3])
        assertEquals(PackedItem("i5", 0, 1, false), r.placements[4])
        assertEquals(PackedItem("i8", 3, 1, false), r.placements[7])
        assertNull(r.failedItemId)
    }

    @Test
    fun `第9个失败且无部分结果`() {
        val items = (1..9).map { PackItem("i$it", 1, 1) }
        val r = FixedGridPacker.pack(GridSize(4, 2), items)
        assertFalse(r.success)
        assertTrue(r.placements.isEmpty())
        assertEquals("i9", r.failedItemId)
    }

    @Test
    fun `3x2可旋转为2x3放入2x3网格`() {
        val r = FixedGridPacker.pack(
            GridSize(2, 3),
            listOf(PackItem("big", 3, 2, canRotate = true))
        )
        assertTrue(r.success)
        assertEquals(PackedItem("big", 0, 0, true), r.placements.single())
    }

    @Test
    fun `禁旋转时摆不下`() {
        val r = FixedGridPacker.pack(
            GridSize(2, 3),
            listOf(PackItem("big", 3, 2, canRotate = false))
        )
        assertFalse(r.success)
        assertTrue(r.placements.isEmpty())
        assertEquals("big", r.failedItemId)
    }

    @Test
    fun `正方形不标旋转`() {
        val r = FixedGridPacker.pack(
            GridSize(4, 4),
            listOf(PackItem("sq", 2, 2, canRotate = true))
        )
        assertTrue(r.success)
        assertEquals(PackedItem("sq", 0, 0, false), r.placements.single())
    }

    @Test
    fun `障碍后寻找下一位置`() {
        val r = FixedGridPacker.pack(
            GridSize(4, 1),
            listOf(PackItem("gem", 1, 1)),
            occupied = listOf(GridRect(0, 0, 1, 1))
        )
        assertTrue(r.success)
        assertEquals(PackedItem("gem", 1, 0, false), r.placements.single())
    }

    @Test
    fun `相接不算重叠`() {
        // 障碍占左格，1x1 贴边放右格即成功
        val r = FixedGridPacker.pack(
            GridSize(2, 1),
            listOf(PackItem("gem", 1, 1)),
            occupied = listOf(GridRect(0, 0, 1, 1))
        )
        assertTrue(r.success)
        assertEquals(PackedItem("gem", 1, 0, false), r.placements.single())
    }

    @Test
    fun `输入顺序稳定`() {
        val items = listOf(
            PackItem("c", 1, 1), PackItem("a", 1, 1), PackItem("b", 1, 1)
        )
        val r = FixedGridPacker.pack(GridSize(3, 1), items)
        assertTrue(r.success)
        assertEquals(listOf("c", "a", "b"), r.placements.map { it.id })
        assertEquals(listOf(0, 1, 2), r.placements.map { it.x })
    }

    @Test
    fun `同坐标原方向优先`() {
        // 1x2 在 2x2 网格中两个方向都能放，(0,0) 原方向胜出
        val r = FixedGridPacker.pack(
            GridSize(2, 2),
            listOf(PackItem("bar", 1, 2, canRotate = true))
        )
        assertTrue(r.success)
        assertEquals(PackedItem("bar", 0, 0, false), r.placements.single())
    }

    @Test
    fun `大于网格的物品属于正常失败`() {
        val r = FixedGridPacker.pack(GridSize(2, 2), listOf(PackItem("long", 3, 1)))
        assertFalse(r.success)
        assertEquals("long", r.failedItemId)
    }

    @Test
    fun `失败后输入不变`() {
        val items = listOf(PackItem("a", 1, 1), PackItem("b", 3, 3))
        val occupied = listOf(GridRect(0, 0, 1, 1))
        val itemsBefore = items.map { it.copy() }
        val occupiedBefore = occupied.map { it.copy() }
        val r = FixedGridPacker.pack(GridSize(4, 2), items, occupied)
        assertFalse(r.success)
        assertEquals(itemsBefore, items)
        assertEquals(occupiedBefore, occupied)
    }

    @Test
    fun `4x2背包实际例子`() {
        val r = FixedGridPacker.pack(
            GridSize(4, 2),
            listOf(
                PackItem("sword", 3, 1),
                PackItem("shield", 1, 2),
                PackItem("card", 1, 1),
                PackItem("potion", 1, 1)
            )
        )
        assertTrue(r.success)
        assertEquals(
            listOf(
                PackedItem("sword", 0, 0, false),
                PackedItem("shield", 3, 0, false),
                PackedItem("card", 0, 1, false),
                PackedItem("potion", 1, 1, false)
            ),
            r.placements
        )
    }

    @Test
    fun `6x6宝箱实际例子`() {
        val r = FixedGridPacker.pack(
            GridSize(6, 6),
            listOf(
                PackItem("helm", 2, 2),
                PackItem("armor", 3, 2),
                PackItem("boots", 1, 1),
                PackItem("ring", 1, 1),
                PackItem("scroll", 2, 1, canRotate = false)
            ),
            occupied = listOf(GridRect(5, 5, 1, 1))
        )
        assertTrue(r.success)
        assertEquals(PackedItem("helm", 0, 0, false), r.placements[0])
        assertEquals(PackedItem("armor", 2, 0, false), r.placements[1])
        assertEquals(5, r.placements.size)
        // 所有摆放互不重叠且不压障碍
        val cells = HashSet<Pair<Int, Int>>()
        val dims = mapOf("helm" to (2 to 2), "armor" to (3 to 2), "boots" to (1 to 1), "ring" to (1 to 1), "scroll" to (2 to 1))
        for (p in r.placements) {
            val (w, h) = dims.getValue(p.id)
            for (y in p.y until p.y + h) for (x in p.x until p.x + w) {
                assertTrue("重叠于 ($x,$y)", cells.add(x to y))
            }
        }
        assertTrue((5 to 5) !in cells)
    }

    @Test
    fun `非法输入抛异常`() {
        expectIllegal("网格宽为0") { FixedGridPacker.pack(GridSize(0, 2), emptyList()) }
        expectIllegal("网格高超界") { FixedGridPacker.pack(GridSize(4, 65), emptyList()) }
        expectIllegal("重复ID") {
            FixedGridPacker.pack(GridSize(4, 2), listOf(PackItem("a", 1, 1), PackItem("a", 1, 1)))
        }
        expectIllegal("空白ID") { FixedGridPacker.pack(GridSize(4, 2), listOf(PackItem(" ", 1, 1))) }
        expectIllegal("物品宽为0") { FixedGridPacker.pack(GridSize(4, 2), listOf(PackItem("a", 0, 1))) }
        expectIllegal("物品高超界") { FixedGridPacker.pack(GridSize(4, 2), listOf(PackItem("a", 1, 65))) }
        expectIllegal("物品数超限") {
            FixedGridPacker.pack(GridSize(64, 64), (1..257).map { PackItem("i$it", 1, 1) })
        }
        expectIllegal("障碍负坐标") {
            FixedGridPacker.pack(GridSize(4, 2), emptyList(), listOf(GridRect(-1, 0, 1, 1)))
        }
        expectIllegal("障碍尺寸为0") {
            FixedGridPacker.pack(GridSize(4, 2), emptyList(), listOf(GridRect(0, 0, 0, 1)))
        }
        expectIllegal("障碍越界") {
            FixedGridPacker.pack(GridSize(4, 2), emptyList(), listOf(GridRect(3, 0, 2, 1)))
        }
        expectIllegal("障碍重叠") {
            FixedGridPacker.pack(
                GridSize(4, 2), emptyList(),
                listOf(GridRect(0, 0, 2, 1), GridRect(1, 0, 2, 1))
            )
        }
    }
}
