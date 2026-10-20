import io

p = "app/src/main/java/com/example/douqiuqiu/inventory/CombatExtensionCodec.kt"
s = io.open(p, encoding="utf-8").read()

old = """    fun node(out: DataOutputStream, value: NodeEventInstance?) {
        out.writeBoolean(value != null)
        value?.let { out.writeUTF(it.nodeId); out.writeUTF(it.kind.name); out.writeInt(it.roll)
            out.writeUTF(it.requiredItemDefinitionId); out.writeInt(it.priceGold)
            out.writeBoolean(it.resolvedOptionId != null); it.resolvedOptionId?.let(out::writeUTF)
            out.writeInt(it.outcomes.size)
            it.outcomes.toSortedMap().forEach { (id, result) ->
                out.writeUTF(id); out.writeInt(result.goldDelta); out.writeDouble(result.hpDeltaPercentOfMax)
                out.writeInt(result.grantDefinitionIds.size); result.grantDefinitionIds.forEach(out::writeUTF)
                out.writeBoolean(result.possibleDeath)
            }
        }
    }
    fun node(input: DataInputStream): NodeEventInstance? {
        if (!input.readBoolean()) return null
        val node = NodeEventInstance(input.readUTF(), EventKind.valueOf(input.readUTF()), input.readInt(), input.readUTF(), input.readInt(),
            if (input.readBoolean()) input.readUTF() else null)
        val outcomes = mutableMapOf<String, EventOutcome>()
        repeat(count(input, 3)) {
            val id = input.readUTF(); val gold = input.readInt(); val hp = input.readDouble()
            val items = List(count(input, 10)) { input.readUTF() }
            require(outcomes.put(id, EventOutcome(node.nodeId, id, goldDelta = gold, hpDeltaPercentOfMax = hp,
                grantDefinitionIds = items, possibleDeath = input.readBoolean())) == null)
        }
        return node.copy(outcomes = outcomes)
    }"""

new = """    fun node(out: DataOutputStream, value: NodeEventInstance?) {
        out.writeBoolean(value != null)
        value?.let { out.writeUTF(it.nodeId); out.writeUTF(it.kind.name); out.writeInt(it.roll)
            out.writeUTF(it.requiredItemDefinitionId); out.writeInt(it.priceGold)
            out.writeBoolean(it.resolvedOptionId != null); it.resolvedOptionId?.let(out::writeUTF)
            out.writeInt(it.outcomes.size)
            it.outcomes.toSortedMap().forEach { (id, result) ->
                out.writeUTF(id); out.writeInt(result.goldDelta); out.writeDouble(result.hpDeltaPercentOfMax)
                out.writeInt(result.grantDefinitionIds.size); result.grantDefinitionIds.forEach(out::writeUTF)
                out.writeBoolean(result.possibleDeath)
                // v14：冻结结局的完整语义（结局 ID / 插画键 / 交付实例 / 附毒）
                out.writeUTF(result.outcomeId); out.writeUTF(result.artKey)
                out.writeInt(result.consumeInstanceIds.size); result.consumeInstanceIds.forEach(out::writeUTF)
                out.writeInt(result.applyNormalPoisonLayers); out.writeDouble(result.poisonDamagePerTick)
            }
        }
    }
    fun node(input: DataInputStream, version: Int): NodeEventInstance? {
        if (!input.readBoolean()) return null
        val node = NodeEventInstance(input.readUTF(), EventKind.valueOf(input.readUTF()), input.readInt(), input.readUTF(), input.readInt(),
            if (input.readBoolean()) input.readUTF() else null)
        val outcomes = mutableMapOf<String, EventOutcome>()
        repeat(count(input, 4)) {
            val id = input.readUTF(); val gold = input.readInt(); val hp = input.readDouble()
            val items = List(count(input, 10)) { input.readUTF() }
            val death = input.readBoolean()
            if (version < 14) {
                require(outcomes.put(id, EventOutcome(node.nodeId, id, goldDelta = gold, hpDeltaPercentOfMax = hp,
                    grantDefinitionIds = items, possibleDeath = death)) == null)
            } else {
                val outcomeId = input.readUTF(); val artKey = input.readUTF()
                val consumed = List(count(input, 10)) { input.readUTF() }
                val poisonLayers = input.readInt(); val poisonDamage = input.readDouble()
                require(outcomes.put(id, EventOutcome(node.nodeId, id, goldDelta = gold, hpDeltaPercentOfMax = hp,
                    grantDefinitionIds = items, consumeInstanceIds = consumed, possibleDeath = death,
                    outcomeId = outcomeId, artKey = artKey, applyNormalPoisonLayers = poisonLayers,
                    poisonDamagePerTick = poisonDamage)) == null)
            }
        }
        return node.copy(outcomes = outcomes)
    }"""

assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("extension codec patched")

p = "app/src/main/java/com/example/douqiuqiu/inventory/CampaignBinaryCodec.kt"
s = io.open(p, encoding="utf-8").read()

old = """            CombatExtensionCodec.node(out, r.nodeEvent)
            out.writeInt(r.rewardVersion)"""
new = """            CombatExtensionCodec.node(out, r.nodeEvent)
            out.writeInt(r.recentEventKinds.size); r.recentEventKinds.forEach { out.writeUTF(it.name) }
            out.writeInt(r.rewardVersion)"""
assert old in s
s = s.replace(old, new, 1)

old = """            val node = if (version >= 9) CombatExtensionCodec.node(input) else null
            val rewards = if (version >= 10) input.readInt() else 1"""
new = """            val node = if (version >= 9) CombatExtensionCodec.node(input, version) else null
            val recentEvents = if (version >= 14) List(count(input, 8)) {
                try { com.example.douqiuqiu.event.EventKind.valueOf(input.readUTF()) }
                catch (_: IllegalArgumentException) { throw IOException("未知事件类型") }
            }.also { if (it.distinct().size != it.size) throw IOException("事件历史重复") } else emptyList()
            val rewards = if (version >= 10) input.readInt() else 1"""
assert old in s
s = s.replace(old, new, 1)

# find the JourneyState construction to add recentEventKinds
old = """                stage = JourneyStage.valueOf(stage), fight = fight)"""
if old not in s:
    print("WARN: JourneyState ctor anchor not found; inspect manually")
else:
    s = s.replace(old, """                stage = JourneyStage.valueOf(stage), fight = fight, recentEventKinds = recentEvents)""", 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("campaign codec patched")
