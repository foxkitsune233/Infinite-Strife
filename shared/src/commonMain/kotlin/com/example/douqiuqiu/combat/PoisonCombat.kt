package com.example.douqiuqiu.combat

import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.status.*

/** Battle-time poison integration. Source serials persist; no wall time or random roll is used. */
object PoisonCombat {
    val config = PoisonCoreConfig(3, 1000, 1000)
    fun apply(target: Fighter, sourceUnit: Int, sourceId: String, serial: Int, nowTick: Int,
              virulent: Boolean, layers: Int, damage: Double, pierces: Boolean = false): Fighter {
        if (target.hp <= 0 || target.invulnerable > 0 || (!pierces && target.equipment.immunityMs > 0)) return target
        var pool = target.poisonPool ?: PoisonCorePool()
        repeat(layers) { n ->
            val id = "p:$sourceUnit:$serial:$n"
            pool = if (virulent) PoisonEngine.addVirulent(pool, VirulentPoisonSpec(id, sourceId, sourceUnit.toString(), damage, pierces), nowTick * 100L, config)
            else PoisonEngine.addNormal(pool, NormalPoisonSpec(id, sourceId, sourceUnit.toString(), damage, 3, pierces), nowTick * 100L, config)
        }
        return target.copy(poisonPool = pool)
    }
    fun validate(f: Fighter, tick: Int) {
        require(f.poisonSerial in 0..100000)
        require(f.equipment.immunityMs >= 0)
        f.poisonPool?.let { pool ->
            require(pool.layers.size <= 12000)
            PoisonEngine.validatePool(pool)
            require(pool.layers.all { it.casterUnitId in setOf("0", "1") && it.sourceId.length <= 256 &&
                it.instanceId.length <= 256 && it.damagePerTick <= 1000000 &&
                it.nextTickAtMs > tick * 100L && it.nextTickAtMs <= tick * 100L + 1000 &&
                (it.kind != PoisonKind.NORMAL || it.ticksRemaining in 1..3) })
        }
    }

    /**
     * 推进本场新毒核心一个 tick（100ms）。
     *
     * 返回新状态与本次应结算的伤害跳；跳是**未减伤的基础魔法法术伤害**，由调用方
     * 按固定次序送进伤害管线（普通毒按 atMs 与 instanceId 排序已在引擎内完成）。
     * 空池物化为 null，避免存档里留下无意义的空对象。
     */
    fun advance(f: Fighter, nowTick: Int): Pair<Fighter, List<PoisonCoreTick>> {
        val pool = f.poisonPool ?: return f to emptyList()
        val advanced = PoisonEngine.advance(pool, nowTick * 100L, config)
        val blockedSpawns = if (f.equipment.immunityMs > 0) advanced.spawned.filterNot { it.piercesDebuffImmunity }.map { it.instanceId }.toSet() else emptySet()
        val next = advanced.pool.copy(normal = advanced.pool.normal.filterNot { it.instanceId in blockedSpawns })
        return f.copy(poisonPool = next.takeIf { !it.isEmpty() }) to advanced.effectiveTicks.filterNot { it.instanceId in blockedSpawns }
    }
}
