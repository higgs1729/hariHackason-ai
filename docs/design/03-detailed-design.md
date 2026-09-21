# 03 — Detailed Design / 詳細設計

> 実装に落とせる粒度。テーブル定義・API仕様・アルゴリズム・設定・テスト。
> 前提: `01-requirements.md`, `02-basic-design.md`

---

## 1. Database schema

> テーブル定義。H2 file mode。`ddl-auto=update` で開発し、DDLは記録用。

### 1.1 Tables

> 全12テーブル。UUIDは`CHAR(36)`、日時は`TIMESTAMP WITH TIME ZONE`。

**`users`**

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | CHAR(36) | PK |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL |
| `password_hash` | VARCHAR(60) | NOT NULL (BCrypt) |
| `display_name` | VARCHAR(32) | NOT NULL |
| `avatar_path` | VARCHAR(255) | NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `deleted_at` | TIMESTAMPTZ | NULL |

`INDEX idx_users_email (email)`

**`refresh_tokens`**

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | CHAR(36) | PK |
| `user_id` | CHAR(36) | FK → users |
| `token_hash` | CHAR(64) | UNIQUE (SHA-256) |
| `family_id` | CHAR(36) | NOT NULL — ローテーション系列 |
| `expires_at` | TIMESTAMPTZ | NOT NULL |
| `revoked_at` | TIMESTAMPTZ | NULL |
| `user_agent` | VARCHAR(255) | NULL |

`INDEX idx_rt_user (user_id)`, `INDEX idx_rt_family (family_id)`

**`friendships`** — 承認時に双方向2行を書く

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | CHAR(36) | PK |
| `user_id` | CHAR(36) | FK → users |
| `friend_id` | CHAR(36) | FK → users |
| `status` | VARCHAR(16) | `PENDING` / `ACCEPTED` |
| `requested_by` | CHAR(36) | FK → users |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `responded_at` | TIMESTAMPTZ | NULL |

`UNIQUE (user_id, friend_id)`, `INDEX idx_fs_user_status (user_id, status)`

**`blocks`** — `UNIQUE (user_id, blocked_user_id)`

**`photos`**

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | CHAR(36) | PK |
| `owner_id` | CHAR(36) | FK → users |
| `storage_path` | VARCHAR(255) | NOT NULL |
| `sha256` | CHAR(64) | NOT NULL |
| `width` / `height` | INT | NOT NULL (回転補正後) |
| `bytes` | BIGINT | NOT NULL |
| `taken_at` | TIMESTAMPTZ | NOT NULL |
| `taken_at_source` | VARCHAR(8) | `EXIF` / `UPLOAD` |
| `lat` / `lng` | DOUBLE | NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `deleted_at` | TIMESTAMPTZ | NULL |

`UNIQUE (owner_id, sha256)`, `INDEX idx_photos_owner_taken (owner_id, taken_at)`

**`albums`**

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | CHAR(36) | PK |
| `title` | VARCHAR(64) | NOT NULL |
| `summary` | VARCHAR(255) | NULL |
| `cover_photo_id` | CHAR(36) | FK → photos, NULL |
| `date` | DATE | NOT NULL |
| `place` | VARCHAR(64) | NULL |
| `ai_generated` | BOOLEAN | NOT NULL |
| `version` | INT | NOT NULL DEFAULT 0 — `@Version` |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `deleted_at` | TIMESTAMPTZ | NULL |

**`album_members`** — `UNIQUE (album_id, user_id)`, `role` ∈ `OWNER`/`EDITOR`/`VIEWER`

**`album_photos`**

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | CHAR(36) | PK |
| `album_id` | CHAR(36) | FK → albums |
| `photo_id` | CHAR(36) | FK → photos |
| `position` | INT | NOT NULL |
| `caption` | VARCHAR(16) | NULL |
| `place` | VARCHAR(64) | NULL |
| `weather` | VARCHAR(8) | NULL |
| `comment` | VARCHAR(255) | NULL |
| `music` | VARCHAR(128) | NULL |
| `version` | INT | NOT NULL DEFAULT 0 |

`UNIQUE (album_id, photo_id)`, `INDEX idx_ap_album_pos (album_id, position)`

**`decorations`**

| Column | Type | Constraint |
| --- | --- | --- |
| `album_photo_id` | CHAR(36) | PK, FK → album_photos |
| `elements_json` | CLOB | NOT NULL |
| `rendered_path` | VARCHAR(255) | NULL |
| `revision` | INT | NOT NULL DEFAULT 0 |
| `updated_by` | CHAR(36) | FK → users |
| `updated_at` | TIMESTAMPTZ | NOT NULL |

**`generate_jobs`**

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | CHAR(36) | PK |
| `user_id` | CHAR(36) | FK → users |
| `status` | VARCHAR(16) | §3.1 の状態 |
| `progress` | INT | 0–100 |
| `photo_ids` | CLOB | JSON配列 |
| `album_ids` | CLOB | JSON配列、NULL |
| `error_code` | VARCHAR(32) | NULL |
| `idempotency_key` | CHAR(36) | NOT NULL |
| `created_at` / `finished_at` | TIMESTAMPTZ | |

`UNIQUE (user_id, idempotency_key)`

**`shares`**

| Column | Type | Constraint |
| --- | --- | --- |
| `token` | VARCHAR(24) | PK (base64url, 128bit) |
| `album_id` | CHAR(36) | FK → albums, UNIQUE |
| `created_by` | CHAR(36) | FK → users |
| `expires_at` | TIMESTAMPTZ | NULL |
| `view_count` | INT | NOT NULL DEFAULT 0 |
| `revoked_at` | TIMESTAMPTZ | NULL |

**`capsules`** — `album_id` UNIQUE, `open_at` NOT NULL, `opened_at` NULL,
`message` VARCHAR(500)

**`capsule_recipients`** — `UNIQUE (capsule_id, user_id)`

**`notifications`** — `type`, `payload_json`, `read_at`;
`INDEX idx_nt_user_read (user_id, read_at)`

### 1.2 Soft delete

> 論理削除。`deleted_at IS NULL` を必ず条件に入れる。

`users` / `photos` / `albums` は論理削除。JPA では `@Where(clause = "deleted_at
is null")` を付け、物理削除は30日後のバッチ（実装しない。設計上の宣言のみ）。

---

## 2. Class responsibilities

> 主要クラスの責務。1クラス1責務、serviceにトランザクション境界を置く。

| Class | Responsibility |
| --- | --- |
| `JwtFilter` | `Authorization` を検証し `SecurityContext` に載せる |
| `JwtIssuer` | access/refresh の発行・検証・ローテーション |
| `GlobalExceptionHandler` | 例外 → `ApiError` の一元変換 |
| `PhotoService` | 取込（回転・EXIF抽出・ハッシュ・サムネ）、一覧、削除 |
| `ExifReader` | `DateTimeOriginal` / GPS / `Orientation` の抽出のみ |
| `ImageProcessor` | 回転・縮小・EXIF除去・合成。状態を持たない |
| `PhotoClusterer` | 撮影時刻からクラスタ分割。**純関数、DBに触らない** |
| `AlbumGenerationService` | ジョブの状態遷移。`@Async` の入口 |
| `ClaudeAlbumEnricher` | Claude呼び出しと結果の検証。失敗時は例外を投げるだけ |
| `AlbumService` | アルバムCRUD、メンバー、楽観ロック |
| `DecorationService` | 要素JSONとレンダリング済みPNGの保存 |
| `ShareService` | トークン発行・失効、公開DTOへの射影 |
| `ShareViewController` | `/s/{token}` のSSR（Thymeleaf） |
| `CollageRenderer` | OG画像 1200x630 の生成 |
| `CapsuleService` | 封印・開封。**開封前は中身を組み立てない** |
| `AlbumEventPublisher` | SSE配信（`SseEmitter` の保持と送出） |

`PhotoClusterer` を純関数にするのは、ここが唯一まともに単体テストできる
ロジックだから。DBもAIも要らない。

---

## 3. Common specifications

> 共通仕様。全APIが従う。

### 3.1 Conventions

| 項目 | 仕様 |
| --- | --- |
| ID | UUID v4 文字列。`AlbumPhoto` のみ JSON上は `ap_` 接頭辞 |
| 日時 | ISO-8601 オフセット付き `2026-09-20T17:30:00+09:00` |
| 認証 | `Authorization: Bearer <jwt>`。`/api/auth/**`, `/api/health`, `/s/**`, `/og/**`, `/api/share/**` は不要 |
| ページング | `?limit=20&cursor=` → `{items, nextCursor, total}`。max 100 |
| 楽観ロック | `Album` / `AlbumPhoto` / `Decoration` は `ETag` + `If-Match` 必須。未指定は 428 |
| 冪等性 | `POST /api/photos`, `POST /api/albums/generate` は `Idempotency-Key` 必須 |
| アップロード | `multipart/form-data`, field `files`, 10MB/枚, 20枚/回 |

### 3.2 Error codes

> エラーコード一覧。フロントはこの`code`で分岐する。

```json
{ "code": "ALBUM_FORBIDDEN", "message": "…", "details": null, "traceId": "b3f1…" }
```

| HTTP | code | 発生条件 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 入力不正。`details` にフィールド別メッセージ |
| 400 | `UNSUPPORTED_MEDIA` | JPEG/PNG/HEIC 以外 |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | ヘッダ欠落 |
| 401 | `TOKEN_EXPIRED` | access期限切れ → refreshして再送 |
| 401 | `TOKEN_INVALID` | 不正・失効 → ログアウト |
| 401 | `CREDENTIALS_INVALID` | ログイン失敗 |
| 403 | `ALBUM_FORBIDDEN` | メンバーでない |
| 403 | `ROLE_INSUFFICIENT` | VIEWER が書き込み |
| 403 | `NOT_FRIENDS` | 友達でないユーザーを招待 |
| 403 | `USER_BLOCKED` | ブロック関係 |
| 404 | `USER_NOT_FOUND` / `PHOTO_NOT_FOUND` / `ALBUM_NOT_FOUND` / `CAPSULE_NOT_FOUND` | |
| 409 | `VERSION_CONFLICT` | `If-Match` 不一致。`details.current` に現在値 |
| 409 | `FRIEND_REQUEST_EXISTS` | 申請重複 |
| 409 | `CAPSULE_NOT_YET_OPEN` | `now < openAt` |
| 409 | `JOB_ALREADY_RUNNING` | 同一ユーザーの生成が実行中 |
| 410 | `SHARE_REVOKED` / `SHARE_EXPIRED` | 死んだトークン |
| 413 | `FILE_TOO_LARGE` | 10MB超 |
| 422 | `NO_VALID_PHOTOS` | クラスタリング対象が0件 |
| 428 | `IF_MATCH_REQUIRED` | 楽観ロック対象で `If-Match` 未指定 |
| 429 | `RATE_LIMITED` | `Retry-After` 付与 |

### 3.3 Rate limits

| Route | Limit |
| --- | --- |
| `POST /api/albums/generate` | 20 / user / day、同時1件 |
| `POST /api/photos` | 200 files / user / hour |
| `POST /api/auth/login` | 10 / IP / 5min |
| その他 | 120 / user / min |

---

## 4. API specification

> API仕様。P0=デモ必須、P1=余裕があれば、P2=切ってよい。

### 4.1 Auth `/api/auth`

| | Path | P | Request → Response |
| --- | --- | --- | --- |
| POST | `/signup` | P0 | `{email,password,displayName}` → `{accessToken,refreshToken,user}` |
| POST | `/login` | P0 | `{email,password}` → 同上 |
| POST | `/refresh` | P0 | `{refreshToken}` → 新しいペア（ローテーション） |
| POST | `/logout` | P1 | `{refreshToken}` → 204 |
| POST | `/logout-all` | P2 | → 204 |
| GET | `/me` | P0 | → `UserSelf` |
| POST | `/password` | P2 | `{currentPassword,newPassword}` → 204、全セッション失効 |

パスワードは8文字以上。BCrypt cost **10**（12はデモ機で体感できるほど遅い）。

### 4.2 User `/api/users`

| | Path | P | Notes |
| --- | --- | --- | --- |
| GET | `/me` | P0 | → `UserSelf`（`email` を含む） |
| PATCH | `/me` | P1 | `{displayName}` |
| PUT | `/me/avatar` | P1 | multipart `file` → `{avatarUrl}` |
| DELETE | `/me/avatar` | P2 | |
| GET | `/me/stats` | P2 | `{albumCount,photoCount,capsuleCount,friendCount}` |
| GET | `/{id}` | P1 | → `User`（公開項目のみ） |
| GET | `/search?q=` | P1 | → `User[]`。自分・友達・ブロックを除外 |
| GET | `/{id}/avatar` | P1 | 画像バイト |
| DELETE | `/me` | P2 | 論理削除 |

```json
// User（公開）                          // UserSelf（自分のみ email を追加）
{"id":"…","displayName":"わたし","avatarUrl":"/api/users/…/avatar"}
```

### 4.3 Friends `/api/friends`

| | Path | P | Notes |
| --- | --- | --- | --- |
| GET | `` | P0 | → `User[]`（`ACCEPTED` のみ） |
| POST | `/requests` | P1 | `{userId}` → 201。重複は 409 |
| GET | `/requests` | P1 | `{incoming:[],outgoing:[]}` |
| POST | `/requests/{id}/accept` | P1 | **双方向2行を書く** → 204 |
| POST | `/requests/{id}/reject` | P2 | |
| DELETE | `/requests/{id}` | P2 | 自分の申請を取消 |
| DELETE | `/{userId}` | P2 | 双方向削除 |
| POST | `/api/blocks` | P2 | `{userId}`。友達関係も削除 |
| GET | `/api/blocks` | P2 | |
| DELETE | `/api/blocks/{userId}` | P2 | |

### 4.4 Photos `/api/photos`

| | Path | P | Notes |
| --- | --- | --- | --- |
| POST | `` | P0 | multipart。`Idempotency-Key` 必須 |
| GET | `` | P0 | `?unassigned=true&from=&to=&limit=&cursor=` |
| GET | `/{id}` | P0 | バイト。`Cache-Control: public,max-age=31536000,immutable` |
| GET | `/{id}/thumb?w=400` | P1 | `w` ∈ {200,400,800} |
| GET | `/{id}/meta` | P1 | EXIF由来の値 |
| POST | `/batch-delete` | P2 | `{photoIds:[]}` |
| DELETE | `/{id}` | P2 | 所有者のみ |
| POST | `/{id}/restore` | P2 | 30日以内 |

アップロード応答は **1件の失敗が全体を落とさない** よう3分割する:

```json
{
  "uploaded": [{"id":"…","url":"/api/photos/…","width":4032,"height":3024,
                "takenAt":"2026-09-20T17:30:00+09:00","lat":34.7025,"lng":135.4959}],
  "duplicates": [{"filename":"IMG_2011.jpg","existingPhotoId":"…"}],
  "rejected":   [{"filename":"movie.mov","code":"UNSUPPORTED_MEDIA"}]
}
```

### 4.5 Albums `/api/albums`

| | Path | P | Notes |
| --- | --- | --- | --- |
| POST | `/generate` | P0 | `{photoIds:[]}` → 202 `{jobId}` |
| GET | `/jobs/{id}` | P0 | `{status,progress,albumIds,errorCode}` |
| GET | `/jobs/{id}/stream` | P1 | SSE 進捗 |
| DELETE | `/jobs/{id}` | P2 | `ENRICHING` 完了前なら中断 |
| POST | `` | P1 | 手動作成（AIなし） |
| GET | `` | P0 | `?q=&from=&to=&limit=&cursor=` |
| GET | `/{id}` | P0 | → `Album`。`ETag` 付き |
| PATCH | `/{id}` | P1 | `{title,coverPhotoId,summary}`、`If-Match` |
| POST | `/{id}/photos` | P1 | `{photoIds:[]}` 追加 |
| PATCH | `/{id}/photos/{apId}` | P1 | 画面08の6行、`If-Match` |
| DELETE | `/{id}/photos/{apId}` | P2 | アルバムから外す（写真は残る） |
| PUT | `/{id}/photos/order` | P2 | `{albumPhotoIds:[]}` |
| POST | `/{id}/regenerate` | P2 | AI再実行 |
| GET | `/{id}/members` | P1 | |
| POST | `/{id}/members` | P1 | `{userId,role}`。友達でなければ 403 |
| PATCH | `/{id}/members/{userId}` | P2 | ロール変更 |
| DELETE | `/{id}/members/{userId}` | P2 | 所有者、または自分（退出） |
| GET | `/{id}/stream` | P1 | SSE 共同編集イベント |
| DELETE | `/{id}` | P2 | 所有者のみ、論理削除 |

```json
// Album
{
  "id":"…","version":7,
  "title":"最高の1日","summary":"テスト終わりの放課後、みんなで梅田へ。",
  "date":"2026-09-20","place":"梅田",
  "aiGenerated":true,"coverPhotoId":"…","myRole":"OWNER",
  "members":[/* User[] */],
  "photos":[{
    "id":"ap_…",                       // ← 装飾・メタデータはこのIDで指す
    "photoId":"…","url":"/api/photos/…","thumbUrl":"/api/photos/…/thumb?w=400",
    "position":0,"version":3,
    "caption":"放課後","place":"梅田","weather":"晴れ",
    "comment":"テスト終わりの放課後、最高だった♡","music":null,
    "decoration":{"revision":2,"renderedUrl":"/api/albums/…/photos/ap_…/rendered?r=2"}
  }]
}
```

### 4.6 Decoration `/api/albums/{id}/photos/{apId}`

| | Path | P | Notes |
| --- | --- | --- | --- |
| GET | `/decoration` | P0 | → 要素配列 + `revision`、`ETag` |
| PUT | `/decoration` | P0 | 全置換、`If-Match: "<revision>"` |
| POST | `/decoration/rendered` | P0 | クライアント描画済みPNGを保存 |
| DELETE | `/decoration` | P1 | クリア（新revision） |
| GET | `/rendered?r=` | P0 | 写真＋装飾の合成。共有・OGが使う |
| GET | `/api/stickers` | P2 | スタンプ一覧 |

```json
// PUT /decoration — 座標は 0..1 に正規化する
{"elements":[
  {"id":"e1","type":"stroke","color":"#ff6b9d","width":0.008,
   "points":[[0.12,0.33],[0.14,0.35],[0.19,0.41]]},
  {"id":"e2","type":"text","text":"Best Friends ♡","x":0.5,"y":0.72,
   "font":"hand","size":0.06,"color":"#ffffff","rotation":-3},
  {"id":"e3","type":"sticker","assetId":"heart-01","x":0.8,"y":0.2,
   "scale":1.2,"rotation":15},
  {"id":"e4","type":"filter","name":"sunset","intensity":0.6}
]}
```

### 4.7 Share

| | Path | P | Auth | Notes |
| --- | --- | --- | --- | --- |
| POST | `/api/albums/{id}/share` | P0 | 要 | `{expiresInDays?}` → `{token,shareUrl}` |
| GET | `/api/albums/{id}/share` | P1 | 要 | 現在のリンクと `viewCount` |
| DELETE | `/api/albums/{id}/share` | P2 | 要 | 失効 |
| GET | `/s/{token}` | P0 | 不要 | **SSR HTML + OGタグ** |
| GET | `/api/share/{token}` | P0 | 不要 | 公開アルバムJSON |
| GET | `/og/{token}.jpg` | P0 | 不要 | 1200x630 コラージュ |
| GET | `/s/{token}/photos/{apId}` | P1 | 不要 | 合成画像 |

公開JSONは **内部 `Album` とは別のDTO** にする。使い回すと必ず何かが漏れる
（メンバーのID、`photoId`、`version`）。

### 4.8 Capsule `/api/capsules`

| | Path | P | Notes |
| --- | --- | --- | --- |
| POST | `` | P1 | `{albumId,openAt,message,recipientIds[]}` |
| GET | `` | P1 | → `Capsule[]` + `daysRemaining` |
| GET | `/{id}` | P1 | **封印中は中身を含めない** |
| POST | `/{id}/open` | P1 | 早すぎれば 409 `CAPSULE_NOT_YET_OPEN` |
| PATCH | `/{id}` | P2 | 封印中のみメッセージ編集可 |
| DELETE | `/{id}` | P2 | 所有者のみ |
| POST | `/{id}/unseal-now` | P1 | **devプロファイルのみ** |

```json
// SEALED                                 // OPENED
{"id":"…","status":"SEALED",              {"id":"…","status":"OPENED",
 "openAt":"2027-09-20T00:00:00+09:00",     "message":"1年後の自分へ…",
 "daysRemaining":363,                      "album":{/* Album 全体 */}}
 "coverBlurUrl":"/api/capsules/…/blur"}
```

### 4.9 Notifications / Ops

| | Path | P | Notes |
| --- | --- | --- | --- |
| GET | `/api/notifications` | P2 | `?unreadOnly=true` |
| GET | `/api/notifications/unread-count` | P2 | バッジ |
| POST | `/api/notifications/{id}/read` | P2 | |
| POST | `/api/notifications/read-all` | P2 | |
| GET | `/api/health` | P0 | `{status,db,aiReachable,diskFreeMb}` |
| GET | `/api/version` | P2 | git sha + build time |
| POST | `/api/dev/seed` | P1 | **devのみ** 4ユーザー・友達・20枚・2アルバム |

通知種別: `FRIEND_REQUEST`, `FRIEND_ACCEPTED`, `ALBUM_INVITED`,
`ALBUM_UPDATED`, `CAPSULE_OPENABLE`, `SHARE_VIEWED`.

---

## 5. Algorithms

> 中核アルゴリズム。ここだけは実装前に合意しておく。

### 5.1 Photo ingestion

> 取込処理。順序が重要 —— 先に読み、次に回し、最後に消す。

```
for each uploaded file:
  1. MIME/拡張子チェック         → 不可なら rejected[] に積んで次へ
  2. sha256 を計算               → 既存と一致なら duplicates[] に積んで次へ
  3. EXIF を読む                 ← 削除より先に必ず読む
       DateTimeOriginal → takenAt（無ければ now、source=UPLOAD）
       GPSLatitude/Longitude → lat/lng
       Orientation → 1..8
  4. Orientation に従い画素を物理回転
  5. EXIF を全除去して保存        ← 共有時に自宅座標を漏らさない
  6. width/height を「回転後」の値で記録
  7. サムネイル 200/400/800px を生成
```

Orientation の8値はすべて処理する。3, 6, 8 だけ対応した実装は必ずどこかで
横倒しの写真を出す。

| value | 操作 | | value | 操作 |
| --- | --- | --- | --- | --- |
| 1 | なし | | 5 | 転置 |
| 2 | 左右反転 | | 6 | 90° 時計回り |
| 3 | 180° | | 7 | 転置 + 180° |
| 4 | 上下反転 | | 8 | 90° 反時計回り |

**回転を入庫時に済ませる理由**: 下流の消費者（サムネ・Claude・Canvas装飾・
OGコラージュ）が4つあり、各自が回転フラグを解釈すると必ず1つ忘れる。

### 5.2 Clustering

> クラスタリング。撮影時刻の間隔だけで切る。純関数。

```java
List<List<Photo>> cluster(List<Photo> photos)
```

```
1. takenAt 昇順にソート
2. 直前との差が GAP_MINUTES (=30) を超えたら新しいクラスタを開始
3. 1枚だけのクラスタは、時間的に最も近い隣のクラスタへ併合
     （写真1枚のアルバムは「アルバム」として成立しない）
4. 12枚を超えるクラスタは時刻順に12枚ずつ分割
     （Claude 1リクエストあたりの画像枚数上限）
5. 空入力なら 422 NO_VALID_PHOTOS
```

定数 `GAP_MINUTES` は `application.properties` に出す。放課後の3時間は1つの
アルバム、翌日は別のアルバム —— 30分はその直感に合う。

### 5.3 Claude enrichment

> AI連携。1クラスタ1リクエスト、構造化出力、結果は必ず検証する。

**Model** `claude-opus-4-8` / **SDK** `com.anthropic:anthropic-java`

**Request**
- 画像: 各写真を長辺1080pxに縮小 → base64。**縮小は必須**（原寸だと1枚で
  約4,800トークン、1080pなら約2,000トークン）
- テキスト: 各写真の `photoId`, `takenAt`, GPS（あれば）の一覧
- `thinking: adaptive`（`budget_tokens` は 4.8 では 400 になる）
- `effort: medium`、`temperature` は指定しない（同じく 400）
- timeout 60s

**Response** — Java SDK の record ベース `outputConfig` でスキーマを自動生成。
手書きスキーマもJSONパースも不要:

```java
record AlbumDraft(
    String title,          // 「最高の1日」短く、日本語
    String coverPhotoId,   // 入力に含まれるIDであること
    String summary,        // 1文
    List<PhotoInsight> photos) {}

record PhotoInsight(
    String photoId,
    String caption,        // 2〜5文字「放課後」
    String place,          // 推定できなければ null
    String weather,        // 晴れ / 曇り / 雨 / 雪 / null
    String comment) {}     // カジュアルな1文
```

**Validation — 返ってきたIDを信用しない**

```
- photoId が入力に無い PhotoInsight は捨てる
- coverPhotoId が不明なら takenAt 最古にフォールバック
- caption は10文字で切る、comment は255文字で切る
- weather が4種以外なら null にする
- title が空なら "YYYY.MM.DD のアルバム"
```

**Failure policy** — 429/5xx は1回だけ指数バックオフで再試行。それでもダメなら
例外を投げ、`AlbumGenerationService` が規則ベースにフォールバックして
`READY(aiGenerated=false)` で完了する。**`AI_UNAVAILABLE` をアルバム生成の
フローに出さない。**

**Cost** — 1080px 1枚 ≈ 2,000トークン。12枚で約25,000入力トークン ≈ **$0.13**。
着手時に `count_tokens` で実測して置き換えること。

### 5.4 Decoration rendering

> 装飾の描画。クライアントが描き、サーバーは結果を預かる。

| | 担当 | 保存先 |
| --- | --- | --- |
| 要素データ（真実） | クライアントが生成、サーバーが保存 | `decorations.elements_json` |
| 描画結果（キャッシュ） | **クライアントが Canvas で描画**して送る | `rendered/{apId}_{rev}.jpg` |

クライアントに描かせるのは、Canvasも手書き風フォントも既にそちらにあるから。
Java2D で日本語手書きフォントを1px違わず再現するのは1日仕事で、誰も見比べない。

**座標は 0..1 正規化**。スマホのCanvasは390px幅、OG画像は1200px幅。絶対座標で
持つとスタンプが全部ずれる。

`GET /rendered` は、PNGが無ければ **元画像にフォールバック**して返す。共有リンクが
描画アップロードの失敗で壊れてはいけない。

### 5.5 Share token & OG image

> 共有トークンとOG画像。トークンは推測不能、URLはHostから組み立てる。

```java
byte[] raw = new byte[16];                    // 128 bit
SecureRandom.getInstanceStrong().nextBytes(raw);
String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);  // 22 chars
```

**共有URLは必ずリクエストの `Host` ヘッダから組み立てる。**
`application.properties` にベースURLを書いた瞬間、トンネル再接続で既存リンクが
全部死ぬ（`02-basic-design.md` R-4）。

**OG collage** — 1200x630 JPEG、`CollageRenderer`:

```
枚数 1 → 中央クロップ1枚
枚数 2 → 縦2分割
枚数 3 → 左1枚 + 右上下2枚
枚数 4+ → 2x2（表紙を左上に固定）
出力: quality 0.85、og/{token}.jpg にキャッシュ
```

**SSR head**（Thymeleaf）:

```html
<meta property="og:title"       content="最高の1日">
<meta property="og:description" content="テスト終わりの放課後、みんなで梅田へ。">
<meta property="og:image"       content="https://{host}/og/{token}.jpg">
<meta property="og:type"        content="website">
<meta name="twitter:card"       content="summary_large_image">
```

### 5.6 Capsule sealing

> 封印。DTOを組み立てる前に判定する。

```java
if (capsule.getOpenedAt() == null) {
    return CapsuleSealedDto.of(capsule);   // message も album も積まない
}
return CapsuleOpenedDto.of(capsule, albumService.get(capsule.getAlbumId()));
```

封印中のDTOに `message` フィールドを持たせて `null` を入れる実装にしない。
**フィールドごと存在しない型を返す。** そうすれば「うっかり詰める」事故が
型レベルで起きない。

---

## 6. Configuration

> 設定。秘密情報は `.env` にのみ置く。

**`application.properties`**

```properties
spring.application.name=backend
server.port=8080
spring.datasource.url=jdbc:h2:file:./data/hanamizuki;AUTO_SERVER=TRUE
spring.jpa.hibernate.ddl-auto=update
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=200MB

app.storage.root=./storage
app.cluster.gap-minutes=30
app.cluster.max-photos-per-request=12
app.ai.model=claude-opus-4-8
app.ai.timeout-seconds=60
app.ai.max-image-long-edge=1080
app.jwt.access-ttl-minutes=15
app.jwt.refresh-ttl-days=30
```

**`.env.example`**（値は書かない。名前だけ）

```
ANTHROPIC_API_KEY=
APP_JWT_SECRET=
```

**`application-dev.properties`** — `/api/dev/**` と `unseal-now` はこちらでのみ有効化。

---

## 7. Test plan

> テスト方針。全部は書けないので、壊れたら気づけない所だけ書く。

| 対象 | 種別 | 内容 |
| --- | --- | --- |
| `PhotoClusterer` | 単体 | 境界（29分/30分/31分）、1枚クラスタ併合、13枚分割、空入力 |
| `ImageProcessor` | 単体 | Orientation 1〜8 の全8ケース |
| Claude応答検証 | 単体 | 不明ID混入、cover不正、超過長、weather不正値 |
| 機能縮退 | 結合 | AIを強制失敗させ `READY(aiGenerated=false)` に到達すること |
| 楽観ロック | 結合 | 同一 `If-Match` の二重更新が 409 |
| **カプセル封印** | 結合 | **封印中の直叩きに `message` が含まれないこと** |
| 共有 | 手動 | 実機でLINEに貼り、プレビューカードが出ること |
| 権限 | 手動 | 他人のアルバムID直叩きで 403 |
| デモ導線 | 手動 | `01-requirements.md` §8 を実機で2回通す |

単体テストは `PhotoClusterer` と `ImageProcessor` に集中させる。この2つは
純粋で、間違いやすく、壊れても実行時まで気づけない。Controller層は手動で足りる。

---

## 8. Implementation order

> 実装順。各段階で単体で動く状態を保つ。

| # | Slice | 参照 | 目安 |
| --- | --- | --- | --- |
| 1 | Entity + H2 + エラーハンドラ + `/api/health` | §1, §3.2 | 2h |
| 2 | 認証（JWT + refresh） | §4.1 | 3h |
| 3 | ユーザー + 友達 | §4.2, §4.3 | 2h |
| 4 | 写真取込（EXIF・回転・サムネ） | §4.4, §5.1 | 3h |
| 5 | クラスタリング + ジョブ基盤（AIなし） | §4.5, §5.2 | 2h |
| 6 | Claude連携 | §5.3 | 3h |
| 7 | 装飾（要素 + rendered） | §4.6, §5.4 | 2h |
| 8 | 共有 SSR + OG | §4.7, §5.5 | 3h |
| 9 | カプセル | §4.8, §5.6 | 2h |
| 10 | 通知・SSE・seed | §4.9 | 2h |

**1〜8 がデモ本体（20時間）。9〜10 は緩衝。**
各スライスで1ブランチ1PR（`README.md` の workflow に従う）。
