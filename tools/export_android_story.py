#!/usr/bin/env python3
"""Export approved Android story prose verbatim, using only the Python stdlib.

Run normally to regenerate data/android/story.json; --check compares bytes without
writing. Source fingerprints hash original bytes (including source line endings).
The current hero uses the token 'current' and the dynamic name '当前英雄'.
The source's draft import restriction is superseded by explicit user approval.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


PROJECT = Path(__file__).resolve().parents[1]
ANDROID = Path('D:/android_project/DouQiuQiu')
STORY_PATH = 'docs/design/2026-11-04-endless-strife-story-script.md'
ENDING_PATH = 'shared/src/commonMain/kotlin/com/example/douqiuqiu/quest/HeroEndings.kt'
HEROES = dict(zip(
    ('主宰', '亚巴顿', '敌法师', '斧王', '虚空假面', '军团指挥官',
     '天怒法师', '冥界亚龙', '幻影刺客', '瘟疫法师', '当前英雄'),
    ('juggernaut', 'abaddon', 'anti_mage', 'axe', 'faceless_void',
     'legion_commander', 'skywrath', 'viper', 'phantom_assassin', 'necrophos', 'current'),
))
CHAPTER_IDS = (['P00'] + [f'H{i:02}' for i in range(1, 7)]
               + [f'J{i:02}' for i in range(1, 25)]
               + ['T00', 'T05', 'T10', 'T11', 'T15', 'T20']
               + [f'R{i:02}' for i in range(1, 5)])
STRING = r'"(?:[^"\\\r\n]|\\(?:[tbnr\'"\\$]|u[0-9a-fA-F]{4}))*"'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def kotlin_string(token):
    """Decode Kotlin regular strings without corrupting Unicode or newlines."""
    escapes = {'t': '\t', 'b': '\b', 'n': '\n', 'r': '\r', "'": "'",
               '"': '"', '\\': '\\', '$': '$'}
    require(re.fullmatch(STRING, token) is not None, 'Unsupported Kotlin string')
    body = token[1:-1]
    require(re.search(r'(?<!\\)\$(?:\w|\{)', body) is None,
            'Kotlin interpolation requires explicit support')
    return re.sub(r'\\(u[0-9a-fA-F]{4}|.)',
                  lambda m: chr(int(m[1][1:], 16)) if m[1].startswith('u')
                  else escapes[m[1]], body)


def string_list(body):
    tokens = re.findall(STRING, body)
    require(not re.sub(STRING, '', body).replace(',', '').strip(),
            'Unexpected expression in Kotlin page list')
    return [kotlin_string(token) for token in tokens]


def parse_endings(source):
    epilogue_match = re.search(r'val epilogue = listOf\((.*?)\n    \)', source, re.S)
    require(epilogue_match is not None, 'Missing Kotlin epilogue')
    epilogue = string_list(epilogue_match[1])
    require(len(epilogue) == 3, 'Expected three epilogue pages')
    require(re.search(r'\.map\s*\{\s*it\.copy\(pages = it\.pages \+ epilogue\)\s*\}', source),
            'Kotlin epilogue composition changed')
    pattern = (r'HeroEnding\(\s*(' + STRING + r')\s*,\s*(' + STRING
               + r')\s*,\s*(' + STRING + r')\s*,\s*(' + STRING
               + r')\s*,\s*listOf\((.*?)\n        \)\)')
    endings = []
    for match in re.finditer(pattern, source, re.S):
        ending_id, hero_id, hero_name, title = map(kotlin_string, match.groups()[:4])
        pages = string_list(match[5])
        require(len(pages) == 12, f'{ending_id}: expected 12 personal pages')
        require(HEROES.get(hero_name) == hero_id and hero_id != 'current',
                f'{ending_id}: unknown hero')
        require(all(pages + epilogue), f'{ending_id}: empty page')
        endings.append(dict(id=ending_id, hero_id=hero_id, hero_name=hero_name,
                            title=title, pages=pages + epilogue))
    require([e['id'] for e in endings] == [f'E{i:02}' for i in range(1, 11)],
            'Expected exactly E01 through E10 in source order')
    require(len({e['hero_id'] for e in endings}) == 10, 'Duplicate ending hero')
    return endings


def parse_chapters(source):
    headings = list(re.finditer(r'^### ([PHJTRE]\d{2})｜(.+)$', source, re.M))
    chapters = []
    for index, heading in enumerate(headings):
        chapter_id, title = heading.groups()
        if chapter_id.startswith('E'):
            continue
        require(chapter_id in CHAPTER_IDS, f'Unexpected chapter {chapter_id}')
        body = source[heading.end():headings[index + 1].start() if index + 1 < len(headings) else len(source)]
        # Stop at the next major section (especially after the final R paragraph).
        body = re.split(r'^## ', body, maxsplit=1, flags=re.M)[0]
        portraits = []
        pair = re.search(r'左([^，。\n]+)，右([^。\n]+)。', body)
        single = re.search(r'单人立绘：([^。\n]+)。', body)
        names = list(pair.groups()) if pair else [single[1]] if single else []
        for side, name in zip(('left', 'right'), names):
            require(name in HEROES, f'{chapter_id}: unknown portrait {name}')
            portraits.append(dict(hero_id=HEROES[name], display_name=name, side=side))
        lines = []
        for paragraph in re.split(r'\n\s*\n', body.strip()):
            if '：' not in paragraph:
                continue
            name, text = paragraph.split('：', 1)
            if name in ('演出', '触发', '触发建议', '接入说明'):
                continue
            require(name == '场景文字' or name in HEROES,
                    f'{chapter_id}: unknown speaker {name!r}')
            speaker_id = '' if name == '场景文字' else HEROES[name]
            require(not speaker_id or speaker_id in [p['hero_id'] for p in portraits],
                    f'{chapter_id}: speaker {name} has no portrait')
            require(bool(text), f'{chapter_id}: empty line')
            lines.append(dict(speaker_id=speaker_id,
                              speaker_name=name if speaker_id else '', text=text))
        require(bool(lines), f'{chapter_id}: no dialogue')
        require(any(line['speaker_id'] for line in lines) or not portraits,
                f'{chapter_id}: narration must have no portraits')
        chapters.append(dict(id=chapter_id, title=title, portraits=portraits, lines=lines))
    require([c['id'] for c in chapters] == CHAPTER_IDS,
            'Expected exactly 41 chapters in source order, including all 24 tasks')
    require([line['speaker_id'] for line in chapters[0]['lines'][:3]] == ['', '', '']
            and len(chapters[0]['lines']) == 7, 'P00 must contain three narration and four dialogue pages')
    return chapters


def export(android_root):
    sources = []
    texts = []
    for relative in (STORY_PATH, ENDING_PATH):
        raw = (android_root / relative).read_bytes()
        sources.append(dict(path=relative, sha256=hashlib.sha256(raw).hexdigest()))
        texts.append(raw.decode('utf-8-sig').replace('\r\n', '\n'))
    data = dict(metadata=dict(schema_version=1, sources=sources),
                chapters=parse_chapters(texts[0]), endings=parse_endings(texts[1]))
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android-root', type=Path, default=ANDROID)
    parser.add_argument('--output', type=Path, default=PROJECT / 'data/android/story.json')
    parser.add_argument('--check', action='store_true', help='Validate and compare without writing')
    args = parser.parse_args()
    try:
        content = export(args.android_root)
        if args.check:
            require(args.output.is_file() and args.output.read_bytes() == content,
                    f'{args.output}: missing or stale; regenerate the story export')
        else:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_bytes(content)
        print(('Checked' if args.check else 'Exported')
              + ': 41 chapters (24 tasks), 10 endings (12+3 pages each); speakers valid.')
        return 0
    except (OSError, UnicodeError, ValueError) as error:
        print(f'Story export failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
