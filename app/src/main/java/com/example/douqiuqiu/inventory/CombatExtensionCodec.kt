package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.equipment.*
import com.example.douqiuqiu.event.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** v9 extension. v8's immunity/poison fields stay byte-for-byte in their original position. */
internal object CombatExtensionCodec {
    fun cooldown(out: DataOutputStream, value: EquipmentCooldownState) {
        out.writeInt(value.lastPreparedBattle); out.writeInt(value.remainingByGroup.size)
        value.remainingByGroup.toSortedMap().forEach { (group, ms) -> out.writeUTF(group); out.writeLong(ms) }
    }
    fun cooldown(input: DataInputStream): EquipmentCooldownState {
        val battle = input.readInt()
        return EquipmentCooldownState(longMap(input, 100), battle)
    }
    fun gear(out: DataOutputStream, value: EquipmentBattleState) {
        out.writeInt(value.items.size); value.items.forEach { out.writeUTF(it.instanceId); out.writeUTF(it.definitionId) }
        cooldown(out, value.cooldowns)
        out.writeInt(value.activeGroups.size); value.activeGroups.toSortedMap().forEach { (group, ms) -> out.writeUTF(group); out.writeLong(ms) }
        out.writeLong(value.protectionMs); out.writeBoolean(value.started)
        out.writeInt(value.manual.size); value.manual.sorted().forEach(out::writeUTF)
        out.writeInt(value.criticalFailures.size); value.criticalFailures.toSortedMap().forEach { (key, count) -> out.writeUTF(key); out.writeInt(count) }
    }
    fun gear(input: DataInputStream, immunityMs: Long): EquipmentBattleState {
        val items = List(count(input, 6)) { EquippedGear(input.readUTF(), input.readUTF()) }
        val cd = cooldown(input); val active = longMap(input, 20)
        val protection = input.readLong(); val started = input.readBoolean()
        val manual = List(count(input, 6)) { input.readUTF() }.also { require(it.distinct().size == it.size) }.toSet()
        val failures = mutableMapOf<String, Int>()
        repeat(count(input, 6)) { val key = input.readUTF(); require(failures.put(key, input.readInt()) == null) }
        return EquipmentBattleState(immunityMs, items, cd, active, protection, started, manual, criticalFailures = failures)
            .also(EquipmentCombat::validate)
    }
    fun node(out: DataOutputStream, value: NodeEventInstance?) {
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
    }

    private fun longMap(input: DataInputStream, max: Int): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        repeat(count(input, max)) { val key = input.readUTF(); if (map.put(key, input.readLong()) != null) throw IOException("装备组重复") }
        return map
    }
    private fun count(input: DataInputStream, max: Int) = input.readInt().also { if (it !in 0..max) throw IOException("扩展集合长度无效") }
}
