"""S05 设备验收驱动：六英雄轮转直接战斗，用战斗日志核对所选英雄是否生效。

用法：python tools/_s05_hero_matrix.py
仅用于验收，不参与生产构建。
"""
import json
import re
import subprocess
import sys
import time

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"
PKG = "com.example.douqiuqiu"
JOURNAL = "files/test-battle-records"


def adb(args, timeout=60):
    r = subprocess.run([ADB] + args, capture_output=True, timeout=timeout)
    return r.stdout


def tap(x, y):
    adb(["shell", "input", "tap", str(x), str(y)])
    time.sleep(0.9)


def swipe(x1, y1, x2, y2, ms=400):
    adb(["shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms)])
    time.sleep(1.2)


def dump():
    """返回 [(text, cx, cy)]，来自 uiautomator dump。"""
    adb(["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
    raw = adb(["exec-out", "cat", "/sdcard/ui.xml"])
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
            out.append((t, (l + r) // 2, (tp + b) // 2))
    return out


def find(nodes, needle, exact=False):
    for t, x, y in nodes:
        if (t == needle) if exact else (needle in t):
            return x, y
    return None


def journal_files():
    out = adb(["shell", "run-as", PKG, "ls", "-1", JOURNAL])
    return set(l.strip() for l in out.decode("utf-8", "ignore").splitlines() if l.strip())


def newest_record(before_files):
    after = journal_files()
    added = sorted(after - before_files)
    target = added[-1] if added else None
    if target is None:
        return None, None
    raw = adb(["exec-out", "run-as", PKG, "cat", JOURNAL + "/" + target])
    lines = [l for l in raw.decode("utf-8", "ignore").splitlines() if l.strip()]
    if not lines:
        return target, None
    return target, json.loads(lines[0])


def open_testmode():
    """从任意界面回到通用测试模式配置页。"""
    # 先关掉可能残留的弹窗/选择器
    for _ in range(3):
        nodes = dump()
        if find(nodes, "设置", exact=True) or find(nodes, "通用测试模式"):
            break
        adb(["shell", "input", "keyevent", "4"])
        time.sleep(1.0)
    nodes = dump()
    hit = find(nodes, "通用测试模式")
    if hit:                      # 已经停在配置页
        tap(*hit)
        time.sleep(1.5)
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
    # 配置页回到顶部，保证各选择器行坐标固定
    for _ in range(4):
        nodes = dump()
        if find(nodes, "起始场景", exact=True) and find(nodes, "载入所选测试场景"):
            break
        swipe(540, 700, 540, 1900)
    time.sleep(0.5)


def pick(selector_label, value):
    """点开某选择器（值行）并按文本选择。值行固定在标签下方 117px。"""
    rows = {"起始场景": 693, "场号": 962, "我方英雄": 1231,
            "敌方英雄": 1500, "事件类型": 1769, "配装预设": 2038}
    if selector_label not in rows:
        raise RuntimeError("未知选择器：" + selector_label)
    tap(477, rows[selector_label])
    nodes = dump()
    item = find(nodes, value, exact=True)
    if not item:
        raise RuntimeError("选项里没有：%s（当前项：%s）" % (value, [n[0][:8] for n in nodes]))
    tap(*item)
    # 点「已选中项」时下拉不会自动关闭，收尾校验一次
    nodes = dump()
    if not find(nodes, "起始场景"):
        adb(["shell", "input", "keyevent", "4"])
        time.sleep(0.8)


def main():
    heroes = ["主宰", "天怒法师", "幻影刺客", "亚巴顿", "瘟疫法师", "冥界亚龙"]
    ids = ["juggernaut", "skywrath", "phantom_assassin", "abaddon", "necrophos", "viper"]
    plan = [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 0)]
    results = []
    for i, (p, e) in enumerate(plan):
        open_testmode()
        pick("我方英雄", heroes[p])
        pick("敌方英雄", heroes[e])
        nodes = dump()
        load = find(nodes, "载入所选测试场景")
        if not load:
            raise RuntimeError("找不到载入按钮")
        before = journal_files()
        tap(*load)
        time.sleep(7)
        name, rec = newest_record(before)
        if rec is None:
            results.append((heroes[p], heroes[e], "NO_RECORD", name))
            print("[%d] %s vs %s -> 无新日志 %s" % (i + 1, heroes[p], heroes[e], name), flush=True)
            continue
        ph = rec["before"]["player"]["hero"]
        eh = rec["before"]["enemy"]["hero"]
        ok = (ph == ids[p] and eh == ids[e])
        results.append((heroes[p], heroes[e], "%s/%s" % (ph, eh), "OK" if ok else "MISMATCH"))
        print("[%d] 选 %s vs %s -> 日志 %s vs %s  %s  (%s)" % (
            i + 1, heroes[p], heroes[e], ph, eh, "OK" if ok else "不匹配", name), flush=True)
    print("\n=== 汇总 ===", flush=True)
    for r in results:
        print("  %s vs %s -> %s  %s" % r, flush=True)
    bad = [r for r in results if r[3] not in ("OK",)]
    print("全部通过" if not bad else "存在异常 %d 项" % len(bad), flush=True)
    return 0 if not bad else 1


if __name__ == "__main__":
    sys.exit(main())
