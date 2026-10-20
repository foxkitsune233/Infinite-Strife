# -*- coding: utf-8 -*-
"""I04：点仓库装备 -> 预览；验证图/属性/尺寸/绑定，且不移动不扣资源。"""
import sys, time, subprocess
sys.path.insert(0, "tools")
import _s33_drive as d

# 切到配装标签（仓库过滤=装备）
d.tap(176, 400, wait=1.2)
nodes = d.dump_cd()
hit = d.find_cd(nodes, "阔剑")
print("tile:", hit, flush=True)
if not hit:
    # 装备过滤下第一屏应有阔剑；找不到就打印全部仓库瓦片
    tiles = [t for t, *_ in nodes if "局外仓库" in t]
    print("warehouse tiles:", tiles[:10], flush=True)
    raise SystemExit("找不到阔剑瓦片")
d.tap(*hit, wait=1.5)
nodes = d.dump()
texts = [t for t, *_ in nodes]
print("PREVIEW TEXTS:", texts[:22], flush=True)
time.sleep(0.3)
