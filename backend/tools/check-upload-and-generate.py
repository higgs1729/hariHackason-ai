# -*- coding: utf-8 -*-
"""Upload then auto-album: the half of the demo the seed skips over."""
import glob
import json
import os
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://localhost:8080"
CRLF = "\r\n"


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
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def upload(token, paths):
    boundary = uuid.uuid4().hex
    chunks = []
    for path in paths:
        with open(path, "rb") as handle:
            blob = handle.read()
        head = ("--" + boundary + CRLF
                + 'Content-Disposition: form-data; name="files"; filename="'
                + os.path.basename(path) + '"' + CRLF
                + "Content-Type: image/jpeg" + CRLF + CRLF)
        chunks.append(head.encode("utf-8") + blob + CRLF.encode("utf-8"))
    payload = b"".join(chunks) + ("--" + boundary + "--" + CRLF).encode("utf-8")

    req = urllib.request.Request(BASE + "/api/photos", data=payload, method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req) as r:
        return r.status, json.loads(r.read().decode("utf-8"))


_, auth = call("POST", "/api/auth/login",
               body={"userAccount": "nao", "userPassword": "password"})
tok = auth["accessToken"]

# Genuinely new images, not the seed's own files. Now that the seed stores a
# sha256, re-uploading those dedups straight back to rows that are already in
# albums -- correct behaviour, and nothing left to auto-album.
#
# A few bytes after the JPEG's end-of-image marker change the hash without
# touching the picture; decoders ignore trailing data.
import tempfile

source = sorted(glob.glob("storage*/photos/seed/*.jpg"))[:6]
workdir = tempfile.mkdtemp()
files = []
for n, src in enumerate(source):
    with open(src, "rb") as f:
        blob = f.read()
    dst = os.path.join(workdir, "shot-%d.jpg" % n)
    with open(dst, "wb") as f:
        f.write(blob + os.urandom(16))
    files.append(dst)

print("uploading %d files" % len(files))
st, result = upload(tok, files)
print("upload            ", st, "%d accepted, %d rejected"
      % (len(result["uploaded"]), len(result["rejected"])))
for bad in result["rejected"]:
    print("   rejected:", bad)

# A second upload of the same bytes: sha256 already on file, same rows back.
st, again = upload(tok, files)
print("same bytes again  ", st, "%d accepted" % len(again["uploaded"]),
      "same ids:", [p["id"] for p in again["uploaded"]]
      == [p["id"] for p in result["uploaded"]])

st, photos = call("GET", "/api/photos?unassigned=true", tok)
print("unassigned        ", st, photos["total"])

ids = [p["id"] for p in photos["items"]]
key = "demo-" + str(int(time.time()))
st, job = call("POST", "/api/albums/generate", tok, body={"photoIds": ids},
               headers={"Idempotency-Key": key})
print("generate          ", st, "job", job.get("jobId"))

# Replaying the key must return the same job, not start a second run.
st, replay = call("POST", "/api/albums/generate", tok, body={"photoIds": ids},
                  headers={"Idempotency-Key": key})
print("replayed key      ", st, "same job:", replay.get("jobId") == job.get("jobId"))

status = None
for _ in range(60):
    st, status = call("GET", "/api/albums/jobs/%d" % job["jobId"], tok)
    if status["status"] in ("READY", "FAILED"):
        break
    time.sleep(0.5)
print("job               ", st, status["status"], "progress", status["progress"],
      "albums", status["albumIds"])
if status.get("errorMsg"):
    print("   errorMsg:", status["errorMsg"])

for album_id in status["albumIds"]:
    _, album = call("GET", "/api/albums/%d" % album_id, tok)
    print("   album %d: %s / %d photos / ai=%d / %s"
          % (album_id, album["title"], len(album["photos"]),
             album["aiGenerated"], album["albumDate"]))
