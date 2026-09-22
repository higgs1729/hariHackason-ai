# 04 — Frontend Handoff / フロント引き継ぎ

> What the React frontend already calls, what it assumes, and what the new
> feature list needs that `03-detailed-design.md` does not cover yet.
> Frontend → backend, 2026-09-22.
> Prerequisite: `03-detailed-design.md` §3–§4. This document does **not**
> restate the API; it only lists where the frontend depends on it.

---

## 0. How to run against the frontend

| Step | Command / setting |
| --- | --- |
| Install | `npm install` in `frontend/` |
| Mock mode (no backend) | `npm run dev` — with no `VITE_API_MODE` set, dev builds default to `mock` (`frontend/src/api/index.ts`). `.env.*` is git-ignored; copy `.env.example` if you want it explicit |
| Real backend | `VITE_API_MODE=http npm run dev` — Vite proxies `/api/*` → `http://localhost:8080` (`vite.config.ts`) |
| Demo build | `npm run build` → `frontend/dist/`; Spring serves it as the SPA (02-basic-design §7) |

Mock credentials: `nao` / `password` (ids 12–15 match `/api/dev/seed`).
Every screen goes through one `Api` interface (`frontend/src/api/contract.ts`);
`http.ts` and `mock.ts` are the two implementations. **If the backend response
shape differs from `frontend/src/api/types.ts`, changing `types.ts` + `http.ts`
is enough — screens never call `fetch`.**

## 1. Endpoints the frontend already calls

Marked P as in §4. "As-is" means the frontend follows the spec literally and
needs nothing beyond what is written there.

| Screen | Endpoint | P | Status |
| --- | --- | --- | --- |
| 01 Home | `POST /api/auth/register`, `/login`, `/refresh`, `/logout` | P0 | as-is |
| all | `GET /api/users/me` | P0 | as-is |
| 02 Camera | `POST /api/photos` multipart `files` | P0 | as-is; reads `uploaded[]` / `rejected[]` |
| 03 AlbumCreate | `GET /api/photos?unassigned=true&limit=` | P0 | as-is; `Page<Photo>` |
| 03 AlbumCreate | `POST /api/albums/generate` + `Idempotency-Key` | P0 | as-is; 202 `{jobId}`, 409 `JOB_ALREADY_RUNNING` shown |
| 03b AlbumGenerating | `GET /api/albums/jobs/{id}` | P0 | polls every 1.5 s until `READY` / `FAILED` |
| 04 Decorate, 05, 08 | `GET /api/albums/{id}` (+ `ETag`) | P0 | as-is; uses `photos[].id` = `album_photo.id` |
| 04 Decorate | `GET/PUT …/decoration` (+ `If-Match`) | P0 | as-is; on 409 `VERSION_CONFLICT` retries once with `details.current` |
| 04 Decorate | `POST …/decoration/rendered` (`overlay.png`, `composite.jpg`) | P0 | as-is; best-effort, failure does not block |
| 05 Share | `GET /api/friends` | P0 | as-is |
| 05 Share | `POST /api/albums/{id}/members` | P1 | as-is; one call per picked friend |
| 05 Share | `POST /api/albums/{id}/share` | P0 | as-is; `shareUrl` → `navigator.share`, clipboard fallback |
| 06 CapsuleCreate | `POST /api/capsules` | P1 | as-is; `openTime` is ISO-8601 with local offset |
| 07 CapsuleDone | `GET /api/capsules/{id}`, `POST …/open` | P1 | as-is; SEALED / OPENED DTOs |
| 07 CapsuleDone | `POST …/unseal-now` | P1 dev | called only when `/open` returns `CAPSULE_NOT_YET_OPEN` **and** the build is dev/mock |
| 08 Detail | `PATCH /api/albums/{id}/photos/{albumPhotoId}` (+ `If-Match`) | P1 | as-is; edits `photoComment` |

Not called yet (declared in `contract.ts`, no screen): `PATCH /users/me`,
`GET /users/{id}`, `GET /users/search`, `POST /friends/requests`,
`…/accept`, `GET /albums`, `PATCH /albums/{id}`, `GET /albums/{id}/share`,
`GET /capsules`. Nothing under §4.9 Notifications or §4.10 Ops.

## 2. Assumptions to confirm

Each of these is implemented one way in the frontend. If the backend does it
differently, say so and I change the frontend — none of them are hard to move.

| # | Assumption | Where | If wrong |
| --- | --- | --- | --- |
| A-1 | `PUT …/decoration` and `PATCH …/photos/{albumPhotoId}` return the **new** `ETag` on their own response | `http.ts` `etagVersion()` | Frontend would re-`GET` after every save |
| A-2 | `PATCH …/photos/{albumPhotoId}` responds with the updated `AlbumPhoto` row | `Detail.tsx` | Frontend re-`GET`s the album instead |
| A-3 | `GET …/decoration` on a photo that has never been decorated returns `200 {elements: []}` with `ETag: "0"`, not 404 | `Decorate.tsx` | Add a 404 branch |
| A-4 | Refresh token lives in `localStorage` (`ai.refreshToken`); access token in memory only | `tokens.ts` | 02-basic-design §6.1 says "Secure storage". An HttpOnly cookie would be stricter but needs `SameSite` + CSRF handling on both sides; `localStorage` is the simplest thing that works before 9/27. Switching later touches only `tokens.ts` |
| A-5 | `401 TOKEN_EXPIRED` is the **only** 401 that triggers a refresh; any other 401 logs the user out | `http.ts` | If the backend uses a different code for expiry, tell me the code |
| A-6 | Numeric ids appear in routes (`/album/55/decorate/901`) | `routes.ts` | Acceptable per §1.1.2: every route is behind login, and the backend enforces ownership (403 `ALBUM_FORBIDDEN`) |
| A-7 | `Idempotency-Key` is a fresh UUID **per tap** of "AIでまとめる". There is no client-side auto-retry yet; a second tap after a failure sends a new key | `AlbumCreate.tsx` | If you want replay on retry, I keep the key in state until the job is accepted |
| A-8 | Job polling at 1.5 s is fine for a `20 / user / day` rate limit (§3.3 only limits `/generate`, not `/jobs/{id}`) | `AlbumGenerating.tsx` | Say if `/jobs/{id}` is rate-limited too |
| A-9 | HEIC from the iOS file picker: the frontend sends whatever the picker gives. Q-2 in `01-requirements.md` is still open | `Camera.tsx` | If the backend rejects HEIC, it must come back in `rejected[]` with `UNSUPPORTED_MEDIA` so the UI can say "JPEGで撮り直して" |
| A-10 | `capsuleMsg` is shown once, on the Detail screen right after `/open`, via router state. It is **not** fetched again | `CapsuleDone.tsx` → `Detail.tsx` | If the album should remember it, add `capsuleMsg` to the OPENED capsule list |
| A-11 | `navigator.share` receives `shareUrl` only (no files). AirDrop of the composite JPEG would need `GET …/composite` to send `Access-Control-Allow-Origin` or same-origin, which it is behind the proxy | `Share.tsx` | — |

## 3. Endpoints the new feature list needs (not in 03)

From the 必要な機能 triage on Notion (2026-09-22). Numbers are the rows there.
Priorities are the frontend's proposal; nothing here is built yet on either side.

### 3.1 Row 7 — プリクラ shoot hint (P0, the AI moment)

The user types how many people and who; the app answers "こんな動画を撮ろう".
One Claude call, no persistence needed.

| | Path | Request → Response |
| --- | --- | --- |
| POST | `/api/hints/shoot` | `{memberCount, memberNames[], place?, mood?}` → `{hint, poses[]}` |

`hint` is one sentence; `poses` is 2–3 short strings. Rate limit like
`/generate`. The frontend shows this on the Camera screen before the shutter.

### 3.2 Rows 8 / 10 / 12 — QR instead of proximity (P1)

Web Bluetooth and Web NFC are unavailable on iOS Safari (verified against MDN
browser-compat data on 2026-09-22), so "近くのスマホ" becomes "QR を見せ合う".

**QR payload — please decide together.** Two options:

| Option | Payload | Pros | Cons |
| --- | --- | --- | --- |
| a | `ai://friend/{userId}` | trivial | anyone who sees the QR once can add you forever (§1.1.2 — ids are enumerable) |
| b | `ai://friend/{token}` where token = `SecureRandom` 16 bytes, 10-minute TTL, one row in a new `friend_qr` table | can't be replayed | one table + one endpoint more |

**Decided 2026-09-22: option b.** The QR is shown full-screen so the token is
only ever read in person. Endpoints:

| | Path | Notes |
| --- | --- | --- |
| POST | `/api/friends/qr` | → `{token, expiresAt}`; frontend renders QR client-side |
| POST | `/api/friends/qr/{token}/accept` | scanner calls this → both rows `status=1` immediately (no request/accept round trip) |

Row 10 再会モード needs no new endpoint: after a scan, the frontend calls
`GET /api/albums?memberId={friendUserId}` — **one new query param** on the
existing list — and plays those albums' composites as a slideshow.

Row 12 "friends must be present to open the capsule": one new column and one
new rule.

| Change | Detail |
| --- | --- |
| `capsule.requiredUserIds` (json, nullable) | set at `POST /api/capsules` |
| `POST /api/capsules/{id}/open` body | `{presentTokens: []}` — QR tokens scanned in the last 10 min; server checks every required user is among them, else 409 `CAPSULE_MEMBERS_MISSING` (new code, `details.missing: [userId]`) |

### 3.3 Row 3 — 5-second voice memo (P2, only if time remains)

Would need `album_photo.audioPath`, `POST …/photos/{albumPhotoId}/audio`
(multipart `file`, `audio/mp4` from iOS `MediaRecorder`), and `audioUrl` on
`AlbumPhoto`. The frontend has no screen for it. Proposal: **do not build
before 9/26**; if cut, nothing else depends on it.

### 3.4 Row 1 — video

`POST /api/photos` currently rejects `movie.mov` (§4.4 example). Accepting
video means thumbnails, no EXIF `takenTime` (use `creationTime` from the
container or the upload time), and clustering treating it as a photo. The
frontend can already record video via `MediaRecorder`; I will hold it until
photo flow is stable on both sides. If we do it, `Photo` needs `mediaType:
'photo' | 'video'` and `durationSec`.

## 4. Error display

The frontend maps `error.code` to Japanese text in `frontend/src/state/useAsync.ts`
(`describeError`). Any **new** code you add needs a line there; unknown codes
fall back to `message`, so nothing crashes — it just reads like a log line.

## 5. What I need from you first

1. Answer A-1, A-3, A-5 (they decide how much re-fetching the frontend does).
2. `POST /api/auth/*` + `GET /api/albums/{id}` + `GET/PUT …/decoration` working
   on `:8080` with `/api/dev/seed`. With those four the whole demo path runs
   in `http` mode; everything else can stay mock a few more days.
3. `POST /api/friends/qr` + `…/qr/{token}/accept` (§3.2) whenever you get to
   friends; the frontend will build the QR screen against that shape.
