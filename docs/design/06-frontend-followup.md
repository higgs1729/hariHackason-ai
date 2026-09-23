# 06 — Frontend Follow-up / フロント追補

> Reply to `05-backend-answers.md`, plus what the first real end-to-end run found.
> Frontend → backend, 2026-09-23.

---

## 1. 05 の依頼への対応

| 05 | 対応 | 場所 |
| --- | --- | --- |
| §2 A-7 Idempotency-Key | 202 を受け取るまで同じキーを保持。写真の選択を変えたら破棄 | `frontend/src/screens/AlbumCreate.tsx` |
| A-3 ETag | 最初から `GET` の `ETag` をそのまま `If-Match` に使っていたので変更なし | `Decorate.tsx` |
| A-10 capsuleMsg | router state をやめ、`/album/{id}?capsule={capsuleId}` で `GET /api/capsules/{id}` を再取得。リロードしても消えない | `Detail.tsx`, `CapsuleDone.tsx` |
| §8.6 shoot hint | `ShootHint` 型と `api.hints.shoot` を追加。`RATE_LIMITED` は「少し待ってね」。`aiGenerated` では分岐しない。画面は未実装 | `api/types.ts`, `api/http.ts` |
| §7.3 | そのまま守っている | — |

## 2. 結合で見つかったこと

`docker compose up -d` → `spring-boot:run` → `/api/dev/seed` → フロント `http` モードで、
ログイン・詳細・デコ保存（PUT + rendered）・共有・カプセル作成 / 開封・
写真アップロード・生成ジョブまで一周通った（2026-09-23）。

### 2.1 画像が 401 — フロント側で解決済み

`photoUrl` / `thumbUrl` / `compositeUrl` は `/api/...` で認証必須。`<img>` や
CSS の `background-image` は `Authorization` を送れないので、全部 401 だった。

**フロントで Bearer 付き `fetch` → `blob:` URL にして解決した**（`frontend/src/api/images.ts`）。
バックエンドの変更は不要。画像を `permitAll` にするのは §1.1.2（ID が推測できる）に反するので、しないでほしい。

将来、枚数が増えて重くなったら、期限つき署名 URL（`?sig=&exp=`）に切り替える案がある。9/27 までは不要。

### 2.2 JDK 23+ で Lombok が動かない — `pom.xml` を修正済み

JDK 23 以降の `javac` は、classpath 上の注釈処理器を既定では実行しない。そのため JDK 25 では
getter / setter が生成されず、コンパイルエラーが約 100 件出た。
`maven-compiler-plugin` の `annotationProcessorPaths` に Lombok を明示した。JDK 21 でもそのまま動く。

## 3. まだ決まっていないこと

- **Q-2（iPhone か Android か）**: 未決。決まり次第 HEIC の扱いを 05 §6 のとおり進める。
- **プリクラの画面**: API は揃ったので、次にフロントで作る。
