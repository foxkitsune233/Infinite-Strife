"""Read-only inventory of connected sprite silhouettes in the approved review sheets."""
from pathlib import Path
from PIL import Image
import numpy as np
from inspect_attack_components import components

SOURCE = Path(r'C:/Users/HP/.codex/generated_images/01a14305-0370-7be2-b9bb-2c24714189e4')
SHEETS = {
    'antimage': 'exec-b893b679-a707-4077-983f-65707bff2ad3.png',
    'axe': 'exec-1b9ea096-f669-4918-967e-156244ef3e6a.png',
    'faceless_void': 'exec-e987ecc7-c056-461e-a7bf-4be6be1a3ef7.png',
    'legion_commander': 'exec-2c59b842-9eda-42f8-9f57-ac40172b1638.png',
    'axe_spin': 'exec-eaa63046-fce2-4e6d-b0da-e54558242218.png',
}

if __name__ == '__main__':
    for key, name in SHEETS.items():
        im = Image.open(SOURCE / name).convert('RGBA')
        labels, parts = components(im)
        print(key, im.size, im.getchannel('A').getextrema(), flush=True)
        for part in parts[:7]:
            if part['count'] < 10000:
                continue
            print(part, flush=True)
