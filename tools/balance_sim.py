# -*- coding: utf-8 -*-
"""战斗引擎平衡性验证脚本（Kotlin BattleEngine 的 Python 镜像）。

用途：不动安卓工程，直接在 PC 上跑几千场模拟，看 B1~B5 的胜率曲线是否合理。
公式与 engine/BattleEngine.kt 逐条对应，改 battle.json 的数值后重跑本脚本即可。

运行：python tools/balance_sim.py
"""
import io
import json
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CFG = os.path.join(ROOT, "app", "src", "main", "assets", "config")


def load(name):
    with io.open(os.path.join(CFG, name), "r", encoding="utf-8") as f:
        return json.load(f)


HEROES = {h["id"]: h for h in load("heroes.json")["heroes"]}
WEAPONS = {w["id"]: w for w in load("weapons.json")["weapons"]}
CARDS = {c["id"]: c for c in load("skill_cards.json")["cards"]}
SCHOOLS = {s["id"]: s for s in load("schools.json")["schools"]}
BATTLE = load("battle.json")
BALANCE = load("balance.json")
DIFF = load("difficulty.json")

TICK_MS = BATTLE["tickMs"]
MAX_TICKS = BATTLE["maxTicks"]
K = BATTLE["armorK"]
VAR = BATTLE["damageVariance"]
BASE_CRIT = BATTLE["baseCrit"]
BASE_CRIT_DMG = BATTLE["defaultCritDmg"]
SHIELD_PER_STACK = BATTLE["shieldPerStack"]
ES = BATTLE["enemyScale"]
SKILLS = BATTLE["skills"]
THRESHOLDS = BALANCE["schoolExpThresholds"]


def rnd(a=None, b=None):
    if a is None:
        return random.random()
    return random.randint(a, b)


def reduction(res):
    r = abs(res)
    return K * r / (1.0 + K * r)


def damage(base, res):
    v = 1.0 + (random.random() * 2 - 1) * VAR
    return max(1, int(round(base * (1.0 - reduction(res)) * v)))


class Unit(object):
    def __init__(self, side, hid, hp, attack, aps, armor, mr, crit, crit_dmg,
                 evasion, lifesteal, skills, max_shield=0, shield=0, ult_dmg=1.0):
        self.side = side
        self.id = hid
        self.hp = hp
        self.max_hp = hp
        self.attack = attack
        self.base_attack = attack
        self.armor = armor
        self.mr = mr
        self.crit = crit
        self.crit_dmg = crit_dmg
        self.evasion = evasion
        self.lifesteal = lifesteal
        self.skills = skills
        self.max_shield = max_shield
        self.shield = shield
        self.ult_dmg = ult_dmg
        self.stun = 0
        self.interval = max(2, int(round(1000.0 / aps / TICK_MS)))
        self.next_attack = self.interval
        self.as_bonus = 0
        self.tmp_evasion = 0
        self.tmp_evasion_ticks = 0
        self.tmp_atk_pct = 0
        self.tmp_atk_ticks = 0
        self.dots = []

    @property
    def alive(self):
        return self.hp > 0

    def hp_ratio(self):
        return self.hp / float(self.max_hp) if self.max_hp else 0.0

    def cur_interval(self):
        return max(2, int(round(self.interval * 100.0 / (100.0 + self.as_bonus))))

    def refresh(self):
        if self.tmp_atk_ticks > 0:
            self.attack = int(round(self.base_attack * (1.0 + self.tmp_atk_pct / 100.0)))
        else:
            self.attack = self.base_attack


def make_player(hero, weapons, card_ids, buff_atk=0, buff_armor=0, hp=None):
    hp_f = float(hero["hp"])
    atk = float(hero["attack"])
    as_pct = 0
    armor = float(hero["armor"])
    mr = float(hero["magicResist"])
    crit = BASE_CRIT
    crit_dmg = BASE_CRIT_DMG
    eva = 0
    ls = 0
    shield = 0
    for wid in weapons:
        s = WEAPONS[wid]["stats"]
        atk += s.get("all", 0) * 2 + s.get("str", 0) + s.get("agi", 0) + s.get("attack", 0)
        hp_f += s.get("all", 0) * 12 + s.get("str", 0) * 20 + s.get("hp", 0)
        armor += s.get("all", 0) * 0.5
        as_pct += s.get("all", 0) * 2 + s.get("agi", 0) + s.get("atkSpeedPct", 0)
        mr += s.get("int", 0) * 0.5 + s.get("all", 0) * 0.5
        crit += s.get("crit", 0)
        eva += s.get("evasion", 0)
        ls += s.get("lifesteal", 0)

    best_star, best_exp, best = 0, 0, None
    for sc in SCHOOLS.values():
        exp = sum(CARDS[c]["exp"] for c in card_ids if CARDS[c]["school"] == sc["id"])
        star = sum(1 for t in THRESHOLDS if exp >= t)
        if star > best_star or (star == best_star and exp > best_exp):
            best_star, best_exp, best = star, exp, sc
    ult_dmg, ult_cd = 1.0, 1.0
    if best and best_star > 0:
        e = best["effects"][best_star - 1]
        atk += e.get("attack", 0)
        as_pct += e.get("atkSpeedPct", 0)
        armor += e.get("physResistPct", 0)
        mr += e.get("magicResistPct", 0)
        crit += e.get("crit", 0)
        crit_dmg += e.get("critDmgPct", 0)
        eva += e.get("evasion", 0)
        shield += e.get("shieldStack", 0) * SHIELD_PER_STACK
        hp_f *= 1.0 + e.get("hpPct", 0) / 100.0
        ult_dmg = 1.0 + e.get("ultPct", 0) / 100.0
        ult_cd = max(0.4, 1.0 - e.get("manaRegen", 0) * 0.02)

    atk *= 1.0 + buff_atk / 100.0
    armor += buff_armor

    max_hp = max(1, int(round(hp_f)))
    skills = []
    for t in SKILLS:
        if t["hero"] != hero["id"]:
            continue
        skills.append(dict(t, cd=max(10, int(round(t.get("cooldownTicks", 0) * ult_cd))),
                           cd_left=int(round(t.get("cooldownTicks", 0) * ult_cd * 0.5))))
    aps = hero["attackSpeed"] * (1.0 + as_pct / 100.0)
    u = Unit("P", hero["id"], max_hp if hp is None else min(hp, max_hp),
             max(1, int(round(atk))), aps, armor, mr, crit, crit_dmg, eva, ls,
             skills, max_shield=shield, shield=shield, ult_dmg=ult_dmg)
    return u


def make_enemy(hero, coef):
    hp = max(50, int(round(hero["hp"] * coef * ES["hp"])))
    atk = max(5, int(round(hero["attack"] * coef * ES["attack"])))
    armor = hero["armor"] * coef * ES["armor"]
    mr = hero["magicResist"] * coef * ES["magicResist"]
    aps = hero["attackSpeed"] * coef * ES["attackSpeed"]
    skills = [dict(t, cd=t.get("cooldownTicks", 0), cd_left=int(round(t.get("cooldownTicks", 0) * 0.5)))
              for t in SKILLS if t["hero"] == hero["id"]]
    return Unit("E", hero["id"], hp, atk, aps, armor, mr, BASE_CRIT, BASE_CRIT_DMG, 0, 0, skills)


def apply_damage(dst, dmg):
    remain = max(0, dmg)
    if dst.shield > 0:
        absorbed = min(dst.shield, remain)
        dst.shield -= absorbed
        remain -= absorbed
        if dst.shield <= 0:
            dst.shield = 0
    dst.hp = max(0, dst.hp - remain)


def cast(src, dst, sk):
    t = sk["tpl"] if "tpl" in sk else sk
    scale = src.ult_dmg if src.side == "P" else 1.0
    kind = t["kind"]
    if kind == "multi_hit":
        per = src.base_attack * t.get("dmgPct", 0) / 100.0 / t.get("hits", 1) * scale
        for _ in range(t.get("hits", 1)):
            if not dst.alive:
                return
            apply_damage(dst, damage(per, dst.armor))
    elif kind == "damage":
        apply_damage(dst, damage(src.base_attack * t.get("dmgPct", 0) / 100.0 * scale, dst.mr))
        if t.get("selfDamagePct", 0) > 0:
            src.hp = max(0, src.hp - int(round(src.max_hp * t["selfDamagePct"] / 100.0)))
    elif kind == "execute":
        if dst.hp <= dst.max_hp * t.get("thresholdPct", 0) / 100.0:
            dst.hp = 0
        else:
            apply_damage(dst, damage(src.base_attack * t.get("dmgPct", 0) / 100.0 * scale, dst.mr))
    elif kind == "heal":
        if src.hp < src.max_hp:
            src.hp = min(src.max_hp, src.hp + int(round(src.max_hp * t.get("healPct", 0) / 100.0)))
    elif kind == "shield":
        src.shield = min(src.max_shield, src.shield + int(round(src.max_hp * t.get("shieldPct", 0) / 100.0)))
    elif kind == "buff_evasion":
        src.tmp_evasion += t.get("buffValue", 0)
        src.tmp_evasion_ticks = t.get("buffTicks", 0)
    elif kind == "buff_atk":
        src.tmp_atk_pct += t.get("buffValue", 0)
        src.tmp_atk_ticks = t.get("buffTicks", 0)
        src.refresh()
    elif kind == "stun_damage":
        apply_damage(dst, damage(src.base_attack * t.get("dmgPct", 0) / 100.0 * scale, dst.mr))
        if dst.alive and t.get("stunTicks", 0) > 0:
            dst.stun = max(dst.stun, t["stunTicks"])


def start_passives(src, dst):
    for sk in src.skills:
        if sk.get("slot") != "passive":
            continue
        if sk["kind"] == "dot" and sk.get("dotPct", 0) > 0:
            per = max(1, int(round(src.max_hp * sk["dotPct"] / 100.0)))
            dst.dots.append([per, MAX_TICKS])


def passives(src, dst, is_crit):
    for sk in src.skills:
        if sk.get("slot") != "passive":
            continue
        kind = sk["kind"]
        if kind in ("onhit_extra", "onhit_counter", "onhit_splash"):
            if rnd(0, 99) < sk.get("chance", 0):
                apply_damage(dst, damage(src.attack * sk.get("dmgPct", 0) / 100.0, dst.armor))
        elif kind == "onhit_crit":
            if not is_crit and rnd(0, 99) < sk.get("chance", 0):
                base = damage(src.attack, dst.armor)
                apply_damage(dst, int(round(base * sk.get("critDmgPct", 0) / 100.0)))
        elif kind == "onhit_heal":
            if rnd(0, 99) < sk.get("chance", 0) and src.hp < src.max_hp:
                src.hp = min(src.max_hp, src.hp + int(round(src.max_hp * sk.get("healPct", 0) / 100.0)))
        elif kind == "onhit_stun":
            if dst.alive and rnd(0, 99) < sk.get("chance", 0):
                dst.stun = max(dst.stun, sk.get("stunTicks", 0))
        elif kind == "aspd_lowhp":
            src.as_bonus = int(round(sk.get("maxBonusPct", 0) * (1.0 - src.hp_ratio())))
        if not dst.alive:
            return


def attack(src, dst):
    if not src.alive or not dst.alive or src.stun > 0:
        return
    src.next_attack -= 1
    if src.next_attack > 0:
        return
    src.next_attack = src.cur_interval()
    eva = dst.evasion + dst.tmp_evasion
    if eva > 0 and rnd(0, 99) < eva:
        return
    is_crit = rnd(0, 99) < src.crit
    dmg = damage(src.attack, dst.armor)
    if is_crit:
        dmg = int(round(dmg * src.crit_dmg / 100.0))
    apply_damage(dst, dmg)
    if not dst.alive:
        return
    if src.lifesteal > 0 and src.hp < src.max_hp:
        src.hp = min(src.max_hp, src.hp + int(round(dmg * src.lifesteal / 100.0)))
    passives(src, dst, is_crit)


def simulate(p, e):
    start_passives(p, e)
    start_passives(e, p)
    tick = 0
    timeout = False
    while True:
        if not p.alive or not e.alive:
            break
        if tick >= MAX_TICKS:
            timeout = True
            break
        tick += 1
        for u in (p, e):
            if u.stun > 0:
                u.stun -= 1
            if u.tmp_evasion_ticks > 0:
                u.tmp_evasion_ticks -= 1
                if u.tmp_evasion_ticks == 0:
                    u.tmp_evasion = 0
            if u.tmp_atk_ticks > 0:
                u.tmp_atk_ticks -= 1
                if u.tmp_atk_ticks == 0:
                    u.tmp_atk_pct = 0
            u.refresh()
        # DoT
        for victim, owner in ((p, e), (e, p)):
            if not victim.dots:
                continue
            keep = []
            for per, left in victim.dots:
                if not victim.alive:
                    keep.append([per, left])
                    continue
                apply_damage(victim, damage(per, victim.mr))
                left -= 1
                if left > 0:
                    keep.append([per, left])
            victim.dots = keep
        if not p.alive or not e.alive:
            break
        # 大招
        for src, dst in ((p, e), (e, p)):
            for sk in src.skills:
                if sk.get("slot") != "ult":
                    continue
                if sk["cd_left"] > 0:
                    sk["cd_left"] -= 1
                    continue
                sk["cd_left"] = sk["cd"]
                cast(src, dst, sk)
                if not dst.alive:
                    break
        if not p.alive or not e.alive:
            break
        attack(p, e)
        if not p.alive or not e.alive:
            break
        attack(e, p)
    if not e.alive and not p.alive:
        return p.hp_ratio() >= e.hp_ratio(), tick, timeout
    if not e.alive:
        return True, tick, timeout
    if not p.alive:
        return False, tick, timeout
    return (p.hp_ratio() >= e.hp_ratio()), tick, timeout


def power(u):
    aps = 1000.0 / (u.interval * TICK_MS)
    return int(round(u.max_hp * 0.5 + u.base_attack * 10.0 * aps
                     + abs(u.armor) * 5.0 + u.crit * 3.0 + u.max_shield * 0.3))


# ---------------- 场景 ----------------
# 按一局正常推进的携带物水平近似：越往后装备/流派越好
SCENARIOS = [
    ("裸装（无武器无卡）", [], []),
    ("1 蓝武 + 无卡", ["yasha"], []),
    ("1 蓝武 + 普攻2星", ["yasha"], ["basic_blue", "basic_purple"]),
    ("2 蓝武 + 普攻2星", ["yasha", "sange"], ["basic_blue", "basic_purple"]),
    ("紫武×2 + 暴击3星", ["daedalus", "butterfly"],
     ["crit_blue", "crit_purple", "crit_gold", "basic_blue", "basic_purple"]),
    ("金武×2 + 暴击4星", ["aegis", "heart"],
     ["crit_gold", "crit_purple", "crit_blue", "basic_gold", "basic_purple", "basic_blue"]),
]


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 400
    if len(sys.argv) > 2:
        # 支持临时扫曲线：python tools/balance_sim.py 400 1.0,1.15,1.35,1.6,1.9
        DIFF["roundCoefs"] = [float(x) for x in sys.argv[2].split(",")]
    hero = HEROES["phantom_lancer"]
    coefs = DIFF["roundCoefs"]
    print("英雄：%s  模拟场次/格：%d" % (hero["name"], n))
    print("%-22s %s" % ("场景", "  ".join("B%d(×%.2f)" % (i + 1, c) for i, c in enumerate(coefs))))
    print("-" * 78)
    for name, weapons, cards in SCENARIOS:
        row = []
        total_ticks = 0
        for idx, coef in enumerate(coefs):
            wins = 0
            for _ in range(n):
                pool = [h for h in HEROES.values() if h["id"] != hero["id"]]
                enemy = random.choice(pool)
                p = make_player(hero, weapons, cards, hp=hero["hp"])
                e = make_enemy(enemy, coef)
                w, t, _ = simulate(p, e)
                wins += 1 if w else 0
                total_ticks += t
            row.append("%5.0f%%" % (100.0 * wins / n))
        print("%-22s %s   平均 %.1fs" % (name, "      ".join(row),
                                        total_ticks / float(n * len(coefs)) / 10.0))


if __name__ == "__main__":
    main()
