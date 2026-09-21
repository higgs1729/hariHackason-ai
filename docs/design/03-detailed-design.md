# 03 — Detailed Design / 詳細設計

> 実装に落とせる粒度。API仕様・アルゴリズム・設定・テスト。
> 前提: `01-requirements.md`, `02-basic-design.md`
> **スキーマの正: `backend/sql/create_table.sql`**

---

## 1. Schema

> スキーマ。列定義はSQLファイルが唯一の正。ここには設計判断だけを残す。

`backend/sql/create_table.sql` is the single source of truth. This document does
**not** restate column definitions — duplicating them guarantees they drift.

### 1.1 Conventions baked into the DDL

| 規約 | 内容 |
| --- | --- |
| 主キー | `bigint auto_increment` |
| 列名 | camelCase（`userAccount`, `createTime`, `takenTime`） |
| 論理削除 | 主エンティティは `isDelete tinyint`。関係表・行為表は物理削除 |
| 外部キー | **張らない。** 整合性はアプリ層。索引は検索に必要な分だけ |
| 時刻 | `datetime`。業務時刻は別名（`takenTime` / `openTime`）で `createTime` と混ぜない |
| 文字コード | `utf8mb4_unicode_ci` |

### 1.2 Tables

全13テーブル。

| Table | 論理削除 | 役割 |
| --- | --- | --- |
| `user` | ○ | アカウント。`userRole` = user/admin/ban、`lineUserId` は予約 |
| `refresh_token` | × | リフレッシュトークン。ハッシュで保持、`familyId` で盗用検知 |
| `friend` | × | 双方向2行。`status` 0=待通過 1=已通過 |
| `block` | × | **単向**。拉黑は相手に見えない |
| `photo` | ○ | 写真そのもの。EXIF由来の `takenTime` / 緯度経度 / `sha256` |
| `album` | ○ | アルバム。`aiGenerated` で文案の出どころ、`version` で楽観ロック |
| `album_member` | × | `memberRole` = owner/editor |
| `album_photo` | ○ | **「この写真 × このアルバム」**。説明・並び順・装飾・`version` |
| `album_share` | × | `shareToken` は乱数。`revokeTime` 非空で410 |
| `capsule` | ○ | `openTime` 前は中身を返さない |
| `capsule_recipient` | × | 0件なら「自分だけ」。`notifyTime` で重複通知を防ぐ |
| `album_job` | × | 非同期成册タスク。`progress` / `idempotencyKey` |
| `notification` | × | `payload` は json。種別ごとに列を増やさない |

### 1.2.1 Denormalisation policy

> 冗余方針。一覧も詳細も join しない。代償は整合性なので、規則を先に決める。

Three house rules, taken from the team's reference schema:

1. **カウントは冗余列。** `count(*)` を撃たない（`post.thumbNum` に倣う）
2. **配列は varchar の json。** 子テーブルに割らない（`post.tags` に倣う）
3. **表示に要る他表の列は、当該表へ複製する。** join を消す

Rule 3 is what makes this rigorous or sloppy, depending on discipline. Every
redundant column in `create_table.sql` is annotated with three things:

| 注記 | 意味 |
| --- | --- |
| `[源]` | 権威データはどこか |
| `[同步]` | いつ複製を書くか |
| `[漂移]` | ずれたら何が起きるか |

**同期タイミングは2種類だけ。3つ目を発明しないこと。**

| モード | 意味 | 例 |
| --- | --- | --- |
| **快照 / snapshot** | 書き込み時に1回だけ複製。以後、源が変わっても追随しない | `album_share.albumTitle` — 送ったリンクの中身が後から変わるべきではない |
| **跟随 / follow** | 源の更新時に必ず複製も更新する | `user.userName` → `friend.friendUserName` |

### 1.2.2 Sync rules — 全冗余列

> 同期規則。実装時はこの表を見て、源を更新する箇所にすべて同期処理を入れる。

**follow（源の更新時に同期が必要）**

| 源 | 複製先 | 同期の起点 |
| --- | --- | --- |
| `user.userName` | `friend.friendUserName`, `photo.userName`, `album.userName`, `album_member.userName` | `PATCH /api/users/me` |
| `user.userAvatar` | `friend.friendUserAvatar`, `album_member.userAvatar` | `PUT /api/users/me/avatar` |
| `album.title` | `album_member.albumTitle` | `PATCH /api/albums/{id}` |
| `album.coverPhotoId` | `album.coverPhotoUrl`, `album.coverThumbUrl` | 表紙変更時（同一行なので同時更新） |
| `album_share.shareToken` | `album.shareToken` | 分享作成時に書き、撤回時に `null` |
| `album_share.viewNum` | `album.viewNum` | 公開ページ閲覧時 |

**counter（増減時に ±1）**

| 冗余列 | 源 | 増える時 | 減る時 |
| --- | --- | --- | --- |
| `user.photoNum` | `photo` | 写真アップロード | 写真削除 |
| `user.albumNum` | `album_member` | アルバム作成・招待 | 退出・削除 |
| `user.friendNum` | `friend` (`status=1`) | 友達承認 | 友達削除・ブロック |
| `user.capsuleNum` | `capsule` | カプセル作成 | カプセル削除 |
| `photo.albumNum` | `album_photo` | アルバムへ追加 | アルバムから除外 |
| `album.photoNum` | `album_photo` | 写真追加 | 写真除外 |
| `album.memberNum` | `album_member` | 招待 | 退出 |
| `capsule.recipientNum` | `capsule_recipient` | 宛先追加 | 宛先削除 |

`photo.albumNum = 0` が「未成册」の定義。画面03の写真グリッドはこの1列で絞る
（`idx_userId_albumNum`）。`album_photo` への `not exists` を撃たない。

**snapshot（書き込み時に1回だけ。追随しない）**

| 複製先 | 源 | 追随しない理由 |
| --- | --- | --- |
| `album_share.albumTitle` / `albumSummary` / `albumDate` / `coverPhotoUrl` / `photoNum` | `album` | **送信済みリンクの内容が後から変わるべきではない。** OGカードの内容を固定する |
| `capsule.albumTitle` / `coverPhotoUrl` / `photoNum` | `album` | 封印したのは「その時点のアルバム」 |
| `album_photo.photoUrl` / `thumbUrl` / `picWidth` / `picHeight` / `picScale` / `takenTime` | `photo` | 原図パスは入庫後に変化しない。安全な快照 |
| `notification.*` | 各業務表 | 通知は「その時起きたこと」。履歴を書き換えない |
| `block.blockedUserName`, `capsule_recipient.userName` | `user` | 表示のみ。ずれても実害なし |

### 1.2.3 Drift recovery

> 漂移の兜底。冗余列は必ず源から再計算できること。

**すべての冗余列は源から再構築できる。** `POST /api/dev/rebuild-denorm`
（devプロファイル）が全件を再計算する。デモ前に1回流す。

> ⚠️ **冗余列を唯一のデータ源にしないこと。** `album_share.albumTitle` しか
> タイトルを持たない状態を作ってはいけない。源は常に `album.title` で、
> 分享行はその複製にすぎない。この原則が崩れると再計算が不可能になる。

`photo` を物理削除しない（`isDelete`）のも同じ理由 —— `album_photo.photoUrl`
の快照が指す先を消さないため。

### 1.3 Changes on 2026-09-22

> 09-22の変更。スキーマは要件の全件を収容する方針に統一した（Q-11）。

**追加した列**

| 列 | 理由 |
| --- | --- |
| `album_photo.overlayData` (`json`) | PNGだけでは**刷新後に一筆撤销も貼紙の移動もできない**。要素配列を真実とし、PNGは描画キャッシュに降格。座標は 0〜1 正規化（390px と 1200px でずれるため） |
| `album_job.progress` (`int`) | ENRICHING は20枚で30秒かかりうる。進捗がないとフロントは干転圈しかできない（NFR-01.2） |
| `album_job.idempotencyKey` | 再送でClaudeが2回走るのを防ぐ（Q-10）。`uk_userId_idempotencyKey` で一意 |
| `album_job.finishTime` | `createTime` との差が生成所要時間。デモで「20枚18秒」と言える |
| `album.version` / `album_photo.version` | 楽観ロック（FR-09.2）。ETag / If-Match の実体 |
| `photo.sha256` | 重複アップロード検出。`idx_userId_sha256` |
| `photo.takenTimeSource` | `takenTime` がEXIF由来か上传時刻回落かの区別。「時間」行の信頼度表示に使う |

**復活させた表**

| 表 | 対応する要件 |
| --- | --- |
| `refresh_token` | FR-07.1 ログイン状態の保持 |
| `block` | FR-12 ブロック |
| `notification` | FR-11 通知 |
| `capsule_recipient` | FR-06 カプセルの複数宛先 |

**方針**: P1/P2 の機能でも、**列と表だけは最初から用意する**。実装順で絞るのは
よいが、スキーマで絞ると後から「動いているコードに触ってテーブルを足す」作業に
なる。DDLを一度で確定させる方が安い。

### 1.4 Deliberately absent

> それでも意図的に持たないもの。

| 無いもの | 判断 |
| --- | --- |
| `decoration` 表 | 装飾は `album_photo` の3列（`overlayData`/`overlayPath`/`compositePath`）で持つ。1:1 の別表にする理由がない。**ただし実装側には独立した `Decoration` エンティティが存在する — Q-12 で決着させること** |
| 外部キー制約 | 家の規約。整合性はアプリ層、索引は検索に必要な分だけ |
| `album_member` の `viewer` ロール | 画面05に閲覧専用メンバーの概念がない。共有リンクがその役目 |

### 1.5 ⚠️ Entity / SQL mismatch (2026-09-22)

> 実装中の実体クラスとSQLが一致していない。着手前に片方へ寄せること。

`backend/src/main/java/.../domain/` の13クラスは、**本書の初版（H2 + UUID +
snake_case）**を見て書かれており、`create_table.sql` と一致しない。

| | `create_table.sql`（正） | 実体クラス（要修正） |
| --- | --- | --- |
| 主キー | `bigint auto_increment` | `UUID` 文字列 `CHAR(36)` |
| テーブル名 | `user` / `album` / `photo` | `users` / `albums` / `photos` |
| 列名 | camelCase `createTime` | snake_case `created_at` |
| 論理削除 | `isDelete tinyint` | `deleted_at` + `@SQLRestriction` |
| ログインID | `userAccount` | `email` |
| 装飾 | `album_photo` の3列 | 独立した `Decoration` エンティティ |

**一致するテーブルは1つもない。** 現在 H2 + `ddl-auto=update` で起動できるのは
Hibernateが実体から別のスキーマを勝手に作っているからで、`create_table.sql` は
使われていない。MySQL + `ddl-auto=none` に切り替えた瞬間に起動不能になる。

`create_table.sql` を正とし、実体クラスを寄せること（§6.2 の `ddl-auto=none`）。
`Decoration` の扱いだけ Q-12 の決定を待つ。

---

## 2. Class responsibilities

> 主要クラスの責務。1クラス1責務、serviceにトランザクション境界を置く。

| Class | Responsibility |
| --- | --- |
| `JwtFilter` | `Authorization` を検証し `SecurityContext` に載せる |
| `JwtIssuer` | JWTの発行と検証（リフレッシュなし） |
| `GlobalExceptionHandler` | 例外 → `ApiError` の一元変換 |
| `PhotoService` | 取込（回転・EXIF抽出・サムネ）、一覧、論理削除 |
| `ExifReader` | `DateTimeOriginal` / GPS / `Orientation` の抽出のみ |
| `ImageProcessor` | 回転・縮小・EXIF除去・合成。**状態を持たない** |
| `PhotoClusterer` | 撮影時刻からクラスタ分割。**純関数、DBに触らない** |
| `AlbumGenerationService` | ジョブの状態遷移と進捗更新。`@Async` の入口 |
| `ClaudeAlbumEnricher` | Claude呼び出しと結果の検証。失敗時は例外を投げるだけ |
| `AlbumService` | アルバムCRUD、メンバー、権限判定 |
| `DecorationService` | `overlayData` の保存と PNG / 合成画像の受け取り |
| `ShareService` | トークン発行・失効、公開DTOへの射影 |
| `ShareViewController` | `/s/{token}` のSSR（Thymeleaf） |
| `CollageRenderer` | OG画像 1200x630 の生成 |
| `CapsuleService` | 封印・開封。**開封前は中身を組み立てない** |

`PhotoClusterer` と `ImageProcessor` を純粋に保つのは、ここが唯一まともに
単体テストできるロジックだから。DBもAIも要らない。

---

## 3. Common specifications

> 共通仕様。全APIが従う。

### 3.1 Conventions

| 項目 | 仕様 |
| --- | --- |
| ID | `bigint`。JSONでは **数値**（文字列化しない） |
| JSONキー | camelCase。DBの列名とそろえる |
| 日時 | ISO-8601 オフセット付き `2026-09-20T17:30:00+09:00` |
| 認証 | `Authorization: Bearer <jwt>`。access 15分 / refresh 30日 |
| 認証不要 | `/api/auth/**`, `/api/health`, `/s/**`, `/og/**`, `/api/share/**` |
| ページング | `?limit=20&cursor=` → `{items, nextCursor, total}`。max 100 |
| 楽観ロック | `album` / `album_photo` は `version` を `ETag` で返す。更新は `If-Match` 必須、未指定は **428** |
| 冪等性 | `POST /api/albums/generate` は `Idempotency-Key: <uuid>` 必須。24時間以内の再送は元の応答を返す |
| アップロード | `multipart/form-data`, field `files`, 10MB/枚, 20枚/回 |

### 3.2 Error codes

> エラーコード一覧。フロントはこの `code` で分岐する。

```json
{ "code": "ALBUM_FORBIDDEN", "message": "…", "details": null, "traceId": "b3f1…" }
```

| HTTP | code | 発生条件 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 入力不正。`details` にフィールド別メッセージ |
| 400 | `UNSUPPORTED_MEDIA` | JPEG/PNG 以外 |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | `Idempotency-Key` ヘッダ欠落 |
| 401 | `TOKEN_EXPIRED` | access期限切れ → **1回だけ refresh して再送** |
| 401 | `TOKEN_INVALID` | 不正・失効 → ログイン画面へ |
| 401 | `CREDENTIALS_INVALID` | ログイン失敗 |
| 403 | `ACCOUNT_BANNED` | `userRole = 'ban'` |
| 403 | `ALBUM_FORBIDDEN` | メンバーでない |
| 403 | `ROLE_INSUFFICIENT` | owner専用操作をeditorが実行 |
| 403 | `NOT_FRIENDS` | 友達でないユーザーを招待 |
| 403 | `USER_BLOCKED` | ブロック関係にある |
| 404 | `USER_NOT_FOUND` / `PHOTO_NOT_FOUND` / `ALBUM_NOT_FOUND` / `CAPSULE_NOT_FOUND` / `JOB_NOT_FOUND` | |
| 409 | `ACCOUNT_EXISTS` | `userAccount` 重複 |
| 409 | `VERSION_CONFLICT` | `If-Match` 不一致。`details.current` に現在値を載せる |
| 409 | `FRIEND_REQUEST_EXISTS` | 申請重複 |
| 409 | `CAPSULE_NOT_YET_OPEN` | `now < openTime` |
| 409 | `JOB_ALREADY_RUNNING` | 同一ユーザーの生成が実行中 |
| 410 | `SHARE_REVOKED` | `revokeTime` が非空 |
| 413 | `FILE_TOO_LARGE` | 10MB超 |
| 422 | `NO_VALID_PHOTOS` | クラスタリング対象が0件 |
| 428 | `IF_MATCH_REQUIRED` | 楽観ロック対象で `If-Match` 未指定 |
| 429 | `RATE_LIMITED` | `Retry-After` 付与 |

### 3.3 Rate limits

| Route | Limit |
| --- | --- |
| `POST /api/albums/generate` | 20 / user / day、**同時1件（409 `JOB_ALREADY_RUNNING`）** |
| `POST /api/photos` | 200 files / user / hour |
| `POST /api/auth/login` | 10 / IP / 5min |
| その他 | 120 / user / min |

同時1件の制限が、冪等キーが無い状態での二重実行対策を兼ねる（§1.4）。

---

## 4. API specification

> API仕様。P0=デモ必須、P1=余裕があれば、P2=切ってよい。

### 4.1 Auth `/api/auth`

| | Path | P | Request → Response |
| --- | --- | --- | --- |
| POST | `/register` | P0 | `{userAccount,userPassword,userName}` → `{accessToken,refreshToken,user}` |
| POST | `/login` | P0 | `{userAccount,userPassword}` → 同上 |
| POST | `/refresh` | P0 | `{refreshToken}` → 新しいペア（**ローテーション**） |
| POST | `/logout` | P1 | `{refreshToken}` → 204。該当行に `revokeTime` |
| POST | `/logout-all` | P2 | → 204。そのユーザーの全 `refresh_token` を失効 |
| GET | `/me` | P0 | → `User` |

`userAccount` は4文字以上、`userPassword` は8文字以上。BCrypt cost **10**
（12はデモ機で体感できるほど遅い）。`userRole = 'ban'` は 403 `ACCOUNT_BANNED`。

リフレッシュは使い捨て。使ったトークンに `revokeTime` を打ち、同じ `familyId`
で新しい行を作る。**失効済みトークンが再使用されたら盗用とみなし、その
`familyId` 全体を失効させる。**

### 4.2 User `/api/users`

| | Path | P | Notes |
| --- | --- | --- | --- |
| GET | `/me` | P0 | → `User` |
| PATCH | `/me` | P1 | `{userName, userProfile}` |
| PUT | `/me/avatar` | P1 | multipart `file` → `{userAvatar}` |
| GET | `/{id}` | P1 | 公開プロフィール |
| GET | `/search?q=` | P1 | `userAccount` / `userName` 前方一致。自分と既存の友達を除外 |

```json
// User
{"id":12,"userAccount":"nao","userName":"わたし",
 "userAvatar":"/storage/avatars/12.jpg","userProfile":null,"userRole":"user"}
```

`userPassword` は**いかなる応答にも含めない**。

### 4.3 Friends `/api/friends`

| | Path | P | Notes |
| --- | --- | --- | --- |
| GET | `` | P0 | → `User[]`（`status=1` のみ） |
| POST | `/requests` | P1 | `{userId}` → 201。重複は409 |
| GET | `/requests` | P1 | `{incoming:[],outgoing:[]}` |
| POST | `/requests/{id}/accept` | P1 | **双方向2行にする** → 204 |
| POST | `/requests/{id}/reject` | P2 | 行を削除 |
| DELETE | `/{userId}` | P2 | 双方向削除 |
| POST | `/api/blocks` | P2 | `{userId}`。**友達関係も同時に双方向削除** |
| GET | `/api/blocks` | P2 | → `User[]` |
| DELETE | `/api/blocks/{userId}` | P2 | 解除 |

ブロックは **単向**（相手には見えない）。ブロック中は検索結果・友達申請・
アルバム招待の3か所で除外する。

申請時は `userId → friendId` の1行のみ（`status=0`）。承認時に逆向きの行を
追加し、両方を `status=1` にする。これで友達一覧は
`where userId = ? and status = 1` の単列クエリで済む。

### 4.4 Photos `/api/photos`

| | Path | P | Notes |
| --- | --- | --- | --- |
| POST | `` | P0 | multipart `files` |
| GET | `` | P0 | `?unassigned=true&from=&to=&limit=&cursor=` |
| GET | `/{id}` | P0 | バイト。`Cache-Control: public,max-age=31536000,immutable` |
| GET | `/{id}/thumb?w=400` | P1 | `w` ∈ {200,400,800} |
| DELETE | `/{id}` | P2 | 所有者のみ、`isDelete=1` |

アップロード応答は **1件の失敗が全体を落とさない** よう2分割する:

```json
{
  "uploaded": [{"id":101,"url":"/api/photos/101","picWidth":4032,"picHeight":3024,
                "takenTime":"2026-09-20T17:30:00+09:00",
                "latitude":34.7025000,"longitude":135.4959000}],
  "rejected": [{"filename":"movie.mov","code":"UNSUPPORTED_MEDIA"}]
}
```

重複検出は行わない（`sha256` 列なし）。同じ写真を2回上げれば2行できる。

### 4.5 Albums `/api/albums`

| | Path | P | Notes |
| --- | --- | --- | --- |
| POST | `/generate` | P0 | `{photoIds:[]}` + `Idempotency-Key` → 202 `{jobId}`。実行中なら409 |
| GET | `/jobs/{id}` | P0 | `{status,progress,albumIds,errorMsg,finishTime}` |
| DELETE | `/jobs/{id}` | P2 | `ENRICHING` 完了前なら中断 |
| GET | `` | P0 | 自分がメンバーのアルバム。`?limit=&cursor=` |
| GET | `/{id}` | P0 | → `Album`（`photos[]` 込み）、`ETag: "<version>"` |
| PATCH | `/{id}` | P1 | `{title,coverPhotoId,summary}`、**`If-Match` 必須** |
| POST | `/{id}/photos` | P1 | `{photoIds:[]}` 追加 |
| PATCH | `/{id}/photos/{albumPhotoId}` | P1 | 画面08の行を編集、**`If-Match` 必須** |
| DELETE | `/{id}/photos/{albumPhotoId}` | P2 | アルバムから外す（写真は残る） |
| PUT | `/{id}/photos/order` | P2 | `{albumPhotoIds:[]}` |
| GET | `/{id}/members` | P1 | |
| POST | `/{id}/members` | P1 | `{userId}` → 友達でなければ403 |
| DELETE | `/{id}/members/{userId}` | P2 | owner、または自分（退出） |
| DELETE | `/{id}` | P2 | ownerのみ、`isDelete=1` |

```json
// Album — album 1行 + album_photo N行。join なし
{
  "id": 55,
  "version": 7,                       // ETag の実体
  "title": "最高の1日",
  "summary": "テスト終わりの放課後、みんなで梅田へ。",
  "albumDate": "2026-09-20",
  "place": "梅田",
  "aiGenerated": 1,
  "aiModel": "claude-opus-4-8",
  "coverPhotoId": 101,
  "coverThumbUrl": "/storage/thumbs/101_400.jpg",   // 冗余。photo を引かない
  "userId": 12,
  "userName": "わたし",                              // 冗余
  "photoNum": 6, "memberNum": 3, "viewNum": 14,      // 冗余カウント
  "shareToken": "kQ3n…",                             // 冗余。null なら未共有
  "myRole": "owner",
  "members": [                                       // album_member をそのまま
    {"userId": 12, "userName": "わたし", "userAvatar": "…", "memberRole": "owner"}
  ],
  "photos": [{
    "id": 901,                        // ← album_photo.id。装飾・メタデータはこれで指す
    "photoId": 101,
    "version": 3,
    "position": 0,
    "photoUrl": "/storage/photos/2026/09/101.jpg",   // 冗余
    "thumbUrl": "/storage/thumbs/101_400.jpg",       // 冗余
    "picWidth": 4032, "picHeight": 3024, "picScale": 1.333,
    "takenTime": "2026-09-20T17:30:00+09:00",        // 冗余。画面08「時間」行
    "caption": "放課後",
    "place": "梅田",
    "weather": "晴れ",
    "photoComment": "テスト終わりの放課後、最高だった♡",
    "music": null,
    "hasOverlay": true,
    "overlayUserName": "あやか",
    "compositeUrl": "/api/albums/55/photos/901/composite?t=1758556800"
  }]
}
```

**この応答は `album` 1行と `album_photo` N行だけで組み立てる。** `photo` にも
`user` にも触らない —— それが §1.2.1 の冗余方針の目的。

`compositeUrl` の `t` は `overlayUpdateTime` のエポック秒。リビジョン列が無い
ためキャッシュ破棄はこれで行う。`hasOverlay` は `overlayData is not null`。

### 4.6 Decoration `/api/albums/{id}/photos/{albumPhotoId}`

| | Path | P | Notes |
| --- | --- | --- | --- |
| GET | `/decoration` | P0 | → `{elements, overlayUserId, overlayUpdateTime}`、`ETag: "<version>"` |
| PUT | `/decoration` | P0 | 全置換。`overlayData` を書き `overlayUserId` を記録。**`If-Match` 必須** |
| POST | `/decoration/rendered` | P0 | multipart `overlay`(PNG) + `composite`(JPEG) |
| DELETE | `/decoration` | P1 | `overlayData=null`、PNGも消す |
| GET | `/composite?t=` | P0 | 合成画像。共有・OGが使う |
| GET | `/api/stickers` | P2 | スタンプ一覧 |

```json
// PUT /decoration — 座標はすべて 0..1 正規化
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

描画は**クライアントが行う**（§5.4）。サーバーは `overlayData` を保存し、
送られてきたPNGとJPEGをそのまま `overlayPath` / `compositePath` に置く。

`GET /composite` は `compositePath` が無ければ **元画像にフォールバック**する。
共有リンクが描画アップロードの失敗で壊れてはいけない。

### 4.7 Share

| | Path | P | Auth | Notes |
| --- | --- | --- | --- | --- |
| POST | `/api/albums/{id}/share` | P0 | 要 | → `{shareToken, shareUrl}` |
| GET | `/api/albums/{id}/share` | P1 | 要 | 現在のリンクと `viewCount` |
| DELETE | `/api/albums/{id}/share` | P2 | 要 | `revokeTime` を打つ |
| GET | `/s/{token}` | P0 | 不要 | **SSR HTML + OGタグ**、`viewCount++` |
| GET | `/api/share/{token}` | P0 | 不要 | 公開アルバムJSON |
| GET | `/og/{token}.jpg` | P0 | 不要 | 1200x630 コラージュ |

公開JSONは **内部 `Album` とは別のDTO** にする。使い回すと必ず何かが漏れる
（メンバーの `userAccount`、`photoId`、`userId`）。公開DTOに含めてよいのは
タイトル・要約・日付・場所・`userName`・画像URLだけ。

### 4.8 Capsule `/api/capsules`

| | Path | P | Notes |
| --- | --- | --- | --- |
| POST | `` | P1 | `{albumId, openTime, capsuleMsg}` |
| GET | `` | P1 | → `Capsule[]` + `daysRemaining` |
| GET | `/{id}` | P1 | **封印中は `capsuleMsg` と `album` を含めない** |
| POST | `/{id}/open` | P1 | 早すぎれば409 `CAPSULE_NOT_YET_OPEN` |
| DELETE | `/{id}` | P2 | 所有者のみ |
| POST | `/{id}/unseal-now` | P1 | **devプロファイルのみ** |

```json
// SEALED                                  // OPENED
{"id":7,"status":"SEALED",                 {"id":7,"status":"OPENED",
 "openTime":"2027-09-20T00:00:00+09:00",    "openTime":"…","openedTime":"…",
 "daysRemaining":363}                       "capsuleMsg":"1年後の自分へ…",
                                            "album":{/* Album 全体 */}}
```

### 4.9 Notifications `/api/notifications`

| | Path | P | Notes |
| --- | --- | --- | --- |
| GET | `` | P2 | `?unreadOnly=true`、ページング |
| GET | `/unread-count` | P2 | バッジ用 |
| POST | `/{id}/read` | P2 | `readTime` を打つ |
| POST | `/read-all` | P2 | |

`notifyType`: `FRIEND_REQUEST` / `FRIEND_ACCEPTED` / `ALBUM_INVITED` /
`ALBUM_UPDATED` / `CAPSULE_OPENABLE` / `SHARE_VIEWED`。
型ごとの追加情報は `payload`（json）に入れ、**列を増やさない**。

### 4.10 Ops

| | Path | P | Notes |
| --- | --- | --- | --- |
| GET | `/api/health` | P0 | `{status, db, aiReachable, diskFreeMb}` |
| POST | `/api/dev/seed` | P1 | **devのみ** 4ユーザー・友達・20枚・2アルバム |
| POST | `/api/dev/rebuild-denorm` | P1 | **devのみ** 全冗余列を源から再計算（§1.2.3） |

`/api/dev/seed` は1時間かける価値がある。**舞台上で再起動したあとに手作業で
デモ状態を作り直すのが、デモが死ぬ典型パターン。**

---

## 5. Algorithms

> 中核アルゴリズム。ここだけは実装前に合意しておく。

### 5.1 Photo ingestion

> 取込処理。順序が重要 —— 先に読み、次に回し、最後に消す。

```
for each uploaded file:
  1. MIME/拡張子チェック         → 不可なら rejected[] に積んで次へ
  2. EXIF を読む                 ← 削除より先に必ず読む
       DateTimeOriginal → takenTime（無ければ now）
       GPSLatitude/Longitude → latitude / longitude
       Orientation → 1..8
  3. Orientation に従い画素を物理回転
  4. EXIF を全除去して保存        ← 共有時に自宅座標を漏らさない
  5. picWidth / picHeight は「回転後」の値を記録
  6. picSize / picFormat を記録
  7. サムネイル 200/400/800px を生成 → thumbPath
```

Orientation の8値はすべて処理する。3・6・8 だけ対応した実装は必ずどこかで
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
1. takenTime 昇順にソート
2. 直前との差が GAP_MINUTES (=30) を超えたら新しいクラスタを開始
3. 1枚だけのクラスタは、時間的に最も近い隣のクラスタへ併合
     （写真1枚のアルバムは「アルバム」として成立しない）
4. 12枚を超えるクラスタは時刻順に12枚ずつ分割
     （Claude 1リクエストあたりの画像枚数上限）
5. 空入力なら 422 NO_VALID_PHOTOS
```

`GAP_MINUTES` は設定に出す。放課後の3時間は1つのアルバム、翌日は別のアルバム
—— 30分はその直感に合う。

### 5.3 Claude enrichment

> AI連携。1クラスタ1リクエスト、構造化出力、結果は必ず検証する。

**Model** `claude-opus-4-8` / **SDK** `com.anthropic:anthropic-java`

**Request**
- 画像: 各写真を長辺1080pxに縮小 → base64。**縮小は必須**（原寸だと1枚で
  約4,800トークン、1080pなら約2,000トークン）
- テキスト: 各写真の `photoId`, `takenTime`, 緯度経度（あれば）の一覧
- `thinking: adaptive`（`budget_tokens` は 4.8 では 400 になる）
- `effort: medium`、`temperature` は指定しない（同じく 400）
- timeout 60s

**Response** — Java SDK の record ベース `outputConfig` でスキーマを自動生成。
手書きスキーマもJSONパースも不要:

```java
record AlbumDraft(
    String title,          // 「最高の1日」短く、日本語
    Long coverPhotoId,     // 入力に含まれるIDであること
    String summary,        // 1文
    List<PhotoInsight> photos) {}

record PhotoInsight(
    Long photoId,
    String caption,        // 2〜5文字「放課後」
    String place,          // 推定できなければ null
    String weather,        // 晴れ / 曇り / 雨 / 雪 / null
    String comment) {}     // カジュアルな1文
```

**Validation — 返ってきたIDを信用しない**

```
- photoId が入力に無い PhotoInsight は捨てる
- coverPhotoId が不明なら takenTime 最古にフォールバック
- caption は10文字、comment は255文字で切る（列長に合わせる）
- weather が4種以外なら null
- title が空なら "YYYY.MM.DD のアルバム"
```

**Failure policy** — 429/5xx は1回だけ指数バックオフで再試行。それでもダメなら
例外を投げ、`AlbumGenerationService` が規則ベースにフォールバックして
`READY` / `aiGenerated=0` で完了する。**`AI_UNAVAILABLE` をアルバム生成の
フローに出さない。**

**Cost** — 1080px 1枚 ≈ 2,000トークン。12枚で約25,000入力トークン ≈ **$0.13**。
着手時に `count_tokens` で実測して置き換えること。

### 5.4 Decoration rendering

> 装飾の描画。クライアントが描き、サーバーは結果を預かる。

| | 担当 | 保存先 |
| --- | --- | --- |
| 要素データ（真実） | クライアントが生成、サーバーが保存 | `album_photo.overlayData` |
| 透過PNG（キャッシュ） | **クライアントが Canvas で描画** | `album_photo.overlayPath` |
| 合成JPEG（キャッシュ） | **クライアントが合成** | `album_photo.compositePath` |

クライアントに描かせるのは、Canvasも手書き風フォントも既にそちらにあるから。
Java2D で日本語手書きフォントを1px違わず再現するのは1日仕事で、誰も見比べない。

**座標は 0..1 正規化**。スマホのCanvasは390px幅、OG画像は1200px幅。絶対座標で
持つとスタンプが全部ずれる。

保存は `PUT /decoration`（JSON）と `POST /decoration/rendered`（画像2枚）の
2回に分ける。JSONだけ届いて画像が届かなくても、**要素データは失われない**。

### 5.5 Share token & OG image

> 共有トークンとOG画像。トークンは推測不能、URLはHostから組み立てる。

```java
byte[] raw = new byte[24];                    // 192 bit
SecureRandom.getInstanceStrong().nextBytes(raw);
String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);  // 32 chars
```

自增 `id` を公開URLに使わないこと。**公開ページのIDが推測できると、他人の
アルバムが総当たりで読める。**

**共有URLは必ずリクエストの `Host` ヘッダから組み立てる。**
設定ファイルにベースURLを書いた瞬間、トンネル再接続で既存リンクが全部死ぬ
（`02-basic-design.md` R-4）。

**OG collage** — 1200x630 JPEG、`CollageRenderer`:

```
枚数 1 → 中央クロップ1枚
枚数 2 → 縦2分割
枚数 3 → 左1枚 + 右上下2枚
枚数 4+ → 2x2（表紙を左上に固定）
入力は compositePath（装飾込み）。無ければ filePath
出力: quality 0.85、storage/og/{shareToken}.jpg にキャッシュ
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
if (capsule.getOpenedTime() == null) {
    return CapsuleSealedDto.of(capsule);   // capsuleMsg も album も積まない
}
return CapsuleOpenedDto.of(capsule, albumService.get(capsule.getAlbumId()));
```

封印中のDTOに `capsuleMsg` フィールドを持たせて `null` を入れる実装にしない。
**フィールドごと存在しない型を返す。** そうすれば「うっかり詰める」事故が
型レベルで起きない。

---

## 6. Configuration

> 設定。現状はH2向けなので、MySQLへ切り替える差分を示す。

### 6.1 `pom.xml` — 要変更

```diff
-<dependency>
-    <groupId>com.h2database</groupId>
-    <artifactId>h2</artifactId>
-    <scope>runtime</scope>
-</dependency>
+<dependency>
+    <groupId>com.mysql</groupId>
+    <artifactId>mysql-connector-j</artifactId>
+    <scope>runtime</scope>
+</dependency>
```

追加が必要: `spring-boot-starter-security`, `io.jsonwebtoken:jjwt-*`,
`com.drewnoakes:metadata-extractor`, `com.anthropic:anthropic-java`,
`spring-boot-starter-thymeleaf`（SSR用）。

### 6.2 `application.properties` — 要変更

```properties
spring.application.name=backend
server.port=8080

spring.datasource.url=jdbc:mysql://localhost:3306/hanamizuki?useSSL=false&serverTimezone=Asia/Tokyo&characterEncoding=utf8mb4
spring.datasource.username=hanamizuki
spring.datasource.password=hanamizuki
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# DDL は backend/sql/create_table.sql が唯一の正。
# Hibernate にスキーマを触らせない（update にすると二重管理になる）。
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false

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

> ⚠️ `ddl-auto=update` は使わない。エンティティとSQLファイルの両方がスキーマを
> 定義してしまい、どちらが正か分からなくなる。**SQLファイルが正。**

### 6.3 `.env.example`

```
ANTHROPIC_API_KEY=
APP_JWT_SECRET=
```

`.env` は `.gitignore` 済み。`data/` はH2の名残なので `storage/` だけ残せばよい。

---

## 7. Test plan

> テスト方針。全部は書けないので、壊れたら気づけない所だけ書く。

| 対象 | 種別 | 内容 |
| --- | --- | --- |
| `PhotoClusterer` | 単体 | 境界（29分/30分/31分）、1枚クラスタ併合、13枚分割、空入力 |
| `ImageProcessor` | 単体 | Orientation 1〜8 の全8ケース |
| Claude応答検証 | 単体 | 不明ID混入、cover不正、超過長、weather不正値 |
| 機能縮退 | 結合 | AIを強制失敗させ `READY / aiGenerated=0` に到達すること |
| **カプセル封印** | 結合 | **封印中の直叩きに `capsuleMsg` が含まれないこと** |
| 権限 | 結合 | 他人の `albumId` 直叩きで403 |
| 共有トークン | 結合 | 撤回後のアクセスが410 |
| 共有プレビュー | 手動 | 実機でLINEに貼り、カードが出ること |
| デモ導線 | 手動 | `01-requirements.md` §8 を実機で2回通す |

単体テストは `PhotoClusterer` と `ImageProcessor` に集中させる。この2つは
純粋で、間違いやすく、壊れても実行時まで気づけない。Controller層は手動で足りる。

---

## 8. Implementation order

> 実装順。各段階で単体で動く状態を保つ。

| # | Slice | 参照 | 目安 |
| --- | --- | --- | --- |
| 0 | MySQL切替（pom / properties / docker compose） | §6 | 1h |
| 1 | Entity + Repository + エラーハンドラ + `/api/health` | §1, §3.2 | 2h |
| 2 | 認証（JWT） | §4.1 | 2h |
| 3 | ユーザー + 友達 | §4.2, §4.3 | 2h |
| 4 | 写真取込（EXIF・回転・サムネ） | §4.4, §5.1 | 3h |
| 5 | クラスタリング + ジョブ基盤（AIなし） | §4.5, §5.2 | 2h |
| 6 | Claude連携 | §5.3 | 3h |
| 7 | 装飾（overlayData + 画像受け取り） | §4.6, §5.4 | 2h |
| 8 | 共有 SSR + OG | §4.7, §5.5 | 3h |
| 9 | カプセル | §4.8, §5.6 | 2h |
| 10 | seed + 仕上げ | §4.9 | 2h |

**0〜8 がデモ本体（20時間）。9〜10 は緩衝。**
各スライスで1ブランチ1PR（`README.md` の workflow に従う）。
