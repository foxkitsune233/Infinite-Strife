import subprocess, sys, os, re

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"
ROOT = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(os.path.dirname(ROOT), "shots")

def run(args):
    p = subprocess.run([ADB] + args, capture_output=True)
    if p.returncode != 0:
        msg = "adb %s failed rc=%d\n%s" % (" ".join(args), p.returncode,
              p.stderr.decode("utf-8", "replace"))
        open(os.path.join(ROOT, "_devshot_err.txt"), "w", encoding="utf-8").write(msg)
        raise SystemExit(msg)
    return p.stdout

def shot(name):
    out = os.path.join(SHOTS, name)
    data = run(["exec-out", "screencap", "-p"])
    open(out, "wb").write(data)
    print("shot ->", out, len(data), "bytes")

def dump(name):
    # uiautomator 失败时会保留上一次的 /sdcard/ui.xml，必须先删再导，否则读到陈旧界面
    subprocess.run([ADB, "shell", "rm", "-f", "/sdcard/ui.xml"], capture_output=True)
    p = subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    if b"dumped to" not in p.stdout:
        msg = "uiautomator dump failed: " + p.stdout.decode("utf-8", "replace") + p.stderr.decode("utf-8", "replace")
        open(os.path.join(ROOT, "_devshot_err.txt"), "w", encoding="utf-8").write(msg)
        raise SystemExit(msg)
    raw = run(["exec-out", "cat", "/sdcard/ui.xml"])
    if raw[:1] == b"\xff":
        raw = raw[1:]
    if raw[:2] == b"\xff\xfe":
        xml = raw.decode("utf-16-le", "replace")
    elif raw[:2] == b"\xfe\xff":
        xml = raw.decode("utf-16-be", "replace")
    elif raw[:3] == b"\xef\xbb\xbf":
        xml = raw.decode("utf-8-sig", "replace")
    else:
        xml = raw.decode("utf-8", "replace")
    out = os.path.join(ROOT, name)
    open(out, "w", encoding="utf-8").write(xml)
    lines = []
    for m in re.finditer(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="(\[[0-9,]+\]\[[0-9,]+\])"', xml):
        t, b = m.group(1), m.group(2)
        if t.strip():
            lines.append(t + " " + b)
    open(out + ".txt", "w", encoding="utf-8").write("\n".join(lines))
    print("dump ->", out, len(lines), "text nodes")

if __name__ == "__main__":
    try:
        cmd = sys.argv[1]
        if cmd == "shot":
            shot(sys.argv[2])
        elif cmd == "dump":
            dump(sys.argv[2])
        elif cmd == "both":
            shot(sys.argv[2] if len(sys.argv) > 2 else "shot.png")
            dump("ui.xml")
    except SystemExit:
        raise
    except Exception as e:
        import traceback
        open(os.path.join(ROOT, "_devshot_err.txt"), "w", encoding="utf-8").write(traceback.format_exc())
        raise
