# -*- coding: utf-8 -*-
"""Fix C-group test fixtures: real item ids + exclude hand-over options from the safe picker."""
import io

p = "D:/android_project/DouQiuQiu/app/src/test/java/com/example/douqiuqiu/inventory/JourneyLifecycleAcceptanceTest.kt"

with io.open(p, encoding="utf-8") as f:
    src = f.read()

pairs = [
    # iron_branch is not a definition in this catalog; crown is a real 1x1 component
    ('listOf(ChestItem("iron_branch", 0, 0), ChestItem("salve", 1, 0))',
     'listOf(ChestItem("crown", 0, 0), ChestItem("salve", 1, 0))', 1),
    ('StoredItem("bp", "iron_branch", ItemPlace(Container.BACKPACK, 0, 0))',
     'StoredItem("bp", "crown", ItemPlace(Container.BACKPACK, 0, 0))', 2),
    # hand-over options (blocker.item / clinic.supply / terminal.convert) carry no cost and no
    # hp delta in their frozen outcome, so they must be excluded by the option itself
    ("""            val out = node.outcomes[o.id]
            o.cost == null && out != null && out.goldDelta == 0 && out.hpDeltaPercentOfMax >= 0.0 &&
                out.grantDefinitionIds.isEmpty() && out.consumeInstanceIds.isEmpty()""",
     """            val out = node.outcomes[o.id]
            o.cost == null && !o.requiresExplicitItemChoice && o.itemRequirement == null &&
                out != null && out.goldDelta == 0 && out.hpDeltaPercentOfMax >= 0.0 &&
                out.grantDefinitionIds.isEmpty() && out.consumeInstanceIds.isEmpty()""", 1),
]

for old, new, count in pairs:
    n = src.count(old)
    assert n >= count, "missing %d of %r" % (count, old[:60])
    src = src.replace(old, new, count if count else -1)

with io.open(p, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)
print("patched", p)
