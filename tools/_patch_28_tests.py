# -*- coding: utf-8 -*-
"""One-shot patch for the two Task-28 test files: fix leftovers from the parallel-edit race."""
import io

c = "D:/android_project/DouQiuQiu/app/src/test/java/com/example/douqiuqiu/inventory/JourneyLifecycleAcceptanceTest.kt"
b = "D:/android_project/DouQiuQiu/app/src/test/java/com/example/douqiuqiu/combat/EquipmentProcAcceptanceTest.kt"

def patch(path, pairs):
    with io.open(path, encoding="utf-8") as f:
        src = f.read()
    for old, new, count in pairs:
        n = src.count(old)
        assert n >= count, "missing %d of %r in %s" % (count, old[:60], path)
        src = src.replace(old, new, count if count else -1)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)
    print("patched", path)

patch(c, [
    ("import com.example.douqiuqiu.journey.DuelState\n",
     "import com.example.douqiuqiu.journey.ChestItem\nimport com.example.douqiuqiu.journey.DuelState\n", 1),
    ("            s = when (val stage = s.progress.run!!.stage) {",
     "            val stage = s.progress.run!!.stage\n            s = when (stage) {", 1),
    ("        store(file).save(loaded.state)\n        val twice",
     "        store(file).save(loaded)\n        val twice", 1),
    ("assertEquals(3, run(started).fight!!",
     "assertEquals(3, run(started.state).fight!!", 1),
    ("assertEquals(4, run(started).fight!!",
     "assertEquals(4, run(started.state).fight!!", 1),
])

patch(b, [
    ('gearFighter("skywrath", hp = 5000.0, "mjollnir", attackIn = 0.0)',
     'gearFighter("skywrath", "mjollnir", hp = 5000.0, attackIn = 0.0)', 1),
    ('gearFighter("juggernaut", hp = 100000.0, "butterfly")',
     'gearFighter("juggernaut", "butterfly", hp = 100000.0)', 2),
    ('gearFighter("phantom_assassin", hp = 1000.0, "satanic", attackIn = 0.0, maxHp = 5000.0)',
     'gearFighter("phantom_assassin", "satanic", hp = 1000.0, attackIn = 0.0, maxHp = 5000.0)', 1),
    ('gearFighter("phantom_assassin", hp = 1000.0, "heart_of_tarrasque", maxHp = 2364.0)',
     'gearFighter("phantom_assassin", "heart_of_tarrasque", hp = 1000.0, maxHp = 2364.0)', 1),
])
