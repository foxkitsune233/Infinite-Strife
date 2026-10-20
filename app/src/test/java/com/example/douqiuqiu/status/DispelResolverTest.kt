package com.example.douqiuqiu.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DispelResolverTest {

    private fun eff(instanceId: String, sourceId: String, positive: Boolean, cls: DispelClass) =
        DispelEffect(instanceId, sourceId, positive, cls)

    private fun ids(effects: List<DispelEffect>) = effects.map { it.instanceId }

    @Test
    fun `弱清多层普通毒但保留强与不可驱散`() {
        val effects = listOf(
            eff("e1", "venom", positive = false, cls = DispelClass.BASIC),
            eff("e2", "venom", positive = false, cls = DispelClass.BASIC),
            eff("e3", "p4", positive = false, cls = DispelClass.STRONG_ONLY),
            eff("e4", "viper", positive = false, cls = DispelClass.UNDISPELLABLE)
        )
        val r = DispelResolver.resolve(effects, DispelStrength.BASIC, DispelTarget.SELF_OR_ALLY)
        assertEquals(listOf("e1", "e2"), ids(r.removed))
        assertEquals(listOf("e3", "e4"), ids(r.retained))
    }

    @Test
    fun `强清弱与强负面`() {
        val effects = listOf(
            eff("e1", "venom", positive = false, cls = DispelClass.BASIC),
            eff("e2", "p4", positive = false, cls = DispelClass.STRONG_ONLY),
            eff("e3", "viper", positive = false, cls = DispelClass.UNDISPELLABLE)
        )
        val r = DispelResolver.resolve(effects, DispelStrength.STRONG, DispelTarget.SELF_OR_ALLY)
        assertEquals(listOf("e1", "e2"), ids(r.removed))
        assertEquals(listOf("e3"), ids(r.retained))
    }

    @Test
    fun `友方增益一律保留`() {
        val effects = listOf(
            eff("e1", "shield", positive = true, cls = DispelClass.BASIC),
            eff("e2", "bkb", positive = true, cls = DispelClass.STRONG_ONLY),
            eff("e3", "aegis", positive = true, cls = DispelClass.UNDISPELLABLE)
        )
        for (strength in DispelStrength.values()) {
            val r = DispelResolver.resolve(effects, strength, DispelTarget.SELF_OR_ALLY)
            assertTrue(r.removed.isEmpty())
            assertEquals(ids(effects), ids(r.retained))
        }
    }

    @Test
    fun `敌方只清BASIC增益`() {
        val effects = listOf(
            eff("e1", "shield", positive = true, cls = DispelClass.BASIC),
            eff("e2", "bkb", positive = true, cls = DispelClass.STRONG_ONLY),
            eff("e3", "undying", positive = true, cls = DispelClass.UNDISPELLABLE),
            eff("e4", "venom", positive = false, cls = DispelClass.BASIC)
        )
        val r = DispelResolver.resolve(effects, DispelStrength.BASIC, DispelTarget.ENEMY)
        assertEquals(listOf("e1"), ids(r.removed))
        assertEquals(listOf("e2", "e3", "e4"), ids(r.retained))
    }

    @Test
    fun `强驱散不扩大敌方清除范围`() {
        val effects = listOf(
            eff("e1", "bkb", positive = true, cls = DispelClass.STRONG_ONLY),
            eff("e2", "venom", positive = false, cls = DispelClass.BASIC),
            eff("e3", "viper", positive = false, cls = DispelClass.UNDISPELLABLE)
        )
        val r = DispelResolver.resolve(effects, DispelStrength.STRONG, DispelTarget.ENEMY)
        assertTrue(r.removed.isEmpty())
        assertEquals(ids(effects), ids(r.retained))
    }

    @Test
    fun `同来源不同标记分别处理`() {
        val effects = listOf(
            eff("e1", "venom", positive = false, cls = DispelClass.BASIC),
            eff("e2", "venom", positive = false, cls = DispelClass.STRONG_ONLY),
            eff("e3", "venom", positive = false, cls = DispelClass.UNDISPELLABLE),
            eff("e4", "venom", positive = true, cls = DispelClass.BASIC)
        )
        val r = DispelResolver.resolve(effects, DispelStrength.STRONG, DispelTarget.SELF_OR_ALLY)
        assertEquals(listOf("e1", "e2"), ids(r.removed))
        assertEquals(listOf("e3", "e4"), ids(r.retained))
    }

    @Test
    fun `空输入合法`() {
        for (strength in DispelStrength.values()) {
            for (target in DispelTarget.values()) {
                val r = DispelResolver.resolve(emptyList(), strength, target)
                assertTrue(r.retained.isEmpty())
                assertTrue(r.removed.isEmpty())
            }
        }
    }

    @Test
    fun `实例守恒且保留输入顺序`() {
        val effects = listOf(
            eff("e1", "a", positive = true, cls = DispelClass.BASIC),
            eff("e2", "b", positive = false, cls = DispelClass.BASIC),
            eff("e3", "c", positive = false, cls = DispelClass.UNDISPELLABLE),
            eff("e4", "d", positive = true, cls = DispelClass.STRONG_ONLY),
            eff("e5", "e", positive = false, cls = DispelClass.STRONG_ONLY)
        )
        val r = DispelResolver.resolve(effects, DispelStrength.BASIC, DispelTarget.ENEMY)
        assertEquals(listOf("e1"), ids(r.removed))
        assertEquals(listOf("e2", "e3", "e4", "e5"), ids(r.retained))
        // 互斥且合起来恰好等于输入
        val all = (r.retained + r.removed).map { it.instanceId }.sorted()
        assertEquals(ids(effects).sorted(), all)
        assertEquals(effects.size, r.retained.size + r.removed.size)
    }

    @Test
    fun `空白ID与重复实例抛错`() {
        assertThrows(IllegalArgumentException::class.java) {
            DispelResolver.resolve(
                listOf(eff(" ", "a", positive = false, cls = DispelClass.BASIC)),
                DispelStrength.BASIC, DispelTarget.SELF_OR_ALLY
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DispelResolver.resolve(
                listOf(eff("e1", "  ", positive = false, cls = DispelClass.BASIC)),
                DispelStrength.BASIC, DispelTarget.SELF_OR_ALLY
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DispelResolver.resolve(
                listOf(
                    eff("e1", "a", positive = false, cls = DispelClass.BASIC),
                    eff("e1", "b", positive = true, cls = DispelClass.BASIC)
                ),
                DispelStrength.STRONG, DispelTarget.SELF_OR_ALLY
            )
        }
    }

    @Test
    fun `相同输入确定同结果且结果快照隔离`() {
        val effects = mutableListOf(
            eff("e1", "venom", positive = false, cls = DispelClass.BASIC),
            eff("e2", "shield", positive = true, cls = DispelClass.BASIC)
        )
        val r1 = DispelResolver.resolve(effects, DispelStrength.BASIC, DispelTarget.ENEMY)
        val r2 = DispelResolver.resolve(effects, DispelStrength.BASIC, DispelTarget.ENEMY)
        assertEquals(r1, r2)
        // 敌方只清 BASIC 正面：e2 被清，e1 保留
        assertEquals(listOf("e2"), ids(r1.removed))
        assertEquals(listOf("e1"), ids(r1.retained))
        // 事后修改输入，结果不变
        effects.add(eff("e3", "x", positive = false, cls = DispelClass.BASIC))
        assertEquals(listOf("e2"), ids(r1.removed))
        assertEquals(listOf("e1"), ids(r1.retained))
    }
}
