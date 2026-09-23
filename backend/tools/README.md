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
