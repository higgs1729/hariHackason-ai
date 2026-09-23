# tools

Scripts that check things a unit test cannot, because they need MySQL.

Both expect the app running on :8080 against the Docker MySQL, with
`POST /api/dev/seed` already called.

```
docker compose up -d                 # repo root
./mvnw spring-boot:run               # backend/
curl -X POST localhost:8080/api/dev/seed
python tools/check-endpoints.py
python tools/check-album-version.py
```

## check-endpoints.py

Walks every route the frontend contract declares, including the error cases:
missing `If-Match`, a stale one, a non-member reading someone else's album, an
invalid weather value, adding yourself as a friend.

## check-album-version.py

Prints the `@Version` delta for each kind of album PATCH.

Every line has to read `+1`. A title change also rewrites the follow copy on
`album_member`, and doing that before the album is flushed detaches the entity
and costs a second flush — `+2` on a title edit, `+1` on a summary edit.

`AlbumPatchVersionTest` asserts the same thing but runs on H2, where **both
orderings give +1**. This script is the only check that distinguishes them.

## check-shoot-hint.py

Walks `POST /api/hints/shoot` — group sizes, junk input, the rate limit.

With no `ANTHROPIC_API_KEY` every call returns the canned text, so what this
mostly proves is that the fallback is reachable, correctly shaped, and varies
with the group size. **That is the path every demo takes until the key
exists**, which is what makes it worth walking. Re-run with a key set and
`aiGenerated` flips to 1.

Registers its own accounts each run: the walk spends about a dozen calls, and
reusing one account meant the second run was rate-limited by the first.

## check-upload-and-generate.py

Upload → dedup → auto-album, the half of the demo the seed skips past.

Uploads images the server has not seen (the seed's own files, plus a few bytes
after the JPEG end-of-image marker, so the hash differs and the picture does
not), re-uploads the same bytes to confirm `sha256` dedup returns the same
rows, replays the `Idempotency-Key` to confirm it returns the same job rather
than starting a second run, then polls the job to `READY`.

Without an `ANTHROPIC_API_KEY` the album comes back with a rule-based title
and `aiGenerated: 0`. That is the fallback working, not a failure.
