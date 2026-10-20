package com.example.douqiuqiu.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SynergyCounterTest {

    private fun card(
        instanceId: String,
        cardId: String,
        vararg tags: String,
        star: Int = 1,
        active: Boolean = true
    ) = SynergyCard(instanceId, cardId, tags.toSet(), star, active)

    private fun schoolOf(result: List<SchoolSynergy>, id: String): SchoolSynergy =
        result.first { it.schoolId == id }

    @Test
    fun `空卡列表返回空`() {
        assertTrue(SynergyCounter.count(emptyList()).isEmpty())
    }

    @Test
    fun `1种卡未达档也输出流派且activeTiers为空`() {
        val r = SynergyCounter.count(listOf(card("i1", "c1", "warrior")))
        assertEquals(1, r.size)
        assertEquals("warrior", r[0].schoolId)
        assertEquals(listOf("c1"), r[0].uniqueCardIds.toList())
        assertTrue(r[0].activeTiers.isEmpty())
    }

    @Test
    fun `2种卡激活2档`() {
        val r = SynergyCounter.count(
            listOf(card("i1", "c1", "warrior"), card("i2", "c2", "warrior"))
        )
        assertEquals(listOf(2), r[0].activeTiers)
        assertEquals(listOf("c1", "c2"), r[0].uniqueCardIds.toList())
    }

    @Test
    fun `3种卡激活2和3档`() {
        val r = SynergyCounter.count(
            listOf(
                card("i1", "c1", "warrior"),
                card("i2", "c2", "warrior"),
                card("i3", "c3", "warrior")
            )
        )
        assertEquals(listOf(2, 3), r[0].activeTiers)
        assertEquals(listOf("c1", "c2", "c3"), r[0].uniqueCardIds.toList())
    }

    @Test
    fun `4种卡同时激活2_3_4档`() {
        val r = SynergyCounter.count((1..4).map { card("i$it", "c$it", "warrior") })
        assertEquals(listOf(2, 3, 4), r[0].activeTiers)
        assertEquals(listOf("c1", "c2", "c3", "c4"), r[0].uniqueCardIds.toList())
    }

    @Test
    fun `5种卡保留5个卡ID但仍只激活到4档`() {
        val r = SynergyCounter.count((1..5).map { card("i$it", "c$it", "warrior") })
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5"), r[0].uniqueCardIds.toList())
        assertEquals(listOf(2, 3, 4), r[0].activeTiers)
    }

    @Test
    fun `同名重复与不同星每流派只算一种`() {
        val r = SynergyCounter.count(
            listOf(
                card("i1", "c1", "warrior", star = 1),
                card("i2", "c1", "warrior", star = 2),
                card("i3", "c1", "warrior", star = 3)
            )
        )
        assertEquals(1, r.size)
        assertEquals(listOf("c1"), r[0].uniqueCardIds.toList())
        assertTrue(r[0].activeTiers.isEmpty())
    }

    @Test
    fun `双标签分别贡献两个流派`() {
        val r = SynergyCounter.count(
            listOf(
                card("i1", "c1", "warrior", "mage"),
                card("i2", "c2", "warrior")
            )
        )
        assertEquals(listOf("mage", "warrior"), r.map { it.schoolId })
        val warrior = schoolOf(r, "warrior")
        assertEquals(listOf("c1", "c2"), warrior.uniqueCardIds.toList())
        assertEquals(listOf(2), warrior.activeTiers)
        val mage = schoolOf(r, "mage")
        assertEquals(listOf("c1"), mage.uniqueCardIds.toList())
        assertTrue(mage.activeTiers.isEmpty())
    }

    @Test
    fun `多流派同时激活`() {
        val r = SynergyCounter.count(
            listOf(
                card("i1", "c1", "warrior"),
                card("i2", "c2", "warrior"),
                card("i3", "c3", "mage"),
                card("i4", "c4", "mage")
            )
        )
        assertEquals(listOf("mage", "warrior"), r.map { it.schoolId })
        assertEquals(listOf(2), schoolOf(r, "mage").activeTiers)
        assertEquals(listOf(2), schoolOf(r, "warrior").activeTiers)
    }

    @Test
    fun `inactive不计数但参与校验`() {
        val r = SynergyCounter.count(
            listOf(
                card("i1", "c1", "warrior"),
                card("i2", "c2", "warrior", active = false),
                card("i3", "c3", "warrior", active = false)
            )
        )
        val warrior = schoolOf(r, "warrior")
        assertEquals(listOf("c1"), warrior.uniqueCardIds.toList())
        assertTrue(warrior.activeTiers.isEmpty())
    }

    @Test
    fun `inactive独占的流派不输出`() {
        val r = SynergyCounter.count(listOf(card("i1", "c1", "mage", active = false)))
        assertTrue(r.isEmpty())
    }

    @Test
    fun `输入顺序不影响输出`() {
        val a = listOf(
            card("i1", "c1", "warrior"),
            card("i2", "c2", "warrior"),
            card("i3", "c3", "mage", "warrior")
        )
        val ra = SynergyCounter.count(a)
        val rb = SynergyCounter.count(a.reversed())
        assertEquals(ra.map { it.schoolId }, rb.map { it.schoolId })
        for (s in ra) {
            val other = schoolOf(rb, s.schoolId)
            assertEquals(s.uniqueCardIds.toList(), other.uniqueCardIds.toList())
            assertEquals(s.activeTiers, other.activeTiers)
        }
    }

    @Test
    fun `自定义门槛按给定档位判定`() {
        val r = SynergyCounter.count(
            listOf(
                card("i1", "c1", "warrior"),
                card("i2", "c2", "warrior"),
                card("i3", "c3", "warrior")
            ),
            thresholds = listOf(1, 2, 3)
        )
        assertEquals(listOf(1, 2, 3), r[0].activeTiers)
    }

    @Test
    fun `非法门槛抛错`() {
        val one = listOf(card("i1", "c1", "warrior"))
        assertThrows(IllegalArgumentException::class.java) { SynergyCounter.count(one, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { SynergyCounter.count(one, listOf(0, 1)) }
        assertThrows(IllegalArgumentException::class.java) { SynergyCounter.count(one, listOf(-1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { SynergyCounter.count(one, listOf(2, 2)) }
        assertThrows(IllegalArgumentException::class.java) { SynergyCounter.count(one, listOf(3, 2)) }
    }

    @Test
    fun `标签数量非法抛错`() {
        assertThrows(IllegalArgumentException::class.java) { SynergyCounter.count(listOf(card("i1", "c1"))) }
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(listOf(card("i1", "c1", "a", "b", "c")))
        }
    }

    @Test
    fun `空白标签抛错`() {
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(listOf(card("i1", "c1", "  ")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(listOf(card("i1", "c1", "warrior", "\t")))
        }
    }

    @Test
    fun `空白ID抛错`() {
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(listOf(card(" ", "c1", "warrior")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(listOf(card("i1", "", "warrior")))
        }
    }

    @Test
    fun `重复实例ID抛错`() {
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(
                listOf(card("i1", "c1", "warrior"), card("i1", "c2", "warrior"))
            )
        }
    }

    @Test
    fun `同名卡标签冲突抛错`() {
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(
                listOf(card("i1", "c1", "warrior"), card("i2", "c1", "mage"))
            )
        }
        // 双标签顺序不同不算冲突（Set 无序）：同一张双标签卡分别贡献两个流派
        val r = SynergyCounter.count(
            listOf(card("i1", "c1", "warrior", "mage"), card("i2", "c1", "mage", "warrior"))
        )
        assertEquals(listOf("mage", "warrior"), r.map { it.schoolId })
        assertEquals(listOf("c1"), r[0].uniqueCardIds.toList())
        assertEquals(listOf("c1"), r[1].uniqueCardIds.toList())
    }

    @Test
    fun `非法星级抛错`() {
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(listOf(card("i1", "c1", "warrior", star = 0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(listOf(card("i1", "c1", "warrior", star = 4)))
        }
    }

    @Test
    fun `inactive的非法输入同样抛错`() {
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(listOf(card("i1", "c1", "warrior", star = 9, active = false)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SynergyCounter.count(listOf(card("i1", "c1", active = false)))
        }
    }

    @Test
    fun `输入与结果快照隔离`() {
        val tags = mutableSetOf("warrior")
        val cards = mutableListOf(SynergyCard("i1", "c1", tags))
        val r = SynergyCounter.count(cards)
        // 事后修改输入集合，结果不得随之变化
        tags.add("mage")
        cards.add(card("i2", "c2", "warrior"))
        assertEquals(1, r.size)
        assertEquals("warrior", r[0].schoolId)
        assertEquals(listOf("c1"), r[0].uniqueCardIds.toList())
    }
}
