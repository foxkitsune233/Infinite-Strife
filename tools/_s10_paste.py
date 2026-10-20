"""S10 验收：复制当前场景配置 → 退出测试模式 → 重新进入 → 粘贴配置载入。

验证点：重建相同起点、生成新 attempt、配置可与导出包关联。
"""
import json
import subprocess
import sys
import time

sys.path.insert(0, "tools")
from _s33_drive import (adb, dump, find, open_testmode, pick, set_number, tap,
                        SCENES, HEROES, LOADOUTS, journal_files, newest)


def scroll_to(text, tries=6):
    for _ in range(tries):
        nodes = dump()
        hit = find(nodes, text)
        if hit:
            return hit
        from _s33_drive import swipe
        swipe(540, 1600, 540, 1100)
    return None


def main():
    open_testmode()
    # 造一个可辨识配置
    pick("起始场景", SCENES["BATTLE"])
    pick("场号", "第 1 战")
    pick("我方英雄", HEROES["viper"])
    pick("敌方英雄", HEROES["necrophos"])
    pick("配装预设", LOADOUTS["SHIELD"])
    set_number("固定种子", 4242)

    copy_btn = scroll_to("复制当前场景配置 JSON")
    if not copy_btn:
        print("FAIL: 找不到复制按钮", flush=True)
        return 1
    tap(*copy_btn, wait=1.5)
    nodes = dump()
    print("复制后提示:", [t for t, *_ in nodes if "已复制" in t or "场景" in t][:3], flush=True)

    # 退出测试模式
    quit_btn = scroll_to("退出测试模式")
    if not quit_btn:
        print("FAIL: 找不到退出按钮", flush=True)
        return 1
    tap(*quit_btn, wait=1.5)
    nodes = dump()
    if find(nodes, "彻底退出"):                    # 可能的二次确认
        tap(*find(nodes, "彻底退出"), wait=1.5)
    time.sleep(4)
    nodes = dump()
    exited = not find(nodes, "测试存档 · ")
    print("退出测试模式:", "已退出" if exited else "仍在测试模式", flush=True)

    # 重新进入
    open_testmode()
    nodes = dump()
    print("重进后标识:", [t for t, *_ in nodes if "存档 ·" in t][:1], flush=True)

    paste_btn = scroll_to("粘贴配置 JSON 并载入")
    if not paste_btn:
        print("FAIL: 找不到粘贴按钮", flush=True)
        return 1
    tap(*paste_btn, wait=1.5)
    nodes = dump()
    field = find(nodes, "粘贴其他测试者提供的场景JSON")
    if field:
        tap(*field, wait=1.0)
    adb(["shell", "input", "keyevent", "279"])     # KEYCODE_PASTE
    time.sleep(1.0)
    nodes = dump()
    confirm = find(nodes, "载入", exact=True)
    if not confirm:
        print("FAIL: 粘贴对话框没有载入按钮；当前:", [t[:16] for t, *_ in nodes][:8], flush=True)
        return 1
    before = journal_files()
    tap(*confirm, wait=0.5)
    time.sleep(7)

    nodes = dump()
    if find(nodes, "测试场景未载入"):
        print("REJECTED:", [t for t, *_ in nodes][:6], flush=True)
        return 2
    print("载入后标识:", [t for t, *_ in nodes if "存档 ·" in t][:1], flush=True)
    name, rec = newest(before)
    print("新 attempt 文件:", name, flush=True)
    if rec:
        print("RECORD:", json.dumps({"scenario": rec.get("scenario"), "attempt": rec.get("scenarioAttempt"),
                                     "player": rec["before"]["player"]["hero"],
                                     "enemy": rec["before"]["enemy"]["hero"]}, ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
