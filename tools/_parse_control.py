"""Read existing JSONL only. Rolling summaries prove presence, not exact event counts.
Snapshot applications are a lower bound; same-frame application/dispel may be invisible.
"""
import json
import re
import sys
from collections import Counter

FIELDS = {'source', 'caster', 'kind', 'remainingMs', 'dispel', 'appliedAtTick', 'suppressed'}
PATTERN = re.compile(r'^控制\[([^\]]+)\]\s+(?:(\d->\d)\s+)?([A-Z_]+)(?:\s+(\d+)ms)?$')


def analyze(path):
    observed = {}
    mentions = Counter()
    missing = mismatch = entries = frames = ticks = 0
    heroes, frame_keys = set(), set()
    for line in open(path, encoding='utf-8-sig'):
        if not line.strip():
            continue
        r = json.loads(line)
        frames += 1
        identity = (r.get('session'), r.get('scenarioAttempt'), r.get('runId'), r.get('round'))
        frame_key = identity + (r.get('sequence'),)
        if r.get('sequence') is not None and frame_key in frame_keys:
            continue
        frame_keys.add(frame_key)
        ticks += r.get('action') == 'tick'
        for point in ('before', 'after'):
            state = r.get(point) or {}
            for message in state.get('summary') or []:
                match = PATTERN.match(message)
                if match:
                    mentions[match.group(3)] += 1
            for target in ('player', 'enemy'):
                fighter = state.get(target) or {}
                if fighter.get('hero'):
                    heroes.add((target, fighter['hero']))
                for entry in fighter.get('timedControls') or []:
                    entries += 1
                    missing += len(FIELDS - entry.keys())
                    if FIELDS - entry.keys():
                        continue
                    mismatch += bool(entry['suppressed']) != (fighter.get('immunityMs', 0) > 0)
                    key = identity + (target, entry['source'], entry['caster'], entry['kind'], entry['appliedAtTick'])
                    observed[key] = max(observed.get(key, 0), entry['remainingMs'])
    applications = Counter((key[4], key[5], key[7]) for key in observed)
    return dict(frames=frames, tick_frames=ticks, heroes=sorted(heroes),
                unique_snapshot_applications_lower_bound=len(observed),
                snapshot_applications=[dict(target=k[0], source=k[1], kind=k[2], observed=n) for k, n in sorted(applications.items())],
                snapshot_entry_observations=entries, missing_fields=missing, suppressed_mismatches=mismatch,
                summary_presence=sorted(mentions), summary_occurrences_not_event_counts=dict(mentions),
                limits='No exact event counts, probability/CD boundary proof or full scenario coverage from rolling summaries.')


if __name__ == '__main__':
    print(json.dumps(analyze(sys.argv[1]), ensure_ascii=True, indent=2))
