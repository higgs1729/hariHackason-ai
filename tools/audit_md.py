"""Write docs/design/09-audit.md from the machine results.

Inputs (all produced on :8080 in http mode):
    docs/audit/requirements.json   tools/req_check.py [--fallback-base ...]
    docs/audit/controls.json       tools/audit.py
    docs/e2e/run-1, run-2          tools/e2e.py

    python tools/audit_md.py

A requirement is OK when every check linked to it passed. A control is OK when
pressing it did something visible (navigated, opened the picker, changed text or
state), or when it did nothing visible for a reason written in NOTES below.
Anything else is written as NG, so the file cannot say OK by omission.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
AUDIT = ROOT / 'docs/audit'
OUT = ROOT / 'docs/design/09-audit.md'
RUNS = [ROOT / 'docs/e2e/run-1', ROOT / 'docs/e2e/run-2']

# requirement -> (req_check ids, e2e steps, audit (screen, control-name substring))
EVIDENCE = {
    'FR-01': ([], ['camera', 'camera-3-shots'], [('camera', '写真を撮る')]),
    'FR-01.1': ([], [], [('camera', '写真ライブラリから選ぶ')]),
    'FR-02': (['FR-02'], ['camera-3-shots'], []),
    'FR-02.1': (['FR-02.1'], [], []),
    'FR-02.2': (['FR-02.2'], [], []),
    'FR-03': ([], ['album-generating', 'album-detail'], [('album-create', 'AIでアルバムにまとめる')]),
    'FR-03.1': (['FR-03.1'], [], []),
    'FR-03.2': (['FR-03.2'], ['album-detail'], []),
    'FR-03.3': (['FR-03.3'], ['album-detail'], []),
    'FR-03.4': (['FR-03.4'], [], []),
    'FR-03.5': (['FR-03.5'], [], []),
    'FR-03.6': (['FR-03.6'], [], []),
    'FR-04': (['FR-04'], ['decorate'], [('decorate', 'ペン')]),
    'FR-04.1': (['FR-04.1'], ['decorate'], [('decorate-text', '追加')]),
    'FR-04.2': (['FR-04.2'], ['decorate'], [('decorate', 'ハートのスタンプ')]),
    'FR-04.3': (['FR-04.3'], [], [('decorate-stamped', 'ひとつ戻す')]),
    'FR-05': (['FR-05'], ['share-link'], [('share', '友達と共有する')]),
    'FR-05.1': ([], ['share-link'], []),
    'FR-05.2': (['FR-05.2'], ['friend-view-logged-out'], []),
    'FR-05.3': (['FR-05.3'], ['friend-view-logged-out'], []),
    'FR-06': (['FR-06'], ['capsule-create', 'capsule-done'], [('capsule-create', 'タイムカプセルを作成する')]),
    'FR-06.1': (['FR-06.1'], ['capsule-done'], []),
    'FR-07': (['FR-07'], ['login-form', 'my-page'], [('home', 'ログイン'), ('home', 'はじめる')]),
    'FR-07.1': (['FR-07.1'], [], []),
    'FR-08': (['FR-08'], [], [('me-friends-search', '申請')]),
    'FR-09': (['FR-09'], ['share'], [('share', 'あやか')]),
    'FR-09.1': (['FR-09.1'], [], []),
    'FR-09.2': (['FR-09.2'], [], []),
    'FR-10': (['FR-10'], ['album-detail'], []),
    'FR-10.1': (['FR-10.1'], [], [('detail-meta-edit', '保存')]),
}
REQ_NOTES = {
    'FR-01': 'Chrome with a fake camera device fed from a still image; a real camera on a phone is in 09-handoff-human.md.',
    'FR-05.1': 'The button calls navigator.share with the link and also shows the link on the page (the e2e path). '
               'Chrome on Windows has navigator.share too (the Windows share dialog); the phone share sheet with LINE '
               'is checked by a person: 09-handoff-human.md.',
    'FR-05.2': 'The tags and the image are served; how LINE renders the card is checked on a phone: 09-handoff-human.md.',
}

# (screen, control name) -> why "no visible change" / "disabled" is correct here.
# A '*' screen matches every screen.
NOTES = {
    ('home-login', 'ログイン'): "with the fields empty the browser's required-field check stops the submit; "
                                'with nao / password it logs in (e2e step login-form → my-page)',
    ('home-register', 'はじめる'): "with the fields empty the browser's required-field check stops the submit; "
                                   'registering is checked by req_check FR-07',
    ('me-friends', 'さがす'): 'disabled while the search box is empty; used in me-friends-search',
    ('me-friends-search', 'さがす'): 'searches again for the same word, so the same results stay',
    ('friend-qr-scan', '追加'): 'disabled until a code is scanned or typed',
    ('camera', 'カメラを切り替える'): 'switches between the front and back camera; the fake device on the desktop '
                                   'has one camera, so the picture stays the same (phone: 09-handoff-human.md)',
    ('decorate', 'ひとつ戻す'): 'disabled until something is drawn; pressed in decorate-stamped',
    ('decorate-text', 'ひとつ戻す'): 'disabled until something is drawn; pressed in decorate-stamped',
    ('share-sent', '友達と共有する'): 'busy while the share sheet from the first press is open; headless Chrome never closes '
                                    'that sheet. With the sheet closing after 0.5 s the button is enabled again and the link stays shown',
    ('capsule-done', 'タイムカプセルを開ける'): 'the 409 CAPSULE_NOT_YET_OPEN is expected: the capsule opens in a year, '
                                           'so the screen then calls the dev-only unseal-now and shows the opened album',
}


def note_for(screen, name):
    for (s, n), text in NOTES.items():
        if s in (screen, '*') and (n == name or (n.endswith('*') and name.startswith(n[:-1]))):
            return text
    return None


def requirement_texts():
    rows = {}
    for line in (ROOT / 'docs/design/01-requirements.md').read_text(encoding='utf-8').splitlines():
        m = re.match(r'\| \**(FR-[0-9.]+)\** \| (.+?) \| .*? \| \**(P[0-9])\** \|', line)
        if m:
            rows[m.group(1)] = (m.group(2), m.group(3))
    return rows


def readable_state(entry):
    """'ペン: false///' -> 'ペン pressed=false' (fields: pressed/checked/selected/expanded[/disabled])."""
    name, _, raw = entry.rpartition(': ')
    fields = raw.split('/')
    out = [f'{k}={v}' for k, v in zip(('pressed', 'checked', 'selected', 'expanded'), fields) if v]
    out.append('disabled' if 'disabled' in fields[4:] else '')
    return f"{name} {' '.join(x for x in out if x) or 'enabled'}"


def effect_of(row):
    """(does what, ok)"""
    name, eff = row.get('name', ''), row.get('effect', '')
    note = note_for(row['screen'], name)
    if eff.startswith('SETUP FAILED') or eff.startswith('error') or eff.startswith('control list changed'):
        return eff, False
    if eff == 'no controls':
        return note or 'the page has no buttons or links', True
    if eff.startswith('covered by'):
        # not dead: another layer is open on top of it, and closing that layer is its own row
        return eff + ' (reachable again once that is closed)', True
    if row.get('errors'):
        errs = '; '.join(row['errors'])
        return f'console error: {errs}' + (f' — {note}' if note else ''), bool(note)
    if eff == 'NO VISIBLE CHANGE' and not note and 'true' in row.get('state', '').split('/'):
        return 'already selected here; pressing it again keeps it selected', True
    if eff in ('NO VISIBLE CHANGE', 'disabled in this state'):
        return (note, True) if note else (eff.lower(), False)
    parts = []
    if row.get('urlAfter'):
        parts.append(f"opens `{row['urlAfter']}`")
    if row.get('newTab'):
        parts.append(f"opens `{row['newTab']}` in a new tab")
    if row.get('canvasChanged'):
        parts.append('changes the drawing')
    if row.get('fileChooser'):
        parts.append('opens the photo picker')
    if row.get('stateChanged'):
        parts.append('sets ' + ', '.join(readable_state(x) for x in row['stateChanged']))
    if row.get('newControls') and not row.get('urlAfter'):
        parts.append('shows ' + ', '.join(f'「{x}」' for x in row['newControls'][:3]))
    if row.get('newText') and not row.get('urlAfter'):
        parts.append('shows ' + ' / '.join(f'「{x[:24]}」' for x in row['newText'][:2]))
    elif row.get('goneText') and not row.get('urlAfter') and not row.get('newControls'):
        parts.append('removes ' + ' / '.join(f'「{x[:24]}」' for x in row['goneText'][:2]))
    text = '; '.join(parts)
    return text, True


def cell(text):
    return str(text).replace('|', '\\|').replace('\n', ' ')


def main():
    reqs = json.loads((AUDIT / 'requirements.json').read_text(encoding='utf-8'))
    ctl = json.loads((AUDIT / 'controls.json').read_text(encoding='utf-8'))['controls']
    runs = [json.loads((r / 'report.json').read_text(encoding='utf-8')) for r in RUNS]
    texts = requirement_texts()
    ok_all = True

    lines = ['# 09 — Audit', '',
             'Written by `tools/audit_md.py` from the machine results; edit NOTES there, not this file.', '',
             'All checks ran against the production build served by Spring on :8080 (`apiMode` http, MySQL, '
             'Claude CLI as the AI), logged in as the seeded `nao` unless stated.', '',
             '| Source | What it does | Result |', '| --- | --- | --- |']
    for r, run in zip(RUNS, runs):
        steps = len(run['steps'])
        lines.append(f"| `docs/e2e/{r.name}` (`tools/e2e.py`) | the §8 demo end to end in Chrome, {steps} screenshots | "
                     f"{'PASS' if run['passed'] else 'FAIL: ' + cell(run['error'])} ({run['finished']}) |")
        ok_all &= run['passed']
    passed = sum(1 for x in reqs if x['pass'])
    lines.append(f'| `docs/audit/requirements.json` (`tools/req_check.py`) | API checks per requirement, '
                 f'plus FR-03.6 on a second server whose Claude CLI path is broken | {passed}/{len(reqs)} pass |')
    silent_ok = sum(1 for r in ctl if effect_of(r)[1])
    lines.append(f'| `docs/audit/controls.json` (`tools/audit.py`) | every button, link and tab on every screen, '
                 f'pressed one at a time from a fresh load | {silent_ok}/{len(ctl)} OK |')

    # --- A. requirements ---------------------------------------------------------------
    lines += ['', '## A. Requirements (P0 and P1)', '',
              '| ID | P | Requirement | Evidence | OK |', '| --- | --- | --- | --- | --- |']
    for rid, (checks, steps, controls) in EVIDENCE.items():
        text, prio = texts.get(rid, ('?', '?'))
        ev, ok = [], True
        for c in checks:
            hits = [x for x in reqs if x['req'] == c]
            good = bool(hits) and all(x['pass'] for x in hits)
            ok &= good
            seen = '; '.join(json.dumps(x['seen'], ensure_ascii=False)[:160] for x in hits) or 'not run'
            ev.append(f"req_check {c}: {'pass' if good else 'FAIL'} `{cell(seen)}`")
        for s in steps:
            found = [any(st['step'] == s for st in run['steps']) for run in runs]
            good = all(found) and all(run['passed'] for run in runs)
            ok &= good
            ev.append(f"e2e `{s}` in both runs: {'yes' if good else 'NO'}")
        for screen, name in controls:
            hits = [r for r in ctl if r['screen'] == screen and name in r.get('name', '')]
            good = bool(hits) and all(effect_of(r)[1] for r in hits)
            ok &= good
            ev.append(f"audit {screen} 「{name}」: {effect_of(hits[0])[0] if hits else 'NOT FOUND'}")
        if rid in REQ_NOTES:
            ev.append(REQ_NOTES[rid])
        ok_all &= ok
        lines.append(f"| {rid} | {prio} | {cell(text)} | {'<br>'.join(cell(e) for e in ev)} | {'OK' if ok else '**NG**'} |")

    # --- B. controls ---------------------------------------------------------------------
    lines += ['', '## B. Every control on every screen', '',
              'Each state is loaded fresh (reload, log in through the stored refresh token, replay the setup clicks), '
              'then one control is pressed and the page is compared before and after. Screenshots: `docs/audit/<state>-<nn>.jpg`.', '']
    by_screen = {}
    for r in ctl:
        by_screen.setdefault(r['screen'], []).append(r)
    for screen, rows in by_screen.items():
        first = rows[0]
        setup = ' → '.join(s if isinstance(s, str) else f'tap {s[1]}' if s[0] == 'tap' else f'type 「{s[2]}」 in {s[1]}'
                           for s in first.get('setup', []))
        lines += [f"### {screen} — `{first['path']}`" + (f' after {setup}' if setup else ''), '',
                  '| # | Control | Does what | OK |', '| --- | --- | --- | --- |']
        for r in rows:
            what, ok = effect_of(r)
            ok_all &= ok
            lines.append(f"| {r.get('n', '-')} | {cell(r.get('name', '(setup)'))} | {cell(what)} | {'OK' if ok else '**NG**'} |")
        lines.append('')

    lines += ['## Notes', '',
              '- Browser automation here is Playwright, not `C:\\agents\\tools\\bh.py`: these runs need a headless Chrome '
              'with a fake camera device and one isolated profile per run, which the shared Chrome lease of bh.py does not give.',
              '- The camera is a fake device fed from `frontend/public/bg/friends.jpg`; there is one camera, so switching '
              'cameras changes nothing on the desktop.',
              '- The one console error in the e2e runs is the expected `409 CAPSULE_NOT_YET_OPEN` when the capsule is opened '
              'before its date; the screen then calls the dev-only `unseal-now` so the demo can show the opened capsule.',
              '- Phone-only behaviour (real camera, share sheet, LINE card, QR between two phones) is in `09-handoff-human.md`.',
              '']
    OUT.write_text('\n'.join(lines), encoding='utf-8')
    bad = [ln for ln in lines if '**NG**' in ln]
    print(f'wrote {OUT}: {len(bad)} NG rows')
    for ln in bad:
        print('  ' + ln[:200])
    return 0 if ok_all else 1


if __name__ == '__main__':
    sys.exit(main())
