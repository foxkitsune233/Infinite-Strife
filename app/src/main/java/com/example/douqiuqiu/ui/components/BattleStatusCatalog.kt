package com.example.douqiuqiu.ui.components

import com.example.douqiuqiu.cards.*
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.journey.Fighter
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class StatusKind { INVULNERABLE, GHOST, BREAK, STUN, HEX, BANISHED, MUTED, DISARMED, SILENCE,
    IMMUNITY, PROTECTED, HASTE, ARMOR_DOWN, NORMAL_POISON, VIRULENT_POISON, LEGACY_POISON, SLOW, CRITICAL_READY, EQUIPMENT, FROST_DOT }
data class BattleStatusEntry(val key: String, val kind: StatusKind, val detail: String, val badge: String = "")

/** Labels and icon identity share a typed entry; no translated-string parsing. */
object BattleStatusCatalog {
    fun icon(kind: StatusKind): IconSpec = when (kind) {
        StatusKind.INVULNERABLE, StatusKind.IMMUNITY -> IconSpec(IconGlyph.SHIELD, ItemIconCatalog.GOLD)
        StatusKind.GHOST -> IconSpec(IconGlyph.CLOAK, ItemIconCatalog.GREEN)
        StatusKind.BREAK -> IconSpec(IconGlyph.SHIELD, ItemIconCatalog.RED, variant = 2)
        StatusKind.STUN -> IconSpec(IconGlyph.SPIRAL, ItemIconCatalog.GOLD)
        StatusKind.HEX -> IconSpec(IconGlyph.MASK, ItemIconCatalog.PURPLE)
        StatusKind.BANISHED -> IconSpec(IconGlyph.ORB, ItemIconCatalog.PURPLE)
        StatusKind.MUTED, StatusKind.SILENCE -> IconSpec(IconGlyph.BAN, if (kind == StatusKind.MUTED) ItemIconCatalog.GOLD else ItemIconCatalog.PURPLE)
        StatusKind.DISARMED -> IconSpec(IconGlyph.SWORD, ItemIconCatalog.RED)
        StatusKind.PROTECTED -> IconSpec(IconGlyph.EYE, ItemIconCatalog.BLUE)
        StatusKind.HASTE -> IconSpec(IconGlyph.BOOT, ItemIconCatalog.GREEN)
        StatusKind.ARMOR_DOWN -> IconSpec(IconGlyph.HELMET, ItemIconCatalog.RED)
        StatusKind.NORMAL_POISON, StatusKind.LEGACY_POISON -> IconSpec(IconGlyph.DROP, ItemIconCatalog.GREEN)
        StatusKind.VIRULENT_POISON -> IconSpec(IconGlyph.SPORE, ItemIconCatalog.PURPLE, ItemIconCatalog.GREEN)
        StatusKind.SLOW -> IconSpec(IconGlyph.SNOW, ItemIconCatalog.BLUE)
        StatusKind.FROST_DOT -> IconSpec(IconGlyph.SNOW, ItemIconCatalog.BLUE)
        StatusKind.CRITICAL_READY -> IconSpec(IconGlyph.CROSS, ItemIconCatalog.RED)
        StatusKind.EQUIPMENT -> IconSpec(IconGlyph.STAR, ItemIconCatalog.GOLD)
    }
    fun entries(f: Fighter, tick: Int): List<BattleStatusEntry> = buildList {
        fun add(kind: StatusKind, label: String, seconds: Double? = null, amount: String = "") {
            val time = seconds?.let { " ${ceil(it * 10).toInt() / 10.0}秒" } ?: ""
            add(BattleStatusEntry(kind.name, kind, label + time, amount.ifEmpty { seconds?.let { "${ceil(it).toInt()}s" } ?: "" }))
        }
        if (f.invulnerable > 0) add(StatusKind.INVULNERABLE, "无敌", f.invulnerable)
        if (f.ghost > 0) add(StatusKind.GHOST, "虚无", f.ghost)
        if (f.broken > 0) add(StatusKind.BREAK, "破坏", f.broken)
        val control = com.example.douqiuqiu.status.TimedControls.current(f)
        fun controlBadge(kind: StatusKind, label: String, timedKind: com.example.douqiuqiu.status.ControlKind, active: Boolean) {
            if (!active) return
            val sources = f.timedControls.filter { it.kind == timedKind }
            val seconds = sources.maxOfOrNull { it.remainingMs }?.div(1000.0)
            add(kind, label + if (sources.isEmpty()) "" else " · ${sources.size}来源", seconds)
            if (sources.isNotEmpty()) {
                val entry = last()
                this[size - 1] = entry.copy(detail = entry.detail + "\n" + sources.joinToString("\n") {
                    "${it.sourceId} · ${ceil(it.remainingMs / 100.0).toInt() / 10.0}秒 · ${it.dispelClass}"
                })
            }
        }
        controlBadge(StatusKind.STUN, "眩晕", com.example.douqiuqiu.status.ControlKind.STUN, control.stunned)
        controlBadge(StatusKind.HEX, "妖术", com.example.douqiuqiu.status.ControlKind.HEX, control.hexed)
        if (control.banished) add(StatusKind.BANISHED, "放逐")
        if (control.muted) add(StatusKind.MUTED, "物品禁用")
        controlBadge(StatusKind.DISARMED, "缴械", com.example.douqiuqiu.status.ControlKind.DISARM, control.disarmed)
        controlBadge(StatusKind.SILENCE, "沉默", com.example.douqiuqiu.status.ControlKind.SILENCE,
            control.silenced || EquipmentCombat.active(f.equipment, "mask_madness"))
        if (f.equipment.immunityMs > 0) add(StatusKind.IMMUNITY, "减益免疫" +
            if (f.timedControls.isEmpty()) "" else " · 抑制${f.timedControls.size}项控制", f.equipment.immunityMs / 1000.0)
        if (f.equipment.protectionMs > 0) add(StatusKind.PROTECTED, "普攻保护", f.equipment.protectionMs / 1000.0)
        val speed = CardCombat.buffAmount(f.cardState, CardBuffKind.ATTACK_SPEED) + EquipmentCombat.speed(f.equipment)
        if (speed > 0) add(StatusKind.HASTE, "攻速 +${speed.roundToInt()}", amount = "+${speed.roundToInt()}")
        val reduction = CardCombat.buffAmount(f.cardState, CardBuffKind.ARMOR_REDUCTION)
        if (reduction > 0) add(StatusKind.ARMOR_DOWN, "护甲 -${reduction.roundToInt()}", amount = "-${reduction.roundToInt()}")
        val allNormal = f.poisonPool?.normal.orEmpty()
        val frost = allNormal.filter { CardCombat.isFrostDotSource(it.sourceId) }
        if (frost.isNotEmpty()) {
            val remaining = (frost.minOf { it.nextTickAtMs } - tick * 100L).coerceAtLeast(0)
            add(StatusKind.FROST_DOT, "冰霜持续伤害 ×${frost.size} · 下跳${ceil(remaining / 100.0).toInt() / 10.0}秒", amount = "×${frost.size}")
        }
        val normal = allNormal.filterNot { CardCombat.isFrostDotSource(it.sourceId) }
        if (normal.isNotEmpty()) {
            val remaining = (normal.minOf { it.nextTickAtMs } - tick * 100L).coerceAtLeast(0)
            add(StatusKind.NORMAL_POISON, "普通毒 ×${normal.size} · 下跳${ceil(remaining / 100.0).toInt() / 10.0}秒", amount = "×${normal.size}")
        }
        val virulent = f.poisonPool?.virulent.orEmpty()
        if (virulent.isNotEmpty()) add(StatusKind.VIRULENT_POISON, "剧毒 ×${virulent.size} · 每秒产毒", amount = "×${virulent.size}")
        if (f.poisons.isNotEmpty()) add(StatusKind.LEGACY_POISON, "旧档中毒 ×${f.poisons.size}", amount = "×${f.poisons.size}")
        if (f.slowTime > 0 || f.skinSlowTime > 0) add(StatusKind.SLOW, "减速", maxOf(f.slowTime, f.skinSlowTime))
        if (f.criticalReady > 0) add(StatusKind.CRITICAL_READY, "确保暴击就绪", f.criticalReady)
        f.equipment.activeGroups.toSortedMap().forEach { (group, ms) ->
            val effect = EquipmentCombat.effects(f.equipment).firstOrNull { it.group == group }
            val name = effect?.let { EquipmentCombat.definitions[it.item.definitionId]?.name } ?: "装备增益"
            add(BattleStatusEntry("gear:$group", StatusKind.EQUIPMENT, "$name · ${ceil(ms / 100.0).toInt() / 10.0}秒", "${ceil(ms / 1000.0).toInt()}s"))
        }
    }
}
