"""CIE76 comparison of poster crops against React screenshots.

python tools/compare.py 05-share [--record 1] [--report]
"""
import argparse
import json
import os
from pathlib import Path
import sys
import numpy as np
from measure import ROOT, ORDER, measure

COLOR_LIMIT = 10
EDGE_LIMIT = 2.5
PROGRESS = ROOT / 'docs/reference/progress.json'
REPORT = ROOT / 'docs/reference/measurements.md'


def lab(rgb):
    v = np.array(rgb, dtype=float) / 255
    v = np.where(v <= .04045, v / 12.92, ((v + .055) / 1.055)**2.4)
    xyz = np.array([[.4124564, .3575761, .1804375], [.2126729, .7151522, .0721750],
                    [.0193339, .1191920, .9503041]]) @ v
    t = xyz / [.95047, 1, 1.08883]
    t = np.where(t > (6/29)**3, np.cbrt(t), t / (3 * (6/29)**2) + 4/29)
    return np.array([116*t[1]-16, 500*(t[0]-t[1]), 200*(t[1]-t[2])])


def hexcolor(v):
    return '#' + ''.join(f'{int(k):02x}' for k in v) if v is not None else 'missing'


def compare(stem):
    poster, mock = measure(stem), measure(stem, mock=True)
    rows, boxes, residuals = [], [], []
    for name, a in poster['colors'].items():
        b = mock['colors'][name]
        delta = round(float(np.linalg.norm(lab(a['rgb'])-lab(b['rgb']))), 2) if a['rgb'] and b['rgb'] else None
        passed = a['excluded'] or (delta is not None and delta <= COLOR_LIMIT)
        result = 'n/a' if a['excluded'] else 'PASS' if passed else 'FAIL'
        rows.append((name, a['region'], hexcolor(a['rgb']), hexcolor(b['rgb']), delta, result))
        if not passed: residuals.append(f'color {name}: ΔE {delta} exceeds {COLOR_LIMIT}')
    for name, a in poster['boxes'].items():
        b = mock['boxes'][name]
        p, m = a['box'], b['box']
        delta = round(max(abs(u-v) for u,v in zip(p,m)),2) if a['detected'] and b['detected'] else None
        strong = all(v is None or v >= 8 for v in a['edge_strengths']+b['edge_strengths'])
        passed = a['excluded'] or (delta is not None and strong and delta <= EDGE_LIMIT)
        result = 'n/a' if a['excluded'] else 'PASS' if passed else 'FAIL'
        boxes.append((name,p,m,delta,result))
        if not passed:residuals.append(f'box {name}: delta={delta}, edge_detected={strong}')
    return {'stem':stem,'screen':poster['screen'],'colors':rows,'boxes':boxes,
            'passed':not residuals,'residuals':residuals}


def print_result(r):
    print(f"{r['stem']} screen={r['screen']} | ΔE <= {COLOR_LIMIT}; edge <= {EDGE_LIMIT}%")
    print('region | poster | mock | ΔE | pass')
    for name,_,p,m,d,s in r['colors']:print(f'{name} | {p} | {m} | {d} | {s}')
    print('box | poster % | mock % | max edge delta % | pass')
    for name,p,m,d,s in r['boxes']:print(f'{name} | {p} | {m} | {d} | {s}')
    print('RESULT:', 'PASS' if r['passed'] else 'FAIL')


def record(r, rounds):
    data=json.loads(PROGRESS.read_text(encoding='utf-8')) if PROGRESS.exists() else {'screens':[]}
    data['screens']=[s for s in data['screens'] if s['stem']!=r['stem']]
    data['screens'].append({'stem':r['stem'],'rounds':rounds,'passed':r['passed'],'residuals':r['residuals']})
    PROGRESS.parent.mkdir(parents=True,exist_ok=True)
    tmp=PROGRESS.with_suffix('.json.tmp')
    tmp.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    os.replace(tmp,PROGRESS)


def report():
    progress=json.loads(PROGRESS.read_text(encoding='utf-8')) if PROGRESS.exists() else {'screens':[]}
    rounds={p['stem']:p['rounds'] for p in progress['screens']}
    lines=['# Measured poster and React comparison','',
      'Regenerate: `cd frontend && npm run build`; from the repo root run `python tools/shoot.py --sheet`, then `python tools/compare.py 05-share --report` (any stem regenerates the full report).',
      '',f'Thresholds: ΔE (CIE76) ≤ {COLOR_LIMIT}; each bounding-box edge ≤ {EDGE_LIMIT}% of the screen. Generated imagery is n/a.','']
    for stem in ORDER:
        try:r=compare(stem)
        except OSError as e:lines.extend([f'## {stem}',f'Missing screenshot: {e}','']);continue
        lines.extend([f'## {stem}','',f"Inner rectangle (x,y,w,h): `{r['screen']}`; result: **{'PASS' if r['passed'] else 'FAIL'}**; rounds: {rounds.get(stem,'unrecorded')}.",'',
          '| Region (% x,y,w,h) | Poster | Mock | ΔE | Pass |','|---|---|---|---:|---|'])
        for name,region,p,m,d,s in r['colors']:lines.append(f'| {name} {region} | {p} | {m} | {d} | {s} |')
        lines+=['','| Box | Poster % [left,top,right,bottom] | Mock % | Max edge delta % | Pass |','|---|---|---|---:|---|']
        for name,p,m,d,s in r['boxes']:lines.append(f'| {name} | {p} | {m} | {d} | {s} |')
        lines+=['',f"Residuals: {'; '.join(r['residuals']) if r['residuals'] else 'none'}",'']
    tmp=REPORT.with_suffix('.md.tmp')
    tmp.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    os.replace(tmp,REPORT)


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('stem',choices=ORDER)
    ap.add_argument('--record',type=int)
    ap.add_argument('--report',action='store_true')
    args=ap.parse_args()
    r=compare(args.stem)
    print_result(r)
    if args.record is not None:record(r,args.record)
    if args.report:report()
    return 0 if r['passed'] else 1

if __name__=='__main__':sys.exit(main())
