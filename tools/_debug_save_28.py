# -*- coding: utf-8 -*-
"""
Task 28 手动验收调试脚本：自定义编辑对局存档（inventory-v1.dat，v9 二进制格式）。

用途：把当前跑局改写成「第 N 轮 · EVENT 阶段 · 指定事件类型」的确定性状态，
免去随机撞事件。当前 recipe：拦路者（BLOCKER）+ 背包可交付物 + 安全箱物品。

存档格式（与 app/src/main/java/com/example/douqiuqiu/inventory/ 下三个文件逐字节对应）：
  文件头: MAGIC(0x44515131) VERSION(9) payloadLen CRC32(payload) payload
  payload: gold runGold bw bh sw sh skillSlots phase itemCount items...
           + CampaignBinaryCodec(seed batch clears extracted lastEnding shopCycle shopSeen
             shopStock manualItems chests run economy shop unlockedHeroes materialPity)
  run 段: id heroId target(chest) rng hp maxHp round stage pendingItem eventRoll fight?
           equipmentCooldowns nodeEvent
  约束（InventoryRules.validate + JourneyValidation.check）：
    - 有 run 时 chests 必须为空
    - nodeEvent.nodeId 必须等于 "<run.id>:event:<run.round>"
    - nodeEvent.outcomes 可为空（resolve 时按需现算）

用法（在 DouQiuQiu 目录下）：
  python tools/_debug_save_28.py pull                 # 拉取当前存档
  python tools/_debug_save_28.py craft BLOCKER        # 生成一个指定事件快照
  python tools/_debug_save_28.py craft-battle        # 保留当前真实 fight 段，生成 BATTLE 快照
  python tools/_debug_save_28.py run-battle         # pull → craft-battle → push
  python tools/_debug_save_28.py all                  # 拉取后一次生成八类事件快照
  python tools/_debug_save_28.py all-local            # 用已有 _dbg_save.dat 生成八类快照
  python tools/_debug_save_28.py push BLOCKER         # 推送指定快照并重启应用
  python tools/_debug_save_28.py run BLOCKER          # 拉取、生成、推送，一条命令完成
  python tools/_debug_save_28.py restore  # 备份还原（push 前自动留 _dbg_save.before.dat）

仅改存档文件，不碰任何生产代码。
"""
import struct
import sys
import zlib

ADB = "D:/Android/Sdk/platform-tools/adb.exe"
PKG = "com.example.douqiuqiu"
# run-as 的 cwd 是 /data/data/<pkg>，但 adb shell 的重定向在设备 shell（cwd=/）里解析，
# 因此 cat 用相对路径（cwd 即 app 目录）、重定向必须用绝对路径。
SAVE_REMOTE = "files/profile/inventory-v1.dat"
SAVE_ABS = "/data/data/%s/%s" % (PKG, SAVE_REMOTE)
SAVE_LOCAL = "tools/_dbg_save.dat"
SAVE_BEFORE = "tools/_dbg_save.before.dat"
SCENARIO_DIR = "tools/_event_scenarios"
MAGIC = 0x44515131
VERSION = 9

EVENT_KINDS = (
    "ABANDONED_DEPOT", "RUSTED_WORKSHOP", "RUINED_LIBRARY", "COLLAPSED_STORAGE",
    "SAFE_WRECKAGE", "TECH_TERMINAL", "FIELD_CLINIC", "BLOCKER",
)

# 当前资产中的非 1x1 物品。未知定义按 1x1 处理；调试物品本身均为 1x1。
DEF_SIZE = {"broadsword": (2, 1), "backpack_fabric": (1, 2),
            "safe_lock": (3, 2), "safe_frame": (3, 3)}

# ---------------- recipe（按需修改） ----------------
RECIPE = {
    "run_id": "dbg-1",
    "hero": "juggernaut",
    "round": 2,
    "stage": "EVENT",
    "event_kind": "BLOCKER",
    "rng": 123456,
    "hp": 1000.0,
    "max_hp": 1000.0,
    "run_gold": 100,
    "wallet_gold": 999,
}


# ---------------- 二进制读写 ----------------
class Reader:
    def __init__(self, data: bytes, pos: int = 0):
        self.d = data
        self.p = pos

    def int(self):
        v = struct.unpack_from(">i", self.d, self.p)[0]
        self.p += 4
        return v

    def long(self):
        v = struct.unpack_from(">q", self.d, self.p)[0]
        self.p += 8
        return v

    def double(self):
        v = struct.unpack_from(">d", self.d, self.p)[0]
        self.p += 8
        return v

    def bool(self):
        v = self.d[self.p] != 0
        self.p += 1
        return v

    def utf(self):
        n = struct.unpack_from(">H", self.d, self.p)[0]
        self.p += 2
        s = self.d[self.p:self.p + n].decode("utf-8")
        self.p += n
        return s


class Writer:
    def __init__(self):
        self.buf = bytearray()

    def int(self, v):
        self.buf += struct.pack(">i", v)

    def long(self, v):
        self.buf += struct.pack(">q", v)

    def double(self, v):
        self.buf += struct.pack(">d", v)

    def bool(self, v):
        self.buf += b"\x01" if v else b"\x00"

    def utf(self, s):
        e = s.encode("utf-8")
        assert len(e) < 65536
        self.buf += struct.pack(">H", len(e)) + e

    def raw(self, b):
        self.buf += b

    def bytes(self):
        return bytes(self.buf)


def skip_chest(r: Reader):
    r.utf()  # id
    r.utf()  # quality
    r.int()  # difficulty
    r.int()  # ticket
    r.int()  # appraisals
    n = r.int()
    for _ in range(n):
        r.utf(); r.int(); r.int(); r.int()  # def x y reveal
    r.utf()  # theme
    g = r.int()
    for _ in range(g):
        r.utf()


def skip_poison_layer(r: Reader):
    r.utf(); r.utf(); r.utf(); r.utf()
    r.double(); r.int(); r.long(); r.bool()


def skip_gear(r: Reader):
    n = r.int()
    for _ in range(n):
        r.utf(); r.utf()
    # cooldown
    r.int()
    c = r.int()
    for _ in range(c):
        r.utf(); r.long()
    a = r.int()
    for _ in range(a):
        r.utf(); r.long()
    r.long()   # protectionMs
    r.bool()   # started
    m = r.int()
    for _ in range(m):
        r.utf()
    f = r.int()
    for _ in range(f):
        r.utf(); r.int()


def skip_fighter(r: Reader):
    r.utf()  # heroId
    for _ in range(25):
        r.double()
    r.int()  # flareLeft
    n = r.int()
    for _ in range(n):
        r.double(); r.double(); r.utf(); r.bool()
    r.double()  # skinSlowTime
    r.double()  # physicalShield
    if r.bool():  # shieldSources
        c = r.int()
        for _ in range(c):
            r.utf(); r.utf(); r.double()
            if r.bool():
                r.long()
    for _ in range(7):
        r.bool()  # control
    cards = r.int()
    for _ in range(cards):
        r.utf(); r.utf(); r.int()
    r.bool()  # started
    r.int()   # attackCount
    for _ in range(r.int()):
        r.utf(); r.int()
    for _ in range(r.int()):
        r.utf(); r.double()
    for _ in range(r.int()):
        r.utf(); r.utf(); r.double(); r.int()
    for _ in range(r.int()):
        r.utf(); r.int()
    r.int()  # poisonSerial
    if r.bool():  # poisonPool
        for _ in range(r.int()):
            skip_poison_layer(r)
        for _ in range(r.int()):
            skip_poison_layer(r)
    r.long()  # immunityMs
    skip_gear(r)


def skip_fight(r: Reader):
    skip_fighter(r)
    skip_fighter(r)
    r.long()  # rng
    r.int()   # tick
    r.int()   # won
    for _ in range(r.int()):
        r.utf()  # log


def skip_cooldown(r: Reader):
    r.int()
    for _ in range(r.int()):
        r.utf(); r.long()


def skip_node(r: Reader):
    if not r.bool():
        return
    r.utf(); r.utf(); r.int(); r.utf(); r.int()
    r.bool() and r.utf()
    for _ in range(r.int()):
        r.utf(); r.int(); r.double()
        for _ in range(r.int()):
            r.utf()
        r.bool()


def roll_for(seed: int, node_id: str) -> int:
    state = min(max(seed, 1), 2147483646)
    for ch in node_id:
        state = (state * 48271 + ord(ch)) % 2147483647
    return state % 100


# ---------------- 序列化 ----------------
def write_chest(w: Writer, chest_id: str, quality: str, difficulty: int, ticket: int,
                items, theme: str = "SUPPLIES", guaranteed=()):
    w.utf(chest_id); w.utf(quality); w.int(difficulty); w.int(ticket); w.int(0)
    w.int(len(items))
    for it in items:
        w.utf(it[0]); w.int(it[1]); w.int(it[2]); w.int(it[3])
    w.utf(theme)
    w.int(len(guaranteed))
    for g in sorted(guaranteed):
        w.utf(g)


def build_run(w: Writer, recipe):
    run_id = recipe["run_id"]
    rnd = recipe["round"]
    node_id = "%s:event:%d" % (run_id, rnd)
    w.bool(True)
    w.utf(run_id)
    w.utf(recipe["hero"])
    # target chest：最小合法箱（1 件铁树枝干）
    write_chest(w, "dbg-chest", "WHITE", 0, 60, [("iron_branch", 0, 0, 0)])
    w.long(recipe["rng"])
    w.double(recipe["hp"]); w.double(recipe["max_hp"])
    w.int(rnd); w.utf(recipe["stage"])
    w.utf("")            # pendingItem
    w.int(0)             # eventRoll
    fight_raw = recipe.get("fight_raw")
    w.bool(fight_raw is not None)
    if fight_raw is not None:
        w.raw(fight_raw)
    # equipmentCooldowns：空
    w.int(0); w.int(0)
    if recipe["stage"] == "EVENT":
        # nodeEvent：指定事件，outcomes 留空（resolve 时现算）
        w.bool(True)
        w.utf(node_id)
        w.utf(recipe["event_kind"])
        w.int(roll_for(recipe["rng"], node_id))
        w.utf("")                               # requiredItemDefinitionId
        w.int(20 + rnd * 10 + 0 * 10)           # priceGold = 20+round*10+difficulty*10
        w.bool(False)                           # resolvedOptionId = null
        w.int(0)                                # outcomes 空
    else:
        w.bool(False)                           # BATTLE 不携带事件节点


def free_slot(items, container, width, height, definition_id):
    """为调试物品找真实空格，避免固定写入 (0,0) 覆盖用户已有物品。"""
    dw, dh = DEF_SIZE.get(definition_id, (1, 1))
    occupied = set()
    for _, definition, cont, x, y, rotated, *_ in items:
        if cont != container:
            continue
        w, h = DEF_SIZE.get(definition, (1, 1))
        if rotated:
            w, h = h, w
        for xx in range(x, x + w):
            for yy in range(y, y + h):
                occupied.add((xx, yy))
    for y in range(height - dh + 1):
        for x in range(width - dw + 1):
            if all((x + xx, y + yy) not in occupied for xx in range(dw) for yy in range(dh)):
                return x, y
    raise ValueError("没有可放置调试物品的 %s 空格，请先整理背包/安全箱" % container)


def craft(data: bytes, event_kind="BLOCKER") -> bytes:
    if event_kind not in EVENT_KINDS:
        raise ValueError("未知事件 %s，可选：%s" % (event_kind, ", ".join(EVENT_KINDS)))
    assert data[:4] == struct.pack(">i", MAGIC), "不是有效存档（MAGIC 不匹配）"
    version, length, crc = struct.unpack_from(">iiQ", data, 4)
    assert version == VERSION, "仅支持 v9，当前 v%d" % version
    payload = data[20:]
    assert len(payload) == length, "长度字段不符"
    assert zlib.crc32(payload) & 0xFFFFFFFFFFFFFFFF == crc, "CRC 不符"

    r = Reader(payload)
    gold = r.int(); run_gold = r.int()
    bw, bh, sw, sh = r.int(), r.int(), r.int(), r.int()
    slots = r.int()
    phase = r.utf()
    item_count = r.int()
    items = []
    for _ in range(item_count):
        iid = r.utf(); defn = r.utf(); cont = r.utf()
        x = r.int(); y = r.int(); rot = r.bool(); star = r.int(); bound = r.bool(); cnt = r.int()
        items.append((iid, defn, cont, x, y, rot, star, bound, cnt))
    # 调试物品不应从基底继承：任何 dbg- 前缀的物品在此清除，避免污染非 BLOCKER 快照
    items = [it for it in items if not it[0].startswith("dbg-")]
    # ---- campaign ----
    seed = r.long(); batch = r.int(); clears = r.int(); extracted = r.bool()
    last_ending = r.utf(); shop_cycle = r.long(); shop_seen = r.long()
    shop_stock = []
    for _ in range(r.int()):
        shop_stock.append((r.utf(), r.int()))
    manual = []
    for _ in range(r.int()):
        manual.append(r.utf())
    chest_count = r.int()
    for _ in range(chest_count):
        skip_chest(r)
    # run 段（整段丢弃重建）
    had_run = r.bool()
    fight_raw = None
    if had_run:
        r.utf(); r.utf(); skip_chest(r)
        r.long(); r.double(); r.double(); r.int(); r.utf(); r.utf(); r.int()
        if r.bool():
            fight_start = r.p
            skip_fight(r)
            fight_raw = payload[fight_start:r.p]
        skip_cooldown(r); skip_node(r)
    # economy
    eco = (r.bool(), r.int(), r.long(), r.int(), r.long(), r.int(), r.int())
    # shop
    shop = None
    if r.bool():
        rev = r.int(); nseed = r.long()
        offers = []
        for _ in range(r.int()):
            offers.append((r.utf(), r.utf(), r.int(), r.int()))
        purchases = []
        for _ in range(r.int()):
            purchases.append((r.utf(), r.int()))
        shop = (rev, nseed, offers, purchases)
    heroes = [r.utf() for _ in range(r.int())]
    pity = []
    for _ in range(r.int()):
        pity.append((r.utf(), r.int(), r.bool()))
    assert r.p == len(payload), "解析未到结尾：%d != %d（格式可能有变）" % (r.p, len(payload))

    # ---------------- 改写 ----------------
    recipe = dict(RECIPE)
    recipe["event_kind"] = event_kind
    recipe["stage"] = "EVENT"
    recipe["fight_raw"] = None
    recipe["add_items"] = []
    existing_ids = {it[0] for it in items}
    if event_kind == "BLOCKER":
        additions = (
            ("dbg-bp-1", "iron_branch", "BACKPACK", bw, bh),
            ("dbg-safe-1", "circlet", "SAFE", sw, sh),
        )
        for iid, definition, container, width, height in additions:
            if iid in existing_ids:
                continue
            x, y = free_slot(items, container, width, height, definition)
            items.append((iid, definition, container, x, y, False, 1, False, 1))
            existing_ids.add(iid)

    # ---------------- 重装 ----------------
    w = Writer()
    w.int(gold)
    w.int(recipe["wallet_gold"])
    w.int(bw); w.int(bh); w.int(sw); w.int(sh); w.int(slots)
    w.utf("PREPARATION")
    w.int(len(items))
    for it in items:
        w.utf(it[0]); w.utf(it[1]); w.utf(it[2])
        w.int(it[3]); w.int(it[4]); w.bool(it[5]); w.int(it[6]); w.bool(it[7]); w.int(it[8])
    w.long(seed); w.int(batch); w.int(clears); w.bool(extracted)
    w.utf(last_ending); w.long(shop_cycle); w.long(shop_seen)
    w.int(len(shop_stock))
    for k, v in shop_stock:
        w.utf(k); w.int(v)
    w.int(len(manual))
    for m in sorted(manual):
        w.utf(m)
    w.int(0)  # chests 置空（有 run 时的硬约束）
    build_run(w, recipe)
    w.bool(eco[0]); w.int(eco[1]); w.long(eco[2]); w.int(eco[3]); w.long(eco[4]); w.int(eco[5]); w.int(eco[6])
    if shop is None:
        w.bool(False)
    else:
        w.bool(True)
        w.int(shop[0]); w.long(shop[1])
        w.int(len(shop[2]))
        for o in shop[2]:
            w.utf(o[0]); w.utf(o[1]); w.int(o[2]); w.int(o[3])
        w.int(len(shop[3]))
        for k, v in shop[3]:
            w.utf(k); w.int(v)
    w.int(len(heroes))
    for h in sorted(heroes):
        w.utf(h)
    w.int(len(pity))
    for cat, misses, pending in pity:
        w.utf(cat); w.int(misses); w.bool(pending)

    new_payload = w.bytes()
    out = Writer()
    out.int(MAGIC); out.int(VERSION); out.int(len(new_payload))
    out.long(zlib.crc32(new_payload) & 0xFFFFFFFFFFFFFFFF)
    out.raw(new_payload)
    return out.bytes()


def craft_battle(data: bytes, round_override: int = None) -> bytes:
    """把当前存档改写为 BATTLE 节点，并原样保留源存档的真实 fight 段。

    先在应用中进入任意一场战斗，再执行 pull + craft-battle；这样生成的快照
    包含 Kotlin 真实编码的 Fighter、卡牌运行态、毒池、装备冷却、战斗 RNG/tick。
    """
    assert data[:4] == struct.pack(">i", MAGIC), "不是有效存档（MAGIC 不匹配）"
    version, length, crc = struct.unpack_from(">iiQ", data, 4)
    assert version == VERSION, "仅支持 v9，当前 v%d" % version
    payload = data[20:]
    assert len(payload) == length and zlib.crc32(payload) & 0xFFFFFFFFFFFFFFFF == crc, "存档长度或 CRC 不符"

    # 复用 craft 的解析逻辑，通过一次临时事件重装后再把 stage/fight 设置为 BATTLE。
    # 这里直接复制 craft 的输出解析入口，保证所有资产、商店和物品段保持一致。
    r = Reader(payload)
    gold = r.int(); run_gold = r.int()
    bw, bh, sw, sh = r.int(), r.int(), r.int(), r.int(); slots = r.int(); r.utf()
    items = []
    for _ in range(r.int()):
        iid = r.utf(); defn = r.utf(); cont = r.utf(); x = r.int(); y = r.int(); rot = r.bool(); star = r.int(); bound = r.bool(); cnt = r.int()
        if not iid.startswith("dbg-"):
            items.append((iid, defn, cont, x, y, rot, star, bound, cnt))
    seed = r.long(); batch = r.int(); clears = r.int(); extracted = r.bool()
    last_ending = r.utf(); shop_cycle = r.long(); shop_seen = r.long()
    shop_stock = [(r.utf(), r.int()) for _ in range(r.int())]
    manual = [r.utf() for _ in range(r.int())]
    for _ in range(r.int()): skip_chest(r)
    if not r.bool(): raise ValueError("源存档没有 run，无法生成 BATTLE 快照")
    run_id = r.utf(); hero = r.utf(); target_start = r.p
    skip_chest(r)
    rng = r.long(); hp = r.double(); max_hp = r.double(); rnd = r.int(); stage = r.utf(); pending = r.utf(); event_roll = r.int()
    if not r.bool(): raise ValueError("源存档没有 run.fight，请先在应用中进入战斗后再 pull")
    fight_start = r.p; skip_fight(r); fight_raw = payload[fight_start:r.p]
    cooldown_start = r.p; skip_cooldown(r); cooldown_raw = payload[cooldown_start:r.p]
    node_start = r.p; skip_node(r); node_raw = payload[node_start:r.p]
    eco = (r.bool(), r.int(), r.long(), r.int(), r.long(), r.int(), r.int())
    shop = None
    if r.bool():
        rev = r.int(); nseed = r.long(); offers = [(r.utf(), r.utf(), r.int(), r.int()) for _ in range(r.int())]
        purchases = [(r.utf(), r.int()) for _ in range(r.int())]; shop = (rev, nseed, offers, purchases)
    heroes = [r.utf() for _ in range(r.int())]
    pity = [(r.utf(), r.int(), r.bool()) for _ in range(r.int())]
    assert r.p == len(payload), "解析未到结尾：%d != %d" % (r.p, len(payload))

    w = Writer(); w.int(gold); w.int(RECIPE["wallet_gold"]); w.int(bw); w.int(bh); w.int(sw); w.int(sh); w.int(slots); w.utf("BATTLE")
    w.int(len(items))
    for it in items:
        w.utf(it[0]); w.utf(it[1]); w.utf(it[2]); w.int(it[3]); w.int(it[4]); w.bool(it[5]); w.int(it[6]); w.bool(it[7]); w.int(it[8])
    w.long(seed); w.int(batch); w.int(clears); w.bool(extracted); w.utf(last_ending); w.long(shop_cycle); w.long(shop_seen)
    w.int(len(shop_stock))
    for k, v in shop_stock: w.utf(k); w.int(v)
    w.int(len(manual))
    for m in sorted(manual): w.utf(m)
    w.int(0)
    w.bool(True); w.utf(run_id); w.utf(hero)
    # 目标箱从原 payload 直接拷贝，避免 Python 重复定义主题/线索字段
    target_end = target_start
    rr = Reader(payload, target_start); skip_chest(rr); target_end = rr.p
    w.raw(payload[target_start:target_end])
    w.long(rng); w.double(hp); w.double(max_hp); w.int(round_override if round_override else rnd); w.utf("BATTLE")
    w.utf(pending); w.int(event_roll); w.bool(True); w.raw(fight_raw)
    w.raw(cooldown_raw); w.bool(False)  # 战斗节点不携带事件节点
    w.bool(eco[0]); w.int(eco[1]); w.long(eco[2]); w.int(eco[3]); w.long(eco[4]); w.int(eco[5]); w.int(eco[6])
    if shop is None:
        w.bool(False)
    else:
        w.bool(True); w.int(shop[0]); w.long(shop[1]); w.int(len(shop[2]))
        for o in shop[2]: w.utf(o[0]); w.utf(o[1]); w.int(o[2]); w.int(o[3])
        w.int(len(shop[3]))
        for k, v in shop[3]: w.utf(k); w.int(v)
    w.int(len(heroes))
    for h in sorted(heroes): w.utf(h)
    w.int(len(pity))
    for cat, misses, pending_flag in pity: w.utf(cat); w.int(misses); w.bool(pending_flag)
    new_payload = w.bytes(); out = Writer(); out.int(MAGIC); out.int(VERSION); out.int(len(new_payload)); out.long(zlib.crc32(new_payload) & 0xFFFFFFFFFFFFFFFF); out.raw(new_payload)
    return out.bytes()


# ---------------- 设备交互 ----------------
def adb(args, stdin_bytes=None):
    import subprocess
    cmd = [ADB] + args
    p = subprocess.run(cmd, input=stdin_bytes, capture_output=True)
    if p.returncode != 0:
        raise SystemExit("adb 失败: %s\n%s" % (" ".join(cmd), p.stderr.decode("utf-8", "replace")))
    return p.stdout


def pull():
    data = adb(["exec-out", "run-as", PKG, "cat", SAVE_REMOTE])
    with open(SAVE_LOCAL, "wb") as f:
        f.write(data)
    print("pull -> %s (%d bytes)" % (SAVE_LOCAL, len(data)))


def scenario_path(event_kind):
    if event_kind not in EVENT_KINDS:
        raise ValueError("未知事件 %s" % event_kind)
    return "%s/%s.dat" % (SCENARIO_DIR, event_kind.lower())


def push(event_kind=None):
    path = SAVE_LOCAL if event_kind is None else scenario_path(event_kind)
    with open(path, "rb") as f:
        data = f.read()
    # 留底
    try:
        before = adb(["exec-out", "run-as", PKG, "cat", SAVE_REMOTE])
        with open(SAVE_BEFORE, "wb") as f:
            f.write(before)
        print("设备原存档已备份 -> %s" % SAVE_BEFORE)
    except SystemExit:
        print("（设备无原存档，跳过备份）")
    # 先停应用再写文件：若应用存活，其内存态（如鉴定结算存档）会在写入后、
    # force-stop 前落盘覆盖刚推的文件（N05 实测踩坑：慢速金箱被旧白箱状态回写）。
    adb(["shell", "am", "force-stop", PKG])
    import time
    time.sleep(1)
    import os
    tmp = "tools/_dbg_push.tmp"
    with open(tmp, "wb") as f:
        f.write(data)
    adb(["root"])
    time.sleep(2)
    adb(["push", tmp.replace("/", os.sep), SAVE_ABS])
    adb(["shell", "chown u0_a223:u0_a223 %s" % SAVE_ABS])
    adb(["shell", "chmod 600 %s" % SAVE_ABS])
    # 校验：拉回逐字节对比，杜绝半截写入再次发生
    back = adb(["exec-out", "run-as", PKG, "cat", SAVE_REMOTE])
    assert back == data, "设备写入校验失败：本地 %d 字节 vs 设备 %d 字节" % (len(data), len(back))
    adb(["shell", "am", "start", "-n", "%s/.MainActivity" % PKG])
    print("push 完成并重启应用（%d bytes，已校验）" % len(data))


def do_craft(event_kind="BLOCKER", output=None):
    with open(SAVE_LOCAL, "rb") as f:
        data = f.read()
    out = craft(data, event_kind)
    target = output or SAVE_LOCAL
    if output:
        import os
        os.makedirs(SCENARIO_DIR, exist_ok=True)
    with open(target, "wb") as f:
        f.write(out)
    print("craft 完成：%s @ round %d stage %s -> %s（%d bytes）" %
          (event_kind, RECIPE["round"], RECIPE["stage"], target, len(out)))


# ---------------- 宝箱节点场景（run == null，progress.chests 非空） ----------------
CHEST_QUALS = (
    ("box-white", "白", "SUPPLIES", 30, 2),
    ("box-blue", "蓝", "ARSENAL", 50, 4),
    ("box-purple", "紫", "ARCANE", 80, 7),
    ("box-gold", "金", "SAFE", 120, 10),
)


def craft_chests(data: bytes, chests=None) -> bytes:
    """把存档改写成「营地首页 + 候选宝箱」状态（无 run）。
    chests 默认四品质各一；传自定义列表 (id, quality, theme, ticket, clues) 用于旧箱等场景。"""
    assert data[:4] == struct.pack(">i", MAGIC), "不是有效存档（MAGIC 不匹配）"
    version, length, crc = struct.unpack_from(">iiQ", data, 4)
    assert version == VERSION, "仅支持 v9，当前 v%d" % version
    payload = data[20:]
    assert len(payload) == length, "长度字段不符"
    assert zlib.crc32(payload) & 0xFFFFFFFFFFFFFFFF == crc, "CRC 不符"

    r = Reader(payload)
    gold = r.int(); run_gold = r.int()
    bw, bh, sw, sh = r.int(), r.int(), r.int(), r.int()
    slots = r.int()
    phase = r.utf()
    item_count = r.int()
    items = []
    for _ in range(item_count):
        iid = r.utf(); defn = r.utf(); cont = r.utf()
        x = r.int(); y = r.int(); rot = r.bool(); star = r.int(); bound = r.bool(); cnt = r.int()
        items.append((iid, defn, cont, x, y, rot, star, bound, cnt))
    items = [it for it in items if not it[0].startswith("dbg-")]
    # ---- campaign ----
    seed = r.long(); batch = r.int(); clears = r.int(); extracted = r.bool()
    last_ending = r.utf(); shop_cycle = r.long(); shop_seen = r.long()
    shop_stock = []
    for _ in range(r.int()):
        shop_stock.append((r.utf(), r.int()))
    manual = []
    for _ in range(r.int()):
        manual.append(r.utf())
    chest_count = r.int()
    for _ in range(chest_count):
        skip_chest(r)
    # run 段整段丢弃（本场景无 run）
    had_run = r.bool()
    if had_run:
        r.utf(); r.utf(); skip_chest(r)
        r.long(); r.double(); r.double(); r.int(); r.utf(); r.utf(); r.int()
        if r.bool():
            skip_fight(r)
        skip_cooldown(r); skip_node(r)
    eco = (r.bool(), r.int(), r.long(), r.int(), r.long(), r.int(), r.int())
    shop = None
    if r.bool():
        rev = r.int(); nseed = r.long()
        offers = []
        for _ in range(r.int()):
            offers.append((r.utf(), r.utf(), r.int(), r.int()))
        purchases = []
        for _ in range(r.int()):
            purchases.append((r.utf(), r.int()))
        shop = (rev, nseed, offers, purchases)
    heroes = [r.utf() for _ in range(r.int())]
    pity = []
    for _ in range(r.int()):
        pity.append((r.utf(), r.int(), r.bool()))
    assert r.p == len(payload), "解析未到结尾：%d != %d" % (r.p, len(payload))

    # ---------------- 候选宝箱 ----------------
    if chests is None:
        chests = []
        for cid, quality, theme, ticket, nclues in CHEST_QUALS:
            clues = []
            for i in range(nclues):
                cx = i % 6
                cy = i // 6
                clues.append(("iron_branch", cx, cy, 0))
            chests.append((cid, quality, theme, ticket, clues))

    # ---------------- 重装（无 run） ----------------
    w = Writer()
    w.int(gold)
    w.int(run_gold)
    w.int(bw); w.int(bh); w.int(sw); w.int(sh); w.int(slots)
    w.utf("HOME")  # 营地/选箱界面必须 HOME，否则 appraise 报「挑战中不能鉴定」
    w.int(len(items))
    for it in items:
        w.utf(it[0]); w.utf(it[1]); w.utf(it[2])
        w.int(it[3]); w.int(it[4]); w.bool(it[5]); w.int(it[6]); w.bool(it[7]); w.int(it[8])
    w.long(seed); w.int(batch); w.int(clears); w.bool(extracted)
    w.utf(last_ending); w.long(shop_cycle); w.long(shop_seen)
    w.int(len(shop_stock))
    for k, v in shop_stock:
        w.utf(k); w.int(v)
    w.int(len(manual))
    for m in sorted(manual):
        w.utf(m)
    w.int(len(chests))
    for cid, quality, theme, ticket, clues in chests:
        write_chest(w, cid, quality, 0, ticket, clues, theme)
    w.bool(False)  # 无 run
    w.bool(eco[0]); w.int(eco[1]); w.long(eco[2]); w.int(eco[3]); w.long(eco[4]); w.int(eco[5]); w.int(eco[6])
    if shop is None:
        w.bool(False)
    else:
        w.bool(True)
        w.int(shop[0]); w.long(shop[1])
        w.int(len(shop[2]))
        for o in shop[2]:
            w.utf(o[0]); w.utf(o[1]); w.int(o[2]); w.int(o[3])
        w.int(len(shop[3]))
        for k, v in shop[3]:
            w.utf(k); w.int(v)
    w.int(len(heroes))
    for h in sorted(heroes):
        w.utf(h)
    w.int(len(pity))
    for cat, misses, pending in pity:
        w.utf(cat); w.int(misses); w.bool(pending)

    new_payload = w.bytes()
    out = Writer()
    out.int(MAGIC); out.int(VERSION); out.int(len(new_payload))
    out.long(zlib.crc32(new_payload) & 0xFFFFFFFFFFFFFFFF)
    out.raw(new_payload)
    return out.bytes()


def do_craft_chests(output=None, chests=None):
    with open(SAVE_LOCAL, "rb") as f:
        data = f.read()
    out = craft_chests(data, chests)
    target = output or SAVE_LOCAL
    if output:
        import os
        os.makedirs(SCENARIO_DIR, exist_ok=True)
    with open(target, "wb") as f:
        f.write(out)
    print("craft_chests 完成 -> %s（%d bytes，%d 个宝箱，无 run）" % (target, len(out), len(chests or CHEST_QUALS)))


# N02：旧白箱（容量 4×3）携带第 6 列/第 6 行边缘物品，验证按原坐标兼容扩展
OLD_BOX = [("old-white-1", "白", "SUPPLIES", 30,
            [("iron_branch", 0, 0, 0), ("iron_branch", 5, 0, 0), ("iron_branch", 0, 5, 0)])]

# N05：金箱 + 4 件 3×3 大件线索（四角摆放不重叠不越界——JourneyValidation.validBox 要求；
# 3×3 件放 6 个在 36 格里必然重叠，应用会按损坏档回退 .bak 并回写旧箱，N05 实测踩坑）。
SLOW_BOX = [("slow-gold-1", "金", "SAFE", 120,
             [("safe_frame", 0, 0, 0), ("safe_frame", 3, 0, 0), ("safe_frame", 0, 3, 0),
              ("safe_frame", 3, 3, 0)])]


def do_all():
    pull()
    for event_kind in EVENT_KINDS:
        do_craft(event_kind, scenario_path(event_kind))
    print("已生成八类事件快照；用 push <EVENT_KIND> 推送其中一个到设备。")


if __name__ == "__main__":
    if not (2 <= len(sys.argv) <= 3) or sys.argv[1] not in ("pull", "craft", "craft-battle", "run-battle", "battle-at", "push", "run", "all", "all-local", "restore", "chest", "chest-push", "oldbox-push", "slowbox-push"):
        print(__doc__)
        raise SystemExit(2)
    mode = sys.argv[1]
    kind = sys.argv[2] if len(sys.argv) == 3 else "BLOCKER"
    if mode == "pull":
        pull()
    elif mode == "craft":
        do_craft(kind)
    elif mode == "craft-battle":
        with open(SAVE_LOCAL, "rb") as f:
            battle = craft_battle(f.read())
        with open(SAVE_LOCAL, "wb") as f:
            f.write(battle)
        print("craft-battle 完成：保留真实 run.fight，生成 BATTLE 快照（%d bytes）" % len(battle))
    elif mode == "run-battle":
        pull()
        with open(SAVE_LOCAL, "rb") as f:
            battle = craft_battle(f.read())
        with open(SAVE_LOCAL, "wb") as f:
            f.write(battle)
        push()
        print("run-battle 完成：已将真实 fight 段重装并推送到设备")
    elif mode == "battle-at":
        # battle-at <round>：拉取当前真实战斗，改写场号后重推（用于 N06 第五战奖励验收）
        target_round = int(kind)
        pull()
        with open(SAVE_LOCAL, "rb") as f:
            battle = craft_battle(f.read(), round_override=target_round)
        with open(SAVE_LOCAL, "wb") as f:
            f.write(battle)
        push()
        print("battle-at 完成：真实 fight 段 + 场号改写为第 %d 战" % target_round)
    elif mode == "push":
        push(kind if len(sys.argv) == 3 else None)
    elif mode == "run":
        pull()
        do_craft(kind)
        push()
    elif mode == "all":
        if len(sys.argv) != 2:
            raise SystemExit("all 不接受事件参数")
        do_all()
    elif mode == "all-local":
        if len(sys.argv) != 2:
            raise SystemExit("all-local 不接受事件参数")
        for event_kind in EVENT_KINDS:
            do_craft(event_kind, scenario_path(event_kind))
        print("已用本地 _dbg_save.dat 生成八类事件快照。")
    elif mode == "chest":
        do_craft_chests()
    elif mode == "chest-push":
        do_craft_chests()
        push()
    elif mode == "oldbox-push":
        do_craft_chests(chests=OLD_BOX)
        push()
    elif mode == "slowbox-push":
        do_craft_chests(chests=SLOW_BOX)
        push()
    elif mode == "restore":
        import os
        with open(SAVE_BEFORE, "rb") as f:
            data = f.read()
        tmp = "tools/_dbg_push.tmp"
        with open(tmp, "wb") as f:
            f.write(data)
        adb(["root"])
        import time
        time.sleep(2)
        adb(["push", tmp.replace("/", os.sep), SAVE_ABS])
        adb(["shell", "chown u0_a223:u0_a223 %s" % SAVE_ABS])
        adb(["shell", "chmod 600 %s" % SAVE_ABS])
        adb(["shell", "am", "force-stop", PKG])
        print("已还原设备原存档并重启")
    else:
        pull()
        do_craft()
        push()
