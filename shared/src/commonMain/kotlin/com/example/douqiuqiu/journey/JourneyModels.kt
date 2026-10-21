package com.example.douqiuqiu.journey

/** 战斗日志保留上限；旧版按 10 上限读回，v14 起放宽到该值。 */
const val LOG_CAP: Int = 200

data class ChestItem(val definitionId: String, val x: Int, val y: Int, val reveal: Int = 0)
data class TargetChest(val id: String, val quality: String, val difficulty: Int, val ticket: Int,
                       val items: List<ChestItem>, val appraisals: Int = 0,
                       val theme: ChestTheme = ChestTheme.SUPPLIES,
                       val guaranteedCategories: Set<String> = emptySet())

enum class ChestTheme(val title: String) {
    SUPPLIES("综合补给"), ARSENAL("武备"), ARCANE("秘术"), BACKPACK("背包工程"), SKILLS("技能传承"), SAFE("安全箱工程")
}
enum class JourneyStage { READY, BATTLE, REWARD, EVENT, REST }
enum class RewardChoice { HEAL, GOLD, ITEM }
data class JourneyState(
    val id: String, val heroId: String, val target: TargetChest, val rng: Long,
    val hp: Double, val maxHp: Double, val round: Int = 1,
    val stage: JourneyStage = JourneyStage.READY, val fight: DuelState? = null,
    val pendingItem: String = "", val eventRoll: Int = 0,
    val nodeEvent: com.example.douqiuqiu.event.NodeEventInstance? = null,
    /** 最近两次已生成的事件类型，用于避免连续重复；随存档保存。 */
    val recentEventKinds: List<com.example.douqiuqiu.event.EventKind> = emptyList(),
    val equipmentCooldowns: com.example.douqiuqiu.equipment.EquipmentCooldownState = com.example.douqiuqiu.equipment.EquipmentCooldownState(),
    val rewardVersion: Int = EconomyBalance.VERSION,
    val nodeLoot: NodeLoot? = null
)
data class CampaignProgress(
    val seed: Long = 1729, val batch: Int = 0, val chests: List<TargetChest> = emptyList(),
    val run: JourneyState? = null, val clears: Int = 0, val extracted: Boolean = false,
    val lastEnding: String = "", val shopStock: Map<String, Int> = emptyMap(),
    val shopCycle: Long = 0, val shopSeen: Long = 0,
    val manualItems: Set<String> = emptySet(),
    val economy: HomeEconomyState = HomeEconomyState(),
    val shop: com.example.douqiuqiu.shop.ShopSnapshot? = null,
    val unlockedHeroes: Set<String> = setOf("juggernaut", "abaddon", "anti_mage", "axe", "faceless_void", "legion_commander"),
    val materialPity: com.example.douqiuqiu.loot.MaterialPityState = com.example.douqiuqiu.loot.MaterialPityState(),
    /** Camp-only hero selection. Active runs keep [JourneyState.heroId] immutable. */
    val activeHeroId: String = "juggernaut",
    /** Each hero has at most three saved equipment / card configurations. */
    val loadouts: Map<String, List<com.example.douqiuqiu.inventory.LoadoutPreset>> = emptyMap(),
    /** Last explicitly saved or applied preset per hero, used by hero switching. */
    val loadoutSelections: Map<String, Int> = emptyMap(),
    /** Instance locks prevent sale and crafting consumption, but never death losses. Stale IDs are allowed. */
    val lockedItems: Set<String> = emptySet(),
    /** Per-hero levels for the 0.1.6 hero level system. Missing heroes default to level 1. */
    val heroLevels: Map<String, Int> = emptyMap(),
    /** Quest counters: questId -> progress value. One-shot quests keep their final value after claiming. */
    val questCounters: Map<String, Int> = emptyMap(),
    /** Quests whose rewards have been claimed. */
    val claimedQuests: Set<String> = emptySet(),
    /** Equipment guard level 0..3: how many equipped items survive death. */
    val equipmentGuardLevel: Int = 0,
    /** Skill guard level 0..4: how many skill cards survive death. */
    val skillGuardLevel: Int = 0,
    /** Permanently protected equipment instance IDs until manually unguarded. Stale IDs are allowed. */
    val guardedEquipment: Set<String> = emptySet(),
    /** Permanently protected skill card instance IDs until manually unguarded. Stale IDs are allowed. */
    val guardedSkills: Set<String> = emptySet()
)

/** Platform-neutral persisted counters. Recovery calculations use the supplied clock in the adapter. */
data class HomeEconomyState(
    val initialized: Boolean = false,
    val refreshCharges: Int = 3, val refreshAnchorMs: Long = 0,
    val reliefCharges: Int = 2, val reliefAnchorMs: Long = 0,
    val reliefClaims: Int = 0, val vouchers: Int = 0
)
data class Poison(val dps: Double, val remaining: Double, val source: String, val dispellable: Boolean = true)
data class Fighter(
    val heroId: String, val hp: Double, val maxHp: Double,
    val mana: Double, val maxMana: Double, val attack: Double,
    val speed: Double, val bat: Double, val armor: Double, val magicResist: Double,
    val regen: Double, val manaRegen: Double,
    val shield: Double = 0.0, val magicShield: Double = 0.0, val skyShield: Double = 0.0,
    val attackIn: Double = 0.0, val ultIn: Double = 0.0,
    val invulnerable: Double = 0.0, val ghost: Double = 0.0, val slashIn: Double = 0.0,
    val criticalReady: Double = 0.0, val broken: Double = 0.0,
    val slowTime: Double = 0.0, val slow: Double = 0.0, val skinIn: Double = 0.0,
    val flareLeft: Int = 0, val flareIn: Double = 0.0,
    val poisons: List<Poison> = emptyList(), val skinSlowTime: Double = 0.0,
    val shieldSources: com.example.douqiuqiu.combat.ShieldPool? = null,
    val physicalShield: Double = 0.0,
    val control: com.example.douqiuqiu.status.ActionControlState = com.example.douqiuqiu.status.ActionControlState(),
    val cardState: com.example.douqiuqiu.cards.CardBattleState = com.example.douqiuqiu.cards.CardBattleState(),
    val poisonPool: com.example.douqiuqiu.status.PoisonCorePool? = null,
    val poisonSerial: Int = 0,
    val equipment: com.example.douqiuqiu.equipment.EquipmentBattleState = com.example.douqiuqiu.equipment.EquipmentBattleState(),
    val timedControls: List<com.example.douqiuqiu.status.TimedControl> = emptyList(),
    /** 敌法师：本场对当前目标实际削蓝总量（随战斗快照保存）。 */
    val amManaBurn: Double = 0.0,
    /** 敌法师：削蓝每秒至多触发一次的就绪倒计时（秒）。 */
    val amBurnReady: Double = 0.0,
    /** 斧王：距上次反击螺旋已承受的「进入伤害结算」普攻次数。 */
    val axeHitsSinceSpin: Int = 0,
    /** 斧王/军团：临时护甲增益数值（生效于 damage 护甲计算）。 */
    val armorBuff: Double = 0.0,
    /** 护甲增益剩余时间（秒），归零即清除。 */
    val armorBuffMs: Double = 0.0,
    /** 军团：临时攻速增益数值（生效于 interval 速度计算）。 */
    val speedBuff: Double = 0.0,
    /** 攻速增益剩余时间（秒），归零即清除。 */
    val speedBuffMs: Double = 0.0
)
data class DuelState(val player: Fighter, val enemy: Fighter, val rng: Long, val tick: Int = 0,
                     val won: Boolean? = null, val log: List<String> = emptyList())
data class HeroSpec(val id: String, val name: String, val strength: Int, val agility: Int, val intelligence: Int,
                    val baseAttack: Int, val bat: Double, val baseArmor: Int, val manaRegen: Double, val regen: Double,
                    val manaCost: Int, val cooldown: Double)

object FirstRunContent {
    val heroes = listOf(
        HeroSpec("juggernaut", "主宰", 24, 26, 16, 35, 1.5, 1, 5.0, 0.5, 80, 16.0),
        HeroSpec("skywrath", "天怒法师", 18, 18, 30, 30, 1.7, 0, 9.0, 0.5, 90, 8.0),
        HeroSpec("phantom_assassin", "幻影刺客", 22, 28, 15, 40, 1.7, 1, 5.0, 0.5, 70, 10.0),
        HeroSpec("abaddon", "亚巴顿", 28, 18, 22, 22, 1.7, 2, 7.0, 1.0, 140, 9.0),
        HeroSpec("necrophos", "瘟疫法师", 24, 16, 27, 30, 1.7, 1, 7.0, 1.0, 85, 18.0),
        HeroSpec("viper", "冥界亚龙", 24, 24, 20, 34, 1.7, 1, 6.0, 0.5, 75, 9.0),
        // 0.1.5 新增四英雄（女性敌法师 / 斧王 / 虚空假面 / 军团指挥官）
        HeroSpec("anti_mage", "敌法师", 20, 30, 18, 36, 1.45, 1, 5.0, 0.5, 75, 12.0),
        HeroSpec("axe", "斧王", 32, 20, 16, 30, 1.70, 3, 4.0, 1.5, 90, 14.0),
        HeroSpec("faceless_void", "虚空假面", 26, 28, 18, 34, 1.60, 2, 5.0, 0.75, 85, 16.0),
        HeroSpec("legion_commander", "军团指挥官", 30, 24, 18, 33, 1.55, 2, 4.5, 1.0, 80, 15.0)
    )
    fun hero(id: String) = heroes.first { it.id == id }
    fun next(seed: Long): Long = (seed.coerceIn(1, 2147483646) * 48271) % 2147483647
}
