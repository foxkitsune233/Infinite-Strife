# -*- coding: utf-8 -*-
"""Reproduce the warehouse-entry crash: reinstall APK, arm logcat, enter inventory, capture crash."""
import subprocess, sys, time, os

SDK = r"D:\Android\Sdk\platform-tools"
APK = r"D:\android_project\DouQiuQiu\dist\v1.0-preview\DouQiuQiu-v1.0-preview-inventory.apk"
SHOTS = r"D:\android_project\DouQiuQiu\docs\testing\results\shots"

def sh(*args, timeout=60):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout, shell=True)

def adb(*args, timeout=60):
    return sh(os.path.join(SDK, "adb.exe"), *args, timeout=timeout)

def log(msg):
    print(msg, flush=True)

# 1. reinstall the exact user APK
r = adb("install", "-r", APK)
log("install: " + (r.stdout or r.stderr).strip()[-120:])

# 2. arm logcat
adb("logcat", "-c")
adb("shell", "am", "start", "-n", "com.example.douqiuqiu/.MainActivity")
time.sleep(5)

# 3. find the inventory entry button on main screen
adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
xml = adb("shell", "exec-out", "cat", "/sdcard/ui.xml").stdout
target = None
import re
for m in re.finditer(r'<node[^>]*text="([^"]*整备[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    x1, y1, x2, y2 = int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))
    target = ((x1 + x2) // 2, (y1 + y2) // 2)
    log("entry button: %s at %s" % (m.group(1), target))
    break
if target is None:
    log("WARN: inventory entry button not found; dumping texts")
    for m in re.finditer(r'text="([^"]+)"', xml):
        log("  text: " + m.group(1))
    sys.exit(2)

# 4. enter inventory
adb("shell", "input", "tap", str(target[0]), str(target[1]))
time.sleep(4)

# 5. check crash buffer + current focus
crash = adb("logcat", "-d", "-b", "crash").stdout
main = adb("shell", "dumpsys", "activity", "activities").stdout
focus = ""
for line in main.splitlines():
    if "mResumedActivity" in line or "ResumedActivity" in line:
        focus = line.strip()
        break
log("resumed: " + focus)
if "FATAL" in crash or "Exception" in crash:
    log("=== CRASH DETECTED ===")
    print(crash[:4000])
else:
    log("no crash in buffer; crash buffer len=%d" % len(crash))

# screenshot whatever is on screen (binary-safe via shell redirect)
sh("cmd", "/c", os.path.join(SDK, "adb.exe"), "exec-out", "screencap", "-p", ">", os.path.join(SHOTS, "47-repro-warehouse.png"))
log("screenshot: 47-repro-warehouse.png")
