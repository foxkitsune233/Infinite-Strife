"""Validate exported art and assemble a labelled review sheet; does not retouch artwork."""
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'app/src/main/assets/art/cards/v0.1.5/manifest.json').read_text(encoding='utf-8'))
rows = {r['id']: r for r in manifest['art']}
ids = ['RG1','RG2','RG3','RG4','F1','F2','F3','F4','D7','D8','D9','D10','D11','D12','D13']
names = {}
for folder in ['cards-v015-gpt-v3-rage','cards-v015-gpt-v4-expansion']:
    log = json.loads((root / 'art-source' / folder / 'generation-log.json').read_text(encoding='utf-8-sig'))
    names.update({r['id']:r['name'] for r in log['generated']})
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 20)
sheet = Image.new('RGB',(1100,1128),'#10252c')
draw = ImageDraw.Draw(sheet)
for i, ident in enumerate(ids):
    row = rows[ident]
    source = root / row['source']
    assert row['provider'] == 'GPT built-in image_gen'
    assert hashlib.sha256(source.read_bytes()).hexdigest() == row['sourceSha256']
    game = root / 'app/src/main/assets/art/cards' / row['file']
    with Image.open(game) as im:
        im.load()
        assert im.size == (512,768), (ident,im.size)
        thumb = im.convert('RGB').resize((208,312),Image.Resampling.LANCZOS)
    x,y = (i % 5)*220+6,(i // 5)*376+6
    sheet.paste(thumb,(x,y))
    draw.text((x,y+318),ident,fill='#ffcf68',font=font)
    draw.text((x,y+344),names[ident],fill='#e5edf0',font=font)
out = root / 'art-source/cards-v015-gpt-v4-expansion/contact-sheet.jpg'
sheet.save(out,quality=94)
print('Verified 15 source hashes and 15 game images (512x768).')
print(out)
