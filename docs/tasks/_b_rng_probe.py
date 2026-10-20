#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""复刻 DuelEngine 的 roll 消耗与 CriticalPrd，用于为 B 组测试挑选固定 seed 与可解释轨迹。"""

M = 2147483647

def nxt(s):
    return (min(max(s, 1), M - 1) * 48271) % M

def calibrate(p):
    low, high = 0.0, p
    for _ in range(52):
        c = (low + high) / 2
        survival = 1.0
        expected = 0.0
        trial = 1
        while survival > 1e-14:
            expected += survival
            survival *= 1 - min(1.0, c * trial)
            trial += 1
            assert trial <= 100000
        if 1 / expected > p:
            high = c
        else:
            low = c
    return (low + high) / 2

def prd_threshold(p, failures):
    if p == 0.0 or p == 1.0:
        return p
    return min(1.0, calibrate(p) * (failures + 1))

class Rng:
    def __init__(self, seed):
        self.s = seed
        self.n = 0
    def roll(self):
        self.s = nxt(self.s)
        self.n += 1
        return self.s / M

# ---------- S1: maelstrom chain（每 tick 顺序 roll + chain chance roll） ----------
def s1(seed, ticks):
    r = Rng(seed)
    procs = []
    for t in range(1, ticks + 1):
        order = r.roll()          # 顺序
        chain = r.roll()          # maelstrom.chain chance 0.25
        if chain < 0.25:
            procs.append(t)
    return procs

# ---------- S2: mjollnir static 反击 + 非递归 ----------
# 每 tick：顺序 roll；敌人普攻命中 -> 玩家 static（CD 门禁，0.20）；
# 随后敌人 ATTACK_LAND -> chain（0.25）-> 命中玩家 -> 玩家 static 再判一次（CD 门禁）
def s2(seed, ticks):
    r = Rng(seed)
    static_cd = 0
    log = []
    for t in range(1, ticks + 1):
        static_cd = max(0, static_cd - 100)
        order = r.roll()
        line = [f"t{t} order={order:.4f}"]
        # 1) 敌人普攻命中玩家 -> 玩家 static
        if static_cd > 0:
            line.append("static=CD")
        else:
            v = r.roll()
            if v < 0.20:
                line.append(f"static PROC({v:.4f}) counter60")
                static_cd = 500
            else:
                line.append(f"static miss({v:.4f})")
        # 2) 敌人 chain（ ATTACK_LAND ）
        c = r.roll()
        if c < 0.25:
            line.append(f"chain PROC({c:.4f}) 120")
            if static_cd > 0:
                line.append("static2=CD")
            else:
                v2 = r.roll()
                if v2 < 0.20:
                    line.append(f"static2 PROC({v2:.4f}) counter60")
                    static_cd = 500
                else:
                    line.append(f"static2 miss({v2:.4f})")
        else:
            line.append(f"chain miss({c:.4f})")
        log.append(" ".join(line))
    return log

# ---------- S3: butterfly 闪避（每 tick 顺序 + evasion roll，0.65 保留） ----------
def s3(seed, ticks):
    r = Rng(seed)
    out = []
    for t in range(1, ticks + 1):
        order = r.roll()
        ev = r.roll()
        out.append((t, "EVADED" if ev >= 0.65 else "land", round(ev, 4)))
    return out

# ---------- S4: crystalys + daedalus 暴击 PRD ----------
def s4(seed, ticks):
    r = Rng(seed)
    fd = 0  # daedalus failures
    fc = 0  # crystalys failures
    out = []
    for t in range(1, ticks + 1):
        order = r.roll()
        td = prd_threshold(0.25, fd)
        rd = r.roll()
        if rd < td:
            out.append((t, "daedalus CRIT", round(rd, 4), round(td, 4), fd, fc))
            fd = 0
            continue
        fd += 1
        tc = prd_threshold(0.20, fc)
        rc = r.roll()
        if rc < tc:
            out.append((t, "crystalys CRIT", round(rc, 4), round(tc, 4), fd, fc))
            fc = 0
        else:
            fc += 1
            out.append((t, "no crit", round(rc, 4), round(tc, 4), fd, fc))
    return out

if __name__ == "__main__":
    print("calibrate(0.25) =", calibrate(0.25))
    print("calibrate(0.20) =", calibrate(0.20))
    print("prd(0.25,0) =", prd_threshold(0.25, 0))
    print("prd(0.20,0) =", prd_threshold(0.20, 0))
    print()
    # 找一个 seed：S1 前 4 tick 呈 触发/不触发/触发/不触发
    for seed in range(1, 200000):
        p = s1(seed, 4)
        if p == [1, 3]:
            print("S1 seed", seed, "procs", p)
            r = Rng(seed)
            vals = [r.roll() for _ in range(8)]
            print("  rolls:", [round(v, 4) for v in vals])
            break
    # S2：tick1 static 即触发，且 tick1 chain 不触发（简单）
    for seed in range(1, 200000):
        log = s2(seed, 3)
        if "static PROC" in log[0] and "chain miss" in log[0] and "static PROC" in log[1] and "chain miss" in log[1]:
            print("S2 seed", seed)
            for l in log:
                print("  ", l)
            break
    # S3：tick1 闪避、tick2 命中
    for seed in range(1, 200000):
        o = s3(seed, 2)
        if o[0][1] == "EVADED" and o[1][1] == "land":
            print("S3 seed", seed, o)
            break
    # S4：tick1 daedalus 未评估 crystalys 即暴击 -> 观察 failures
    for seed in range(1, 200000):
        o = s4(seed, 3)
        if o[0][1] == "daedalus CRIT":
            print("S4 seed", seed)
            for row in o:
                print("  ", row)
            break
