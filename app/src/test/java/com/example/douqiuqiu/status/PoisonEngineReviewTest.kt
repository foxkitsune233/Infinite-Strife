package com.example.douqiuqiu.status

import org.junit.Assert.*
import org.junit.Test

/**
 * H03 静态复核补测（R04 / R05）。
 *
 * 覆盖复核文档 `docs/testing/2026-10-17-H03-H08-review.md` 的 HR09～HR12、HR16。
 * 时序断言用的是本轮定案的技术基线：3 跳 / 1 秒间隔 / 首跳与首次产毒延后 1 秒。
 */
class PoisonEngineReviewTest {

    private val cfg = PoisonCoreConfig()

    private fun normal(id: String, dmg: Double, pierce: Boolean = false) =
        NormalPoisonSpec(id, "card:P1", "player", dmg, 3, pierce)

    private fun virulent(id: String, dmg: Double) = VirulentPoisonSpec(id, "card:P2", "enemy", dmg)

    // ---------------- HR09 生成 ID 不撞已有层 ----------------

    @Test fun hr09_generatedIdsNeverCollideWithExistingLayers() {
        // a) 外部 ID 占用生成命名空间 → 入口直接拒绝，输入保持不变
        val empty = PoisonCorePool()
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.addNormal(empty, normal("v~1000", 3.0), 0L, cfg)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.addVirulent(empty, virulent("v~1000", 5.0), 0L, cfg)
        }
        assertTrue("拒绝时输入不得被改动", empty.isEmpty())

        // b) 即便池里已存在同 ID 的层（绕过入口直接构造），推进也不得产出重复 ID
        val crafted = PoisonCorePool(
            normal = listOf(PoisonCoreLayer("v~1000", PoisonKind.NORMAL, "s", "u", 3.0, 3, 1000L)),
            virulent = listOf(PoisonCoreLayer("v", PoisonKind.VIRULENT, "s", "u", 5.0, 0, 1000L))
        )
        val r = PoisonEngine.advance(crafted, 1000L, cfg)
        val ids = r.pool.layers.map { it.instanceId }
        assertEquals("产出池不得含重复 ID", ids.size, ids.toSet().size)
        assertTrue("撞车的生成 ID 必须去歧义: $ids", r.spawned.single().instanceId != "v~1000")

        // c) 正常情形：ID 派生只依赖剧毒 ID + 产出时刻，可持久化、可复现
        val clean = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 5.0), 0L, cfg)
        assertEquals("v1~1000", PoisonEngine.advance(clean, 1000L, cfg).spawned.single().instanceId)
    }

    // ---------------- HR10 最后一跳仍带标记与时刻 ----------------

    @Test fun hr10_finalTickStillCarriesTimeAndPierceFlag() {
        val pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 4.0, pierce = true), 0L, cfg)
        val r = PoisonEngine.advance(pool, 3000L, cfg)

        assertTrue("最后一跳后层已被移除", r.pool.normal.isEmpty())
        assertEquals(3, r.ticks.size)
        assertEquals(listOf(1000L, 2000L, 3000L), r.ticks.map { it.atMs })

        val last = r.ticks.last()
        assertEquals(3000L, last.atMs)
        assertTrue("层已删除，标记只能从跳上取回", last.piercesDebuffImmunity)
        assertEquals("a", last.instanceId)
        assertEquals("card:P1", last.sourceId)
    }

    // ---------------- HR11 / HR12 时序与排序 ----------------

    /**
     * 两层普通毒：a 在 0ms 施加（首跳 1000ms）、b 在 500ms 施加（首跳 1500ms）。
     *
     * [idsInPoolOrder] 只改变**池内排列顺序**，两层的施加时刻与 ID 绑定关系不变，
     * 这样才是在测「顺序不影响输出」，而不是测「换了两层的施加时刻」。
     */
    private fun twoLayers(idsInPoolOrder: List<String>): PoisonCorePool {
        val firstTickAt = mapOf("a" to 1000L, "b" to 1500L)
        val damage = mapOf("a" to 1.0, "b" to 2.0)
        return PoisonCorePool(
            normal = idsInPoolOrder.map { id ->
                PoisonCoreLayer(id, PoisonKind.NORMAL, "s", "u", damage.getValue(id), 3, firstTickAt.getValue(id))
            }
        )
    }

    private fun signature(ticks: List<PoisonCoreTick>): List<Pair<String, Long>> =
        ticks.map { it.instanceId to it.atMs }

    @Test fun hr11_batchedAdvanceMatchesSteppedAdvance() {
        val batched = PoisonEngine.advance(twoLayers(listOf("a", "b")), 3500L, cfg)

        var pool = twoLayers(listOf("a", "b"))
        val stepped = ArrayList<PoisonCoreTick>()
        for (t in listOf(500L, 1000L, 1500L, 2000L, 2500L, 3000L, 3500L)) {
            val r = PoisonEngine.advance(pool, t, cfg)
            stepped += r.ticks
            pool = r.pool
        }

        assertEquals(signature(batched.ticks), signature(stepped))
        assertEquals(
            "批量输出必须按实际结算时刻排序",
            signature(batched.ticks).map { it.second }.sorted(),
            signature(batched.ticks).map { it.second }
        )
        assertEquals(
            listOf("a" to 1000L, "b" to 1500L, "a" to 2000L, "b" to 2500L, "a" to 3000L, "b" to 3500L),
            signature(batched.ticks)
        )
    }

    @Test fun hr12_poolOrderDoesNotChangeTheTickSequence() {
        val forward = PoisonEngine.advance(twoLayers(listOf("a", "b")), 3500L, cfg)
        val reversed = PoisonEngine.advance(twoLayers(listOf("b", "a")), 3500L, cfg)

        assertEquals(signature(forward.ticks), signature(reversed.ticks))

        // 非同刻不得逆序：按时刻排序后，同刻内部按 instanceId 字典序这一稳定规则
        val times = signature(reversed.ticks).map { it.second }
        assertEquals(times.sorted(), times)
        val sameTick = listOf(
            PoisonCoreLayer("z", PoisonKind.NORMAL, "s", "u", 1.0, 1, 1000L),
            PoisonCoreLayer("m", PoisonKind.NORMAL, "s", "u", 1.0, 1, 1000L)
        )
        assertEquals(
            listOf("m", "z"),
            PoisonEngine.advance(PoisonCorePool(normal = sameTick), 1000L, cfg).ticks.map { it.instanceId }
        )
        assertEquals(
            listOf("m", "z"),
            PoisonEngine.advance(PoisonCorePool(normal = sameTick.reversed()), 1000L, cfg).ticks.map { it.instanceId }
        )
    }

    // ---------------- HR16 本轮定案时序 ----------------

    @Test fun hr16_reviewBaselineTimings() {
        // 普通毒：施加后第 1 / 2 / 3 秒伤害
        val pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        val r = PoisonEngine.advance(pool, 3000L, cfg)
        assertEquals(listOf(1000L, 2000L, 3000L), r.ticks.map { it.atMs })
        assertTrue(r.pool.normal.isEmpty())

        // 剧毒：第 1 秒产毒，本体不造成伤害
        val vp = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 5.0), 0L, cfg)
        val first = PoisonEngine.advance(vp, 1000L, cfg)
        assertEquals(1, first.spawned.size)
        assertEquals("v1~1000", first.spawned.single().instanceId)
        assertTrue("产毒不是伤害事件", first.ticks.isEmpty())
        assertEquals(2000L, first.spawned.single().nextTickAtMs)

        // 第 1 秒产出的那一层：第 2 秒首次伤害，随后 3 / 4 秒
        var pool2 = first.pool
        val firstLayerTicks = ArrayList<Long>()
        for (t in listOf(2000L, 3000L, 4000L)) {
            val step = PoisonEngine.advance(pool2, t, cfg)
            firstLayerTicks += step.ticks.filter { it.instanceId == "v1~1000" }.map { it.atMs }
            pool2 = step.pool
        }
        assertEquals(listOf(2000L, 3000L, 4000L), firstLayerTicks)
        assertFalse("该层 3 跳后应消失", pool2.normal.any { it.instanceId == "v1~1000" })
        assertEquals("剧毒持续本场", 1, pool2.virulent.size)
        // 剧毒每秒持续产毒：到第 4 秒已产出 4 层（其中第 1 层已耗尽）
        assertEquals(3, pool2.normal.size)
    }
}
