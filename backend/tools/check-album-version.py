# -*- coding: utf-8 -*-
"""Measures the version delta of each kind of album PATCH against MySQL."""
import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080"


def call(method, path, token=None, body=None, headers=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode("utf-8")
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        return e.code, json.loads(raw) if raw else raw


_, tok = call("POST", "/api/auth/login",
              body={"userAccount": "nao", "userPassword": "password"})
tok = tok["accessToken"]

_, page = call("GET", "/api/albums", tok)
aid = page["items"][0]["id"]


def delta(label, body):
    _, before = call("GET", "/api/albums/%d" % aid, tok)
    v0 = before["version"]
    st, after = call("PATCH", "/api/albums/%d" % aid, tok, body=body,
                     headers={"If-Match": str(v0)})
    if st != 200:
        print("%-28s %s %s" % (label, st, after))
        return
    v1 = after["version"]
    print("%-28s %d -> %d   (delta %+d)" % (label, v0, v1, v1 - v0))


delta("title only", {"title": "タイトルA"})
delta("title only again", {"title": "タイトルB"})
delta("summary only", {"summary": "ようやくA"})
delta("summary only again", {"summary": "ようやくB"})
delta("title + summary", {"title": "タイトルC", "summary": "ようやくC"})

_, album = call("GET", "/api/albums/%d" % aid, tok)
print("\nalbum_member.albumTitle follows:", album["title"])
