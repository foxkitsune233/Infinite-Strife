"""任务34 角色动作外部验收：录屏 → 抽帧 → 逐帧差异统计。

不参与生产构建。仅用于 F01–F05 的动作可见性取证。

用法：
  python tools/_f_anim.py --tag f02off --seconds 6
  python tools/_f_anim.py --tag f01scale0 --seconds 6 --fps 30 --region 40,410,690,760

region 用录屏像素坐标（默认录 720x1600，是 1080x2400 的 2/3）。

输出：
  shots/<tag>/frame_%03d.png     抽出的帧
  shots/<tag>/sheet.png          差异最大的若干帧拼图（人工核对姿态）
  shots/<tag>/report.txt         每帧差异与结论
"""
import argparse
import os
import subprocess
import sys

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"
FFMPEG = r"D:\meru_agent\ffmpeg\ffmpeg-9.0.2-essentials_build\bin\ffmpeg.exe"
ROOT = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(os.path.dirname(ROOT), "shots")

from PIL import Image, ImageChops, ImageStat


def adb(args, timeout=180):
    p = subprocess.run([ADB] + args, capture_output=True, timeout=timeout)
    return p.stdout, p.returncode


def record(seconds):
    remote = "/sdcard/animp.mp4"
    adb(["shell", "rm", "-f", remote])
    proc = subprocess.run(
        [ADB, "shell", "screenrecord", "--time-limit", str(seconds),
         "--bit-rate", "8000000", "--size", "720x1600", remote],
        capture_output=True, timeout=seconds + 90)
    return remote, proc.returncode


def fresh_dir(base):
    """沙箱禁止删除文件，重跑时改用新的子目录，避免清理旧帧。"""
    path, n = base, 1
    while os.path.isdir(path) and os.listdir(path):
        n += 1
        path = "%s_run%d" % (base, n)
    os.makedirs(path, exist_ok=True)
    return path


def split_pngs(blob):
    """把 image2pipe 输出的连续 PNG 拆成单帧字节串。"""
    out, i, n = [], 0, len(blob)
    sig = b"\x89PNG\r\n\x1a\n"
    while True:
        start = blob.find(sig, i)
        if start < 0:
            break
        pos = start + len(sig)
        while pos + 8 <= n:
            length = int.from_bytes(blob[pos:pos + 4], "big")
            ctype = blob[pos + 4:pos + 8]
            pos += 8 + length + 4
            if ctype == b"IEND":
                break
        out.append(blob[start:pos])
        i = pos
    return out


def extract(clip, outdir, fps):
    # 沙箱禁止外部进程写盘，改用管道回传，再由 Python 落盘
    p = subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", clip,
                        "-vf", "fps=%d" % fps, "-f", "image2pipe",
                        "-vcodec", "png", "-"], capture_output=True)
    frames = split_pngs(p.stdout)
    if not frames:
        err = p.stderr.decode("utf-8", "replace")[-400:]
        print("ffmpeg produced no frames: " + err, flush=True)
    for idx, data in enumerate(frames, 1):
        open(os.path.join(outdir, "frame_%03d.png" % idx), "wb").write(data)
    return len(frames)


def analyze(outdir, region, tag, fps, top=8):
    frames = sorted(f for f in os.listdir(outdir) if f.startswith("frame_"))
    if len(frames) < 2:
        print("NO_FRAMES tag=%s" % tag, flush=True)
        return 2
    l, t, r, b = (int(x) for x in region.split(","))
    prev, diffs = None, []
    for i, name in enumerate(frames):
        im = Image.open(os.path.join(outdir, name)).convert("RGB")
        box = im.crop((l, t, min(r, im.width), min(b, im.height)))
        if prev is not None:
            d = ImageStat.Stat(ImageChops.difference(prev, box)).mean
            diffs.append((sum(d) / 3.0, i))
        prev = box

    vals = [d for d, _ in diffs]
    changed = [i for d, i in diffs if d >= 1.0]
    lines = ["tag=%s fps=%d frames=%d region=%s" % (tag, fps, len(frames), region),
             "diff mean=%.2f max=%.2f min=%.2f" % (sum(vals) / len(vals), max(vals), min(vals)),
             "changed_frames(>=1.0)=%d/%d" % (len(changed), len(diffs)),
             "sample_seq=" + ",".join("%.1f" % v for v in vals[:80])]

    picks = sorted(diffs, reverse=True)[:top]
    cells = []
    for d, idx in picks:
        im = Image.open(os.path.join(outdir, frames[idx])).convert("RGB")
        im = im.crop((l, t, min(r, im.width), min(b, im.height)))
        im.thumbnail((340, 380))
        cells.append(im)
    if cells:
        cw = max(im.width for im in cells)
        ch = max(im.height for im in cells)
        cols = min(4, len(cells))
        rows = (len(cells) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * cw, rows * ch), (20, 20, 20))
        for n, im in enumerate(cells):
            sheet.paste(im, ((n % cols) * cw, (n // cols) * ch))
        sheet.save(os.path.join(outdir, "sheet.png"))

    report = os.path.join(outdir, "report.txt")
    open(report, "w", encoding="utf-8").write("\n".join(lines))
    print("\n".join(lines), flush=True)
    print("dir ->", outdir, flush=True)
    return 0


def capture(tag, seconds=6, fps=30, region="40,410,690,760", top=8):
    outdir = fresh_dir(os.path.join(SHOTS, tag))
    clip = os.path.join(SHOTS, tag + ".mp4")
    remote, rc = record(seconds)
    print("screenrecord rc=%d" % rc, flush=True)
    adb(["pull", remote, clip])
    extract(clip, outdir, fps)
    print("frames:", len([f for f in os.listdir(outdir) if f.startswith("frame_")]), flush=True)
    return analyze(outdir, region, tag, fps, top)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--seconds", type=int, default=6)
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--region", default="40,410,690,760")
    ap.add_argument("--top", type=int, default=8)
    a = ap.parse_args()
    print("recording %ds ..." % a.seconds, flush=True)
    return capture(a.tag, a.seconds, a.fps, a.region, a.top)


if __name__ == "__main__":
    sys.exit(main())
