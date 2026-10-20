"""Bailian image edits for failed visual-QA candidates. Leaves originals untouched.

Usage: python tools/repair_v015_art.py probe|events|cards|heroes
Every invocation is recorded; retries are explicit, never infinite or automatic.
"""
from pathlib import Path
import concurrent.futures
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'art-generation/v0.1.5-repaired'


def jobs(kind):
    if kind == 'axe-forehand':
        yield 'axe-windup-forehand', ROOT / 'art-generation/v0.1.5-repaired/axe-strike/axe-strike.png', (
            '以参考图作为攻击命中帧，画出它前一个正手举斧蓄力关键帧。保持同一角色、同一斧头、同一双手握柄的先后顺序、相同的朝右站位和腿部姿势。'
            '这是双手正手劈砍：胸甲正面仍朝向观众和右方，脸朝右，不能扭成背身反手。'
            '双臂从身体前侧抬起，双肘在脸的前上方，双手位于额头正上方略靠画面右侧，手腕不反折，两只前臂不交叉。'
            '斧柄从头顶前方的双手，向左上方伸到头顶后上方的斧头；斧头在画面左上，双手在斧头的右下，斧头完整高过头顶，'
            '形成从左上方经过头顶向画面右侧正手下劈的弧线。这是正手，不是从身体后侧向右反抽。'
            '保留参考图两只手的握柄方向，不交换左右手，不改变斧头的刃面和金色星形。不要复刻胸前横握姿势。'
            '下半身完全保持参考图的脚部朝向和低重心站位，人物、斧头和披风完整不截断。'
            '单人全身，纯白无阴影背景，四周10%空白，2D卡通画风与参考一致，无文字无特效无框。'), '1536*1536'
    if kind == 'axe-windup':
        yield 'axe-windup-overhead', ROOT / 'art-source/heroes/v0.1.5/frames/axe-1.png', (
            '修改参考图斧王的攻击蓄力姿势。必须保持同一人物身份、同一脸和发型、红铜铠甲、红披风、同一把斧头、朝画面右侧的站位。'
            '把双手和斧头举到头顶上方：双臂抬高弯曲，双手紧握斧柄，双手均高于额头；整块斧刃的最低点也高于头顶，'
            '斧柄斜横在头顶后上方，表现下一瞬间向右下方大力劈砍的蓄力姿势。绝对不要在胸前或腰前横握斧头。'
            '下半身维持原图双腿分开的低重心战斗姿势，不改变腿和脚的位置。'
            '斧头尺寸与原图一致，握柄长度合理，只有两只手且不遮住脸。'
            '画布只有一个完整人物，完整的斧头与披风不裁切，四周留至少10%空白，头顶留够举斧空间。'
            '纯白背景，无地面阴影，无特效，无文字无框，保持参考图的2D卡通画风。'), '1536*1536'
    common = ('保持参考图的主体、构图与清晰轮廓。统一为高饱和硬边2D卡通游戏插画，大块平涂，'
              '粗深蓝描边，青绿深蓝阴影、金色高光，像纸雕插画。不要写实油画、不要细密花纹。'
              '画面必须完全无文字，无数字，无字母，无符文字符，无徽标水印，无界面按钮，无卡框，无装饰边框。')
    if kind == 'spin':
        yield 'axe-spin-left', ROOT / 'art-source/heroes/v0.1.5/originals/axe_spin.png', (
            '只保留参考图从左往右第四个人物姿势，画布只有一个斧王。保持同一张脸、同一红铜盔甲和红披风。'
            '人物为背面三分之四视角，头转向画面左侧，双手横握同一把斧头，斧刃朝向左侧，表示原地水平旋转到最后四分之一周。'
            '补全右侧被画布截断的披风。完整脚底、完整武器、完整披风，四周留10%纯白空白，无地面阴影。'
            '保持原图2D卡通画风。无任何文字和边框。'), '1536*1024'
    if kind == 'card-focus':
        for key, subject in [
            ('dual_05_hot_recovery_flat', '画面中只有一只戴红金护腕的卡通手，托住一团金黄色治疗光与红色余烬形成的心形能量；暗青绿背景，能量是主体'),
            ('rage_02_wound_flat', '画面中只有一块带清楚缺口的红铜色护胸，裂口中爆出红橙色火焰和三片金色碎屑；暗青绿背景，护胸是主体'),
        ]:
            yield key, ROOT / 'app/src/main/assets/art/cards/A1-v1.png', (
                '参考图只用作画风参考，不要保留原来的刀剑。完全重新画新的主题：' + subject + '。'
                '极简硬边卡通，大块平涂，每块只有两级明暗，粗深蓝描边，高饱和，剪纸矢量感。'
                '绝对不要人物脸部、身体、毛发、皮肤细节、油画笔触、写实纹理、文字、符文、数字、装饰框、边框。'
                '竖版全幅插画，主体位于中央，轮廓小尺寸可读。'), '1024*1536'
    if kind == 'event-focus':
        for key, subject in [
            ('hunter_camp.barter_flat', '月夜的无人猎人营地，木桌上摆着打开的战利品匣、兽牙、药草与金币，帐篷和营火位于后方'),
            ('hunter_camp.leave_flat', '月夜的无人猎人营地，熄灭的篝火与收拢的帐篷，蜿蜒小路从画面右侧延伸进入青绿森林'),
        ]:
            yield key, ROOT / 'art-generation/v0.1.5-repaired/hunter_camp.share_rations/hunter_camp.share_rations.png', (
                '保留参考图的硬边2D卡通画风、青绿月光和金黄灯光。重绘成以下内容：' + subject + '。'
                '这是全幅环境插画，不是游戏截图。图中绝对没有人、人物剪影、商人、文字、英文单词、数字、标题、标志、徽章、角标、装饰边框、用户界面、提示牌。'
                '只画环境与物件，画面一直延伸到四边，不留装饰框。'), '1536*864'
    if kind in ('events', 'probe'):
        base = ROOT / 'art-generation/v0.1.5-events'
        manifest = json.loads((base / 'event-art-manifest.json').read_text(encoding='utf-8'))['assets']
        for key, file in manifest.items():
            if kind == 'probe' and key != 'war_banner.claim':
                continue
            prompt = common + '删除所有人物、文字、界面、卡框和装饰边角，并用周围自然场景补全。只有环境叙事。横版16:9全幅画面，画到四边。'
            yield key, base / file, prompt, '1536*864'
    if kind in ('cards', 'probe'):
        files = sorted((ROOT / 'art-generation/v0.1.5-cards').glob('*/*.png'))
        for file in files:
            if kind == 'probe' and file.stem != 'dual_05_hot_recovery':
                continue
            yield file.stem, file, common + '保留原主题，压平渲染细节，删除装饰框和伪文字。竖版卡牌插画主体清晰占画面70%，图案延伸至四边。', '1024*1536'
    if kind in ('heroes', 'probe'):
        for hero in ['axe', 'legion_commander']:
            if kind == 'probe' and hero != 'legion_commander':
                continue
            file = ROOT / f'art-source/heroes/v0.1.5/originals/{hero}.png'
            prompt = ('只绘制参考图从左往右第四个攻击命中姿势，保留同一人物、同一铠甲、同一武器和朝右方向。'
                      '画布中只有一个完整人物，删除其余四个人物。必须重新补全被最右侧人物遮住的武器末端，'
                      '武器完整且由双手握持，不允许断刃或多出来的手。角色、头发、披风、脚和武器全部位于画布内部，'
                      '四周至少留10%空白。纯白色背景，无地面无阴影，无文字无框。2D卡通，保持参考图人物身份。')
            yield hero + '-strike', file, prompt, '1536*1024'


def run(job):
    key, file, prompt, size = job
    directory = OUT / key
    directory.mkdir(parents=True, exist_ok=True)
    if list(directory.glob('*.png')):
        return key + ': cached'
    (directory / 'prompt.txt').write_text(prompt, encoding='utf-8')
    command = ['bl.cmd', 'image', 'edit', '--image', str(file), '--prompt', prompt,
               '--size', size, '--watermark', 'false', '--prompt-extend', 'false',
               '--out-dir', str(directory), '--out-prefix', key, '--output', 'json']
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=360)
    (directory / 'response.json').write_text(result.stdout, encoding='utf-8')
    (directory / 'stderr.log').write_text(result.stderr, encoding='utf-8')
    return key + ': ' + str(result.returncode)


if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        for result in pool.map(run, jobs(sys.argv[1])):
            print(result, flush=True)
