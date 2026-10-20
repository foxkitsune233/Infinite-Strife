package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.combat.*
import com.example.douqiuqiu.equipment.EquipmentBattleState
import com.example.douqiuqiu.status.ActionControlState
import com.example.douqiuqiu.status.PoisonCoreLayer
import com.example.douqiuqiu.status.PoisonCorePool
import com.example.douqiuqiu.status.PoisonKind
import com.example.douqiuqiu.shop.*
import com.example.douqiuqiu.loot.*
import com.example.douqiuqiu.cards.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Versioned platform adapter; no file or Android API enters the shared core. */
internal object CampaignBinaryCodec {
    fun write(out: DataOutputStream, p: CampaignProgress) {
        out.writeLong(p.seed); out.writeInt(p.batch); out.writeInt(p.clears); out.writeBoolean(p.extracted)
        out.writeUTF(p.lastEnding); out.writeLong(p.shopCycle); out.writeLong(p.shopSeen)
        out.writeInt(p.shopStock.size); p.shopStock.forEach { (id, n) -> out.writeUTF(id); out.writeInt(n) }
        out.writeInt(p.manualItems.size); p.manualItems.sorted().forEach(out::writeUTF)
        out.writeInt(p.chests.size); p.chests.forEach { chest(out, it) }
        out.writeBoolean(p.run != null)
        p.run?.let { r ->
            out.writeUTF(r.id); out.writeUTF(r.heroId); chest(out, r.target); out.writeLong(r.rng)
            out.writeDouble(r.hp); out.writeDouble(r.maxHp); out.writeInt(r.round); out.writeUTF(r.stage.name)
            out.writeUTF(r.pendingItem); out.writeInt(r.eventRoll); out.writeBoolean(r.fight != null)
            r.fight?.let { fight ->
                fighter(out, fight.player); fighter(out, fight.enemy); out.writeLong(fight.rng); out.writeInt(fight.tick)
                out.writeInt(if (fight.won == null) -1 else if (fight.won == true) 1 else 0)
                out.writeInt(fight.log.size); fight.log.forEach { out.writeUTF(it) }
            }
            CombatExtensionCodec.cooldown(out, r.equipmentCooldowns)
            CombatExtensionCodec.node(out, r.nodeEvent)
            out.writeInt(r.recentEventKinds.size); r.recentEventKinds.forEach { out.writeUTF(it.name) }
            out.writeInt(r.rewardVersion)
            out.writeBoolean(r.nodeLoot != null)
            r.nodeLoot?.let { loot ->
                out.writeUTF(loot.id); out.writeUTF(loot.source.name); out.writeLong(loot.inspectionMs); out.writeInt(loot.goldAwarded)
                out.writeInt(loot.items.size)
                loot.items.forEach { item ->
                    out.writeUTF(item.instanceId); out.writeUTF(item.definitionId); out.writeInt(item.star); out.writeBoolean(item.bound); out.writeInt(item.count)
                }
                out.writeInt(loot.picked.size); loot.picked.sorted().forEach(out::writeUTF)
                out.writeInt(loot.putDown.size)
                loot.putDown.forEach { item -> out.writeUTF(item.instanceId); out.writeUTF(item.definitionId); out.writeInt(item.star); out.writeBoolean(item.bound); out.writeInt(item.count) }
            }
        }
        with(p.economy) {
            out.writeBoolean(initialized); out.writeInt(refreshCharges); out.writeLong(refreshAnchorMs)
            out.writeInt(reliefCharges); out.writeLong(reliefAnchorMs); out.writeInt(reliefClaims); out.writeInt(vouchers)
        }
        out.writeBoolean(p.shop != null)
        p.shop?.let { shelf ->
            out.writeInt(shelf.revision); out.writeLong(shelf.nextSeed); out.writeInt(shelf.offers.size)
            shelf.offers.forEach { offer -> out.writeUTF(offer.definitionId); out.writeUTF(offer.shelf.name); out.writeInt(offer.price); out.writeInt(offer.stock) }
            out.writeInt(shelf.purchases.size); shelf.purchases.forEach { (id, n) -> out.writeUTF(id); out.writeInt(n) }
        }
        out.writeInt(p.unlockedHeroes.size); p.unlockedHeroes.sorted().forEach(out::writeUTF)
        out.writeInt(p.materialPity.counters.size)
        p.materialPity.counters.toSortedMap().forEach { (category, counter) ->
            out.writeUTF(category); out.writeInt(counter.misses); out.writeBoolean(counter.pending)
        }
        out.writeUTF(p.activeHeroId)
        out.writeInt(p.loadouts.size)
        p.loadouts.toSortedMap().forEach { (hero, presets) ->
            out.writeUTF(hero); out.writeInt(presets.size)
            presets.forEach { preset(out, it) }
        }
        out.writeInt(p.loadoutSelections.size)
        p.loadoutSelections.toSortedMap().forEach { (hero, slot) -> out.writeUTF(hero); out.writeInt(slot) }
        out.writeInt(p.lockedItems.size); p.lockedItems.sorted().forEach(out::writeUTF)
    }
    fun read(input: DataInputStream, version: Int): CampaignProgress {
        val seed = input.readLong(); val batch = input.readInt(); val clears = input.readInt(); val extracted = input.readBoolean()
        val ending = input.readUTF(); val cycle = input.readLong(); val seen = input.readLong()
        val stock = mutableMapOf<String, Int>()
        repeat(count(input, 1000)) { val id = input.readUTF(); if (stock.put(id, input.readInt()) != null) throw IOException("库存ID重复") }
        val manual = if (version >= 3) List(count(input, 10000)) { input.readUTF() }.toSet() else emptySet()
        val boxes = List(count(input, 8)) { chest(input, version) }
        val run = if (!input.readBoolean()) null else {
            val id = input.readUTF(); val hero = input.readUTF(); val target = chest(input, version); val rng = input.readLong()
            val hp = input.readDouble(); val max = input.readDouble(); val round = input.readInt(); val stage = JourneyStage.valueOf(input.readUTF())
            val pending = input.readUTF(); val event = input.readInt()
            val fight = if (!input.readBoolean()) null else {
                val player = fighter(input, version); val enemy = fighter(input, version); val random = input.readLong(); val tick = input.readInt()
                if (version >= 8) { PoisonCombat.validate(player, tick); PoisonCombat.validate(enemy, tick) }
                val won = when (input.readInt()) { -1 -> null; 0 -> false; 1 -> true; else -> throw IOException("胜负状态无效") }
                DuelState(player, enemy, random, tick, won, List(count(input, LOG_CAP)) { input.readUTF() })
            }
            val cooldown = if (version >= 9) CombatExtensionCodec.cooldown(input) else com.example.douqiuqiu.equipment.EquipmentCooldownState()
            val node = if (version >= 9) CombatExtensionCodec.node(input, version) else null
            val recentEvents = if (version >= 14) List(count(input, 8)) {
                try { com.example.douqiuqiu.event.EventKind.valueOf(input.readUTF()) }
                catch (_: IllegalArgumentException) { throw IOException("未知事件类型") }
            }.also { if (it.distinct().size != it.size) throw IOException("事件历史重复") } else emptyList()
            val rewards = if (version >= 10) input.readInt() else 1
            val loot = if (version >= 11 && input.readBoolean()) {
                val lootId = input.readUTF(); val source = NodeLootSource.valueOf(input.readUTF())
                val elapsed = input.readLong(); val gold = input.readInt()
                val items = List(count(input, 10)) { StoredItem(input.readUTF(), input.readUTF(), ItemPlace(Container.WAREHOUSE), input.readInt(), input.readBoolean(), input.readInt()) }
                val picked = List(count(input, 10)) { input.readUTF() }
                if (picked.distinct().size != picked.size) throw IOException("重复拾取记录")
                val putDown = List(count(input, 64)) { StoredItem(input.readUTF(), input.readUTF(), ItemPlace(Container.WAREHOUSE), input.readInt(), input.readBoolean(), input.readInt()) }
                NodeLoot(lootId, source, items, picked.toSet(), elapsed, gold, putDown)
            } else null
            JourneyState(id, hero, target, rng, hp, max, round, stage, fight, pending, event, node, recentEvents, cooldown, rewards, loot)
        }
        val economy = if (version >= 4) HomeEconomyState(input.readBoolean(), input.readInt(), input.readLong(),
            input.readInt(), input.readLong(), input.readInt(), input.readInt()) else HomeEconomyState()
        val shop = if (version >= 5 && input.readBoolean()) {
            val revision = input.readInt(); val nextSeed = input.readLong()
            val offers = List(count(input, 1000)) { StoredShopOffer(input.readUTF(), ShopShelf.valueOf(input.readUTF()), input.readInt(), input.readInt()) }
            val purchases = mutableMapOf<String, Int>()
            repeat(count(input, 1000)) { val id = input.readUTF(); if (purchases.put(id, input.readInt()) != null) throw IOException("商品购买记录重复") }
            ShopSnapshot(revision, nextSeed, offers, purchases)
        } else null
        val heroes = if (version >= 5) List(count(input, FirstRunContent.heroes.size)) { input.readUTF() }.also { if (it.distinct().size != it.size) throw IOException("英雄解锁记录重复") }.toSet()
            else setOf("juggernaut", "abaddon")
        val pity = mutableMapOf<String, MaterialPityCounter>()
        if (version >= 6) repeat(count(input, 3)) {
            val category = input.readUTF(); val counter = MaterialPityCounter(input.readInt(), input.readBoolean())
            if (pity.put(category, counter) != null) throw IOException("材料保底类别重复")
        }
        val activeHero = if (version >= 12) input.readUTF() else "juggernaut"
        val loadouts = if (version >= 12) buildMap {
            repeat(count(input, FirstRunContent.heroes.size)) {
                val hero = input.readUTF(); val presets = List(count(input, 3)) { preset(input) }
                if (put(hero, presets) != null) throw IOException("配装英雄重复")
            }
        } else emptyMap()
        val selections = if (version >= 12) buildMap {
            repeat(count(input, FirstRunContent.heroes.size)) {
                val hero = input.readUTF(); val slot = input.readInt()
                if (put(hero, slot) != null) throw IOException("配装选择重复")
            }
        } else emptyMap()
        val locks = if (version >= 13) List(count(input, 10000)) { input.readUTF() }.also {
            if (it.distinct().size != it.size) throw IOException("物品锁定记录重复")
        }.toSet() else emptySet()
        return CampaignProgress(seed, batch, boxes, run, clears, extracted, ending, stock, cycle, seen, manual, economy, shop, heroes,
            MaterialPityState(pity), activeHero, loadouts, selections, locks)
    }
    private fun chest(out: DataOutputStream, c: TargetChest) {
        out.writeUTF(c.id); out.writeUTF(c.quality); out.writeInt(c.difficulty); out.writeInt(c.ticket); out.writeInt(c.appraisals)
        out.writeInt(c.items.size); c.items.forEach { out.writeUTF(it.definitionId); out.writeInt(it.x); out.writeInt(it.y); out.writeInt(it.reveal) }
        out.writeUTF(c.theme.name); out.writeInt(c.guaranteedCategories.size); c.guaranteedCategories.sorted().forEach(out::writeUTF)
    }
    private fun preset(out: DataOutputStream, value: LoadoutPreset) {
        fun id(value: String?) { out.writeBoolean(value != null); if (value != null) out.writeUTF(value) }
        value.equipment.forEach(::id); value.skills.forEach(::id)
        out.writeInt(value.manualItems.size); value.manualItems.sorted().forEach(out::writeUTF)
    }
    private fun preset(input: DataInputStream): LoadoutPreset {
        fun id() = if (input.readBoolean()) input.readUTF() else null
        val equipment = List(9) { id() }; val skills = List(8) { id() }
        val manual = List(count(input, 9)) { input.readUTF() }
        if (manual.distinct().size != manual.size) throw IOException("配装手动物品重复")
        return LoadoutPreset(equipment, skills, manual.toSet())
    }
    private fun chest(input: DataInputStream, version: Int): TargetChest {
        val id = input.readUTF(); val quality = input.readUTF(); val difficulty = input.readInt(); val ticket = input.readInt(); val appraisals = input.readInt()
        val items = List(count(input, 36)) { ChestItem(input.readUTF(), input.readInt(), input.readInt(), input.readInt()) }
        val theme = if (version >= 6) ChestTheme.valueOf(input.readUTF()) else ChestTheme.SUPPLIES
        val guaranteed = if (version >= 6) List(count(input, 1)) { input.readUTF() }.toSet() else emptySet()
        return TargetChest(id, quality, difficulty, ticket, items, appraisals, theme, guaranteed)
    }
    private fun fighter(out: DataOutputStream, f: Fighter) {
        out.writeUTF(f.heroId)
        listOf(f.hp, f.maxHp, f.mana, f.maxMana, f.attack, f.speed, f.bat, f.armor, f.magicResist, f.regen, f.manaRegen,
            f.shield, f.magicShield, f.skyShield, f.attackIn, f.ultIn, f.invulnerable, f.ghost, f.slashIn,
            f.criticalReady, f.broken, f.slowTime, f.slow, f.skinIn, f.flareIn).forEach(out::writeDouble)
        out.writeInt(f.flareLeft); out.writeInt(f.poisons.size)
        f.poisons.forEach { out.writeDouble(it.dps); out.writeDouble(it.remaining); out.writeUTF(it.source); out.writeBoolean(it.dispellable) }
        out.writeDouble(f.skinSlowTime)
        out.writeDouble(f.physicalShield); out.writeBoolean(f.shieldSources != null)
        f.shieldSources?.let { pool ->
            out.writeInt(pool.entries.size)
            pool.entries.forEach { entry ->
                out.writeUTF(entry.sourceId); out.writeUTF(entry.kind.name); out.writeDouble(entry.amount)
                out.writeBoolean(entry.remainingMs != null); entry.remainingMs?.let(out::writeLong)
            }
        }
        with(f.control) {
            listOf(dead, banished, stunned, hexed, silenced, muted, disarmed).forEach(out::writeBoolean)
        }
        with(f.cardState) {
            out.writeInt(cards.size)
            cards.forEach { out.writeUTF(it.instanceId); out.writeUTF(it.cardId); out.writeInt(it.star) }
            out.writeBoolean(started); out.writeInt(attackCount)
            out.writeInt(readyAt.size); readyAt.keys.sorted().forEach { out.writeUTF(it); out.writeInt(readyAt.getValue(it)) }
            out.writeInt(healed.size); healed.keys.sorted().forEach { out.writeUTF(it); out.writeDouble(healed.getValue(it)) }
            out.writeInt(buffs.size); buffs.forEach { out.writeUTF(it.sourceKey); out.writeUTF(it.kind.name); out.writeDouble(it.amount); out.writeInt(it.expiresAtTick) }
            out.writeInt(criticalFailures.size); criticalFailures.keys.sorted().forEach { out.writeUTF(it); out.writeInt(criticalFailures.getValue(it)) }
        }
        out.writeInt(f.poisonSerial)
        out.writeBoolean(f.poisonPool != null)
        f.poisonPool?.let { pool ->
            out.writeInt(pool.normal.size); pool.normal.forEach { poisonLayer(out, it) }
            out.writeInt(pool.virulent.size); pool.virulent.forEach { poisonLayer(out, it) }
        }
        out.writeLong(f.equipment.immunityMs)
        CombatExtensionCodec.gear(out, f.equipment)
        out.writeInt(f.timedControls.size)
        f.timedControls.forEach {
            out.writeUTF(it.sourceId); out.writeInt(it.caster); out.writeUTF(it.kind.name)
            out.writeLong(it.remainingMs); out.writeUTF(it.dispelClass.name); out.writeInt(it.appliedAtTick)
        }
        out.writeDouble(f.amManaBurn); out.writeDouble(f.amBurnReady); out.writeInt(f.axeHitsSinceSpin)
        out.writeDouble(f.armorBuff); out.writeDouble(f.armorBuffMs); out.writeDouble(f.speedBuff); out.writeDouble(f.speedBuffMs)
    }
    private fun poisonLayer(out: DataOutputStream, layer: PoisonCoreLayer) {
        out.writeUTF(layer.instanceId); out.writeUTF(layer.kind.name); out.writeUTF(layer.sourceId); out.writeUTF(layer.casterUnitId)
        out.writeDouble(layer.damagePerTick); out.writeInt(layer.ticksRemaining); out.writeLong(layer.nextTickAtMs)
        out.writeBoolean(layer.piercesDebuffImmunity)
    }
    private fun poisonLayer(input: DataInputStream): PoisonCoreLayer {
        val instanceId = input.readUTF(); val kind = PoisonKind.valueOf(input.readUTF())
        val sourceId = input.readUTF(); val casterUnitId = input.readUTF()
        val damagePerTick = input.readDouble(); val ticksRemaining = input.readInt(); val nextTickAtMs = input.readLong()
        return PoisonCoreLayer(instanceId, kind, sourceId, casterUnitId, damagePerTick, ticksRemaining, nextTickAtMs, input.readBoolean())
    }
    private fun fighter(input: DataInputStream, version: Int): Fighter {
        val hero = input.readUTF()
        val v = DoubleArray(25) { input.readDouble() }
        val flare = input.readInt()
        val poisons = List(count(input, 1000)) { Poison(input.readDouble(), input.readDouble(), input.readUTF(), input.readBoolean()) }
        val skinSlow = if (version >= 3) input.readDouble() else 0.0
        val physical = if (version >= 4) input.readDouble() else 0.0
        val shields = if (version >= 4 && input.readBoolean()) ShieldPool(List(count(input, 1000)) {
            val id = input.readUTF(); val kind = ShieldKind.valueOf(input.readUTF()); val amount = input.readDouble()
            ShieldEntry(id, kind, amount, if (input.readBoolean()) input.readLong() else null)
        }) else null
        val control = if (version >= 4) ActionControlState(input.readBoolean(), input.readBoolean(), input.readBoolean(),
            input.readBoolean(), input.readBoolean(), input.readBoolean(), input.readBoolean()) else ActionControlState()
        val decoded = Fighter(hero, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13],
            v[14], v[15], v[16], v[17], v[18], v[19], v[20], v[21], v[22], v[23], flare, v[24], poisons, skinSlow, shields, physical, control)
        if (version >= 7) {
            val cards = List(count(input, 8)) { CombatCard(input.readUTF(), input.readUTF(), input.readInt()) }
            val started = input.readBoolean(); val attacks = input.readInt()
            val ready = mutableMapOf<String, Int>()
            repeat(count(input, 128)) { val key = input.readUTF(); if (ready.put(key, input.readInt()) != null) throw IOException("卡牌冷却来源重复") }
            val healed = mutableMapOf<String, Double>()
            repeat(count(input, 128)) { val key = input.readUTF(); if (healed.put(key, input.readDouble()) != null) throw IOException("卡牌治疗计数重复") }
            val buffs = List(count(input, 64)) { CardBuff(input.readUTF(), CardBuffKind.valueOf(input.readUTF()), input.readDouble(), input.readInt()) }
            val failures = mutableMapOf<String, Int>()
            repeat(count(input, 16)) { val key = input.readUTF(); if (failures.put(key, input.readInt()) != null) throw IOException("暴击来源重复") }
            val runtime = CardBattleState(cards, started, attacks, ready, healed, buffs, failures)
            CardCombat.validate(runtime)
            val base = decoded.copy(cardState = runtime)
            if (version >= 8) {
                val serial = input.readInt()
                val pool = if (input.readBoolean()) {
                    val normal = List(count(input, 11000)) { poisonLayer(input) }
                    val virulent = List(count(input, 1000)) { poisonLayer(input) }
                    PoisonCorePool(normal, virulent)
                } else null
                val immunity = input.readLong()
                val gear = if (version >= 9) CombatExtensionCodec.gear(input, immunity) else EquipmentBattleState(immunity)
                val controls = if (version >= 10) List(count(input, 24)) {
                    com.example.douqiuqiu.status.TimedControl(input.readUTF(), input.readInt(),
                        com.example.douqiuqiu.status.ControlKind.valueOf(input.readUTF()), input.readLong(),
                        com.example.douqiuqiu.status.DispelClass.valueOf(input.readUTF()), input.readInt())
                } else emptyList()
                val amManaBurn = if (version >= 14) input.readDouble() else 0.0
                val amBurnReady = if (version >= 14) input.readDouble() else 0.0
                val axeHitsSinceSpin = if (version >= 14) input.readInt() else 0
                val armorBuff = if (version >= 14) input.readDouble() else 0.0
                val armorBuffMs = if (version >= 14) input.readDouble() else 0.0
                val speedBuff = if (version >= 14) input.readDouble() else 0.0
                val speedBuffMs = if (version >= 14) input.readDouble() else 0.0
                return base.copy(poisonSerial = serial, poisonPool = pool, equipment = gear, timedControls = controls,
                    amManaBurn = amManaBurn, amBurnReady = amBurnReady, axeHitsSinceSpin = axeHitsSinceSpin,
                    armorBuff = armorBuff, armorBuffMs = armorBuffMs, speedBuff = speedBuff, speedBuffMs = speedBuffMs)
            }
            return base
        }
        if (version >= 4) return decoded // Legacy fight preserves its old panel; no opening effects replay.
        // Old versions merged Abaddon's starting/card shield with Aphotic using max().
        // Preserve that old aggregate as one source for this battle, never invent a
        // second grant during migration. The next battle rebuilds independent sources.
        val migrated = FighterEffects.shields(decoded).entries.map { entry ->
            if (hero == "abaddon" && entry.sourceId == "legacy:universal") entry.copy(sourceId = "hero:aphotic") else entry
        }
        return FighterEffects.withShields(decoded, ShieldPool(migrated))
    }
    private fun count(input: DataInputStream, max: Int): Int = input.readInt().also {
        if (it !in 0..max) throw IOException("存档集合长度无效")
    }
}
