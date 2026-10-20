# -*- coding: utf-8 -*-
"""47 号复现 v4：点击 C11 预设 + 载入所选测试场景。"""
import sys, time
sys.path.insert(0, "tools")
import _s33_drive as d

d.open_testmode()

def find_text(sub):
    for _ in range(8):
        nodes = d.dump()
        for t, x, y, _, _ in nodes:
            if sub in t:
                return x, y
        d.adb(["shell", "input", "swipe", "540", "700", "540", "1800"])
        time.sleep(1.0)
    return None

c11 = find_text("C11")
print("C11 btn:", c11, flush=True)
if not c11:
    raise SystemExit("no C11")
d.tap(*c11, wait=1.5)

load = None
for _ in range(4):
    nodes = d.dump()
    for t, x, y, _, _ in nodes:
        if t.strip() == "载入所选测试场景":
            load = (x, y); break
    if load: break
    d.adb(["shell", "input", "swipe", "540", "1800", "540", "600"])
    time.sleep(1.0)
print("load btn:", load, flush=True)
d.tap(*load, wait=3.0)
time.sleep(10)
nodes = d.dump()
print("AFTER:", [t for t, *_ in nodes][:16], flush=True)
d.adb(["logcat", "-c"])
print("logcat armed", flush=True)
