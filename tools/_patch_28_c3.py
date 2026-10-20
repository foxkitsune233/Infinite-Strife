# -*- coding: utf-8 -*-
"""Fix three C-group assertion issues found by the filtered run."""
import io

p = "D:/android_project/DouQiuQiu/app/src/test/java/com/example/douqiuqiu/inventory/JourneyLifecycleAcceptanceTest.kt"

with io.open(p, encoding="utf-8") as f:
    src = f.read()

pairs = [
    # 1) banked gold must be measured as the wallet delta of the terminal step:
    #    clearing all five battles grants the last reward and settles in the same call.
    ("""            last = s.progress.run
            banked = s.runGold
            val stage = s.progress.run!!.stage
            s = when (stage) {""",
     """            last = s.progress.run
            val stage = s.progress.run!!.stage
            val goldBefore = s.gold
            s = when (stage) {""", 1),
    ("""            }
            assertNull("stage=$stage 后状态必须合法", rules.validate(s))""",
     """            }
            // 终止步（撤离/通关/死亡）在同一调用里结算：带入量按钱包增量计
            banked = if (s.progress.run == null) s.gold - goldBefore else s.runGold
            assertNull("stage=$stage 后状态必须合法", rules.validate(s))""", 1),

    # 2) the enemy preview must be taken from the pre-battle run; startBattle advances the rng
    ("""        val started = journey.startBattle(loaded)
        assertTrue(started.error, started.ok)
        val fight = run(started.state).fight!!
        assertEquals(2, run(started.state).round)""",
     """        val build = EnemyBuilds.preview(loaded.progress.run!!, defs)
        val started = journey.startBattle(loaded)
        assertTrue(started.error, started.ok)
        val fight = run(started.state).fight!!
        assertEquals(2, run(started.state).round)""", 1),
    ("""        // 敌人按第 2 场预算真实生成
        val build = EnemyBuilds.preview(run(started.state), defs)
        assertEquals(build.heroId, fight.enemy.heroId)""",
     """        // 敌人按第 2 场预算真实生成，且与开战前预览一致
        assertEquals(build.heroId, fight.enemy.heroId)""", 1),

    # 3) v8 never persists cross-battle cooldowns, so the carried state is built after loading
    ("""        val file = temp.root.resolve("v8-ready-cd.dat")
        // 预置上一场遗留的 BKB 冷却（v9 会持久化、v8 不会，此处按真实状态构造）
        val loaded = v8Ready(file, round = 2, cooldowns = EquipmentCooldownState(mapOf("bkb" to 45000L), 1))

        val started = journey.startBattle(loaded).also { assertTrue(it.error, it.ok) }.state""",
     """        val file = temp.root.resolve("v8-ready-cd.dat")
        // v8 不持久化跨战 CD：读回默认状态后，按上一场遗留 BKB 冷却 45 秒构造携带态
        val loaded = v8Ready(file, round = 2, cooldowns = EquipmentCooldownState())
        val carried = loaded.copy(progress = loaded.progress.copy(run = run(loaded).copy(
            equipmentCooldowns = EquipmentCooldownState(mapOf("bkb" to 45000L), 1))))

        val started = journey.startBattle(carried).also { assertTrue(it.error, it.ok) }.state""", 1),
]

for old, new, count in pairs:
    n = src.count(old)
    assert n >= count, "missing %d of %r" % (count, old[:70])
    src = src.replace(old, new, count if count else -1)

with io.open(p, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)
print("patched", p)
