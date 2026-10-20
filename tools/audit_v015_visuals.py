"""Read-only runtime art integrity audit and local visual review page builder."""
import hashlib
import html
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'app/src/main/assets/art'
PREVIEW = ROOT / 'art-source/v0.1.5'


def load(file):
    return json.loads(file.read_text(encoding='utf-8'))


def audit():
    cards = load(ART / 'cards/v0.1.5/manifest.json')['art']
    events = load(ART / 'events/v0.1.5/manifest.json')['assets']
    heroes = load(ART / 'heroes/v0.1.5/attack-manifest.json')['heroes']
    assert len(cards) == 15 and len({c['id'] for c in cards}) == 15
    assert len(events) == 24 and len(heroes) == 4
    files, sections = [], []
    for card in cards:
        file = ART / 'cards' / card['file']
        assert Image.open(file).size == (512, 768), file
        assert card['repaired'], file
        files.append(file)
    for key, entry in events.items():
        file = ART / 'events' / entry['file']
        assert Image.open(file).size == (960, 540), file
        assert entry['repaired'], key
        files.append(file)
    for hero, entry in heroes.items():
        assert entry['runtimeReady'], hero
        file = ART / 'heroes/atlases' / entry['file']
        im = Image.open(file)
        assert im.mode == 'RGBA' and im.size == (1024, 1024), file
        assert im.getchannel('A').getextrema() == (0, 255), file
        frames = []
        for n in range(4):
            frame = im.crop(((n % 2) * 512, (n // 2) * 512, (n % 2 + 1) * 512, (n // 2 + 1) * 512))
            box = frame.getbbox()
            assert box and min(box[0], box[1], 512 - box[2], 512 - box[3]) >= 10, (hero, n, box)
            frames.append(hashlib.sha256(frame.tobytes()).hexdigest())
        assert len(set(frames)) == 4, hero
        files.append(file)
    spin = load(ART / 'heroes/v0.1.5/axe-spin.json')
    assert Image.open(ART / 'heroes/atlases' / spin['file']).size == (1024, 1024)
    files.append(ART / 'heroes/atlases' / spin['file'])
    report = dict(cards=len(cards), eventOutcomes=len(events), heroes=len(heroes), attackFrames=16,
                  helixFrames=4, indexedBytes=sum(f.stat().st_size for f in files),
                  result='PASS', limitation='Pixel integrity only; game-device timing and aesthetic approval are external checks.')
    (PREVIEW / 'integrity-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

    def image(file, title):
        # Page lives two directories below repo root. All paths remain local to the repo.
        url = '../../' + file.relative_to(ROOT).as_posix()
        return f'<figure><a href="{html.escape(url)}" target="_blank"><img loading="lazy" src="{html.escape(url)}" alt="{html.escape(title)}"></a><figcaption>{html.escape(title)}</figcaption></figure>'

    sections.append('<section id="heroes"><h2>英雄与动作</h2>' + image(PREVIEW / 'previews/hero-attacks.gif', '四英雄普攻与斧王螺旋 · 循环预览') + '<div class="portraits">' + ''.join(image(ART / f'heroes/v0.1.5/{hero}-portrait.png', hero) for hero in heroes) + '</div>' + image(PREVIEW / 'previews/heroes.jpg', '透明动作逐帧联系表') + '</section>')
    sections.append('<section id="cards"><h2>怒火、寒霜与双流派卡面</h2><div class="cards">' + ''.join(image(ART / 'cards' / c['file'], c['id'] + ' · ' + c['name']) for c in cards) + '</div></section>')
    sections.append('<section id="events"><h2>事件结局环境图</h2><div class="events">' + ''.join(image(ART / 'events' / e['file'], key) for key, e in events.items()) + '</div></section>')
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>0.1.5 美术审阅</title><style>
body{margin:0;background:#062d3b;color:#f5e2b2;font:16px system-ui,sans-serif}header{padding:32px 5vw;border-bottom:3px solid #e8af36;background:#093e50}h1{margin:0 0 12px;color:#ffce49}nav{display:flex;gap:24px;margin-top:20px;flex-wrap:wrap}a{color:#ffce49}main{max-width:1400px;margin:auto;padding:24px}section{scroll-margin:20px;margin-bottom:60px}h2{color:#ffce49;border-left:5px solid #ffce49;padding-left:12px}figure{margin:0;background:#0c2534;border:1px solid #31768c;border-radius:8px;overflow:hidden}img{display:block;width:100%;object-fit:contain}figcaption{padding:12px;overflow-wrap:anywhere} .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px}.events{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:16px}.portraits{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin:20px 0}.portraits img{max-height:200px}p{line-height:1.7;max-width:850px}
</style><header><h1>0.1.5 美术资源审阅</h1><p>四名英雄、15张卡面、24个事件结局。点击图片打开原尺寸。动作图按固定脚底注册；演示速度仅用于审阅，实际攻击节奏由战斗时钟控制。</p><nav><a href="#heroes">英雄与动作</a><a href="#cards">卡面</a><a href="#events">事件结局</a></nav></header><main>''' + ''.join(sections) + '</main></html>'
    (PREVIEW / 'gallery.html').write_text(page, encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    audit()
