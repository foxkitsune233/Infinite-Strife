"""Mechanical equal-cell export of generated illustrations; preserves original alpha."""
import json
import shutil
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art-source/illustrated-ui-v1'
ASSETS = ROOT / 'app/src/main/assets/art'

def clean_cell(tile):
    """Remove stray neighboring-cell fragments without repainting generated objects."""
    alpha = np.array(tile.getchannel('A'))
    visible = alpha > 8
    seen = np.zeros(visible.shape, dtype=bool)
    components = []
    height, width = visible.shape
    for yy, xx in np.argwhere(visible):
        if seen[yy, xx]:
            continue
        stack = [(int(yy), int(xx))]
        seen[yy, xx] = True
        pixels = []
        while stack:
            y, x = stack.pop()
            pixels.append((y, x))
            for ny, nx in [(y-1,x),(y+1,x),(y,x-1),(y,x+1)]:
                if 0 <= ny < height and 0 <= nx < width and visible[ny,nx] and not seen[ny,nx]:
                    seen[ny,nx] = True
                    stack.append((ny,nx))
        components.append(pixels)
    components.sort(key=len, reverse=True)
    keep = np.zeros_like(alpha)
    for i, pixels in enumerate(components):
        edge = any(y in (0,height-1) or x in (0,width-1) for y,x in pixels)
        if i == 0 or (len(pixels) >= len(components[0]) * .15 and not edge):
            for y, x in pixels:
                keep[max(0,y-1):y+2, max(0,x-1):x+2] = 255
    tile.putalpha(Image.fromarray(np.minimum(alpha, keep)))
    return tile

def export():
    manifest = json.loads((SOURCE / 'manifest.json').read_text(encoding='utf-8'))
    originals = SOURCE / 'originals'
    originals.mkdir(parents=True, exist_ok=True)
    for key, path in manifest['sources'].items():
        local = originals / f'{key}.png'
        if not local.exists():
            shutil.copy2(path, local)
    out = ASSETS / 'items/illustrated-v1'
    out.mkdir(parents=True, exist_ok=True)
    tiles = []
    for group in manifest['groups']:
        sheet = Image.open(originals / f"{group['key']}.png").convert('RGBA')
        assert sheet.getchannel('A').getextrema()[0] == 0, 'Transparency missing'
        for i, (key, _) in enumerate(group['items']):
            x, y = i % 3, i // 3
            tile = sheet.crop((round(x * sheet.width / 3), round(y * sheet.height / 3),
                               round((x + 1) * sheet.width / 3), round((y + 1) * sheet.height / 3)))
            tile = clean_cell(tile)
            box = tile.getchannel('A').getbbox()
            assert box, key
            tile = tile.crop(box)
            tile.thumbnail((228, 228), Image.Resampling.LANCZOS)
            canvas = Image.new('RGBA', (256, 256))
            canvas.alpha_composite(tile, ((256 - tile.width) // 2, (256 - tile.height) // 2))
            canvas.save(out / f'{key}.png', optimize=True)
            tiles.append((key, canvas))
    scenes = ASSETS / 'scenes'
    scenes.mkdir(parents=True, exist_ok=True)
    back = Image.open(originals / 'background.png').convert('RGB')
    back.thumbnail((1024, 1536), Image.Resampling.LANCZOS)
    back.save(scenes / 'ruins-menu-v1.jpg', quality=90)
    # The same empty courtyard is suitable as battle/camp backdrop; no figures are baked in.
    for key in ['arena', 'camp', 'rest']:
        back.save(scenes / f'{key}-v1.jpg', quality=90)
    atlas = Image.open(originals / 'events.png').convert('RGB')
    names = ['depot', 'workshop', 'library', 'storage', 'vault', 'terminal', 'clinic', 'blocker']
    for i, key in enumerate(names):
        x, y = i % 2, i // 2
        tile = atlas.crop((round(x * atlas.width / 2), round(y * atlas.height / 4),
                           round((x + 1) * atlas.width / 2), round((y + 1) * atlas.height / 4)))
        # Remove the narrow dark seam between generated panels.
        tile = tile.crop((3, 3, tile.width - 3, tile.height - 3))
        tile.save(scenes / f'{key}-v1.jpg', quality=91)
    proof = Image.new('RGB', (9 * 144, 7 * 164), '#102532')
    draw = ImageDraw.Draw(proof)
    for i, (key, tile) in enumerate(tiles):
        x, y = (i % 9) * 144, (i // 9) * 164
        tile = tile.resize((128, 128), Image.Resampling.LANCZOS)
        proof.paste(tile, (x + 8, y + 4), tile)
        draw.text((x + 4, y + 138), key[:23], fill='#E9BC66')
    proof.save(SOURCE / 'item-contact-sheet.jpg', quality=92)
    print(f'Exported {len(tiles)} RGBA item illustrations and 12 scene/background files')

if __name__ == '__main__':
    export()
