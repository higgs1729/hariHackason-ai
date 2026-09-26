"""Press every button and link on every screen, one at a time, and record what happened.

For each screen state below, the script loads the screen fresh (logged in as
nao through an injected refresh token, or logged out for the public page),
lists every visible control (button, link, tab, radio, menu item), then for
each control: reloads, replays the state's setup clicks, clicks the control,
and records the URL change, the text that appeared, a file chooser, native
dialogs and console errors, with a screenshot.

    python tools/audit.py --out docs/audit

Writes <out>/controls.json and <out>/<screen>-<nn>.jpg. The human-readable
table is docs/design/09-audit.md, written from this output.
"""
import argparse
from collections import Counter
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
PHONE = {'width': 390, 'height': 844}
CONTROLS_JS = """() => {
  const sel = 'button, a[href], [role=button], [role=tab], [role=radio], [role=menuitem], input[type=checkbox]'
  const seen = []
  for (const el of document.querySelectorAll(sel)) {
    const r = el.getBoundingClientRect()
    const style = getComputedStyle(el)
    if (r.width < 2 || r.height < 2 || style.visibility === 'hidden' || style.display === 'none') continue
    const name = (el.getAttribute('aria-label') || el.innerText || el.getAttribute('title') || '').trim().replace(/\\s+/g, ' ')
    const state = ['aria-pressed', 'aria-checked', 'aria-selected', 'aria-expanded'].map(a => el.getAttribute(a)).join('/')
      + (el.disabled ? '/disabled' : '')  // e.g. undo turning on after something is drawn
    seen.push({tag: el.tagName.toLowerCase(), role: el.getAttribute('role') || '', name, state,
               href: el.getAttribute('href') || '', disabled: el.disabled === true || el.getAttribute('aria-disabled') === 'true'})
  }
  return seen
}"""
CANVAS_JS = r"""() => [...document.querySelectorAll('canvas')].map(c => {
  // a cheap fingerprint of the bitmap: drawings and stickers live here, not in the DOM
  try { const d = c.toDataURL(); let h = 0; for (let i = 0; i < d.length; i += 7) h = (h * 31 + d.charCodeAt(i)) | 0; return h } catch (e) { return null }
})"""
CLICK_JS = """([i]) => {
  const sel = 'button, a[href], [role=button], [role=tab], [role=radio], [role=menuitem], input[type=checkbox]'
  const els = [...document.querySelectorAll(sel)].filter(el => {
    const r = el.getBoundingClientRect(); const s = getComputedStyle(el)
    return !(r.width < 2 || r.height < 2 || s.visibility === 'hidden' || s.display === 'none')
  })
  const el = els[i]
  el.scrollIntoView({block: 'center'})
  const r = el.getBoundingClientRect()
  const x = r.x + r.width / 2, y = r.y + r.height / 2
  // an open sheet or menu can sit on top: then a finger would press that, not this
  const top = document.elementFromPoint(x, y)
  let cover = null
  if (top && !el.contains(top) && !top.contains(el)) {
    const layer = top.closest('[role=dialog], [role=menu]')
    const labelled = layer && document.getElementById(layer.getAttribute('aria-labelledby') || '')
    cover = layer
      ? (layer.getAttribute('aria-label') || labelled?.innerText || 'an open dialog') + ' (open sheet)'
      : (top.getAttribute('aria-label') || top.innerText || top.tagName)
    cover = cover.trim().split(/\\s*\\n/)[0].slice(0, 40)
  }
  return [x, y, cover]
}"""


def api(base, path, body=None, token=None, method=None):
    req = urllib.request.Request(base + path, method=method or ('POST' if body is not None else 'GET'),
                                 data=None if body is None else json.dumps(body).encode(),
                                 headers={'Content-Type': 'application/json',
                                          **({'Authorization': f'Bearer {token}'} if token else {})})
    with urllib.request.urlopen(req, timeout=60) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


def upload_unassigned(base, token, n=2):
    """A couple of fresh photos so the album-create screen has something to pick."""
    import io, random, uuid
    from PIL import Image
    bd = uuid.uuid4().hex
    parts = []
    crlf = b'\r\n'
    for k, cell in enumerate(['s1r3c1', 's2r1c3'][:n]):
        img = Image.open(ROOT / f'frontend/public/photos/{cell}.jpg').convert('RGB')
        img.putpixel((random.randrange(img.width), random.randrange(img.height)), (random.randrange(256),) * 3)
        buf = io.BytesIO()
        img.save(buf, 'JPEG', quality=92)
        head = f'--{bd}', f'Content-Disposition: form-data; name="files"; filename="audit{k}.jpg"', 'Content-Type: image/jpeg', ''
        parts.append(crlf.join(h.encode() for h in head) + crlf + buf.getvalue() + crlf)
    req = urllib.request.Request(base + '/api/photos', data=b''.join(parts) + f'--{bd}--'.encode() + crlf, method='POST',
                                 headers={'Authorization': f'Bearer {token}', 'Content-Type': f'multipart/form-data; boundary={bd}'})
    urllib.request.urlopen(req, timeout=60).read()


def login(base, account='nao'):
    return api(base, '/api/auth/login', {'userAccount': account, 'userPassword': 'password'})


def fixtures(base):
    """Ids for the parameterised routes, from the seeded data."""
    api(base, '/api/dev/seed', {})
    pair = login(base)
    tok = pair['accessToken']
    upload_unassigned(base, tok)
    albums = api(base, '/api/albums', token=tok)['items']
    album = api(base, f"/api/albums/{albums[0]['id']}", token=tok)
    friends = api(base, '/api/friends', token=tok)
    friends = friends['items'] if isinstance(friends, dict) else friends
    ayaka = next(f for f in friends if f['userAccount'] == 'ayaka')
    capsule = next((c for c in api(base, '/api/capsules', token=tok) if c['status'] == 'SEALED'), None)
    for candidate in albums:
        if capsule:
            break
        try:
            capsule = api(base, '/api/capsules', {'albumId': candidate['id'], 'openTime': '2027-09-27T12:00:00+09:00',
                                                  'capsuleMsg': 'audit'}, tok)
        except urllib.error.HTTPError:
            continue  # already in a capsule
    share = api(base, f"/api/albums/{album['id']}/share", {}, tok)
    return {'album': album['id'], 'photo': album['photos'][0]['id'], 'ayaka': ayaka['id'],
            'capsule': capsule['id'], 'share': share['shareToken']}


def states(f):
    """(screen key, path, setup clicks by accessible name, logged in)."""
    a, ph = f['album'], f['photo']
    s = [
        ('home', '/', [], False),
        ('home-login', '/', ['ログイン'], False),
        ('home-register', '/', ['はじめる'], False),
        ('me-albums', '/me', [], True),
        ('me-capsules', '/me?tab=capsules', [], True),
        ('me-friends', '/me?tab=friends', [], True),
        ('me-name-edit', '/me', ['名前を変える'], True),
        ('friend-qr-show', '/friends/qr?mode=show', [], True),
        ('friend-qr-scan', '/friends/qr?mode=scan', [], True),
        ('reunion', f"/reunion/{f['ayaka']}", [], True),
        ('camera', '/camera', [], True),
        ('camera-hint', '/camera', ['AIに撮り方を聞く'], True),
        ('camera-hint-result', '/camera', ['AIに撮り方を聞く', '聞いてみる'], True),
        ('album-create', '/album/new', [], True),
        ('detail', f'/album/{a}', [], True),
        ('detail-more', f'/album/{a}', ['その他のオプション'], True),
        ('detail-title-edit', f'/album/{a}', ['タイトルを編集'], True),
        ('detail-meta-edit', f'/album/{a}', ['編集'], True),
        ('decorate', f'/album/{a}/decorate/{ph}', [], True),
        ('decorate-text', f'/album/{a}/decorate/{ph}', ['文字'], True),
        ('decorate-stamped', f'/album/{a}/decorate/{ph}', ['ハートのスタンプ', ('tap', '落書きキャンバス')], True),
        ('share', f'/album/{a}/share', [], True),
        ('share-sent', f'/album/{a}/share', ['友達と共有する'], True),
        ('me-friends-search', '/me?tab=friends', [('fill', '友達をさがす', 'r'), 'さがす'], True),
        ('capsule-create', f'/album/{a}/capsule/new', [], True),
        ('capsule-done', f"/capsule/{f['capsule']}", [], True),
        # a job id that does not exist: the only way to reach the failure state on demand
        ('generating-failed', '/album/generating/999999999', [], True),
        ('public-share', f"/s/{f['share']}", [], False),
    ]
    return s


def visible_text(page):
    try:
        return page.evaluate('() => document.body.innerText')
    except Exception:  # noqa: BLE001 — page navigated away mid-read
        return ''


def prepare(page, base, path, setup, logged_in):
    page.goto(base + '/', wait_until='domcontentloaded')
    page.evaluate('() => localStorage.clear()')
    if logged_in:
        page.evaluate("t => localStorage.setItem('ai.refreshToken', t)", login(base)['refreshToken'])
    page.goto(base + path)
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(600)
    for name in setup:
        if isinstance(name, tuple) and name[0] == 'tap':
            page.get_by_label(name[1]).click()  # centre of the element
            page.wait_for_timeout(400)
            continue
        if isinstance(name, tuple):
            page.get_by_label(name[1]).fill(name[2])
            continue
        page.get_by_role('button', name=name, exact=True).first.click()
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(800 if name != '聞いてみる' else 1000)
        if name == '聞いてみる':
            page.get_by_role('button', name='これで撮る').wait_for(timeout=60_000)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--base', default='http://localhost:8080')
    ap.add_argument('--out', default=str(ROOT / 'docs/audit'))
    ap.add_argument('--only', help='comma-separated screen keys')
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    f = fixtures(args.base)
    print('fixtures', f, flush=True)
    results = []
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome', args=[
            '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'])
        ctx = browser.new_context(viewport=PHONE, locale='ja-JP', permissions=['camera'])
        page = ctx.new_page()
        page.set_default_timeout(15_000)
        errors: list[str] = []
        page.on('console', lambda m: m.type == 'error' and errors.append(m.text))
        page.on('pageerror', lambda e: errors.append(f'pageerror: {e}'))
        page.on('dialog', lambda d: d.dismiss())
        chooser: list[bool] = []
        page.on('filechooser', lambda fc: chooser.append(True))
        popups: list = []
        ctx.on('page', lambda pg: popups.append(pg))  # target=_blank links
        for key, path, setup, logged_in in states(f):
            if args.only and key not in args.only.split(','):
                continue
            try:
                prepare(page, args.base, path, setup, logged_in)
            except Exception as e:  # noqa: BLE001
                results.append({'screen': key, 'path': path, 'setup': setup, 'effect': f'SETUP FAILED: {str(e).splitlines()[0][:200]}'})
                print(f'{key}: SETUP FAILED {e}', flush=True)
                continue
            page.screenshot(path=str(out / f'{key}-00.jpg'), type='jpeg', quality=70)
            controls = page.evaluate(CONTROLS_JS)
            print(f'{key}: {len(controls)} controls', flush=True)
            if not controls:
                results.append({'screen': key, 'path': path, 'setup': setup, 'n': 0, 'name': '(none)',
                                'effect': 'no controls', 'shot': f'{key}-00.jpg'})
            for i, c in enumerate(controls):
                row = {'screen': key, 'path': path, 'setup': setup, 'n': i + 1, **c}
                if c['disabled']:
                    row['effect'] = 'disabled in this state'
                    results.append(row)
                    continue
                try:
                    prepare(page, args.base, path, setup, logged_in)
                    now = page.evaluate(CONTROLS_JS)
                    if i >= len(now) or now[i]['name'] != c['name']:
                        row['effect'] = 'control list changed on reload; skipped'
                        results.append(row)
                        continue
                    before_url, before_text = page.url, visible_text(page)
                    before_canvas = page.evaluate(CANVAS_JS)
                    before_states = [(x['name'], x['state']) for x in now]
                    errors.clear(); chooser.clear(); popups.clear()
                    x, y, cover = page.evaluate(CLICK_JS, [i])
                    if cover:
                        row['effect'] = f'covered by 「{cover}」 in this state'
                        results.append(row)
                        print(f"  {i + 1:02d} {c['name'][:30]!r:34} -> {row['effect']}", flush=True)
                        continue
                    t0 = time.time()
                    page.mouse.click(x, y)
                    page.wait_for_timeout(1500)
                    try:
                        page.wait_for_load_state('networkidle', timeout=8000)
                    except Exception:  # noqa: BLE001
                        pass
                    new_tab = None
                    for pg in popups:
                        try:
                            pg.wait_for_load_state('domcontentloaded', timeout=8000)
                            new_tab = pg.url.replace(args.base, '')
                        finally:
                            pg.close()
                    after_text = visible_text(page)
                    try:
                        canvas_changed = page.url == before_url and page.evaluate(CANVAS_JS) != before_canvas
                    except Exception:  # noqa: BLE001
                        canvas_changed = False
                    try:
                        after_states = [(x['name'], x['state']) for x in page.evaluate(CONTROLS_JS)]
                    except Exception:  # noqa: BLE001
                        after_states = before_states
                    toggled = [f'{n}: {a}' for n, a in after_states if (n, a) not in before_states and n in dict(before_states)]
                    renamed = sorted({n for n, _ in after_states} - {n for n, _ in before_states})
                    # multisets, not sets: removing one of two identical stickers must count
                    b_lines = Counter(ln.strip() for ln in before_text.splitlines() if ln.strip())
                    a_lines = Counter(ln.strip() for ln in after_text.splitlines() if ln.strip())
                    new = list((a_lines - b_lines).elements())
                    gone = list((b_lines - a_lines).elements())
                    row.update({
                        'urlAfter': page.url.replace(args.base, '') if page.url != before_url else None,
                        'newTab': new_tab, 'canvasChanged': canvas_changed,
                        'newText': new[:4], 'goneText': gone[:3], 'fileChooser': bool(chooser),
                        'stateChanged': toggled[:3], 'newControls': renamed[:4],
                        'errors': [e for e in errors if 'favicon' not in e][:3], 'ms': round((time.time() - t0) * 1000),
                        'shot': f'{key}-{i + 1:02d}.jpg',
                    })
                    page.screenshot(path=str(out / row['shot']), type='jpeg', quality=70)
                    if not (row['urlAfter'] or row['newTab'] or canvas_changed or new or gone or row['fileChooser'] or toggled or renamed):
                        row['effect'] = 'NO VISIBLE CHANGE'
                except Exception as e:  # noqa: BLE001
                    row['effect'] = f'error: {str(e).splitlines()[0][:200]}'
                results.append(row)
                print(f"  {i + 1:02d} {c['name'][:30]!r:34} -> {row.get('urlAfter') or ''} {row.get('newText', [])[:2]} {row.get('stateChanged', [])[:1]} {row.get('newControls', [])[:2]} {row.get('fileChooser') and 'FILE' or ''} {row.get('effect', '')}", flush=True)
        browser.close()
    if args.only and (out / 'controls.json').exists():
        # --only re-runs some screens and keeps every other screen's rows
        keep = json.loads((out / 'controls.json').read_text(encoding='utf-8'))['controls']
        redone = set(args.only.split(','))
        results = [r for r in keep if r['screen'] not in redone] + results
    (out / 'controls.json').write_text(json.dumps({'fixtures': f, 'controls': results}, ensure_ascii=False, indent=1), encoding='utf-8')
    silent = [r for r in results if r.get('effect') == 'NO VISIBLE CHANGE']
    print(f'{len(results)} controls, {len(silent)} with no visible change', flush=True)


if __name__ == '__main__':
    sys.exit(main())
