"""任务33 通用测试模式：场景载入驱动 + 结果回读。

用于 S05–S09 的设备验收，不参与生产构建。

用法示例：
  python tools/_s33_drive.py --scene BATTLE --hero juggernaut --enemy skywrath --loadout FAST
  python tools/_s33_drive.py --scene BATTLE --control stun --expanded
  python tools/_s33_drive.py --scene REST --round 3
  python tools/_s33_drive.py --scene BATTLE --seed 0            # 非法种子，期望被拒
"""
import argparse
import json
import re
import subprocess
import sys
import time

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"
PKG = "com.example.douqiuqiu"
JOURNAL = "files/test-battle-records"

SCENES = {"EVENT": "指定事件", "REWARD": "战后奖励／第五战通关", "REST": "第三战休息",
          "READY": "开战前整备", "BATTLE": "直接进入战斗", "HOME": "营地／商店／成长",
          "APPRAISAL": "宝箱预览与鉴定"}
HEROES = {"juggernaut": "主宰", "skywrath": "天怒法师", "phantom_assassin": "幻影刺客",
          "abaddon": "亚巴顿", "necrophos": "瘟疫法师", "viper": "冥界亚龙"}
LOADOUTS = {"BASIC": "基础配装", "FAST": "高攻速普攻", "POISON": "普通毒／剧毒",
            "SHIELD": "护盾构筑", "ACTIVE": "主动装备与恢复品", "CONTROL": "自然控制与解控"}
CONTROLS = {"none": "无", "stun": "固定眩晕", "silence": "固定沉默",
            "hex": "固定妖术", "disarm": "固定缴械"}
EVENTS = {"ABANDONED_DEPOT": "废弃补给站", "RUSTED_WORKSHOP": "锈蚀工坊",
          "RUINED_LIBRARY": "残破书库", "COLLAPSED_STORAGE": "坍塌仓室",
          "SAFE_WRECKAGE": "保险库残骸", "TECH_TERMINAL": "技术终端",
          "FIELD_CLINIC": "临时医棚", "BLOCKER": "拦路者"}


def adb(args, timeout=60):
    return subprocess.run([ADB] + args, capture_output=True, timeout=timeout).stdout


def tap(x, y, wait=0.85):
    adb(["shell", "input", "tap", str(int(x)), str(int(y))])
    time.sleep(wait)


def swipe(x1, y1, x2, y2, ms=350):
    adb(["shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms)])
    time.sleep(1.0)


def dump():
    raw = b""
    for _ in range(4):
        adb(["shell", "rm", "-f", "/sdcard/ui.xml"])
        p = subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"],
                           capture_output=True, timeout=60)
        if b"dumped to" not in p.stdout:
            time.sleep(0.7)
            continue
        raw = adb(["exec-out", "cat", "/sdcard/ui.xml"])
        if raw:
            break
    try:
        xml = raw.decode("utf-8")
    except UnicodeDecodeError:
        xml = raw.decode("utf-16", errors="ignore")
    xml = xml.lstrip("\ufeff\x00")
    out = []
    for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        t = m.group(1)
        l, tp, r, b = (int(m.group(i)) for i in (2, 3, 4, 5))
        if t.strip():
            out.append((t, (l + r) // 2, (tp + b) // 2, tp, b))
    return out


def dump_cd():
    """同 dump()，但同时抓 content-desc（自定义 View 只有 contentDescription 没有 text）。
    返回 (text 或 content-desc, cx, cy, top, bottom, class, clickable)。"""
    raw = b""
    for _ in range(4):
        adb(["shell", "rm", "-f", "/sdcard/ui.xml"])
        p = subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"],
                           capture_output=True, timeout=60)
        if b"dumped to" not in p.stdout:
            time.sleep(0.7)
            continue
        raw = adb(["exec-out", "cat", "/sdcard/ui.xml"])
        if raw:
            break
    try:
        xml = raw.decode("utf-8")
    except UnicodeDecodeError:
        xml = raw.decode("utf-16", errors="ignore")
    xml = xml.lstrip("\ufeff\x00")
    out = []
    for m in re.finditer(r"<node\b[^>]*/>", xml):
        tag = m.group(0)
        def attr(name):
            mm = re.search(name + r'="([^"]*)"', tag)
            return mm.group(1) if mm else ""
        label = attr("text") or attr("content-desc")
        if not label.strip():
            continue
        bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if not bm:
            continue
        l, tp, r, b = (int(bm.group(i)) for i in (1, 2, 3, 4))
        out.append((label, (l + r) // 2, (tp + b) // 2, tp, b,
                    attr("class"), attr("clickable") == "true"))
    return out


def find(nodes, needle, exact=False):
    for t, x, y, tp, b in nodes:
        if (t == needle) if exact else (needle in t):
            return x, y
    return None


def find_cd(nodes, needle, exact=False, clickable_only=False):
    """find() 的 dump_cd 版（7 元组）。"""
    for t, x, y, tp, b, cls, cl in nodes:
        if clickable_only and not cl:
            continue
        if (t == needle) if exact else (needle in t):
            return x, y
    return None


def at_top(direction_up=True):
    """把配置页滚到顶部或底部。direction_up=True 表示滚到顶部。"""
    for _ in range(4):
        if direction_up:
            swipe(540, 900, 540, 1900)
        else:
            swipe(540, 1900, 540, 900)


def ensure(label, lo=420, hi=1560, tries=8):
    """保证某标签落在可点区域，返回其中心坐标。"""
    scrolled = False
    for i in range(tries):
        nodes = dump()
        hit = find(nodes, label)
        if hit:
            x, y = hit
            if lo <= y <= hi:
                return x, y
            if y > hi:                       # 太靠下 → 内容上移
                swipe(540, 1500, 540, 1500 - min(650, y - 800))
            else:                            # 太靠上 → 内容下移
                swipe(540, 900, 540, 900 + min(650, 1200 - y))
            continue
        if not scrolled:                     # 先回顶部再逐段下滚
            at_top()
            scrolled = True
        else:
            swipe(540, 1600, 540, 1000)
    raise RuntimeError("定位不到标签：%s" % label)


def pick(label, value, pad=117):
    """展开标签下方的选择器并选中指定文本项。"""
    x, y = ensure(label)
    tap(x, y + pad)
    for _ in range(4):
        nodes = dump()
        item = find(nodes, value, exact=True)
        if item:
            tap(*item)
            nodes = dump()
            if find(nodes, label):        # 下拉已关闭
                return
            adb(["shell", "input", "keyevent", "4"])   # 选的是已选项，手动收起
            return
        swipe(540, 1500, 540, 1200)       # 下拉内滚动
    raise RuntimeError("下拉里找不到：%s" % value)


def set_number(label, text):
    x, y = ensure(label)
    tap(540, y + 90, wait=1.2)
    adb(["shell", "input", "keyevent", "123"])
    for _ in range(8):
        adb(["shell", "input", "keyevent", "67"])
    adb(["shell", "input", "text", str(text)])
    time.sleep(0.4)
    adb(["shell", "input", "keyevent", "111"])   # ESC 收起键盘
    time.sleep(0.6)


def toggle(label):
    x, y = ensure(label)
    tap(x, y)


def open_testmode():
    # 鉴定对话框不可取消，先正常收尾
    for _ in range(2):
        nodes = dump()
        done = find(nodes, "查看结果并继续")
        if not done:
            break
        tap(*done, wait=1.2)
    for _ in range(3):
        nodes = dump()
        if find(nodes, "设置", exact=True) or find(nodes, "起始场景"):
            break
        adb(["shell", "input", "keyevent", "4"])
        time.sleep(0.9)
    nodes = dump()
    if find(nodes, "起始场景"):          # 已在配置页
        at_top()
        return
    hit = find(nodes, "设置", exact=True)
    if hit:
        tap(*hit)
    nodes = dump()
    hit = find(nodes, "存档与诊断")
    if hit:
        tap(*hit)
    nodes = dump()
    hit = find(nodes, "通用测试模式")
    if not hit:
        swipe(540, 1900, 540, 900)
        nodes = dump()
        hit = find(nodes, "通用测试模式")
    if not hit:
        raise RuntimeError("找不到通用测试模式入口")
    tap(*hit)
    time.sleep(2.0)
    at_top()


def journal_files():
    out = adb(["shell", "run-as", PKG, "ls", "-1", JOURNAL])
    return set(l.strip() for l in out.decode("utf-8", "ignore").splitlines() if l.strip())


def newest(before):
    added = sorted(journal_files() - before)
    if not added:
        return None, None
    name = added[-1]
    raw = adb(["exec-out", "run-as", PKG, "cat", JOURNAL + "/" + name])
    lines = [l for l in raw.decode("utf-8", "ignore").splitlines() if l.strip()]
    return name, (json.loads(lines[0]) if lines else None)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scene", default="BATTLE")
    ap.add_argument("--round", type=int, default=1)
    ap.add_argument("--hero", default="juggernaut")
    ap.add_argument("--enemy", default="skywrath")
    ap.add_argument("--event", default="FIELD_CLINIC")
    ap.add_argument("--loadout", default="BASIC")
    ap.add_argument("--control", default="none")
    ap.add_argument("--health", default="100")
    ap.add_argument("--seed", default=None)
    ap.add_argument("--roll", default=None)
    ap.add_argument("--gold", default=None)
    ap.add_argument("--weak", action="store_true")
    ap.add_argument("--expanded", action="store_true")
    ap.add_argument("--wait", type=float, default=7.0)
    ap.add_argument("--label", default="")
    a = ap.parse_args()

    open_testmode()
    pick("起始场景", SCENES[a.scene])
    pick("场号", "第 %d 战" % a.round)
    if a.scene in ("BATTLE", "READY"):
        pick("我方英雄", HEROES[a.hero])
        if a.scene == "BATTLE":
            pick("敌方英雄", HEROES[a.enemy])
    if a.scene == "EVENT":
        pick("事件类型", EVENTS[a.event])
    pick("配装预设", LOADOUTS[a.loadout])
    if a.scene == "BATTLE":
        pick("我方控制夹具", CONTROLS[a.control])
        pick("我方起始生命", a.health + "%")
    if a.seed is not None:
        set_number("固定种子", a.seed)
    if a.roll is not None:
        set_number("事件roll", a.roll)
    if a.gold is not None:
        set_number("本局金币", a.gold)
    if a.weak:
        toggle("弱敌夹具")
    if a.expanded:
        toggle("八技能位")

    nodes = dump()
    load = find(nodes, "载入所选测试场景")
    if not load:
        at_top()
        nodes = dump()
        load = find(nodes, "载入所选测试场景")
    if not load:
        raise RuntimeError("找不到载入按钮")

    before = journal_files()
    tap(*load, wait=0.5)
    time.sleep(a.wait)

    nodes = dump()
    if find(nodes, "测试场景未载入"):
        texts = [t for t, _, _, _, _ in nodes]
        print("REJECTED:", json.dumps(texts, ensure_ascii=False), flush=True)
        return 2

    name, rec = newest(before)
    print("JOURNAL:", name, flush=True)
    if rec is None:
        print("NO_RECORD", flush=True)
        return 3
    view = {
        "scenario": rec.get("scenario"),
        "attempt": rec.get("scenarioAttempt"),
        "action": rec.get("action"),
        "player": rec["before"]["player"]["hero"],
        "enemy": rec["before"]["enemy"]["hero"],
        "playerHp": [rec["before"]["player"]["hp"], rec["before"]["player"]["maxHp"]],
        "speed": [rec["before"]["player"]["speed"], rec["before"]["enemy"]["speed"]],
        "control": rec["before"]["player"]["control"],
        "equipment": [e["id"] for e in rec["before"]["player"]["equipment"]],
        "manual": rec["before"]["player"]["manualItems"],
        "poison": bool(rec["before"]["player"]["poisonState"]),
        "shields": rec["before"]["player"]["shields"],
    }
    print("RECORD:", json.dumps(view, ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
