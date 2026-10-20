#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""追加种子搜索：S2 静电反击、S1c 连锁闪电+暴击共存、S4b 双暴击源 PRD 推进。"""
import importlib.util, sys
spec = importlib.util.spec_from_file_location("probe", "D:/android_project/DouQiuQiu/docs/tasks/_b_rng_probe.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)
Rng, prd_threshold = probe.Rng, probe.prd_threshold

# ---------- S2: 每 tick 顺序 roll；敌人普攻 -> 玩家 static(0.20,CD500)；
#          敌人 chain(0.25) -> 命中玩家 -> static 再判 ----------
def s2(seed, ticks):
    r = Rng(seed); cd = 0; log = []
    for t in range(1, ticks + 1):
        cd = max(0, cd - 100)
        order = r.roll(); line = [f"t{t} order={order:.4f}"]
        if cd > 0:
            line.append("static=CD")
        else:
            v = r.roll()
            if v < 0.20:
                line.append(f"static PROC({v:.4f})"); cd = 500
            else:
                line.append(f"static miss({v:.4f})")
        c = r.roll()
        if c < 0.25:
            line.append(f"chain PROC({c:.4f})")
            if cd > 0:
                line.append("static2=CD")
            else:
                v2 = r.roll()
                if v2 < 0.20:
                    line.append(f"static2 PROC({v2:.4f})"); cd = 500
                else:
                    line.append(f"static2 miss({v2:.4f})")
        else:
            line.append(f"chain miss({c:.4f})")
        log.append(" ".join(line))
    return log

# ---------- S1c: 每 tick 顺序 + crystalys 暴击(0.20,PRD) + chain(0.25) ----------
def s1c(seed, ticks):
    r = Rng(seed); fc = 0; log = []
    for t in range(1, ticks + 1):
        order = r.roll()
        tc = prd_threshold(0.20, fc); rc = r.roll()
        if rc < tc:
            crit = f"CRIT(roll={rc:.4f}<th={tc:.4f})"; fc = 0
        else:
            crit = f"nocrit(roll={rc:.4f}>=th={tc:.4f})"; fc += 1
        ch = r.roll()
        proc = "chain PROC" if ch < 0.25 else "chain miss"
        log.append(f"t{t} order={order:.4f} {crit} {proc}({ch:.4f})")
    return log

# ---------- S4b: daedalus(0.25,PRD) -> crystalys(0.20,PRD) ----------
def s4b(seed, ticks):
    r = Rng(seed); fd = 0; fc = 0; log = []
    for t in range(1, ticks + 1):
        order = r.roll()
        td = prd_threshold(0.25, fd); rd = r.roll()
        if rd < td:
            log.append(f"t{t} daedalus CRIT(roll={rd:.4f}<th={td:.4f}) fd->0 crystalys NOT evaluated")
            fd = 0
            continue
        fd += 1
        tc = prd_threshold(0.20, fc); rc = r.roll()
        if rc < tc:
            log.append(f"t{t} daedalus miss(roll={rd:.4f}) fd={fd}; crystalys CRIT(roll={rc:.4f}<th={tc:.4f}) fc->0")
            fc = 0
        else:
            fc += 1
            log.append(f"t{t} daedalus miss(roll={rd:.4f}) fd={fd}; crystalys miss(roll={rc:.4f}>=th={tc:.4f}) fc={fc}")
    return log

if __name__ == "__main__":
    print("== S2: tick1 static PROC 且 chain miss，tick2 static PROC ==")
    for seed in range(1, 500000):
        log = s2(seed, 2)
        if "static PROC" in log[0] and "chain miss" in log[0] and "static PROC" in log[1] and "chain miss" in log[1]:
            print("seed", seed)
            for l in s2(seed, 6): print("  ", l)
            break
    else:
        print("not found; relax: tick1 static PROC + chain miss only")
        for seed in range(1, 500000):
            log = s2(seed, 4)
            if "static PROC" in log[0] and "chain miss" in log[0]:
                print("seed", seed)
                for l in log: print("  ", l)
                break

    print()
    print("== S1c: tick1 暴击且 chain PROC，tick2 不暴击且 chain miss ==")
    for seed in range(1, 500000):
        log = s1c(seed, 2)
        if "CRIT" in log[0] and "chain PROC" in log[0] and "nocrit" in log[1] and "chain miss" in log[1]:
            print("seed", seed)
            for l in log: print("  ", l)
            break

    print()
    print("== S4b: tick1 daedalus CRIT（crystalys 不评估）==")
    for seed in range(1, 500000):
        log = s4b(seed, 1)
        if "daedalus CRIT" in log[0]:
            print("seed", seed)
            for l in s4b(seed, 4): print("  ", l)
            break
    print()
    print("== S4b2: tick1 daedalus miss + crystalys CRIT（两源都评估）==")
    for seed in range(1, 500000):
        log = s4b(seed, 1)
        if "crystalys CRIT" in log[0]:
            print("seed", seed)
            for l in s4b(seed, 4): print("  ", l)
            break
