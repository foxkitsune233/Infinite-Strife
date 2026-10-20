package com.example.douqiuqiu.status

import org.junit.Assert.*
import org.junit.Test

/**
 * 新毒核心引擎行为测试（H03）。
 *
 * 断言依据 `docs/rules/poison.md`「已确认核心机制」一节；涉及首跳延后的断言
 * 明确标注为**建议稿行为**（配置可注入，改配置即改行为）。
 */
class PoisonEngineTest {

    /** 建议稿配置：3 跳 / 间隔 1s / 首跳延后 1s。 */
    private val cfg = PoisonCoreConfig()

    private fun normal(id: String, dmg: Double, ticks: Int = 3, source: String = "card:P1") =
        NormalPoisonSpec(id, source, "player", dmg, ticks)

    private fun virulent(id: String, dmg: Double, source: String = "card:P2") =
        VirulentPoisonSpec(id, source, "enemy", dmg)

    // ---------------- 普通毒：跳数与计时 ----------------

    @Test fun p01_firstTickIsDelayedByOneSecond() {
        val pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        assertEquals(1000L, pool.normal.single().nextTickAtMs)

        assertTrue(PoisonEngine.advance(pool, 999L, cfg).ticks.isEmpty())
        assertEquals(1, PoisonEngine.advance(pool, 1000L, cfg).ticks.size)
    }

    @Test fun p02_normalLayerTicksExactlyThreeTimesThenDisappears() {
        var pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        val seen = ArrayList<Long>()
        for (t in listOf(1000L, 2000L, 3000L)) {
            val r = PoisonEngine.advance(pool, t, cfg)
            assertEquals(1, r.ticks.size)
            seen += r.ticks.single().amount.toLong()
            pool = r.pool
        }
        assertTrue("第三次结算后层应被移除", pool.normal.isEmpty())
        assertEquals(listOf(3L, 3L, 3L), seen)
    }

    @Test fun p03_newLayerDoesNotRefreshOldLayer() {
        var pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        pool = PoisonEngine.addNormal(pool, normal("b", 6.0), 1500L, cfg)

        var aTicks = 0
        var bTicks = 0
        for (t in listOf(1000L, 2000L, 2500L, 3000L, 3500L, 4500L, 6000L)) {
            val r = PoisonEngine.advance(pool, t, cfg)
            aTicks += r.ticks.count { it.instanceId == "a" }
            bTicks += r.ticks.count { it.instanceId == "b" }
            pool = r.pool
        }
        // 若新层刷新了旧层，a 会超过 3 跳
        assertEquals(3, aTicks)
        assertEquals(3, bTicks)
    }

    @Test fun p04_layerCountIsConfigurable() {
        val five = PoisonCoreConfig(ticksPerLayer = 5)
        var pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 2.0, ticks = 5), 0L, five)
        var ticks = 0
        for (t in listOf(1000L, 2000L, 3000L, 4000L, 5000L)) {
            val r = PoisonEngine.advance(pool, t, five)
            ticks += r.ticks.size
            pool = r.pool
        }
        assertEquals(5, ticks)
        assertTrue(pool.normal.isEmpty())
    }

    @Test fun p05_zeroDamageLayerStillExpiresAfterThreeTicks() {
        var pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 0.0), 0L, cfg)
        val r = PoisonEngine.advance(pool, 3000L, cfg)
        assertEquals(3, r.ticks.size)
        assertTrue("0 伤害不产生伤害事件", r.effectiveTicks.isEmpty())
        assertTrue(r.pool.normal.isEmpty())
    }

    // ---------------- 剧毒：本体无伤、按层产毒 ----------------

    @Test fun p06_virulentBodyDealsNoDamageItself() {
        val pool = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 5.0), 0L, cfg)
        val r = PoisonEngine.advance(pool, 5000L, cfg)
        assertTrue("剧毒本体不出现在伤害事件中", r.ticks.none { it.instanceId == "v1" })
        assertFalse("剧毒层本体持续存在", r.pool.virulent.isEmpty())
    }

    @Test fun p07_virulentSpawnsOneNormalLayerPerSecond() {
        val pool = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 5.0), 0L, cfg)
        val r = PoisonEngine.advance(pool, 5000L, cfg)
        assertEquals(5, r.spawned.size)
        assertTrue(r.spawned.all { it.damagePerTick == 5.0 })
        assertTrue(r.spawned.all { it.kind == PoisonKind.NORMAL })
    }

    @Test fun p08_twoVirulentLayersSpawnTwoNormalsPerSecond() {
        var pool = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 5.0), 0L, cfg)
        pool = PoisonEngine.addVirulent(pool, virulent("v2", 5.0), 0L, cfg)
        val r = PoisonEngine.advance(pool, 3000L, cfg)
        assertEquals(6, r.spawned.size)
    }

    @Test fun p09_spawnedNormalFirstTickIsOneSecondAfterSpawn() {
        val pool = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 5.0), 0L, cfg)
        val first = PoisonEngine.advance(pool, 1000L, cfg)
        assertEquals(1, first.spawned.size)
        assertEquals(2000L, first.spawned.single().nextTickAtMs)
        // 生成瞬间不额外跳伤害
        assertTrue(first.ticks.isEmpty())

        val second = PoisonEngine.advance(first.pool, 2000L, cfg)
        assertEquals(1, second.ticks.size)
        assertEquals("v1~1000", second.ticks.single().instanceId)
    }

    @Test fun p10_differentVirulentStrengthsSpawnSeparately() {
        var pool = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 3.0), 0L, cfg)
        pool = PoisonEngine.addVirulent(pool, virulent("v2", 9.0), 0L, cfg)
        val r = PoisonEngine.advance(pool, 1000L, cfg)
        assertEquals(setOf(3.0, 9.0), r.spawned.map { it.damagePerTick }.toSet())
    }

    @Test fun p11_virulentPersistsForWholeBattle() {
        val pool = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 5.0), 0L, cfg)
        val r = PoisonEngine.advance(pool, 60000L, cfg)
        assertEquals(60, r.spawned.size)
        assertEquals(1, r.pool.virulent.size)
    }

    // ---------------- 驱散 ----------------

    @Test fun p12_weakDispelClearsNormalButKeepsVirulent() {
        var pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        pool = PoisonEngine.addVirulent(pool, virulent("v1", 5.0), 0L, cfg)

        val r = PoisonEngine.dispel(pool, DispelStrength.BASIC)
        assertTrue(r.removedInstanceIds.contains("a"))
        assertFalse(r.removedInstanceIds.contains("v1"))
        assertTrue(r.pool.normal.isEmpty())
        assertEquals(1, r.pool.virulent.size)
    }

    @Test fun p13_strongDispelClearsBoth() {
        var pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        pool = PoisonEngine.addVirulent(pool, virulent("v1", 5.0), 0L, cfg)

        val r = PoisonEngine.dispel(pool, DispelStrength.STRONG)
        assertTrue(r.pool.isEmpty())
        assertEquals(setOf("a", "v1"), r.removedInstanceIds.toSet())
        // 强驱散不提供后续免疫窗口：再施加仍然生效
        val again = PoisonEngine.addNormal(r.pool, normal("b", 3.0), 0L, cfg)
        assertEquals(1, again.normal.size)
    }

    @Test fun p14_enemyTargetDebuffsAreNeverDispelled() {
        var pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        pool = PoisonEngine.addVirulent(pool, virulent("v1", 5.0), 0L, cfg)

        val r = PoisonEngine.dispel(pool, DispelStrength.STRONG, DispelTarget.ENEMY)
        assertTrue(r.removedInstanceIds.isEmpty())
        assertEquals(1, r.pool.normal.size)
        assertEquals(1, r.pool.virulent.size)
    }

    @Test fun p15_weakDispelStillAllowsVirulentToKeepSpawning() {
        val pool = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 5.0), 0L, cfg)
        val after = PoisonEngine.dispel(pool, DispelStrength.BASIC).pool
        val r = PoisonEngine.advance(after, 3000L, cfg)
        assertEquals(3, r.spawned.size)
    }

    // ---------------- 结束与查询 ----------------

    @Test fun p16_endBattleClearsEverything() {
        var pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        pool = PoisonEngine.addVirulent(pool, virulent("v1", 5.0), 0L, cfg)
        assertTrue(PoisonEngine.endBattle(pool).isEmpty())
    }

    @Test fun p17_countsReportLayersAndPendingTicks() {
        var pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        pool = PoisonEngine.addNormal(pool, normal("b", 3.0), 0L, cfg)
        pool = PoisonEngine.addVirulent(pool, virulent("v1", 5.0), 0L, cfg)

        val before = PoisonEngine.counts(pool)
        assertEquals(2, before.normalLayers)
        assertEquals(6, before.pendingNormalTicks)
        assertEquals(1, before.virulentLayers)
        assertEquals(1000L, before.nextNormalTickAtMs)
        assertEquals(1000L, before.nextVirulentSpawnAtMs)

        // 推进到第 1 秒：两个原层各跳一次（6 → 4），同时剧毒产出一层新的普通毒（+3）
        val after = PoisonEngine.counts(PoisonEngine.advance(pool, 1000L, cfg).pool)
        assertEquals(7, after.pendingNormalTicks)
        assertEquals(3, after.normalLayers)
        // 剧毒本体不产生跳数
        assertEquals(1, after.virulentLayers)
    }

    @Test fun p18_ticksCarrySourceAndCaster() {
        val spec = NormalPoisonSpec("a", "equip:witch_blade", "enemy:1", 4.0, 3)
        val pool = PoisonEngine.addNormal(PoisonCorePool(), spec, 0L, cfg)
        val tick = PoisonEngine.advance(pool, 1000L, cfg).ticks.single()
        assertEquals("a", tick.instanceId)
        assertEquals("equip:witch_blade", tick.sourceId)
        assertEquals("enemy:1", tick.casterUnitId)
        assertEquals(4.0, tick.amount, 1e-9)
    }

    @Test fun p19_spawnedLayerInheritsSourceAndPierceFlag() {
        val pool = PoisonEngine.addVirulent(
            PoisonCorePool(),
            VirulentPoisonSpec("v1", "hero:viper_ult", "player", 3.0, piercesDebuffImmunity = true),
            0L, cfg
        )
        val spawned = PoisonEngine.advance(pool, 1000L, cfg).spawned.single()
        assertEquals("hero:viper_ult", spawned.sourceId)
        assertEquals("player", spawned.casterUnitId)
        assertTrue(spawned.piercesDebuffImmunity)
        assertEquals(DispelClass.BASIC, spawned.dispelClass)
    }

    // ---------------- 越界与非法输入 ----------------

    @Test fun p20_jumpAheadCatchesUpAllDueTicks() {
        val pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        val r = PoisonEngine.advance(pool, 3000L, cfg)
        assertEquals(3, r.ticks.size)
        assertTrue(r.pool.normal.isEmpty())
    }

    @Test fun p21_absurdJumpIsRejectedInsteadOfHanging() {
        val pool = PoisonEngine.addVirulent(PoisonCorePool(), virulent("v1", 5.0), 0L, cfg)
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.advance(pool, 10_000_000L, cfg)
        }
    }

    @Test fun p22_duplicateInstanceIdIsRejected() {
        val pool = PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), 0L, cfg)
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.addNormal(pool, normal("a", 5.0), 0L, cfg)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.addVirulent(pool, virulent("a", 5.0), 0L, cfg)
        }
    }

    @Test fun p23_negativeNowIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0), -1L, cfg)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.advance(PoisonCorePool(), -1L, cfg)
        }
    }

    @Test fun p24_negativeDamageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.addNormal(PoisonCorePool(), normal("a", -1.0), 0L, cfg)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.addVirulent(PoisonCorePool(), virulent("v", -3.0), 0L, cfg)
        }
    }

    @Test fun p25_nonPositiveTickCountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.addNormal(PoisonCorePool(), normal("a", 3.0, ticks = 0), 0L, cfg)
        }
    }

    @Test fun p26_layersMustSitInTheMatchingList() {
        val normalLayer = PoisonCoreLayer("a", PoisonKind.NORMAL, "s", "u", 3.0, 3, 1000L)
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.advance(PoisonCorePool(virulent = listOf(normalLayer)), 5000L, cfg)
        }
        val virulentLayer = PoisonCoreLayer("v", PoisonKind.VIRULENT, "s", "u", 3.0, 1, 1000L)
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.advance(PoisonCorePool(normal = listOf(virulentLayer)), 5000L, cfg)
        }
        // 剧毒层不使用跳数
        val bad = PoisonCoreLayer("v", PoisonKind.VIRULENT, "s", "u", 3.0, 2, 1000L)
        assertThrows(IllegalArgumentException::class.java) {
            PoisonEngine.advance(PoisonCorePool(virulent = listOf(bad)), 5000L, cfg)
        }
    }
}
