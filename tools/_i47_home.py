# -*- coding: utf-8 -*-
"""47 号夹具 v3：通过粘贴配置 JSON 载入 HOME + expanded + ACTIVE。"""
import sys, time, re, subprocess
sys.path.insert(0, "tools")
import _s33_drive as d

JSON = ('{"scene":"HOME","round":1,"hero":"juggernaut","enemy":"skywrath",'
        '"event":"FIELD_CLINIC","loadout":"ACTIVE","seed":1729,"eventRoll":0,'
        '"runGold":999,"healthPercent":50,"control":"none","weakEnemy":false,'
        '"expanded":true,"fixture":"none","startPaused":false}')

d.open_testmode()
nodes = d.dump()
btn = d.find(nodes, "粘贴配置 JSON")
print("paste btn:", btn, flush=True)
d.tap(*btn, wait=1.2)

# 弹窗里的 EditText：按 class 定位
xml = d.adb(["exec-out", "cat", "/sdcard/ui.xml"]).decode("utf-8", "ignore")
m = re.search(r'<node[^>]*class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
print("edittext:", m.groups() if m else None, flush=True)
if not m:
    raise SystemExit("找不到粘贴输入框")
ex, ey = (int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2
d.tap(ex, ey, wait=1.0)
subprocess.run([d.ADB, "shell", "input", "text", JSON], timeout=30)
time.sleep(0.5)
nodes = d.dump()
load = d.find(nodes, "载入", exact=True)
print("dialog load btn:", load, flush=True)
if not load:
    # 对话框按钮可能是"载入"两个字的按钮
    for t, x, y, _, _ in nodes:
        if t.strip() == "载入":
            load = (x, y); break
d.tap(*load, wait=1.0)
time.sleep(8)
nodes = d.dump()
print("AFTER:", [t for t, *_ in nodes][:18], flush=True)
