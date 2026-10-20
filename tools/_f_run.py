"""任务34：配置测试场景 → 载入瞬间开录 → 抽帧分析。

与 _s33_drive.py 共用导航逻辑，区别是载入后立刻录屏，避免错过开场动作。

  python tools/_f_run.py --tag f01s0 --hero juggernaut --enemy abaddon --seconds 6
  python tools/_f_run.py --tag f03fast --hero phantom_assassin --enemy abaddon --loadout FAST
"""
import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import _s33_drive as d
import _f_anim as anim


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--scene", default="BATTLE")
    ap.add_argument("--round", type=int, default=1)
    ap.add_argument("--hero", default="juggernaut")
    ap.add_argument("--enemy", default="abaddon")
    ap.add_argument("--event", default="FIELD_CLINIC")
    ap.add_argument("--loadout", default="BASIC")
    ap.add_argument("--control", default="none")
    ap.add_argument("--health", default="100")
    ap.add_argument("--seed", default=None)
    ap.add_argument("--weak", action="store_true")
    ap.add_argument("--expanded", action="store_true")
    ap.add_argument("--seconds", type=int, default=6)
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--region", default="40,410,690,760")
    ap.add_argument("--top", type=int, default=8)
    a = ap.parse_args()

    d.open_testmode()
    d.pick("起始场景", d.SCENES[a.scene])
    d.pick("场号", "第 %d 战" % a.round)
    if a.scene in ("BATTLE", "READY"):
        d.pick("我方英雄", d.HEROES[a.hero])
        if a.scene == "BATTLE":
            d.pick("敌方英雄", d.HEROES[a.enemy])
    if a.scene == "EVENT":
        d.pick("事件类型", d.EVENTS[a.event])
    d.pick("配装预设", d.LOADOUTS[a.loadout])
    if a.scene == "BATTLE":
        d.pick("我方控制夹具", d.CONTROLS[a.control])
        d.pick("我方起始生命", a.health + "%")
    if a.seed is not None:
        d.set_number("固定种子", a.seed)
    if a.weak:
        d.toggle("弱敌夹具")
    if a.expanded:
        d.toggle("八技能位")

    nodes = d.dump()
    load = d.find(nodes, "载入所选测试场景")
    if not load:
        d.at_top()
        load = d.find(d.dump(), "载入所选测试场景")
    if not load:
        raise RuntimeError("找不到载入按钮")

    print("loading scenario ...", flush=True)
    d.tap(*load, wait=0.25)
    return anim.capture(a.tag, a.seconds, a.fps, a.region, a.top)


if __name__ == "__main__":
    sys.exit(main())
