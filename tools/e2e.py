"""Drive the 01-requirements §8 five-minute demo end to end, with screenshots.

Runs against the built SPA that Spring serves (default http://localhost:8080),
in the installed Google Chrome at phone size with a fake camera, so nothing is
downloaded. Every step saves a screenshot and the run ends with report.json /
report.md in the output directory. Exits non-zero on the first failed check.

    python tools/e2e.py --out docs/e2e/run-1
    python tools/e2e.py --out docs/e2e/run-2 --headed

Checks beyond "the screen appeared":
- the shoot hint and the album both come back with aiGenerated = 1 (Claude CLI)
- the album has a title and a caption on every photo
- the share link opens logged out, and its OG image is served
- the sealed capsule opens and shows the message written into it

What a script cannot do is listed in docs/design/09-handoff-human.md: the
phone's native share sheet, LINE's preview card, a real camera, a real phone.
"""
import argparse
import io
import json
import random
import sys
import time
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw
from playwright.sync_api import Page, expect, sync_playwright

ROOT = Path(__file__).resolve().parent.parent
SAMPLES = ROOT / 'frontend/public/photos'
SHOTS = ('s1r1c1', 's1r2c2', 's1r4c3')
PHONE = {'width': 390, 'height': 844}
CAPSULE_MSG = '1年後のわたしへ。この日のこと覚えてる？'


class Run:
    def __init__(self, base: str, out: Path):
        self.base = base.rstrip('/')
        self.out = out
        self.steps: list[dict] = []
        self.facts: dict = {}
        self.t0 = time.time()
        self.page: Page | None = None
        out.mkdir(parents=True, exist_ok=True)
        for old in [*out.glob('*.png'), *out.glob('[0-9]*.jpg')]:
            old.unlink()

    def shot(self, page: Page, name: str, note: str = ''):
        n = len(self.steps) + 1
        path = self.out / f'{n:02d}-{name}.jpg'
        page.screenshot(path=str(path), type='jpeg', quality=80)
        self.steps.append({'n': n, 'step': name, 'note': note, 'url': page.url.replace(self.base, ''),
                           't': round(time.time() - self.t0, 1), 'shot': path.name})
        print(f'[{self.steps[-1]["t"]:6.1f}s] {n:02d} {name} {note}', flush=True)

    def api(self, path: str, token: str | None = None, method: str = 'GET', body=None):
        req = urllib.request.Request(self.base + path, method=method,
                                     data=None if body is None else json.dumps(body).encode(),
                                     headers={'Content-Type': 'application/json',
                                              **({'Authorization': f'Bearer {token}'} if token else {})})
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw and r.headers.get_content_type() == 'application/json' else raw)


def unique_photo(stem: str, out: Path) -> Path:
    """A sample cell with one random dot, so the upload is never deduplicated
    against an earlier run's copy (which would already sit in an album)."""
    img = Image.open(SAMPLES / f'{stem}.jpg').convert('RGB')
    draw = ImageDraw.Draw(img)
    x, y = random.randrange(img.width), random.randrange(img.height)
    draw.point((x, y), fill=(random.randrange(256), random.randrange(256), random.randrange(256)))
    path = out / f'upload-{stem}.jpg'
    img.save(path, quality=92)
    return path


def check(cond: bool, what: str):
    if not cond:
        raise AssertionError(what)


def token_of(r: 'Run') -> str:
    """The SPA keeps the access token in memory only, so the checks log in themselves."""
    _, pair = r.api('/api/auth/login', method='POST', body={'userAccount': 'nao', 'userPassword': 'password'})
    return pair['accessToken']


FAILED: dict = {}


def run(args) -> Run:
    r = FAILED['run'] = Run(args.base, Path(args.out))
    tmp = r.out / '_uploads'
    tmp.mkdir(exist_ok=True)
    uploads = [unique_photo(s, tmp) for s in SHOTS]

    status, seed = r.api('/api/dev/seed', method='POST')
    check(status == 200, 'seed failed')

    with sync_playwright() as p:
        try:
            return _drive(r, p, uploads, args)
        except Exception:
            if r.page:
                try:
                    r.shot(r.page, 'FAILED-here')
                except Exception:  # noqa: BLE001
                    pass
            raise


def _drive(r: Run, p, uploads, args) -> Run:
    if True:
        browser = p.chromium.launch(channel='chrome', headless=not args.headed, args=[
            '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'])
        ctx = browser.new_context(viewport=PHONE, device_scale_factor=2, locale='ja-JP',
                                  permissions=['camera'])
        page = r.page = ctx.new_page()
        page.set_default_timeout(20_000)
        console_errors: list[str] = []
        page.on('console', lambda m: m.type == 'error' and console_errors.append(m.text))

        # 0:00 login
        page.goto(r.base + '/')
        expect(page.get_by_role('button', name='はじめる')).to_be_visible()
        r.shot(page, 'home')
        page.get_by_role('button', name='ログイン').click()
        page.get_by_placeholder('ID', exact=True).fill('nao')
        page.get_by_placeholder('パスワード', exact=True).fill('password')
        r.shot(page, 'login-form')
        page.locator('form').get_by_role('button', name='ログイン').click()
        page.wait_for_url('**/me')
        expect(page.get_by_text('読み込み中')).to_have_count(0)
        r.shot(page, 'my-page')
        token = token_of(r)

        # 0:20 camera: ask the AI how to shoot, then three shots
        page.get_by_role('button', name='写真を撮る').click()
        page.wait_for_url('**/camera')
        page.wait_for_timeout(800)
        r.shot(page, 'camera')
        page.get_by_role('button', name='AIに撮り方を聞く').click()
        with page.expect_response(lambda res: res.url.endswith('/api/hints/shoot'), timeout=60_000) as hint_res:
            page.get_by_role('button', name='聞いてみる').click()
        hint = hint_res.value.json()
        r.facts['shootHint'] = hint
        expect(page.get_by_role('button', name='これで撮る')).to_be_visible()
        r.shot(page, 'shoot-hint', f"aiGenerated={hint.get('aiGenerated')}")
        check(hint.get('aiGenerated') == 1, f'shoot hint was not from the AI: {hint}')
        page.get_by_role('button', name='これで撮る').click()
        for i, path in enumerate(uploads, 1):
            with page.expect_file_chooser() as fc:
                page.get_by_role('button', name='写真を撮る').click()
            with page.expect_response(lambda res: res.url.endswith('/api/photos') and res.request.method == 'POST'):
                fc.value.set_files(str(path))
            expect(page.get_by_role('button', name=f'撮った{i}枚でアルバムを作る')).to_be_visible()
        r.shot(page, 'camera-3-shots')

        # 0:40 hand them to the AI
        page.get_by_role('button', name='撮った3枚でアルバムを作る').click()
        page.wait_for_url('**/album/new')
        generate = page.get_by_role('button', name='AIでアルバムにまとめる')
        expect(generate).to_be_enabled()
        r.facts['selectedForAlbum'] = generate.inner_text()
        r.shot(page, 'album-create', generate.inner_text())
        started = time.time()
        generate.click()
        page.wait_for_url('**/album/generating/**')
        page.wait_for_timeout(1500)
        r.shot(page, 'album-generating')
        page.wait_for_url(lambda url: '/album/' in url and '/generating/' not in url and url.rstrip('/').split('/')[-1].isdigit(),
                          timeout=150_000)
        r.facts['generationSeconds'] = round(time.time() - started, 1)
        album_id = int(page.url.rstrip('/').split('/')[-1])

        # 1:10 title and captions
        _, album = r.api(f'/api/albums/{album_id}', token)
        r.facts['album'] = {'id': album_id, 'title': album['title'], 'aiGenerated': album['aiGenerated'],
                            'aiModel': album.get('aiModel'), 'summary': album.get('summary'),
                            'captions': [ph.get('caption') for ph in album['photos']]}
        expect(page.get_by_role('heading', name=album['title'])).to_be_visible()
        r.shot(page, 'album-detail', f"「{album['title']}」 aiGenerated={album['aiGenerated']}")
        check(album['aiGenerated'] == 1, f'album was not written by the AI: {r.facts["album"]}')
        check(all(ph.get('caption') for ph in album['photos']), 'a photo has no caption')

        # 1:30 decorate one photo: a line, "Best Friends ♡", a heart
        page.get_by_role('link', name='この写真を飾る').click()
        page.wait_for_url('**/decorate/**')
        canvas = page.get_by_label('落書きキャンバス')
        expect(canvas).to_be_visible()
        page.wait_for_timeout(500)
        box = canvas.bounding_box()
        page.mouse.move(box['x'] + box['width'] * 0.2, box['y'] + box['height'] * 0.3)
        page.mouse.down()
        for k in range(1, 13):
            page.mouse.move(box['x'] + box['width'] * (0.2 + k * 0.05), box['y'] + box['height'] * (0.3 + (k % 2) * 0.08))
        page.mouse.up()
        page.get_by_role('button', name='文字').click()
        expect(page.get_by_label('入れる文字')).to_have_value('Best Friends ♡')
        page.get_by_role('button', name='追加').click()
        page.get_by_role('button', name='ハートのスタンプ').click()
        canvas.click(position={'x': box['width'] * 0.78, 'y': box['height'] * 0.25})
        canvas.click(position={'x': box['width'] * 0.25, 'y': box['height'] * 0.62})
        r.shot(page, 'decorate')

        # 2:10 save and share
        page.get_by_role('button', name='保存して友達とシェア').click()
        page.wait_for_url('**/share')
        page.get_by_role('button', name='あやかを見せる相手にする').click()
        r.shot(page, 'share')
        page.get_by_role('button', name='友達と共有する').click()
        link = page.locator('a[href*="/s/"]')
        expect(link).to_be_visible()
        share_url = link.get_attribute('href')
        r.facts['shareUrl'] = share_url
        r.shot(page, 'share-link', share_url)

        # 3:00 what the friend sees, logged out
        guest = browser.new_context(viewport=PHONE, device_scale_factor=2, locale='ja-JP')
        gp = guest.new_page()
        local_share = r.base + share_url[share_url.index('/s/'):]
        gp.goto(local_share)
        og = gp.locator('meta[property="og:image"]').get_attribute('content')
        r.facts['ogImage'] = og
        check(bool(og), 'share page has no og:image')
        og_local = r.base + og[og.index('/og/'):]
        with urllib.request.urlopen(og_local, timeout=30) as res:
            check(res.status == 200 and res.headers.get_content_type() == 'image/jpeg', 'OG image not served')
            (r.out / 'og-image.jpg').write_bytes(res.read())
        r.shot(gp, 'friend-view-logged-out', local_share.replace(r.base, ''))
        guest.close()

        # 3:30 time capsule, one year
        page.get_by_role('button', name='タイムカプセルを作成する').click()
        page.wait_for_url('**/capsule/new')
        page.get_by_role('radio', name='1年後').click()
        page.get_by_placeholder('未来の自分へひとこと（開けるまで誰にも見えません）').fill(CAPSULE_MSG)
        r.shot(page, 'capsule-create')
        page.get_by_role('button', name='タイムカプセルを作成する').click()
        page.wait_for_url(lambda url: url.rstrip('/').split('/')[-1].isdigit() and '/capsule/' in url)
        expect(page.get_by_text('で開けられます')).to_be_visible()
        r.shot(page, 'capsule-done')
        capsule_id = int(page.url.rstrip('/').split('/')[-1])
        status, sealed = r.api(f'/api/capsules/{capsule_id}', token)
        r.facts['capsuleSealed'] = {k: sealed.get(k) for k in ('status', 'daysRemaining', 'capsuleMsg', 'album')}
        check(sealed['status'] == 'SEALED' and not sealed.get('capsuleMsg'), 'sealed capsule leaked its content')

        # 4:00 (dev) open it now
        page.get_by_role('button', name='タイムカプセルを開ける').click()
        page.wait_for_url(f'**/album/*?capsule={capsule_id}')
        expect(page.get_by_text(CAPSULE_MSG)).to_be_visible()
        r.shot(page, 'capsule-opened')

        r.facts['consoleErrors'] = console_errors
        browser.close()
    r.facts['totalSeconds'] = round(time.time() - r.t0, 1)
    return r


def report(r: Run, error: str | None):
    for f in (r.out / '_uploads').glob('*'):
        f.unlink()
    (r.out / '_uploads').rmdir()
    result = {'passed': error is None, 'error': error, 'base': r.base, 'steps': r.steps, 'facts': r.facts,
              'finished': time.strftime('%Y-%m-%d %H:%M:%S')}
    (r.out / 'report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    lines = [f"# e2e run — {'PASS' if error is None else 'FAIL'}", '',
             f"Finished {result['finished']} against `{r.base}`, {r.facts.get('totalSeconds', '?')} s in total.", '']
    if error:
        lines += [f'**Failed:** {error}', '']
    album = r.facts.get('album', {})
    if album:
        lines += [f"Album 「{album['title']}」 — aiGenerated={album['aiGenerated']}, model {album.get('aiModel')}, "
                  f"generated in {r.facts.get('generationSeconds')} s. Captions: {', '.join(c or '—' for c in album['captions'])}", '']
    if 'shootHint' in r.facts:
        lines += [f"Shoot hint — aiGenerated={r.facts['shootHint'].get('aiGenerated')}: {r.facts['shootHint'].get('hint')}", '']
    lines += ['| # | Step | t (s) | Note | Screenshot |', '| --- | --- | --- | --- | --- |']
    lines += [f"| {s['n']} | {s['step']} | {s['t']} | {s['note']} | ![]({s['shot']}) |" for s in r.steps]
    (r.out / 'report.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--base', default='http://localhost:8080')
    ap.add_argument('--out', required=True)
    ap.add_argument('--headed', action='store_true')
    args = ap.parse_args()
    r = None
    try:
        r = run(args)
        report(r, None)
        print('PASS', r.out)
    except Exception as e:  # noqa: BLE001 — every failure ends in a report
        r = FAILED.get('run') or Run(args.base, Path(args.out))
        report(r, f'{type(e).__name__}: {e}'.splitlines()[0][:500])
        print('FAIL', e, file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
