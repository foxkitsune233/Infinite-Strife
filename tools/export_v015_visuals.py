"""Reproducible export of existing 0.1.5 artwork; no generation or repainting.

Run with the bundled Python runtime. Originals are retained; Android exports
are bounded in size. Hero registration is shared across every pose of a hero.
"""
import hashlib
import json
import shutil
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np
from collections import deque
from inspect_attack_components import components
from inspect_v015_art import SHEETS, SOURCE

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/art'
PROOF = ROOT / 'art-source/v0.1.5/previews'
ORIGINALS = ROOT / 'art-source/heroes/v0.1.5/originals'
REPAIRED = ROOT / 'art-generation/v0.1.5-repaired'


def repaired(key, original):
    flat = sorted((REPAIRED / (key + '_flat')).glob('*.png'))
    if flat:
        return flat[0]
    candidates = sorted((REPAIRED / key).glob('*.png'))
    return candidates[0] if candidates else original


def white_to_alpha(im):
    """Remove only boundary-connected white studio background, retaining sword highlights."""
    pixels = np.array(im.convert('RGBA'))
    white = pixels[:, :, :3].min(axis=2) > 235
    h, w = white.shape
    outside = np.zeros((h, w), dtype=bool)
    queue = deque([(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)])
    while queue:
        x, y = queue.popleft()
        if not (0 <= x < w and 0 <= y < h) or outside[y, x] or not white[y, x]:
            continue
        outside[y, x] = True
        queue.extend(((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))
    pixels[outside, 3] = 0
    return Image.fromarray(pixels)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def contact(name, tiles, columns, size):
    w, h = size
    sheet = Image.new('RGB', (columns * w, ((len(tiles) + columns - 1) // columns) * (h + 25)), '#073344')
    draw = ImageDraw.Draw(sheet)
    for n, (key, im) in enumerate(tiles):
        tile = im.copy()
        tile.thumbnail((w - 8, h - 8), Image.Resampling.LANCZOS)
        x, y = n % columns * w, n // columns * (h + 25)
        sheet.paste(tile, (x + (w - tile.width) // 2, y + (h - tile.height) // 2), tile if tile.mode == 'RGBA' else None)
        draw.text((x + 4, y + h), key, fill='#ffe6a3')
    sheet.save(PROOF / name, quality=90)


def export_cards():
    base = ROOT / 'art-generation/v0.1.5-cards'
    files = sorted(base.glob('*/*.png'))
    assert len(files) == 15
    names = ['怒意沸腾', '受创激怒', '狂战回响', '不屈战魂']
    frost = ['霜痕', '凛冬甲壳', '冰封脉冲', '绝对零度']
    dual = ['沸血连斩', '霜影咏唱', '致命沸点', '冰甲反噬', '逆境热血', '冻毒蔓延', '极温崩解']
    pairs = [['basic', 'rage'], ['ultimate', 'frost'], ['crit', 'rage'], ['shield', 'frost'],
             ['restore', 'rage'], ['poison', 'frost'], ['rage', 'frost']]
    entries, tiles = [], []
    dest = ASSETS / 'cards/v0.1.5'
    dest.mkdir(parents=True, exist_ok=True)
    for file in files:
        group = file.parent.name
        i = int(file.stem.split('_')[1]) - 1
        ident = f'D{i + 7}' if group == 'dual' else f'{"RG" if group == "rage" else "F"}{i + 1}'
        title = (dual if group == 'dual' else names if group == 'rage' else frost)[i]
        tags = pairs[i] if group == 'dual' else [group]
        selected = repaired(file.stem, file)
        im = Image.open(selected).convert('RGB')
        # Native rarity frames are outside the illustration; remove baked edge ornaments.
        if selected != file:
            im = im.crop((round(im.width * .045), round(im.height * .035), round(im.width * .955), round(im.height * .965)))
        im = im.resize((512, 768), Image.Resampling.LANCZOS)
        output = dest / (ident + '.webp')
        im.save(output, quality=90, method=6)
        entries.append(dict(id=ident, artKey=file.stem, name=title, tags=tags,
                            file='v0.1.5/' + output.name, source=str(selected.relative_to(ROOT)),
                            sourceSha256=hashlib.sha256(selected.read_bytes()).hexdigest(), repaired=selected != file))
        tiles.append((ident + ' ' + file.stem, im))
    write_json(dest / 'manifest.json', dict(version=1, art=entries))
    contact('cards.jpg', tiles, 5, (192, 288))


def export_events():
    base = ROOT / 'art-generation/v0.1.5-events'
    manifest = json.loads((base / 'event-art-manifest.json').read_text(encoding='utf-8'))
    dest = ASSETS / 'events/v0.1.5'
    dest.mkdir(parents=True, exist_ok=True)
    exported, tiles = {}, []
    for key, source in manifest['assets'].items():
        file = base / source
        selected = repaired(key, file)
        im = Image.open(selected).convert('RGB')
        if selected != file:
            im = im.crop((8, 8, im.width - 8, im.height - 8))
        im = im.resize((960, 540), Image.Resampling.LANCZOS)
        output = dest / (key + '.webp')
        im.save(output, quality=88, method=6)
        exported[key] = dict(file='v0.1.5/' + output.name, source=str(selected.relative_to(ROOT)),
                             sourceSha256=hashlib.sha256(selected.read_bytes()).hexdigest(), repaired=selected != file)
        tiles.append((key, im))
    assert len(exported) == 24
    write_json(dest / 'manifest.json', dict(version=1, assets=exported))
    contact('events.jpg', tiles, 3, (320, 180))


def export_heroes():
    # Component labels are selected by source-space torso seeds, never equal-width cuts.
    # The rightmost recovery duplicates are deliberately excluded: three touch the sheet boundary.
    marks = {
        'antimage': [(180, 330, 206, 690, 45), (1000, 300, 1050, 680, 105),
                     (1520, 350, 1525, 665, 155), (650, 350, 640, 673, 170)],
        'faceless_void': [(250, 300, 265, 660, 100), (1040, 220, 1125, 662, 150),
                          (1500, 380, 1525, 658, 215), (670, 400, 685, 660, 215)],
        'axe': [(220, 340, 210, 644, 140), (1000, 350, 1010, 644, 180),
                (1520, 350, 1490, 644, 190), (600, 380, 600, 644, 240)],
        'legion_commander': [(210, 330, 190, 644, 85), (1040, 350, 1030, 644, 180),
                             (1530, 360, 1470, 644, 185), (620, 390, 580, 644, 185)],
        'axe_spin': [(300, 350, 290, 620, 150), (850, 350, 820, 620, 150),
                     (1420, 350, 1415, 617, 150), (1950, 350, 1920, 617, 150)],
    }
    entries, report, tiles, loops = {}, {}, [], []
    frames_dir = ORIGINALS.parent / 'frames'
    frames_dir.mkdir(parents=True, exist_ok=True)
    dest = ASSETS / 'heroes/atlases'
    portraits = ASSETS / 'heroes/v0.1.5'
    portraits.mkdir(parents=True, exist_ok=True)
    for hero, name in SHEETS.items():
        original = ORIGINALS / (hero + '.png')
        if not original.exists():
            shutil.copy2(SOURCE / name, original)
        im = Image.open(original).convert('RGBA')
        labels, _ = components(im)
        records, notes = [], []
        for i, (sx, sy, rx, ry, head) in enumerate(marks[hero]):
            windup = repaired('axe-windup-forehand', None) if hero == 'axe' and i == 1 else None
            if windup:
                isolated = white_to_alpha(Image.open(windup))
                b = isolated.getbbox()
                records.append((isolated.crop(b), b, 820, 1425, 316 / (1425 - 440)))
                notes.append('windup: forehand overhead; chest faces forward, grip order preserved from strike; previous reverse-hand candidate rejected')
                continue
            spin_replacement = repaired('axe-spin-left', None) if hero == 'axe_spin' and i == 3 else None
            if spin_replacement:
                isolated = white_to_alpha(Image.open(spin_replacement))
                b = isolated.getbbox()
                records.append((isolated.crop(b), b, 800, 946, 316 / (946 - 65)))
                notes.append('last spin pose: independent redraw restores clipped cape')
                continue
            replacement = repaired(hero + '-strike', None) if i == 2 and hero in ('axe', 'legion_commander') else None
            if replacement:
                isolated = white_to_alpha(Image.open(replacement))
                b = isolated.getbbox()
                nrx, nry, nhead = (735, 936, 150) if hero == 'axe' else (686, 940, 180)
                records.append((isolated.crop(b), b, nrx, nry, 316 / (nry - nhead)))
                notes.append('strike: independent Bailian redraw, white background mechanically removed')
                continue
            assert labels[sy, sx] != 0, (hero, i, 'seed on background')
            mask = labels == labels[sy, sx]
            # Two original sheets contain a merged strike/recovery component.
            # Isolate the strike side at its background boundary; report the limitation.
            if hero == 'axe' and i == 2:
                mask[:, 1855:] = False
                notes.append('strike: shared recovery component separated at x=1855; inspect blade edge')
            if hero == 'legion_commander' and i == 2:
                # Overlapping sword/recovery cannot be reconstructed by cropping.
                # Reuse complete low-ready pose as recovery, keep strike flagged for redraw.
                mask[:, 1880:] = False
                notes.append('strike: sword tip overlaps adjacent recovery; cropped candidate, redraw required')
            a = np.where(mask, np.asarray(im.getchannel('A')), 0).astype(np.uint8)
            isolated = im.copy()
            isolated.putalpha(Image.fromarray(a))
            b = isolated.getbbox()
            scale = 316 / (ry - head)
            records.append((isolated.crop(b), b, rx, ry, scale))
        fit = 1.0
        for _, b, rx, ry, scale in records:
            for extent, room in zip((rx - b[0], b[2] - rx, ry - b[1], b[3] - ry), (240, 240, 448, 32)):
                if extent > 0:
                    fit = min(fit, room / (extent * scale))
        atlas = Image.new('RGBA', (1024, 1024))
        frames, bounds = [], []
        for i, (cut, b, rx, ry, scale) in enumerate(records):
            scale *= fit
            cut = cut.resize((round(cut.width * scale), round(cut.height * scale)), Image.Resampling.LANCZOS)
            frame = Image.new('RGBA', (512, 512))
            frame.alpha_composite(cut, (round(256 - (rx - b[0]) * scale), round(464 - (ry - b[1]) * scale)))
            frame.save(frames_dir / f'{hero}-{i}.png')
            atlas.alpha_composite(frame, ((i % 2) * 512, (i // 2) * 512))
            frames.append(frame)
            bounds.append(frame.getbbox())
            tiles.append((hero + '/' + str(i), frame))
        file = hero.replace('_', '-') + '-attack-atlas-v3.png'
        atlas.save(dest / file, optimize=True)
        entry = dict(file=file, frameWidth=512, frameHeight=512, rootX=256, rootY=464,
                     bodyHeight=round(316 * fit, 3), contentWidth=max(b[2] for b in bounds) - min(b[0] for b in bounds),
                     attackOriginX=360, attackOriginY=270, runtimeReady=not any('redraw required' in n or 'inspect blade' in n for n in notes))
        if hero != 'axe_spin':
            entries[hero] = entry
            frames[0].save(portraits / f'{hero}-idle.png', optimize=True)
            # Portrait derives from full-resolution idle, preserving the approved face.
            idle, b, rx, ry, _ = records[0]
            face = im.crop((max(0, rx - 115), marks[hero][0][4] - 5, rx + 125, marks[hero][0][4] + 235))
            face.resize((256, 256), Image.Resampling.LANCZOS).save(portraits / f'{hero}-portrait.png')
        else:
            entry['order'] = ['right', 'front', 'back', 'left']
            write_json(portraits / 'axe-spin.json', entry)
        report[hero] = dict(sourceSha256=hashlib.sha256(original.read_bytes()).hexdigest(), bounds=bounds,
                            replacements=[dict(file=str(f.relative_to(ROOT)), sha256=hashlib.sha256(f.read_bytes()).hexdigest())
                                          for key in [hero + '-strike', 'axe-spin-left' if hero == 'axe_spin' else 'unused',
                                                      'axe-windup-forehand' if hero == 'axe' else 'unused']
                                          for f in (REPAIRED / key).glob('*.png')],
                            notes=notes, status='exported' if entry['runtimeReady'] else 'needs-redraw')
        if hero == 'axe':
            contact('axe-frames.jpg', [(f'axe/{n}', f) for n, f in enumerate(frames)], 4, (256, 256))
            contact('axe-forehand-frames.jpg', [(f'axe/{n}', f) for n, f in enumerate(frames)], 4, (256, 256))
            sequence = []
            for n in (0, 1, 2, 3, 0):
                background = Image.new('RGB', (512, 512), '#073344')
                background.paste(frames[n], (0, 0), frames[n])
                sequence.append(background)
            sequence[0].save(PROOF / 'axe-attack.gif', save_all=True, append_images=sequence[1:],
                             duration=[140, 180, 140, 160, 650], loop=0)
            shutil.copy2(PROOF / 'axe-attack.gif', PROOF / 'axe-forehand-attack.gif')
        loops.append((hero, frames))
    write_json(portraits / 'attack-manifest.json', dict(version=3, heroes=entries))
    write_json(ORIGINALS.parent / 'export-report.json', report)
    contact('heroes.jpg', tiles, 4, (256, 256))
    movie = []
    for n in [0, 1, 2, 3, 0]:
        scene = Image.new('RGB', (1280, 280), '#073344')
        pen = ImageDraw.Draw(scene)
        for col, (hero, frames) in enumerate(loops):
            tile = frames[n].resize((256, 256), Image.Resampling.LANCZOS)
            scene.paste(tile, (col * 256, 0), tile)
            pen.text((col * 256 + 5, 258), hero, fill='#ffe6a3')
        movie.append(scene)
    movie[0].save(PROOF / 'hero-attacks.gif', save_all=True, append_images=movie[1:], duration=[140, 140, 180, 160, 700], loop=0)


if __name__ == '__main__':
    PROOF.mkdir(parents=True, exist_ok=True)
    ORIGINALS.mkdir(parents=True, exist_ok=True)
    export_cards()
    export_events()
    export_heroes()
    print('Exported 15 cards, 24 events, 4 attack atlases, 4 portraits, axe spin candidate; inspect export-report.json')
