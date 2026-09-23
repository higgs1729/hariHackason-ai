# 07 — Screens beyond the poster

> The poster (`image-1.png`) has 8 screens. The API has more than those 8 use.
> This file records how the missing screens fit in. Decided 2026-09-23 by the
> frontend owner; all items were taken as proposed.

## Decisions

| # | Item | Decision |
| --- | --- | --- |
| 1 | Hub | New **My page** at `/me`. Home's "続ける" and login land here instead of the camera. Three tabs: アルバム / カプセル / 友達 (`?tab=` keeps the tab across reloads). A floating camera button is always shown. The poster flow from the camera onward is unchanged. |
| 2 | Lists | Albums: 2-column grid of cover thumb + title + date → Detail. Capsules: one row each with sealed/opened, open date and days left. Sealed → capsule screen (07); opened → Detail with `?capsule=`. |
| 3 | Shoot hint (row 7) | Button "AIに撮り方を聞く" on the Camera screen opens a bottom sheet. Inputs: member count and a mood chip; place is optional; names are not asked. Shows `hint` and `poses[]`. |
| 4 | QR friend add (rows 8) | Built now, working against the mock only. Two libraries: one to draw the QR, one to read it from the camera. The backend endpoints (05 §3.2) stay at the backend's P1; see below. |
| 5 | Reunion mode (row 10) / members-present capsule (row 12) | Reunion mode is built and already works in `http` mode (see below). Row 12 is **not** built: it is a rule, not a screen, and costs a column plus a new 409. |
| 6 | Search / request / accept friends | **Not built.** QR makes both sides friends at once; the http demo uses the seeded friends. |
| 7 | Edit title / profile | Album title is edited inline on Detail, the same way as the comment (`PATCH /api/albums/{id}` with `If-Match`). Profile: the display name only, edited on My page (`PATCH /api/users/me`). |

## Order and branches

One branch per item, each stacked on the previous one because later items live
inside My page. Pushed directly; merging into `main` is the owner's call.

1. `feat/my-page` — hub, album list, capsule list, friends list
2. `feat/shoot-hint` — Camera bottom sheet
3. `feat/edit-title-profile` — title on Detail, name on My page
4. `feat/qr-friends` — QR show / scan (mock only), reunion mode

## Asks for the backend

Shapes are the ones fixed in 05 §3.2; nothing new is asked. The backend's
"do not build the QR screens early" (05 §3.2) is respected on its side: the
frontend runs against the mock, so the backend can keep QR at P1.

| Endpoint | For | Status | Frontend assumes |
| --- | --- | --- | --- |
| `POST /api/friends/qr` → `{qrToken, expireTime}` | QR show | not built | QR encodes `ai://friend/{qrToken}`; a new token is fetched when `expireTime` passes |
| `POST /api/friends/qr/{token}/accept` | QR scan | not built | **returns the QR owner as `User`** (05 does not say; tell me if it is 204). 410 = expired or used; the UI branches on the status, not the code |
| `GET /api/albums?memberId={userId}` | reunion mode | not built, **optional** | the frontend also filters by each album's `members`, so reunion mode already works in `http` mode; the parameter only saves requests |

Until the QR endpoints exist, `http` mode gets Spring's 404 `NOT_FOUND` and
the screen shows 「この機能はまだ準備中です」.

## Mock-only demo data

- User `sora` (id 16) is not a friend of `nao`. Entering `sora-demo` in the
  scan screen's code field adds her, so the flow can be tried on one device.
- `ayaka` / `miki` / `rin` are members of the seeded albums, so reunion mode
  has photos to play.
