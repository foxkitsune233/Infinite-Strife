# -*- coding: utf-8 -*-
"""生成 app/src/main/assets/config 下的游戏配置 JSON。
数据驱动：改这里重新生成，即可调整平衡性，不用改 Kotlin 代码。
运行：python tools/gen_config.py
"""
import os
import json
import io

HERE = os.path.dirname(os.path.abspath(__file__))      # .../DouQiuQiu/tools
ROOT = os.path.dirname(HERE)                            # .../DouQiuQiu
CFG = os.path.join(ROOT, "app", "src", "main", "assets", "config")
os.makedirs(CFG, exist_ok=True)


def w(name, obj):
    p = os.path.join(CFG, name)
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
    print("WROTE", name)


# ---------------- heroes.json : 8 英雄 ----------------
heroes = [
    dict(id="phantom_lancer", name="幻影长矛手", emoji="\U0001F5E1️", trait="敏", color="#4CAF50",
         hp=1150, attack=58, attackSpeed=1.15, armor=6, magicResist=5,
         ult="灵魂之矛：召唤 2 个分身协战", passive="并列：攻击有概率生成幻象"),
    dict(id="axe", name="斧王", emoji="\U0001FA93", trait="力", color="#E53935",
         hp=1500, attack=64, attackSpeed=0.95, armor=10, magicResist=4,
         ult="淘汰之刃：直接斩杀低血量目标", passive="反击螺旋：受击概率反击"),
    dict(id="legion_commander", name="军团指挥官", emoji="⚔️", trait="力", color="#FF7043",
         hp=1400, attack=66, attackSpeed=1.00, armor=8, magicResist=5,
         ult="决斗：强制单挑，胜者永久加攻", passive="勇气之霎：受击时回血"),
    dict(id="phantom_assassin", name="幻影刺客", emoji="\U0001F52A", trait="敏", color="#8E24AA",
         hp=1050, attack=72, attackSpeed=1.20, armor=5, magicResist=4,
         ult="模糊：大幅提升闪避", passive="恩赐解脱：高倍率暴击"),
    dict(id="necrolyte", name="瘟疫法师", emoji="☠️", trait="智", color="#26A69A",
         hp=1200, attack=54, attackSpeed=1.00, armor=5, magicResist=10,
         ult="死亡镰刀：斩杀并眩晕", passive="光环：持续伤害周围敌人"),
    dict(id="huskar", name="哈斯卡", emoji="\U0001F525", trait="力", color="#FB8C00",
         hp=1350, attack=62, attackSpeed=1.05, armor=7, magicResist=5,
         ult="牺牲：冲撞目标并自损", passive="沸血之矛：血越低攻速越高"),
    dict(id="magnataur", name="马格纳斯", emoji="\U0001F98F", trait="力", color="#795548",
         hp=1450, attack=60, attackSpeed=0.90, armor=9, magicResist=5,
         ult="两极反转：拉扯并聚拢敌人", passive="授予力量：攻击溅射"),
    dict(id="shadow_shaman", name="暗影萨满", emoji="\U0001F40D", trait="智", color="#5C6BC0",
         hp=1100, attack=56, attackSpeed=1.05, armor=4, magicResist=11,
         ult="裂地沟壑：眩晕并造成伤害", passive="妖术：变羊控制"),
]
w("heroes.json", {"heroes": heroes})

# ---------------- schools.json : 6 流派 ----------------
schools = [
    dict(id="basic", name="普攻流", emoji="⚔️",
         effects=[dict(atkSpeedPct=10, attack=3), dict(atkSpeedPct=20, attack=6),
                  dict(atkSpeedPct=30, attack=9), dict(atkSpeedPct=50, attack=15)]),
    dict(id="ultimate", name="大招流", emoji="\U0001F4A5",
         effects=[dict(ultPct=20, manaRegen=2), dict(ultPct=40, manaRegen=4),
                  dict(ultPct=60, manaRegen=6), dict(ultPct=100, manaRegen=8)]),
    dict(id="life", name="生命流", emoji="❤️",
         effects=[dict(magicResistPct=5, hpPct=3), dict(magicResistPct=10, hpPct=6),
                  dict(magicResistPct=15, hpPct=9), dict(magicResistPct=25, hpPct=15)]),
    dict(id="shield", name="护盾流", emoji="\U0001F6E1️",
         effects=[dict(shieldStack=2), dict(shieldStack=4),
                  dict(shieldStack=6), dict(shieldStack=10)]),
    dict(id="crit", name="暴击流", emoji="\U0001F3AF",
         effects=[dict(critDmgPct=20, crit=2), dict(critDmgPct=40, crit=4),
                  dict(critDmgPct=60, crit=6), dict(critDmgPct=100, crit=10)]),
    dict(id="evade", name="闪避流", emoji="\U0001F4A8",
         effects=[dict(physResistPct=5, evasion=2), dict(physResistPct=10, evasion=4),
                  dict(physResistPct=15, evasion=6), dict(physResistPct=25, evasion=10)]),
]
w("schools.json", {"schools": schools})

# ---------------- weapons.json : 8 武器 ----------------
weapons = [
    dict(id="branch", name="树枝", emoji="\U0001F33F", quality="白", value=20, price=50, stats=dict(all=1)),
    dict(id="circlet", name="圆环", emoji="\U0001F534", quality="白", value=40, price=155, stats=dict(all=2)),
    dict(id="yasha", name="夜叉", emoji="\U0001F32A️", quality="蓝", value=200, price=2050,
         stats=dict(agi=16, atkSpeedPct=15)),
    dict(id="sange", name="散华", emoji="\U0001FA78", quality="蓝", value=200, price=2050,
         stats=dict(str=16, lifesteal=15)),
    dict(id="daedalus", name="代达罗斯之殇", emoji="\U0001F3AF", quality="紫", value=600, price=5150,
         stats=dict(attack=81, crit=30)),
    dict(id="butterfly", name="蝴蝶", emoji="\U0001F98B", quality="紫", value=600, price=5475,
         stats=dict(agi=35, evasion=35)),
    dict(id="aegis", name="圣剑", emoji="⚔️", quality="金", value=1200, price=6200, stats=dict(attack=200)),
    dict(id="heart", name="龙心", emoji="\U0001F496", quality="金", value=1200, price=5200,
         stats=dict(str=45, hp=250)),
]
w("weapons.json", {"weapons": weapons})

# ---------------- skill_cards.json ----------------
cards = []
for s in schools:
    sid = s["id"]
    sname = s["name"]
    cards.append(dict(id=sid + "_blue", school=sid, quality="蓝", exp=1, price=100,
                      name=sname + "·蓝", desc=sname + "经验 +1"))
    cards.append(dict(id=sid + "_purple", school=sid, quality="紫", exp=2, price=200,
                      name=sname + "·紫", desc=sname + "经验 +2", requireStar=2))
    cards.append(dict(id=sid + "_gold", school=sid, quality="金", exp=4, price=300,
                      name=sname + "·金", desc=sname + "经验 +4", requireStar=3))
w("skill_cards.json", {"cards": cards})

# ---------------- balance.json ----------------
w("balance.json", {
    "initialGold": 300,
    "incomePerRound": 250,
    "interestPer100": 10,
    "interestCap": 100,
    "winBase": 50,
    "winStreakBonus": 20,
    "winStreakCap": 100,
    "losePerHp": 15,
    "loseStreakBonus": 15,
    "loseStreakCap": 60,
    "intelCost": 80,
    "maxIntelPerRound": 3,
    "cheerPerBattle": 3,
    "schoolExpThresholds": [4, 10, 20, 40],
    "starHpBonus": [80, 120, 200, 400],
    "initialPlayerHp": 100,
    "courierDamageBase": 6
})

# ---------------- difficulty.json ----------------
w("difficulty.json", {
    "baseCoef": 1.0,
    "growthPerRound": 0.16,
    "hpCoef": 1.0,
    "attackCoef": 1.0,
    "armorCoef": 0.6,
    # v0.3：固定 5 局，每局一个属性系数（B1~B5 递增）
    # M2 平衡实测后拉平：原曲线 1.25/1.55/1.90/2.35 在 B2->B3 出现 85%->0% 的悬崖
    # （普攻是唯一输出手段，被眩晕/闪避克制时胜负被技能方差主导）
    "maxRound": 5,
    "roundCoefs": [1.00, 1.10, 1.25, 1.45, 1.70]
})

# ---------------- loot_pool.json ----------------
w("loot_pool.json", {
    "crateSize": [4, 6],
    "revealCount": 2,
    "tiers": [
        {"roundFrom": 1, "roundTo": 4,
         "items": [{"type": "gold", "min": 120, "max": 260, "weight": 5},
                   {"type": "card", "quality": "蓝", "weight": 4},
                   {"type": "weapon", "quality": "白", "weight": 3}]},
        {"roundFrom": 5, "roundTo": 9,
         "items": [{"type": "gold", "min": 260, "max": 520, "weight": 5},
                   {"type": "card", "quality": "蓝", "weight": 3},
                   {"type": "card", "quality": "紫", "weight": 2},
                   {"type": "weapon", "quality": "蓝", "weight": 3}]},
        {"roundFrom": 10, "roundTo": 14,
         "items": [{"type": "gold", "min": 520, "max": 900, "weight": 4},
                   {"type": "card", "quality": "紫", "weight": 3},
                   {"type": "weapon", "quality": "紫", "weight": 3},
                   {"type": "artifact", "weight": 1}]},
        {"roundFrom": 15, "roundTo": 99,
         "items": [{"type": "gold", "min": 900, "max": 1600, "weight": 4},
                   {"type": "card", "quality": "金", "weight": 3},
                   {"type": "weapon", "quality": "金", "weight": 3},
                   {"type": "artifact", "weight": 2}]}
    ],
    "milestoneRounds": [3, 6, 9, 12, 15]
})

# ---------------- run_map.json : 【v0.3】5 局固定地图 + 4 节点 ----------------
# 节点序列：B1 -> 事件 -> B2 -> 事件 -> B3 -> [返回/休息] -> B4 -> 事件 -> B5
# kind: battle(战斗) / event(随机事件) / rest_exit(返回或休息)
w("run_map.json", {
    "totalBattles": 5,
    "nodes": [
        {"index": 0, "kind": "battle", "battleRound": 1, "label": "第 1 场", "emoji": "\u2694\ufe0f"},
        {"index": 1, "kind": "event", "nodeOrder": 1, "label": "事件", "emoji": "\u2753"},
        {"index": 2, "kind": "battle", "battleRound": 2, "label": "第 2 场", "emoji": "\u2694\ufe0f"},
        {"index": 3, "kind": "event", "nodeOrder": 2, "label": "事件", "emoji": "\u2753"},
        {"index": 4, "kind": "battle", "battleRound": 3, "label": "第 3 场", "emoji": "\u2694\ufe0f"},
        {"index": 5, "kind": "rest_exit", "nodeOrder": 3, "label": "返回 / 休息", "emoji": "\U0001F3D5\ufe0f"},
        {"index": 6, "kind": "battle", "battleRound": 4, "label": "第 4 场", "emoji": "\u2694\ufe0f"},
        {"index": 7, "kind": "event", "nodeOrder": 4, "label": "事件", "emoji": "\u2753"},
        {"index": 8, "kind": "battle", "battleRound": 5, "label": "第 5 场 · 终局", "emoji": "\U0001F3C6"}
    ],
    "battleVictoryHealPct": 15,
    "restNode": {
        "healPct": 40,
        "bonusGold": 100,
        "clearDebuff": True,
        "clearBonusMultiplier": 1.5
    },
    "endings": {
        "clearTitle": "\U0001F3C6 通关",
        "clearDesc": "五场全胜，你带着满仓战利品离开了蛐蛐罐。",
        "deadTitle": "\U0001F480 阵亡",
        "deadDesc": "蛐蛐倒下了，带进去的卡与武器全部掉落。",
        "extractTitle": "\U0001F6AA 安全撤离",
        "extractDesc": "见好就收，战利品全部落袋为安。"
    }
})

# ---------------- events.json : 【v0.3】随机事件池（纯随机触发） ----------------
# nodes: 允许出现的节点序号（1..4）；weight: 权重；kind: 效果类型
# kind 说明 gold_add / gold_lose / hp_heal / hp_lose / weapon_gain / card_gain /
#           weapon_lose / school_exp / buff_atk / buff_armor / gamble_gold / reveal_next
w("events.json", {
    "events": [
        {"id": "coin_pouch", "name": "遗失的钱袋", "emoji": "\U0001F4B0", "kind": "gold_add",
         "value": 120, "valueMax": 260, "weight": 5, "nodes": [1, 2, 3, 4],
         "text": "路边捡到一个沉甸甸的钱袋，金币 +{v}"},
        {"id": "chest", "name": "野外宝箱", "emoji": "\U0001F4E6", "kind": "weapon_gain",
         "weight": 3, "nodes": [1, 2, 3, 4],
         "text": "宝箱里躺着一件武器：{v}"},
        {"id": "mentor", "name": "前辈指点", "emoji": "\U0001F4D6", "kind": "card_gain",
         "weight": 3, "nodes": [1, 2, 3, 4],
         "text": "一位老蛐蛐送你一张卡：{v}"},
        {"id": "spring", "name": "清泉", "emoji": "\U0001F4A7", "kind": "hp_heal",
         "value": 30, "valueMax": 50, "weight": 4, "nodes": [1, 2, 3, 4],
         "text": "饮下清泉，生命 +{v}"},
        {"id": "whetstone", "name": "磨刀石", "emoji": "\u2692\ufe0f", "kind": "buff_atk",
         "value": 15, "weight": 3, "nodes": [1, 2, 3, 4],
         "text": "磨利了武器，下一场攻击 +{v}%"},
        {"id": "armorsmith", "name": "护甲匠", "emoji": "\U0001F6E1\ufe0f", "kind": "buff_armor",
         "value": 15, "weight": 3, "nodes": [1, 2, 3, 4],
         "text": "加固了护甲，下一场护甲 +{v}"},
        {"id": "dojo", "name": "修炼场", "emoji": "\U0001F3CB\ufe0f", "kind": "school_exp",
         "value": 3, "weight": 3, "nodes": [1, 2, 3, 4],
         "text": "闭关修炼，{v} 流派经验 +3"},
        {"id": "trap", "name": "陷阱", "emoji": "\U0001F573\ufe0f", "kind": "hp_lose",
         "value": 15, "valueMax": 35, "weight": 3, "nodes": [1, 2, 3, 4],
         "text": "踩中了陷阱，生命 -{v}"},
        {"id": "pickpocket", "name": "扒手", "emoji": "\U0001F977", "kind": "weapon_lose",
         "weight": 2, "nodes": [2, 3, 4],
         "text": "扒手偷走了你的 {v}"},
        {"id": "gambler", "name": "赌神", "emoji": "\U0001F3B2", "kind": "gamble_gold",
         "weight": 2, "nodes": [2, 3, 4],
         "text": "赌了一把：{v}"},
        {"id": "stall", "name": "破旧摊位", "emoji": "\U0001F9FA", "kind": "gold_lose",
         "value": 80, "weight": 2, "nodes": [1, 2, 3, 4],
         "text": "被摊主讹了一笔，金币 -{v}"},
        {"id": "oracle", "name": "神谕", "emoji": "\U0001F52E", "kind": "reveal_next",
         "weight": 2, "nodes": [1, 2, 3],
         "text": "神谕揭示了下一场仓库中的 1 件物品"}
    ]
})

# ---------------- battle.json : 【M2】真战斗引擎参数 + 技能表 ----------------
# tickMs        引擎逻辑步长（毫秒），UI 回放也按这个步长推进
# maxTicks      单场战斗上限（100 tick = 10 秒）；超时按剩余血量百分比判胜
# armorK        抗性减伤系数：减伤 = k × 抗性 / (1 + k × 抗性)   （护甲/魔抗同一套公式）
# damageVariance 伤害浮动 ±10%
# shieldPerStack 护盾流 1 星 = 多少点护盾
# enemyScale    AI 敌人在 difficulty.roundCoefs 之上再乘的属性系数（调难度只改这里）
# skills        每个英雄 1 个大招(slot=ult，按 cooldownTicks 冷却释放)
#               + 1 个被动(slot=passive，按 chance 概率触发)
# kind 说明：
#   ult   : multi_hit 多段物理 / damage 魔法 / execute 斩杀 / heal 治疗 / shield 护盾
#           buff_evasion 闪避 buff / buff_atk 攻击 buff / stun_damage 魔法+眩晕
#   passive: onhit_extra 追加物理 / onhit_counter 反击 / onhit_splash 溅射
#           onhit_crit 追加暴击 / onhit_heal 受击回血 / onhit_stun 受击眩晕
#           dot 持续伤害(开场给对手挂) / aspd_lowhp 血越少攻速越快
skills = [
    # ---- 幻影长矛手 ----
    dict(id="pl_ult", hero="phantom_lancer", slot="ult", name="灵魂之矛", kind="multi_hit",
         hits=3, dmgPct=55, cooldownTicks=70, text="{src} 投出灵魂之矛，幻影分身齐射！"),
    dict(id="pl_passive", hero="phantom_lancer", slot="passive", name="并列", kind="onhit_extra",
         chance=25, dmgPct=45, text="{src} 的攻击唤出了并列幻象"),
    # ---- 斧王 ----
    dict(id="axe_ult", hero="axe", slot="ult", name="淘汰之刃", kind="execute",
         thresholdPct=20, dmgPct=190, cooldownTicks=80, text="{src} 高高跃起，挥出淘汰之刃！"),
    dict(id="axe_passive", hero="axe", slot="passive", name="反击螺旋", kind="onhit_counter",
         chance=20, dmgPct=65, text="{src} 触发反击螺旋"),
    # ---- 军团指挥官 ----
    dict(id="lc_ult", hero="legion_commander", slot="ult", name="决斗", kind="buff_atk",
         buffValue=30, buffTicks=110, dmgPct=120, cooldownTicks=90, text="{src} 强制发起决斗，攻击力大涨！"),
    dict(id="lc_passive", hero="legion_commander", slot="passive", name="勇气之霎", kind="onhit_heal",
         chance=28, healPct=4, text="{src} 凭借勇气之霎回复了生命"),
    # ---- 幻影刺客 ----
    dict(id="pa_ult", hero="phantom_assassin", slot="ult", name="模糊", kind="buff_evasion",
         buffValue=25, buffTicks=110, cooldownTicks=100, text="{src} 融入模糊的阴影，难以命中"),
    dict(id="pa_passive", hero="phantom_assassin", slot="passive", name="恩赐解脱", kind="onhit_crit",
         chance=30, critDmgPct=260, text="{src} 的恩赐解脱找到了破绽！"),
    # ---- 瘟疫法师 ----
    dict(id="necro_ult", hero="necrolyte", slot="ult", name="死亡镰刀", kind="stun_damage",
         dmgPct=210, stunTicks=12, cooldownTicks=95, text="{src} 举起死亡镰刀！"),
    dict(id="necro_passive", hero="necrolyte", slot="passive", name="死亡脉冲", kind="dot",
         dotPct=0.35, text="{src} 的死亡脉冲持续灼烧对手"),
    # ---- 哈斯卡 ----
    dict(id="huskar_ult", hero="huskar", slot="ult", name="牺牲", kind="damage",
         dmgPct=240, selfDamagePct=4, cooldownTicks=75, text="{src} 以自身鲜血为代价冲撞！"),
    dict(id="huskar_passive", hero="huskar", slot="passive", name="沸血之矛", kind="aspd_lowhp",
         maxBonusPct=70, text="{src} 血流得越多，长矛越快"),
    # ---- 马格纳斯 ----
    dict(id="mag_ult", hero="magnataur", slot="ult", name="两极反转", kind="stun_damage",
         dmgPct=150, stunTicks=14, cooldownTicks=85, text="{src} 引发两极反转！"),
    dict(id="mag_passive", hero="magnataur", slot="passive", name="授予力量", kind="onhit_splash",
         chance=30, dmgPct=55, text="{src} 的攻击溅射开来"),
    # ---- 暗影萨满 ----
    dict(id="ss_ult", hero="shadow_shaman", slot="ult", name="裂地沟壑", kind="stun_damage",
         dmgPct=175, stunTicks=16, cooldownTicks=90, text="{src} 撕裂大地！"),
    dict(id="ss_passive", hero="shadow_shaman", slot="passive", name="妖术", kind="onhit_stun",
         chance=12, stunTicks=8, text="{src} 把对手变成了小动物！"),
]
w("battle.json", {
    "tickMs": 100,
    "maxTicks": 900,
    "armorK": 0.06,
    "damageVariance": 0.1,
    "baseCrit": 5,
    "defaultCritDmg": 200,
    "shieldPerStack": 60,
    "enemyScale": {
        "hp": 0.80,
        "attack": 0.75,
        "armor": 0.50,
        "magicResist": 0.60,
        "attackSpeed": 0.85
    },
    "skills": skills
})

print("--- config done ---")
