"""API-level evidence for the 01-requirements rows the e2e run does not show.

    python tools/req_check.py [--base http://localhost:8080] [--out docs/audit/requirements.json]

Each check prints PASS/FAIL with the values it saw. Uses the seeded users
(nao, ayaka). FR-03.1 runs the real AI on 6 photos (~20 s).
"""
import argparse
import io
import json
import random
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BASE = 'http://localhost:8080'
results = []


def call(method, path, body=None, token=None, headers=None, files=None):
    h = dict(headers or {})
    data = None
    if token:
        h['Authorization'] = 'Bearer ' + token
    if files:
        bd = uuid.uuid4().hex
        crlf = b'\r\n'
        parts = []
        for name, content in files:
            head = f'--{bd}', f'Content-Disposition: form-data; name="files"; filename="{name}"', 'Content-Type: image/jpeg', ''
            parts.append(crlf.join(x.encode() for x in head) + crlf + content + crlf)
        data = b''.join(parts) + f'--{bd}--'.encode() + crlf
        h['Content-Type'] = 'multipart/form-data; boundary=' + bd
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
        h['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            raw = r.read()
            ctype = r.headers.get_content_type()
            return r.status, (json.loads(raw) if raw and ctype == 'application/json' else raw), dict(r.headers)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw), dict(e.headers)
        except ValueError:
            return e.code, raw, dict(e.headers)


def login(account):
    return call('POST', '/api/auth/login', {'userAccount': account, 'userPassword': 'password'})[1]['accessToken']


def check(req, what, ok, seen):
    results.append({'req': req, 'check': what, 'pass': bool(ok), 'seen': seen})
    print(f"{'PASS' if ok else 'FAIL'} {req:8} {what} :: {json.dumps(seen, ensure_ascii=False)[:300]}", flush=True)


def rational(x):  # PIL writes floats as RATIONAL
    return float(x)


def jpeg(cell='s1r1c1', taken=None, gps=None, orientation=None, size=None):
    img = Image.open(ROOT / f'frontend/public/photos/{cell}.jpg').convert('RGB')
    if size:
        img = img.resize(size)
    img.putpixel((random.randrange(img.width), random.randrange(img.height)), (random.randrange(256),) * 3)
    exif = Image.Exif()
    if taken:
        exif.get_ifd(0x8769)[36867] = taken  # DateTimeOriginal
        exif[306] = taken
    if gps:
        lat, lon = gps
        exif.get_ifd(0x8825).update({1: 'N', 2: tuple(rational(v) for v in (int(lat), int(lat * 60) % 60, (lat * 3600) % 60)),
                                     3: 'E', 4: tuple(rational(v) for v in (int(lon), int(lon * 60) % 60, (lon * 3600) % 60))})
    if orientation:
        exif[274] = orientation
    buf = io.BytesIO()
    img.save(buf, 'JPEG', quality=92, exif=exif.tobytes())
    return buf.getvalue()


def fallback():
    """FR-03.6 against a server whose Claude CLI path does not exist:
    ./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --app.ai.cli.command=C:/nope/claude.exe"
    """
    nao = login('nao')
    health = call('GET', '/api/health')[1]
    files = [(f'f{i}.jpg', jpeg(c, taken=f'2026:09:22 12:0{i}:00')) for i, c in enumerate(['s1r1c1', 's1r2c2', 's1r4c3'])]
    ids = [p['id'] for p in call('POST', '/api/photos', token=nao, files=files)[1]['uploaded']]
    accepted = call('POST', '/api/albums/generate', {'photoIds': ids}, nao, {'Idempotency-Key': uuid.uuid4().hex})[1]
    t0, job = time.time(), {'status': 'PENDING'}
    while job.get('status') not in ('READY', 'FAILED') and time.time() - t0 < 120:
        time.sleep(1)
        job = call('GET', f"/api/albums/jobs/{accepted['jobId']}", token=nao)[1]
    album = call('GET', f"/api/albums/{job['albumIds'][0]}", token=nao)[1] if job.get('albumIds') else {}
    check('FR-03.6', 'AI unavailable -> album still made with rule-based title and captions',
          job.get('status') == 'READY' and album.get('aiGenerated') == 0 and album.get('title') and all(p['caption'] for p in album.get('photos', [])),
          {'aiReachable': health.get('aiReachable'), 'status': job.get('status'), 'title': album.get('title'), 'aiGenerated': album.get('aiGenerated'),
           'captions': [p['caption'] for p in album.get('photos', [])], 'seconds': round(time.time() - t0, 1)})
    st, hint, _ = call('POST', '/api/hints/shoot', {'memberCount': 3, 'place': '梅田'}, nao)
    check('FR-03.6', 'AI unavailable -> shoot hint falls back to fixed text', st == 200 and hint.get('aiGenerated') == 0 and hint.get('hint'),
          {'status': st, 'hint': hint.get('hint') if isinstance(hint, dict) else hint, 'aiGenerated': hint.get('aiGenerated') if isinstance(hint, dict) else None})


def main():
    global BASE
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--base', default=BASE)
    ap.add_argument('--out', default=str(ROOT / 'docs/audit/requirements.json'))
    ap.add_argument('--fallback-base', help='a second server started with a broken AI (see fallback()); adds the FR-03.6 checks')
    args = ap.parse_args()
    if args.fallback_base:
        BASE = args.fallback_base
        fallback()
        BASE = args.base
    call('POST', '/api/dev/seed', {})
    nao, ayaka = login('nao'), login('ayaka')

    # FR-02 / 02.1 / 02.2 -------------------------------------------------------
    st, up, _ = call('POST', '/api/photos', token=nao, files=[
        ('exif.jpg', jpeg('s1r2c3', taken='2026:09:20 17:30:00', gps=(34.7025, 135.4959))),
        ('rotated.jpg', jpeg('s2r4c3', taken='2026:09:20 17:31:00', orientation=6, size=(300, 200))),
    ])
    ok = st == 200 and len(up.get('uploaded', [])) == 2
    check('FR-02', 'two photos in one multipart upload', ok, {'status': st, 'uploaded': len(up.get('uploaded', [])) if isinstance(up, dict) else up})
    if ok:
        exif, rot = up['uploaded']
        check('FR-02.1', 'EXIF DateTimeOriginal and GPS are read', exif['takenTime'].startswith('2026-09-20T17:30') and
              exif['latitude'] and abs(exif['latitude'] - 34.7025) < 0.01 and abs(exif['longitude'] - 135.4959) < 0.01,
              {k: exif[k] for k in ('takenTime', 'latitude', 'longitude')})
        check('FR-02.2', 'EXIF orientation 6 (300x200) is stored upright as 200x300', (rot['picWidth'], rot['picHeight']) == (200, 300),
              {'picWidth': rot['picWidth'], 'picHeight': rot['picHeight']})

    # FR-03.1 / 03.2 / 03.3 / 03.4 / 03.5 -----------------------------------------
    files = [(f'm{i}.jpg', jpeg(c, taken=f'2026:09:21 {h}')) for i, (c, h) in enumerate([
        ('s1r1c1', '10:00:00'), ('s1r2c2', '10:05:00'), ('s1r4c3', '10:10:00'),
        ('s2r2c3', '18:00:00'), ('s2r1c3', '18:04:00'), ('s1r3c1', '18:08:00')])]
    st, up, _ = call('POST', '/api/photos', token=nao, files=files)
    ids = [p['id'] for p in up['uploaded']]
    t0 = time.time()
    st, accepted, _ = call('POST', '/api/albums/generate', {'photoIds': ids}, nao, {'Idempotency-Key': uuid.uuid4().hex})
    job = {'status': 'PENDING'}
    while job.get('status') not in ('READY', 'FAILED') and time.time() - t0 < 180:
        time.sleep(2)
        job = call('GET', f"/api/albums/jobs/{accepted['jobId']}", token=nao)[1]
    secs = round(time.time() - t0, 1)
    check('FR-03.1', '6 photos 8 h apart -> 2 albums in one request', job.get('status') == 'READY' and len(job.get('albumIds', [])) == 2,
          {'status': job.get('status'), 'albumIds': job.get('albumIds'), 'seconds': secs})
    albums = [call('GET', f'/api/albums/{a}', token=nao)[1] for a in job.get('albumIds', [])]
    check('FR-03.2', 'AI title and summary on each album', albums and all(a['aiGenerated'] == 1 and a['title'] and a['summary'] for a in albums),
          [{'title': a['title'], 'aiGenerated': a['aiGenerated'], 'aiModel': a['aiModel']} for a in albums])
    check('FR-03.3', 'a caption on every photo', albums and all(p['caption'] for a in albums for p in a['photos']),
          [p['caption'] for a in albums for p in a['photos']])
    check('FR-03.4', 'cover is one of the album photos', albums and all(a['coverPhotoId'] in [p['photoId'] for p in a['photos']] for a in albums),
          [a['coverPhotoId'] for a in albums])
    weather = [p['weather'] for a in albums for p in a['photos']]
    places = [p['place'] for a in albums for p in a['photos']]
    check('FR-03.5', 'weather (and place when visible) estimated from the images', any(weather),
          {'weather': weather, 'place': places})

    # FR-04 / 04.1 / 04.2 / 04.3 -- decoration save, edit, and read back ------------
    album = albums[0] if albums else call('GET', '/api/albums', token=nao)[1]['items'][0]
    ap_id = album['photos'][0]['id']
    base = f"/api/albums/{album['id']}/photos/{ap_id}/decoration"
    st, deco, hdr = call('GET', base, token=nao)
    etag = hdr.get('ETag') or hdr.get('Etag')
    elements = [
        {'id': 's1', 'type': 'stroke', 'color': '#ff6fa8', 'width': 0.01, 'points': [[0.1, 0.1], [0.5, 0.2]]},
        {'id': 't1', 'type': 'text', 'text': 'Best Friends ♡', 'x': 0.5, 'y': 0.8, 'font': 'hand', 'size': 0.08, 'color': '#fff', 'rotation': 0},
        {'id': 'h1', 'type': 'sticker', 'assetId': 'heart', 'x': 0.8, 'y': 0.2, 'scale': 0.16, 'rotation': 0},
    ]
    st1, _, hdr1 = call('PUT', base, {'elements': elements}, nao, {'If-Match': etag})
    st2, _, hdr2 = call('PUT', base, {'elements': elements[:2]}, nao, {'If-Match': hdr1.get('ETag') or hdr1.get('Etag')})
    _, back, _ = call('GET', base, token=nao)
    types = [e['type'] for e in back['elements']]
    check('FR-04', 'a stroke is saved', st1 in (200, 204) and 'stroke' in types, {'put': st1, 'elements': types})
    check('FR-04.1', 'text in the hand font is saved', any(e['type'] == 'text' and e['font'] == 'hand' for e in back['elements']), types)
    check('FR-04.2', 'a sticker is accepted (then removed by the edit below)', st1 in (200, 204), {'put with sticker': st1})
    check('FR-04.3', 'saved decoration can be edited later (second PUT drops the sticker)', st2 in (200, 204) and types == ['stroke', 'text'],
          {'second put': st2, 'elements now': types})

    # FR-09 / 09.1 / 09.2 -------------------------------------------------------------
    ayaka_id = next(f['id'] for f in call('GET', '/api/friends', token=nao)[1] if f['userAccount'] == 'ayaka')
    st, _, _ = call('POST', f"/api/albums/{album['id']}/members", {'userId': ayaka_id}, nao)
    members = [m['userName'] for m in call('GET', f"/api/albums/{album['id']}", token=nao)[1]['members']]
    check('FR-09', 'a friend is added as album member', st in (200, 201, 204) and 'あやか' in members, {'status': st, 'members': members})
    _, _, h = call('GET', base, token=ayaka)
    st, _, _ = call('PUT', base, {'elements': elements[:1]}, ayaka, {'If-Match': h.get('ETag') or h.get('Etag')})
    check('FR-09.1', 'the member can decorate', st in (200, 204), {'ayaka put': st})
    st, body, _ = call('PUT', base, {'elements': elements}, nao, {'If-Match': hdr2.get('ETag') or hdr2.get('Etag')})
    check('FR-09.2', 'a stale If-Match is refused (optimistic lock)', st in (409, 412), {'status': st, 'code': body.get('code') if isinstance(body, dict) else None})

    # FR-10 / 10.1 --------------------------------------------------------------------
    a = call('GET', f"/api/albums/{album['id']}", token=nao)[1]
    p0 = a['photos'][0]
    check('FR-10', 'place / time / music / comment / members / weather are on the album photo',
          all(k in p0 for k in ('place', 'takenTime', 'music', 'photoComment', 'weather')) and a['members'],
          {k: p0.get(k) for k in ('place', 'takenTime', 'music', 'photoComment', 'weather')})
    st, patched, _ = call('PATCH', f"/api/albums/{album['id']}/photos/{p0['id']}", {'photoComment': 'audit comment', 'music': 'audit song'}, nao,
                          {'If-Match': f"\"{p0['version']}\""})
    again = call('GET', f"/api/albums/{album['id']}", token=nao)[1]['photos'][0]
    check('FR-10.1', 'details can be edited by hand', st == 200 and again['photoComment'] == 'audit comment' and again['music'] == 'audit song',
          {'status': st, 'photoComment': again['photoComment'], 'music': again['music']})

    # FR-08 -------------------------------------------------------------------------------
    st, found, _ = call('GET', '/api/users/search?q=rin', token=nao)
    check('FR-08', 'search finds a user by ID', st == 200 and any(u['userAccount'] == 'rin' for u in found), [u['userAccount'] for u in found] if st == 200 else found)
    # a throwaway account asks nao; nao accepts from the inbox
    acct = 'aud' + uuid.uuid4().hex[:6]
    call('POST', '/api/auth/register', {'userAccount': acct, 'userPassword': 'password', 'userName': '監査'})
    other = login(acct)
    nao_id = call('GET', '/api/users/me', token=nao)[1]['id']
    st_req, _, _ = call('POST', '/api/friends/requests', {'userId': nao_id}, other)
    inbox = call('GET', '/api/friends/requests', token=nao)[1]
    req = next((r for r in inbox if r['userName'] == '監査'), None)
    st_acc = call('POST', f"/api/friends/requests/{req['id']}/accept", token=nao)[0] if req else None
    now_friends = any(f['userAccount'] == acct for f in call('GET', '/api/friends', token=nao)[1])
    check('FR-08', 'request -> shows in the inbox -> accept -> friends', st_req == 204 and req and st_acc == 204 and now_friends,
          {'request': st_req, 'inbox': bool(req), 'accept': st_acc, 'friends': now_friends})

    # FR-05 / 05.2 / 05.3 -- share link, read with no login ------------------------------
    st, share, _ = call('POST', f"/api/albums/{album['id']}/share", {}, nao)
    tok = share.get('shareToken') if isinstance(share, dict) else None
    st_pub, pub, _ = call('GET', f'/api/share/{tok}')
    st_page, page, _ = call('GET', f'/s/{tok}')
    page = page.decode('utf-8', 'replace') if isinstance(page, bytes) else str(page)
    st_og, og, hdr_og = call('GET', f'/og/{tok}.jpg')
    check('FR-05', 'a share link is issued', st == 200 and tok and len(tok) >= 22, {'status': st, 'shareUrl': share.get('shareUrl') if isinstance(share, dict) else share})
    check('FR-05.2', '/s/{token} carries og:title and og:image, and the og image is a JPEG',
          st_page == 200 and 'og:image' in page and 'og:title' in page and st_og == 200 and og[:2] == b'\xff\xd8',
          {'page': st_page, 'og': st_og, 'ogBytes': len(og) if isinstance(og, bytes) else None})
    check('FR-05.3', 'the shared album is readable with no login', st_pub == 200 and pub.get('photos'),
          {'status': st_pub, 'title': pub.get('title') if isinstance(pub, dict) else None,
           'photos': len(pub.get('photos', [])) if isinstance(pub, dict) else None})

    # FR-06 / 06.1 -- seal, then try to read it back before the open date -----------------
    target = albums[1] if len(albums) > 1 else album
    st, cap, _ = call('POST', '/api/capsules', {'albumId': target['id'], 'openTime': '2027-09-27T12:00:00+09:00',
                                                'capsuleMsg': 'audit secret message'}, nao)
    cap_id = cap.get('id') if isinstance(cap, dict) else None
    st_get, sealed, _ = call('GET', f'/api/capsules/{cap_id}', token=nao)
    st_open, refused, _ = call('POST', f'/api/capsules/{cap_id}/open', token=nao)
    listed = call('GET', '/api/capsules', token=nao)[1]
    leak = 'audit secret message' in json.dumps([sealed, refused, listed], ensure_ascii=False)
    check('FR-06', 'an album is sealed as a capsule', st in (200, 201) and cap_id and cap.get('status') == 'SEALED',
          {'status': st, 'capsule': cap})
    check('FR-06.1', 'before the open date the server returns neither the message nor the album',
          st_get == 200 and set(sealed) <= {'id', 'status', 'openTime', 'daysRemaining'} and st_open == 409 and not leak,
          {'get': st_get, 'fields': sorted(sealed) if isinstance(sealed, dict) else sealed, 'open': st_open,
           'code': refused.get('code') if isinstance(refused, dict) else None, 'messageLeaked': leak})

    # FR-07 / 07.1 -----------------------------------------------------------------------
    acct = 'reg' + uuid.uuid4().hex[:6]
    st_reg, _, _ = call('POST', '/api/auth/register', {'userAccount': acct, 'userPassword': 'password1', 'userName': '登録'})
    st_in, pair_new, _ = call('POST', '/api/auth/login', {'userAccount': acct, 'userPassword': 'password1'})
    st_bad, _, _ = call('POST', '/api/auth/login', {'userAccount': acct, 'userPassword': 'wrong-pass'})
    me = call('GET', '/api/users/me', token=pair_new.get('accessToken'))[1] if st_in == 200 else {}
    check('FR-07', 'register, then log in with the name and password (a wrong password is refused)',
          st_reg in (200, 201, 204) and st_in == 200 and st_bad in (400, 401) and me.get('userAccount') == acct,
          {'register': st_reg, 'login': st_in, 'wrongPassword': st_bad, 'me': me.get('userAccount')})
    st, pair, _ = call('POST', '/api/auth/login', {'userAccount': 'nao', 'userPassword': 'password'})
    st2, fresh, _ = call('POST', '/api/auth/refresh', {'refreshToken': pair['refreshToken']})
    check('FR-07.1', 'a stored refresh token gets a new access token (what a reload does)', st2 == 200 and fresh.get('accessToken'),
          {'refresh': st2})

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding='utf-8')
    failed = [r for r in results if not r['pass']]
    print(f'{len(results) - len(failed)}/{len(results)} passed')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
