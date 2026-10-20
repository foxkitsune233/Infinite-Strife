"""Read-only alpha component inventory for technical sprite separation."""
from pathlib import Path
from collections import deque
import numpy as np
from PIL import Image


def components(image):
    alpha = np.asarray(image.getchannel("A"))
    mask = alpha > 8
    height, width = mask.shape
    labels = np.zeros((height, width), dtype=np.int32)
    entries = []
    number = 0
    for flat in np.flatnonzero(mask):
        y, x = divmod(int(flat), width)
        if labels[y, x]:
            continue
        number += 1
        labels[y, x] = number
        queue = deque([(x, y)])
        count = 0
        left = right = x
        top = bottom = y
        while queue:
            px, py = queue.popleft()
            count += 1
            left, right = min(left, px), max(right, px)
            top, bottom = min(top, py), max(bottom, py)
            for nx, ny in ((px - 1, py), (px + 1, py), (px, py - 1), (px, py + 1)):
                if 0 <= nx < width and 0 <= ny < height and mask[ny, nx] and not labels[ny, nx]:
                    labels[ny, nx] = number
                    queue.append((nx, ny))
        entries.append({"label": number, "count": count, "bounds": [left, top, right + 1, bottom + 1]})
    return labels, sorted(entries, key=lambda e: -e["count"])


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    for path in sorted((root / "art-source/heroes/attack-v3").glob("*-source-v3.png")):
        image = Image.open(path).convert("RGBA")
        _, entries = components(image)
        print(path.name, image.size, entries[:7], flush=True)
