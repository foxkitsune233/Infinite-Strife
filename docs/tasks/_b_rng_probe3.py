#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""S2 扩展：找 tick1 static PROC + chain PROC 的种子，并打印 12 tick 完整轨迹。"""
import importlib.util
spec = importlib.util.spec_from_file_location("probe", "D:/android_project/DouQiuQiu/docs/tasks/_b_rng_probe.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)
Rng = probe.Rng

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

found = False
for seed in range(1, 2000000):
    log = s2(seed, 12)
    if "static PROC" in log[0] and "chain PROC" in log[0]:
        print("seed", seed)
        for l in log: print("  ", l)
        found = True
        break
if not found:
    print("no seed with t1 static+chain both proc; fallback: t1 static PROC only")
    for l in s2(1, 12): print("  ", l)
