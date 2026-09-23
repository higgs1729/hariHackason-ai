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
| 4 | QR friend add (rows 8) | Built now, working against the mock only. Two libraries: one to draw the QR, one to read it from the camera. Backend endpoints (`POST /api/friends/qr`, `POST /api/friends/qr/{token}/accept`, 04 §3.2) are requested from the backend. |
| 5 | Reunion mode (row 10) / members-present capsule (row 12) | Reunion mode is built against the mock (needs `GET /api/albums?memberId=` on the backend). Row 12 is **not** built: it is a rule, not a screen, and costs a column plus a new 409. |
| 6 | Search / request / accept friends | **Not built.** QR makes both sides friends at once; the http demo uses the seeded friends. |
| 7 | Edit title / profile | Album title is edited inline on Detail, the same way as the comment (`PATCH /api/albums/{id}` with `If-Match`). Profile: the display name only, edited on My page (`PATCH /api/users/me`). |

## Order and branches

One branch per item, each stacked on the previous one because later items live
inside My page. Pushed directly; merging into `main` is the owner's call.

1. `feat/my-page` — hub, album list, capsule list, friends list
2. `feat/shoot-hint` — Camera bottom sheet
3. `feat/edit-title-profile` — title on Detail, name on My page
4. `feat/qr-friends` — QR show / scan, reunion mode (mock only)

## Asks for the backend

| Endpoint | For | Status |
| --- | --- | --- |
| `POST /api/friends/qr` → `{token, expiresAt}` | QR show | schema only (05 §3.2) |
| `POST /api/friends/qr/{token}/accept` → friend `User` | QR scan | schema only |
| `GET /api/albums?memberId={userId}` | reunion mode | not started |

Until these exist, the QR and reunion screens show a short "準備中" note in
`http` mode instead of failing.
