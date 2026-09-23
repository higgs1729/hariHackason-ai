# -*- coding: utf-8 -*-
"""
Walks every route the frontend contract declares, against the live app.

Isolation checks use accounts registered fresh each run rather than the seeded
ones. The seed makes all four users members of each other's albums and friends
with each other, so asking whether a seeded user is locked out of a seeded
album answers "no" for the wrong reason.
"""
import json
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
STAMP = str(int(time.time()))[-6:]


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
            return r.status, (json.loads(raw) if raw else None), dict(r.headers)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw), dict(e.headers)
        except Exception:
            return e.code, raw, dict(e.headers)


def login(account):
    _, body, _ = call("POST", "/api/auth/login",
                      body={"userAccount": account, "userPassword": "password"})
    return body["accessToken"]


def register(account, name):
    _, body, _ = call("POST", "/api/auth/register",
                      body={"userAccount": account + STAMP,
                            "userPassword": "password", "userName": name})
    return body["accessToken"], body["user"]["id"]


def show(label, status, body, extra=""):
    text = body if isinstance(body, str) else json.dumps(body, ensure_ascii=False)
    if len(text) > 170:
        text = text[:170] + "…"
    print("%-32s %s  %s %s" % (label, status, text, extra))


def code(body):
    return body.get("code") if isinstance(body, dict) else None


nao = login("nao")
outsider, outsider_id = register("sora", "そら")
other, other_id = register("kaede", "かえで")

print("--- albums.list ---")
st, page, _ = call("GET", "/api/albums", nao)
show("GET /api/albums", st, {"total": page["total"], "n": len(page["items"]),
                             "cursor": page["nextCursor"]})
first = page["items"][0]
print("   fields:", ", ".join(sorted(first.keys())))
print("   photos/members absent:", "photos" not in first and "members" not in first)
album_id, version = first["id"], first["version"]

st, page, _ = call("GET", "/api/albums", outsider)
show("outsider's empty home", st, page)

print("\n--- albums.patch ---")
st, body, _ = call("PATCH", "/api/albums/%d" % album_id, nao, body={"title": "文化祭のあと"})
show("no If-Match", st, code(body))
st, body, _ = call("PATCH", "/api/albums/%d" % album_id, nao,
                   body={"title": "文化祭のあと"}, headers={"If-Match": "999"})
show("stale If-Match", st, body)
st, body, hdr = call("PATCH", "/api/albums/%d" % album_id, nao,
                     body={"title": "文化祭のあと"}, headers={"If-Match": str(version)})
show("ok", st, {"title": body["title"], "version": body["version"]},
     "ETag=" + str(hdr.get("ETag")))
version = body["version"]
st, body, _ = call("PATCH", "/api/albums/%d" % album_id, nao,
                   body={"title": "   "}, headers={"If-Match": str(version)})
show("blank title", st, code(body))
st, body, _ = call("PATCH", "/api/albums/%d" % album_id, nao,
                   body={"coverPhotoId": 99999}, headers={"If-Match": str(version)})
show("cover not in album", st, code(body))

print("\n--- albums.patchPhoto ---")
st, album, _ = call("GET", "/api/albums/%d" % album_id, nao)
photo = album["photos"][0]
st, body, _ = call("PATCH", "/api/albums/%d/photos/%d" % (album_id, photo["id"]), nao,
                   body={"caption": "この日はずっと晴れてた", "weather": "晴れ"},
                   headers={"If-Match": str(photo["version"])})
show("ok", st, {"caption": body["caption"], "weather": body["weather"],
                "version": body["version"]})
st, body, _ = call("PATCH", "/api/albums/%d/photos/%d" % (album_id, photo["id"]), nao,
                   body={"weather": "台風"}, headers={"If-Match": str(body["version"])})
show("invalid weather", st, code(body))
st, body, _ = call("PATCH", "/api/albums/%d/photos/%d" % (album_id, 999999), nao,
                   body={"caption": "x"}, headers={"If-Match": "0"})
show("photo from another album", st, code(body))

print("\n--- isolation (outsider is in no album) ---")
st, body, _ = call("GET", "/api/albums/%d" % album_id, outsider)
show("GET someone else's album", st, code(body))
st, body, _ = call("PATCH", "/api/albums/%d" % album_id, outsider,
                   body={"title": "のっとり"}, headers={"If-Match": str(version)})
show("PATCH it", st, code(body))
st, body, _ = call("PATCH", "/api/albums/%d/photos/%d" % (album_id, photo["id"]), outsider,
                   body={"caption": "のっとり"}, headers={"If-Match": "1"})
show("PATCH a photo in it", st, code(body))
st, body, _ = call("POST", "/api/albums/%d/members" % album_id, outsider,
                   body={"userId": outsider_id})
show("add self as member", st, code(body))
st, album, _ = call("GET", "/api/albums/%d" % album_id, nao)
show("title survived", st, album["title"])

print("\n--- users ---")
st, body, _ = call("GET", "/api/users/me", nao)
show("GET /api/users/me", st, body)
st, body, _ = call("GET", "/api/users/search?q=%E3%81%82", nao)
show("search 'あ'", st, [u["userName"] for u in body])
st, body, _ = call("GET", "/api/users/search?q=", nao)
show("search '' (empty)", st, body)
st, body, _ = call("GET", "/api/users/search?q=nao", nao)
show("search own account", st, [u["userAccount"] for u in body])
st, body, _ = call("GET", "/api/users/2", nao)
show("GET /api/users/2", st, body["userName"])
st, body, _ = call("GET", "/api/users/99999", nao)
show("GET missing user", st, code(body))
st, body, _ = call("GET", "/api/users/me")
show("GET /me with no token", st, code(body))

print("\n--- friends: request, inbox, accept ---")
st, body, _ = call("POST", "/api/friends/requests", outsider, body={"userId": outsider_id})
show("add self", st, code(body))
st, body, _ = call("POST", "/api/friends/requests", outsider, body={"userId": 99999})
show("add missing user", st, code(body))
st, body, _ = call("POST", "/api/friends/requests", outsider, body={"userId": other_id})
show("sora -> kaede", st, body)
st, body, _ = call("GET", "/api/friends", outsider)
show("sora's friends (pending)", st, [u["userName"] for u in body])
st, inbox, _ = call("GET", "/api/friends/requests", other)
show("kaede's inbox", st, inbox)
st, body, _ = call("GET", "/api/friends/requests", outsider)
show("sora's own inbox", st, body)
request_id = inbox[0]["id"]
st, body, _ = call("POST", "/api/friends/requests/%d/accept" % request_id, outsider)
show("sora accepts own request", st, code(body))
st, body, _ = call("POST", "/api/friends/requests/%d/accept" % request_id, other)
show("kaede accepts", st, body)
st, body, _ = call("POST", "/api/friends/requests/%d/accept" % request_id, other)
show("kaede accepts twice", st, body)
st, body, _ = call("GET", "/api/friends", outsider)
show("sora's friends", st, [u["userName"] for u in body])
st, body, _ = call("GET", "/api/friends", other)
show("kaede's friends", st, [u["userName"] for u in body])
st, body, _ = call("POST", "/api/friends/requests", outsider, body={"userId": other_id})
show("sora -> kaede again", st, code(body))

print("\n--- mutual request auto-accepts ---")
third, third_id = register("hina", "ひな")
st, body, _ = call("POST", "/api/friends/requests", outsider, body={"userId": third_id})
show("sora -> hina", st, body)
st, body, _ = call("POST", "/api/friends/requests", third, body={"userId": outsider_id})
show("hina -> sora (mutual)", st, body)
st, body, _ = call("GET", "/api/friends", third)
show("hina's friends", st, [u["userName"] for u in body])
st, body, _ = call("GET", "/api/friends/requests", third)
show("hina's inbox now empty", st, body)

print("\n--- addMember ---")
st, body, _ = call("POST", "/api/albums/%d/members" % album_id, nao,
                   body={"userId": outsider_id})
show("nao adds sora", st, body)
st, body, _ = call("POST", "/api/albums/%d/members" % album_id, nao,
                   body={"userId": outsider_id})
show("adds sora again", st, body)
st, body, _ = call("GET", "/api/albums/%d" % album_id, outsider)
show("sora can now read it", st, {"myRole": body["myRole"], "memberNum": body["memberNum"]})
st, page, _ = call("GET", "/api/albums", outsider)
show("on sora's home", st, {"total": page["total"],
                            "titles": [a["title"] for a in page["items"]]})
st, body, _ = call("POST", "/api/albums/%d/members" % album_id, nao, body={"userId": 99999})
show("add missing user", st, code(body))

print("\n--- rename reaches the follow copies ---")
st, body, _ = call("PATCH", "/api/users/me", outsider,
                   body={"userName": "そらそら", "userProfile": "3年B組"})
show("sora renames", st, {"userName": body["userName"], "userProfile": body["userProfile"]})
st, album, _ = call("GET", "/api/albums/%d" % album_id, nao)
names = [m["userName"] for m in album["members"]]
show("album_member.userName", st, names, "<- follow")
st, body, _ = call("GET", "/api/friends", other)
show("friend.friendUserName", st, [u["userName"] for u in body], "<- via users, follow")
st, body, _ = call("PATCH", "/api/users/me", outsider, body={"userName": "  "})
show("blank name refused", st, code(body))
st, body, _ = call("PATCH", "/api/users/me", outsider, body={"userProfile": ""})
show("blank profile clears it", st, body["userProfile"])
