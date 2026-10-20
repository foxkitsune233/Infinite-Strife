"""Summarize existing CSV evidence. Does not run generation, combat, or tests."""
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'docs/testing/results/sampling')
results = []
for path in sorted(root.glob('*.csv')):
    with path.open(encoding='utf-8-sig', newline='') as f:
        rows = list(csv.DictReader(f))
    report = dict(file=path.name, rows=len(rows))
    if path.name.startswith('boxes-'):
        groups = defaultdict(list)
        keys = set()
        duplicates = 0
        for row in rows:
            key = tuple(row[k] for k in ('clears', 'quality', 'difficulty', 'theme', 'seed'))
            duplicates += key in keys
            keys.add(key)
            groups[(row['quality'], row['difficulty'], row['clears'])].append(int(row['sellableValue']))
        seeds = {int(r['seed']) for r in rows}
        report.update(unique_cases=len(keys), duplicate_cases=duplicates,
                      matrix_cells=len({k[:-1] for k in keys}), observed_seeds=sorted(seeds),
                      low_tier_main_items=sum(int(r['mainItems']) for r in rows if r['quality'] in ('白', '蓝')),
                      full_matrix_if_samples_100=(len(keys) == 36000 and len({k[:-1] for k in keys}) == 360 and seeds == set(range(1,101)) and not duplicates))
        report['clears_0_sellable_medians_by_quality_difficulty'] = [dict(quality=k[0], difficulty=k[1], median=statistics.median(values)) for k,values in sorted(groups.items()) if k[2] == '0']
    else:
        cases = {tuple(r[k] for k in ('seed', 'hero', 'school', 'profile', 'strategy', 'difficulty')) for r in rows}
        endings = [r for r in rows if r.get('ending')]
        report.update(observed_cases=len(cases), completed_cases=len(endings), last_row=rows[-1] if rows else None,
                      completeness='No original requested sample count/status manifest: do not treat this historical CSV as a completed matrix.')
    results.append(report)
print(json.dumps(results, ensure_ascii=True, indent=2))
