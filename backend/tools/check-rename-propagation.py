# -*- coding: utf-8 -*-
"""
Renames a user who owns things, then reports which copies of the name moved.

The name is denormalised onto eight tables. Four follow the source and four are
snapshots of who did something at the time they did it. Getting this wrong in
either direction is invisible until someone renames themselves, which nobody
does while testing, so it is checked here on purpose.

Prints the new name's position in each table; verify with

    docker exec hanamizuki-mysql mysql -uhanamizuki -phanamizuki hanamizuki \\
      -e "source tools/check-rename-propagation.sql"
"""
import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
NEW_NAME = "なおなお"


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
        return e.code, (json.loads(raw) if raw else raw)


_, body = call("POST", "/api/auth/login",
               body={"userAccount": "nao", "userPassword": "password"})
tok = body["accessToken"]
old_name = body["user"]["userName"]
print("nao is currently:", old_name)

# A decoration writes album_photo.overlayUserName, which the DDL marks as a
# snapshot. Without one the "snapshots do not move" check has no rows and
# passes for the wrong reason.
_, page = call("GET", "/api/albums", tok)
album_id = page["items"][0]["id"]
_, album = call("GET", "/api/albums/%d" % album_id, tok)
photo = album["photos"][0]
st, body = call("PUT", "/api/albums/%d/photos/%d/decoration" % (album_id, photo["id"]),
                tok,
                body={"elements": [{"type": "stroke", "color": "#ff0000", "width": 4,
                                    "points": [[0.1, 0.1], [0.4, 0.4]]}]},
                headers={"If-Match": str(photo["version"])})
print("drew a stroke ->", st, "(writes overlayUserName as a snapshot)")

st, body = call("PATCH", "/api/users/me", tok, body={"userName": NEW_NAME})
print("renamed ->", st, body.get("userName") if isinstance(body, dict) else body)
print()
print("now run the SQL beside this file; every 'follow' row must be 1/1")
print("and every 'snapshot' row must be 0 out of its row count.")
