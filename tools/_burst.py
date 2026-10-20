import subprocess, sys, time, os

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"
SHOTS = r"D:\android_project\DouQiuQiu\shots"
prefix = sys.argv[1]
count = int(sys.argv[2]) if len(sys.argv) > 2 else 3
gap = float(sys.argv[3]) if len(sys.argv) > 3 else 1.2
delay = float(sys.argv[4]) if len(sys.argv) > 4 else 1.0
time.sleep(delay)
for i in range(count):
    p = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True)
    open(os.path.join(SHOTS, "%s_%d.png" % (prefix, i + 1)), "wb").write(p.stdout)
    print("shot %d: %d bytes" % (i + 1, len(p.stdout)))
    if i < count - 1:
        time.sleep(gap)
