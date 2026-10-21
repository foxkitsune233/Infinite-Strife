package com.example.douqiuqiu.ui.components

import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.ItemKind

enum class IconGlyph { SWORD, AXE, HAMMER, STAFF, BOW, SPEAR, DUAL_SWORDS, SHIELD, CROWN, GLOVE, BOOT, CLOAK,
    HELMET, ARMLET, RING, AMULET, GEM, ORB, MASK, BUTTERFLY, HEART, PIPE, LEAF, POTION, FLAME, CHEESE,
    SCROLL, FABRIC, LOCK, FRAME, SHARD, CLAW, SPIRAL, STAR, DROP, SPORE, CHAIN, CROSS, EYE, SNOW, BAN,
    COIN, CUP, VASE, PAINTING, CLOCK, STATUE, BOX }
data class IconSpec(val glyph: IconGlyph, val tint: Int, val accent: Int = 0xFFE9BC66.toInt(), val variant: Int = 0)

/** Explicit ID mapping; neither Chinese labels nor hash codes decide an item's artwork. */
object ItemIconCatalog {
    const val RED = 0xFFDC796B.toInt()
    const val GREEN = 0xFF89CE86.toInt()
    const val BLUE = 0xFF77BFF0.toInt()
    const val PURPLE = 0xFFB29DE8.toInt()
    const val GOLD = 0xFFE9BC66.toInt()
    private val icons = buildMap<String, IconSpec> {
        fun putAll(ids: String, glyph: IconGlyph, color: Int, variant: Int = 0, accent: Int = GOLD) {
            ids.split(' ').forEach { put(it, IconSpec(glyph, color, accent, variant)) }
        }
        putAll("iron_branch", IconGlyph.LEAF, GREEN, 1)
        putAll("gauntlets", IconGlyph.GLOVE, RED)
        putAll("slippers", IconGlyph.BOOT, GREEN)
        putAll("mantle", IconGlyph.CLOAK, BLUE)
        putAll("circlet", IconGlyph.RING, GOLD)
        putAll("broadsword", IconGlyph.SWORD, 0xFFDADBD2.toInt())
        putAll("bracer", IconGlyph.ARMLET, RED)
        putAll("wraith_band", IconGlyph.ARMLET, GREEN, 1)
        putAll("null_talisman", IconGlyph.AMULET, BLUE)
        putAll("crown", IconGlyph.CROWN, GOLD)
        putAll("diadem", IconGlyph.CROWN, BLUE, 1)
        putAll("belt_of_strength", IconGlyph.ARMLET, RED, 2)
        putAll("band_of_elvenskin", IconGlyph.FABRIC, GREEN)
        putAll("robe_of_magi", IconGlyph.CLOAK, BLUE, 1)
        putAll("ogre_axe", IconGlyph.AXE, RED)
        putAll("reaver", IconGlyph.AXE, RED, 1)
        putAll("blade_of_alacrity", IconGlyph.SWORD, GREEN, 1)
        putAll("staff_of_wizardry", IconGlyph.STAFF, BLUE)
        putAll("mystic_staff", IconGlyph.STAFF, PURPLE, 1)
        putAll("eaglesong", IconGlyph.BOW, GREEN)
        putAll("blades_of_attack", IconGlyph.CLAW, 0xFFE0DBCC.toInt())
        putAll("claymore", IconGlyph.SWORD, 0xFFB7C6D3.toInt(), 1)
        putAll("mithril_hammer", IconGlyph.HAMMER, BLUE)
        putAll("demon_edge", IconGlyph.SWORD, RED, 2)
        putAll("javelin", IconGlyph.SPEAR, 0xFFDADBD2.toInt())
        putAll("gloves_of_haste", IconGlyph.GLOVE, GOLD, 1)
        putAll("quarterstaff", IconGlyph.STAFF, GOLD, 2)
        putAll("hyperstone", IconGlyph.GEM, PURPLE, 2)
        putAll("orb_of_venom", IconGlyph.ORB, GREEN)
        putAll("chainmail", IconGlyph.SHIELD, 0xFF9AB0BA.toInt(), 2)
        putAll("helm_of_iron_will", IconGlyph.HELMET, RED)
        putAll("cloak", IconGlyph.CLOAK, PURPLE)
        putAll("shawl", IconGlyph.CLOAK, GREEN, 1)
        putAll("fluffy_hat", IconGlyph.HELMET, RED, 1)
        putAll("talisman_of_evasion", IconGlyph.AMULET, GREEN, 1)
        putAll("ring_of_health", IconGlyph.RING, RED, 1)
        putAll("ring_of_regen", IconGlyph.RING, GREEN, 1)
        putAll("vitality_booster", IconGlyph.ORB, RED)
        putAll("energy_booster", IconGlyph.ORB, BLUE)
        putAll("point_booster", IconGlyph.ORB, PURPLE, 1)
        putAll("voodoo_mask", IconGlyph.RING, GREEN, 2)
        putAll("morbid_mask", IconGlyph.MASK, RED)
        putAll("sage_mask", IconGlyph.MASK, BLUE)
        putAll("void_stone", IconGlyph.GEM, BLUE, 1)
        putAll("perseverance", IconGlyph.CROWN, GREEN, 2)
        putAll("vanguard_shield", IconGlyph.SHIELD, 0xFFD9BA92.toInt())
        putAll("oblivion_staff", IconGlyph.STAFF, BLUE, 2)
        putAll("soul_booster", IconGlyph.ORB, PURPLE, 2)
        putAll("dragon_lance", IconGlyph.SPEAR, RED, 1)
        putAll("expert_array", IconGlyph.CROSS, GOLD)
        putAll("mask_of_madness", IconGlyph.MASK, RED, 1)
        putAll("maelstrom", IconGlyph.HAMMER, BLUE, 1)
        putAll("mjollnir", IconGlyph.HAMMER, PURPLE, 2)
        putAll("kaya", IconGlyph.SWORD, BLUE, 2)
        putAll("sange", IconGlyph.SWORD, RED, 1)
        putAll("yasha", IconGlyph.SWORD, GREEN, 1)
        putAll("sange_and_yasha", IconGlyph.DUAL_SWORDS, RED, accent = GREEN)
        putAll("kaya_and_sange", IconGlyph.DUAL_SWORDS, BLUE, accent = RED)
        putAll("yasha_and_kaya", IconGlyph.DUAL_SWORDS, GREEN, accent = BLUE)
        putAll("octarine_core", IconGlyph.GEM, PURPLE, 3)
        putAll("crystalys", IconGlyph.SWORD, BLUE, 3)
        putAll("daedalus", IconGlyph.BOW, RED, 1)
        putAll("crimson_guard", IconGlyph.SHIELD, RED, 1)
        putAll("pipe_of_insight", IconGlyph.PIPE, GREEN)
        putAll("armlet_of_mordiggian", IconGlyph.ARMLET, RED, 3)
        putAll("heart_of_tarrasque", IconGlyph.HEART, RED)
        putAll("satanic", IconGlyph.MASK, RED, 2)
        putAll("butterfly", IconGlyph.BUTTERFLY, GREEN)
        putAll("witch_blade", IconGlyph.SWORD, PURPLE, 3)
        putAll("rod_of_atos", IconGlyph.AXE, PURPLE, 1)
        putAll("viper_bite", IconGlyph.CLAW, GREEN, 1)
        putAll("force_staff", IconGlyph.STAFF, BLUE, 3)
        putAll("hurricane_pike", IconGlyph.SPEAR, BLUE, 2)
        putAll("black_king_bar", IconGlyph.STAFF, GOLD, 3, accent = 0xFF45414B.toInt())
        putAll("skull_basher", IconGlyph.HAMMER, RED, 3)
        putAll("orchid", IconGlyph.STAFF, PURPLE, 3)
        putAll("heavens_halberd", IconGlyph.AXE, GOLD, 2, accent = GREEN)
        // 0.1.5 怒火／寒霜装备：显式图标，卷轴自动继承产物配色
        putAll("bloodrage_gauntlet", IconGlyph.GLOVE, RED, 2)
        putAll("frenzy_heart", IconGlyph.HEART, RED, 1)
        putAll("frost_shard_blade", IconGlyph.SWORD, BLUE, 2, accent = 0xFFBFE6FF.toInt())
        putAll("glacier_plate", IconGlyph.SHIELD, BLUE, 2, accent = 0xFFDCEEFF.toInt())
        putAll("salve", IconGlyph.POTION, GREEN)
        putAll("tango", IconGlyph.LEAF, GREEN)
        putAll("faerie_fire", IconGlyph.FLAME, GOLD)
        putAll("cheese", IconGlyph.CHEESE, GOLD)
        putAll("essence", IconGlyph.SPIRAL, PURPLE)
        putAll("backpack_fabric", IconGlyph.FABRIC, GOLD)
        putAll("skill_shard", IconGlyph.SHARD, BLUE)
        putAll("safe_lock", IconGlyph.LOCK, GOLD)
        putAll("safe_frame", IconGlyph.FRAME, GOLD)
        // 0.1.6 护芯：复用已有 glyph + 变体配色，零新增素材
        putAll("equipment_guard_core", IconGlyph.SHIELD, GOLD, 1)
        putAll("skill_guard_core", IconGlyph.SHARD, PURPLE, 1)
        putAll("loot_copper_coin", IconGlyph.COIN, 0xFFC78C58.toInt())
        putAll("loot_silver_coin", IconGlyph.COIN, 0xFFBDD0DA.toInt())
        putAll("loot_tin_cup", IconGlyph.CUP, 0xFF96A6A6.toInt())
        putAll("loot_wood_box", IconGlyph.BOX, 0xFFB58C63.toInt())
        putAll("loot_faded_painting", IconGlyph.PAINTING, 0xFF9DAB9B.toInt())
        putAll("loot_old_carpet", IconGlyph.FABRIC, 0xFF968E84.toInt())
        putAll("loot_pocket_watch", IconGlyph.CLOCK, GOLD)
        putAll("loot_porcelain_vase", IconGlyph.VASE, BLUE)
        putAll("loot_silver_cups", IconGlyph.CUP, 0xFFCDDDE4.toInt(), 1)
        putAll("loot_tapestry", IconGlyph.FABRIC, RED, 1)
        putAll("loot_ruby", IconGlyph.GEM, RED)
        putAll("loot_gold_seal", IconGlyph.BOX, GOLD, 1)
        putAll("loot_jade_statue", IconGlyph.STATUE, GREEN)
        putAll("loot_oil_painting", IconGlyph.PAINTING, BLUE)
        putAll("loot_bronze_clock", IconGlyph.CLOCK, 0xFFBC9763.toInt(), 1)
        putAll("loot_star_diamond", IconGlyph.GEM, 0xFFCAECFA.toInt(), 3)
        putAll("loot_royal_pendant", IconGlyph.AMULET, PURPLE, 1)
        putAll("loot_jeweled_egg", IconGlyph.ORB, PURPLE, 2)
        putAll("loot_golden_statue", IconGlyph.STATUE, GOLD)
        putAll("loot_royal_screen", IconGlyph.PAINTING, RED, 1)
    }
    private val recipeAliases = mapOf("recipe_armlet" to "armlet_of_mordiggian", "recipe_heart" to "heart_of_tarrasque")
    fun explicit(id: String): IconSpec? {
        icons[id]?.let { return it }
        if (id.startsWith("recipe_")) {
            val product = recipeAliases[id] ?: id.removePrefix("recipe_")
            icons[product]?.let { return IconSpec(IconGlyph.SCROLL, it.tint, it.accent, it.glyph.ordinal) }
        }
        return null
    }
    fun spec(d: ItemDefinition) = explicit(d.id) ?: IconSpec(when (d.kind) {
        ItemKind.SCROLL -> IconGlyph.SCROLL; ItemKind.CONSUMABLE -> IconGlyph.POTION
        ItemKind.MATERIAL -> IconGlyph.SHARD; ItemKind.LOOT -> IconGlyph.GEM
        ItemKind.CARD -> IconGlyph.STAR; else -> IconGlyph.SWORD
    }, GOLD)
}
