"""Export independent component illustrations and aligned chest states (no repainting)."""
import json
import shutil
from pathlib import Path
from PIL import Image, ImageDraw
from export_illustrated_ui import clean_cell

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art-source/illustrated-ui-v2'
manifest = json.loads((SOURCE / 'manifest.json').read_text(encoding='utf-8'))
originals = SOURCE / 'originals'
originals.mkdir(parents=True, exist_ok=True)
for key, path in manifest['sources'].items():
    if not (originals / f'{key}.png').exists():
        shutil.copy2(path, originals / f'{key}.png')
dest = ROOT / 'app/src/main/assets/art/items/illustrated-v1'
tiles = []
for group in manifest['groups']:
    sheet = Image.open(originals / f"{group['key']}.png").convert('RGBA')
    for i, (key, _) in enumerate(group['items']):
        x, y = i % 3, i // 3
        tile = clean_cell(sheet.crop((round(x * sheet.width/3), round(y * sheet.height/3), round((x+1)*sheet.width/3), round((y+1)*sheet.height/3))))
        box = tile.getchannel('A').getbbox()
        assert box, key
        tile = tile.crop(box)
        tile.thumbnail((228,228), Image.Resampling.LANCZOS)
        canvas = Image.new('RGBA',(256,256))
        canvas.alpha_composite(tile,((256-tile.width)//2,(256-tile.height)//2))
        canvas.save(dest / f'{key}.png', optimize=True)
        tiles.append((key,canvas))
sheet = Image.open(originals / 'chest.png').convert('RGBA')
chests = ROOT / 'app/src/main/assets/art/chests'
chests.mkdir(parents=True, exist_ok=True)
for i, name in enumerate(['closed','open']):
    tile = sheet.crop((round(i*sheet.width/2),0,round((i+1)*sheet.width/2),sheet.height))
    tile.resize((384,384), Image.Resampling.LANCZOS).save(chests / f'{name}-v1.png', optimize=True)
proof = Image.new('RGB',(9*144,3*164),'#102532')
draw = ImageDraw.Draw(proof)
for i,(key,tile) in enumerate(tiles):
    x,y=i%9*144,i//9*164
    tile=tile.resize((128,128),Image.Resampling.LANCZOS)
    proof.paste(tile,(x+8,y+4),tile)
    draw.text((x+4,y+138),key,fill='#E9BC66')
proof.save(SOURCE / 'components-contact-sheet.jpg',quality=92)
print(f'Exported {len(tiles)} components and 2 chest states')
