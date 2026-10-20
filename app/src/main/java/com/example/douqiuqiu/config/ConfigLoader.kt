package com.example.douqiuqiu.config

import android.content.Context
import com.example.douqiuqiu.model.BalanceConfig
import com.example.douqiuqiu.model.BattleConfig
import com.example.douqiuqiu.model.DifficultyConfig
import com.example.douqiuqiu.model.EndingTexts
import com.example.douqiuqiu.model.EnemyScale
import com.example.douqiuqiu.model.GameEventTemplate
import com.example.douqiuqiu.model.HeroTemplate
import com.example.douqiuqiu.model.NodeKind
import com.example.douqiuqiu.model.RestNodeConfig
import com.example.douqiuqiu.model.RunMapConfig
import com.example.douqiuqiu.model.RunNode
import com.example.douqiuqiu.model.SchoolEffect
import com.example.douqiuqiu.model.SchoolTemplate
import com.example.douqiuqiu.model.SkillCardTemplate
import com.example.douqiuqiu.model.SkillTemplate
import com.example.douqiuqiu.model.WeaponStat
import com.example.douqiuqiu.model.WeaponTemplate
import org.json.JSONArray
import org.json.JSONObject

/**
 * 从 assets/config 目录下的 json 文件读取配置，转成 Kotlin 数据对象。
 * 注意：注释里不要写 "斜杠+星号"，Kotlin 支持嵌套块注释会误判。
 * 数据驱动：调平衡只改 JSON，不改代码。
 */
object ConfigLoader {

    fun loadHeroes(context: Context): List<HeroTemplate> {
        val arr = readJson(context, "heroes.json").getJSONArray("heroes")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HeroTemplate(
                id = o.getString("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", ""),
                trait = o.optString("trait", ""),
                color = o.optString("color", "#FFFFFF"),
                hp = o.optInt("hp", 1000),
                attack = o.optInt("attack", 50),
                attackSpeed = o.optDouble("attackSpeed", 1.0),
                armor = o.optInt("armor", 0),
                magicResist = o.optInt("magicResist", 0),
                ult = o.optString("ult", ""),
                passive = o.optString("passive", "")
            )
        }
    }

    fun loadSchools(context: Context): List<SchoolTemplate> {
        val arr = readJson(context, "schools.json").getJSONArray("schools")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val effArr = o.getJSONArray("effects")
            val effects = (0 until effArr.length()).map { j ->
                val e = effArr.getJSONObject(j)
                SchoolEffect(
                    atkSpeedPct = e.optInt("atkSpeedPct", 0),
                    attack = e.optInt("attack", 0),
                    ultPct = e.optInt("ultPct", 0),
                    manaRegen = e.optInt("manaRegen", 0),
                    magicResistPct = e.optInt("magicResistPct", 0),
                    hpPct = e.optInt("hpPct", 0),
                    shieldStack = e.optInt("shieldStack", 0),
                    critDmgPct = e.optInt("critDmgPct", 0),
                    crit = e.optInt("crit", 0),
                    physResistPct = e.optInt("physResistPct", 0),
                    evasion = e.optInt("evasion", 0)
                )
            }
            SchoolTemplate(
                id = o.getString("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", ""),
                effects = effects
            )
        }
    }

    fun loadWeapons(context: Context): List<WeaponTemplate> {
        val arr = readJson(context, "weapons.json").getJSONArray("weapons")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val s = o.optJSONObject("stats")
            WeaponTemplate(
                id = o.getString("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", ""),
                quality = o.optString("quality", "白"),
                value = o.optInt("value", 0),
                price = o.optInt("price", 0),
                stats = if (s == null) WeaponStat() else WeaponStat(
                    all = s.optInt("all", 0),
                    str = s.optInt("str", 0),
                    agi = s.optInt("agi", 0),
                    int = s.optInt("int", 0),
                    attack = s.optInt("attack", 0),
                    hp = s.optInt("hp", 0),
                    crit = s.optInt("crit", 0),
                    evasion = s.optInt("evasion", 0),
                    lifesteal = s.optInt("lifesteal", 0),
                    atkSpeedPct = s.optInt("atkSpeedPct", 0)
                )
            )
        }
    }

    fun loadSkillCards(context: Context): List<SkillCardTemplate> {
        val arr = readJson(context, "skill_cards.json").getJSONArray("cards")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SkillCardTemplate(
                id = o.getString("id"),
                school = o.getString("school"),
                quality = o.optString("quality", "蓝"),
                exp = o.optInt("exp", 1),
                price = o.optInt("price", 0),
                name = o.optString("name", ""),
                desc = o.optString("desc", ""),
                requireStar = o.optInt("requireStar", 0)
            )
        }
    }

    fun loadBalance(context: Context): BalanceConfig {
        val o = readJson(context, "balance.json")
        return BalanceConfig(
            initialGold = o.optInt("initialGold", 300),
            incomePerRound = o.optInt("incomePerRound", 250),
            interestPer100 = o.optInt("interestPer100", 10),
            interestCap = o.optInt("interestCap", 100),
            winBase = o.optInt("winBase", 50),
            winStreakBonus = o.optInt("winStreakBonus", 20),
            winStreakCap = o.optInt("winStreakCap", 100),
            losePerHp = o.optInt("losePerHp", 15),
            loseStreakBonus = o.optInt("loseStreakBonus", 15),
            loseStreakCap = o.optInt("loseStreakCap", 60),
            intelCost = o.optInt("intelCost", 80),
            maxIntelPerRound = o.optInt("maxIntelPerRound", 3),
            cheerPerBattle = o.optInt("cheerPerBattle", 3),
            schoolExpThresholds = o.optJSONArray("schoolExpThresholds")?.toIntList()
                ?: listOf(4, 10, 20, 40),
            starHpBonus = o.optJSONArray("starHpBonus")?.toIntList()
                ?: listOf(80, 120, 200, 400),
            initialPlayerHp = o.optInt("initialPlayerHp", 100),
            courierDamageBase = o.optInt("courierDamageBase", 6)
        )
    }

    fun loadDifficulty(context: Context): DifficultyConfig {
        val o = readJson(context, "difficulty.json")
        return DifficultyConfig(
            baseCoef = o.optDouble("baseCoef", 1.0),
            growthPerRound = o.optDouble("growthPerRound", 0.16),
            hpCoef = o.optDouble("hpCoef", 1.0),
            attackCoef = o.optDouble("attackCoef", 1.0),
            armorCoef = o.optDouble("armorCoef", 0.6),
            maxRound = o.optInt("maxRound", 5),
            roundCoefs = o.optJSONArray("roundCoefs")?.toDoubleList()
                ?: listOf(1.0, 1.25, 1.55, 1.90, 2.35)
        )
    }

    /** v0.3：5 局固定地图 */
    fun loadRunMap(context: Context): RunMapConfig {
        val o = readJson(context, "run_map.json")
        val arr = o.getJSONArray("nodes")
        val nodes = (0 until arr.length()).map { i ->
            val n = arr.getJSONObject(i)
            RunNode(
                index = n.optInt("index", i),
                kind = when (n.optString("kind", "battle")) {
                    "event" -> NodeKind.EVENT
                    "rest_exit" -> NodeKind.REST_EXIT
                    else -> NodeKind.BATTLE
                },
                label = n.optString("label", ""),
                emoji = n.optString("emoji", ""),
                battleRound = n.optInt("battleRound", 0),
                nodeOrder = n.optInt("nodeOrder", 0)
            )
        }
        val r = o.optJSONObject("restNode")
        val rest = if (r == null) RestNodeConfig() else RestNodeConfig(
            healPct = r.optInt("healPct", 40),
            bonusGold = r.optInt("bonusGold", 100),
            clearDebuff = r.optBoolean("clearDebuff", true),
            clearBonusMultiplier = r.optDouble("clearBonusMultiplier", 1.5)
        )
        val e = o.optJSONObject("endings")
        val endings = if (e == null) EndingTexts() else EndingTexts(
            clearTitle = e.optString("clearTitle", "通关"),
            clearDesc = e.optString("clearDesc", ""),
            deadTitle = e.optString("deadTitle", "阵亡"),
            deadDesc = e.optString("deadDesc", ""),
            extractTitle = e.optString("extractTitle", "安全撤离"),
            extractDesc = e.optString("extractDesc", "")
        )
        return RunMapConfig(
            totalBattles = o.optInt("totalBattles", 5),
            nodes = nodes,
            battleVictoryHealPct = o.optInt("battleVictoryHealPct", 15),
            restNode = rest,
            endings = endings
        )
    }

    /** v0.3：随机事件池 */
    fun loadEvents(context: Context): List<GameEventTemplate> {
        val arr = readJson(context, "events.json").getJSONArray("events")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GameEventTemplate(
                id = o.getString("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", ""),
                kind = o.optString("kind", ""),
                value = o.optInt("value", 0),
                valueMax = o.optInt("valueMax", 0),
                weight = o.optInt("weight", 1),
                nodes = o.optJSONArray("nodes")?.toIntList() ?: emptyList(),
                text = o.optString("text", "")
            )
        }
    }

    /** M2：真战斗引擎参数 + 技能表 */
    fun loadBattle(context: Context): BattleConfig {
        val o = readJson(context, "battle.json")
        val es = o.optJSONObject("enemyScale")
        val scale = if (es == null) EnemyScale() else EnemyScale(
            hp = es.optDouble("hp", 1.0),
            attack = es.optDouble("attack", 1.0),
            armor = es.optDouble("armor", 0.6),
            magicResist = es.optDouble("magicResist", 1.0),
            attackSpeed = es.optDouble("attackSpeed", 1.0)
        )
        val arr = o.optJSONArray("skills") ?: JSONArray()
        val skills = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            SkillTemplate(
                id = s.getString("id"),
                hero = s.getString("hero"),
                slot = s.optString("slot", "ult"),
                name = s.optString("name", ""),
                kind = s.optString("kind", ""),
                chance = s.optInt("chance", 0),
                hits = s.optInt("hits", 1),
                dmgPct = s.optInt("dmgPct", 0),
                healPct = s.optInt("healPct", 0),
                shieldPct = s.optInt("shieldPct", 0),
                buffValue = s.optInt("buffValue", 0),
                buffTicks = s.optInt("buffTicks", 0),
                thresholdPct = s.optInt("thresholdPct", 0),
                selfDamagePct = s.optInt("selfDamagePct", 0),
                stunTicks = s.optInt("stunTicks", 0),
                dotPct = s.optDouble("dotPct", 0.0),
                critDmgPct = s.optInt("critDmgPct", 0),
                maxBonusPct = s.optInt("maxBonusPct", 0),
                cooldownTicks = s.optInt("cooldownTicks", 0),
                text = s.optString("text", "")
            )
        }
        return BattleConfig(
            tickMs = o.optInt("tickMs", 100),
            maxTicks = o.optInt("maxTicks", 900),
            armorK = o.optDouble("armorK", 0.06),
            damageVariance = o.optDouble("damageVariance", 0.1),
            baseCrit = o.optInt("baseCrit", 5),
            defaultCritDmg = o.optInt("defaultCritDmg", 200),
            shieldPerStack = o.optInt("shieldPerStack", 60),
            enemyScale = scale,
            skills = skills
        )
    }

    // ---------- 内部工具 ----------

    private fun JSONArray.toIntList(): List<Int> =
        (0 until this.length()).map { this.optInt(it, 0) }

    private fun JSONArray.toDoubleList(): List<Double> =
        (0 until this.length()).map { this.optDouble(it, 0.0) }

    private fun readJson(context: Context, name: String): JSONObject {
        context.assets.open("config/$name").use { input ->
            val text = input.readBytes().toString(Charsets.UTF_8)
            return JSONObject(text)
        }
    }
}
