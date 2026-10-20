from pathlib import Path
import json
import shutil
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = root / 'art-source/illustrated-ui-v2'
spec = json.loads((source / 'saturated-background.json').read_text(encoding='utf-8'))
original = source / 'originals/saturated-background.png'
if not original.exists():
    shutil.copy2(spec['source'], original)
image = Image.open(original).convert('RGB')
image.thumbnail((1024, 1536), Image.Resampling.LANCZOS)
image.save(root / 'app/src/main/assets/art/scenes/ruins-menu-v2.jpg', quality=92)
y = int(image.height * .46)
banner = image.crop((0, y, image.width, min(image.height, y + int(image.width * .625))))
banner.save(root / 'app/src/main/assets/art/scenes/camp-v2.jpg', quality=92)
print('Exported saturated menu / camp backgrounds')
