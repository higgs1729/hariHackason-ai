# 09 — Audit

Written by `tools/audit_md.py` from the machine results; edit NOTES there, not this file.

All checks ran against the production build served by Spring on :8080 (`apiMode` http, MySQL, Claude CLI as the AI), logged in as the seeded `nao` unless stated.

| Source | What it does | Result |
| --- | --- | --- |
| `docs/e2e/run-1` (`tools/e2e.py`) | the §8 demo end to end in Chrome, 16 screenshots | PASS (2026-09-27 02:43:20) |
| `docs/e2e/run-2` (`tools/e2e.py`) | the §8 demo end to end in Chrome, 16 screenshots | PASS (2026-09-27 02:43:47) |
| `docs/audit/requirements.json` (`tools/req_check.py`) | API checks per requirement, plus FR-03.6 on a second server whose Claude CLI path is broken | 28/28 pass |
| `docs/audit/controls.json` (`tools/audit.py`) | every button, link and tab on every screen, pressed one at a time from a fresh load | 303/303 OK |

## A. Requirements (P0 and P1)

| ID | P | Requirement | Evidence | OK |
| --- | --- | --- | --- | --- |
| FR-01 | P0 | ブラウザのカメラで写真を撮影できる | e2e `camera` in both runs: yes<br>e2e `camera-3-shots` in both runs: yes<br>audit camera 「写真を撮る」: opens the photo picker<br>Chrome with a fake camera device fed from a still image; a real camera on a phone is in 09-handoff-human.md. | OK |
| FR-01.1 | P1 | 端末のライブラリからも選択できる | audit camera 「写真ライブラリから選ぶ」: opens the photo picker | OK |
| FR-02 | P0 | 撮影・選択した写真を複数枚まとめてアップロードできる | req_check FR-02: pass `{"status": 200, "uploaded": 2}`<br>e2e `camera-3-shots` in both runs: yes | OK |
| FR-02.1 | P0 | 撮影時刻・位置情報を自動で取り出す | req_check FR-02.1: pass `{"takenTime": "2026-09-20T17:30:00+09:00", "latitude": 34.7025, "longitude": 135.49589999999998}` | OK |
| FR-02.2 | P0 | 画像の向きを自動補正する | req_check FR-02.2: pass `{"picWidth": 200, "picHeight": 300}` | OK |
| FR-03 | P0 | AIが写真を自動でアルバムにまとめる | e2e `album-generating` in both runs: yes<br>e2e `album-detail` in both runs: yes<br>audit album-create 「AIでアルバムにまとめる」: opens `/album/generating/17` | OK |
| FR-03.1 | P0 | 撮影時刻でグループ分けし、1回の操作で複数アルバムができる | req_check FR-03.1: pass `{"status": "READY", "albumIds": [32, 33], "seconds": 18.1}` | OK |
| FR-03.2 | P0 | アルバムのタイトル・説明をAIが生成する | req_check FR-03.2: pass `[{"title": "遊園地日和", "aiGenerated": 1, "aiModel": "claude-cli:sonnet"}, {"title": "友達との時間", "aiGenerated": 1, "aiModel": "claude-cli:sonnet"}]`<br>e2e `album-detail` in both runs: yes | OK |
| FR-03.3 | P0 | 写真ごとの短いキャプションをAIが生成する | req_check FR-03.3: pass `["集合写真", "屋台グルメ", "遊園地で", "夕焼け", "花火大会", "桜並木"]`<br>e2e `album-detail` in both runs: yes | OK |
| FR-03.4 | P1 | 表紙をAIが選ぶ | req_check FR-03.4: pass `[98, 100]` | OK |
| FR-03.5 | P1 | 天気・場所を画像からAIが推定する | req_check FR-03.5: pass `{"weather": ["晴れ", null, "晴れ", "晴れ", null, "晴れ"], "place": [null, null, null, null, null, null]}` | OK |
| FR-03.6 | P0 | AI失敗時も規則ベースでアルバムを作る | req_check FR-03.6: pass `{"aiReachable": false, "status": "READY", "title": "2026.09.22 のアルバム", "aiGenerated": 0, "captions": ["12:00", "12:01", "12:02"], "seconds": 1.0}; {"status": 200, "hint": "前後に少しずらして並ぶと、全員の顔が入るよ", "aiGenerated": 0}` | OK |
| FR-04 | P0 | 写真の上に手書きで線を描ける | req_check FR-04: pass `{"put": 200, "elements": ["stroke", "text"]}`<br>e2e `decorate` in both runs: yes<br>audit decorate 「ペン」: sets ペン pressed=false | OK |
| FR-04.1 | P0 | 文字を入れられる（手書き風フォント） | req_check FR-04.1: pass `["stroke", "text"]`<br>e2e `decorate` in both runs: yes<br>audit decorate-text 「追加」: changes the drawing; sets 文字 pressed=false; removes 「追加」 | OK |
| FR-04.2 | P1 | スタンプを貼れる | req_check FR-04.2: pass `{"put with sticker": 200}`<br>e2e `decorate` in both runs: yes<br>audit decorate 「ハートのスタンプ」: sets ペン pressed=false, ハートのスタンプ pressed=true | OK |
| FR-04.3 | P0 | 装飾を後から編集・取り消しできる | req_check FR-04.3: pass `{"second put": 200, "elements now": ["stroke", "text"]}`<br>audit decorate-stamped 「ひとつ戻す」: changes the drawing | OK |
| FR-05 | P0 | アルバムの共有リンクを発行できる | req_check FR-05: pass `{"status": 200, "shareUrl": "http://localhost:8080/s/h6K824-xNe3z7uCIuay9aGXcmouD2qq9"}`<br>e2e `share-link` in both runs: yes<br>audit share 「友達と共有する」: shows 「http://localhost:8080/s/5rQBASFSi9z01bd0F14jJBVHvECnGwNs」, 「タイムカプセルを作成する」; shows 「http://localhost:8080/s/」 / 「タイムカプセルを作成する」 | OK |
| FR-05.1 | P0 | LINE等にネイティブ共有シートで送れる | e2e `share-link` in both runs: yes<br>The button calls navigator.share with the link and also shows the link on the page (the e2e path). Chrome on Windows has navigator.share too (the Windows share dialog); the phone share sheet with LINE is checked by a person: 09-handoff-human.md. | OK |
| FR-05.2 | P0 | 共有リンクがLINEでプレビューカードとして表示される | req_check FR-05.2: pass `{"page": 200, "og": 200, "ogBytes": 86484}`<br>e2e `friend-view-logged-out` in both runs: yes<br>The tags and the image are served; how LINE renders the card is checked on a phone: 09-handoff-human.md. | OK |
| FR-05.3 | P0 | 受け取った側はログイン不要で閲覧できる | req_check FR-05.3: pass `{"status": 200, "title": "遊園地日和", "photos": 3}`<br>e2e `friend-view-logged-out` in both runs: yes | OK |
| FR-06 | P1 | アルバムをタイムカプセルとして封印できる | req_check FR-06: pass `{"status": 200, "capsule": {"id": 9, "status": "SEALED", "openTime": "2027-09-27T12:00:00+09:00", "daysRemaining": 365}}`<br>e2e `capsule-create` in both runs: yes<br>e2e `capsule-done` in both runs: yes<br>audit capsule-create 「タイムカプセルを作成する」: opens `/capsule/8` | OK |
| FR-06.1 | P1 | 開封日まで中身が取得できない | req_check FR-06.1: pass `{"get": 200, "fields": ["daysRemaining", "id", "openTime", "status"], "open": 409, "code": "CAPSULE_NOT_YET_OPEN", "messageLeaked": false}`<br>e2e `capsule-done` in both runs: yes | OK |
| FR-07 | P0 | アカウント名とパスワードで登録・ログインできる | req_check FR-07: pass `{"register": 200, "login": 200, "wrongPassword": 401, "me": "reg0c7975"}`<br>e2e `login-form` in both runs: yes<br>e2e `my-page` in both runs: yes<br>audit home 「ログイン」: shows 「アカウントを作る」; shows 「アカウントを作る」<br>audit home 「はじめる」: shows 「ログインはこちら」; shows 「アカウントを作る」 / 「ログインはこちら」 | OK |
| FR-07.1 | P0 | ログイン状態が保持される | req_check FR-07.1: pass `{"refresh": 200}` | OK |
| FR-08 | P1 | ユーザーを検索して友達申請・承認できる | req_check FR-08: pass `["rin"]; {"request": 204, "inbox": true, "accept": 204, "friends": true}`<br>audit me-friends-search 「申請」: shows 「申請しました」 | OK |
| FR-09 | P1 | アルバムに友達をメンバーとして追加できる | req_check FR-09: pass `{"status": 204, "members": ["わたし", "あやか"]}`<br>e2e `share` in both runs: yes<br>audit share 「あやか」: sets あやかを見せる相手にする pressed=true; shows 「2人」 | OK |
| FR-09.1 | P1 | メンバーが写真追加・装飾できる | req_check FR-09.1: pass `{"ayaka put": 200}` | OK |
| FR-09.2 | P1 | 同時編集の競合を検出できる（楽観ロック） | req_check FR-09.2: pass `{"status": 409, "code": "VERSION_CONFLICT"}` | OK |
| FR-10 | P0 | 写真の詳細情報（場所・時間・音楽・コメント・メンバー・天気）を見られる | req_check FR-10: pass `{"place": null, "takenTime": "2026-09-21T10:00:00+09:00", "music": null, "photoComment": "3人とも良い笑顔すぎる！", "weather": "晴れ"}`<br>e2e `album-detail` in both runs: yes | OK |
| FR-10.1 | P1 | 詳細情報を手で編集できる | req_check FR-10.1: pass `{"status": 200, "photoComment": "audit comment", "music": "audit song"}`<br>audit detail-meta-edit 「保存」: shows 「編集」; shows 「編集」 | OK |

## B. Every control on every screen

Each state is loaded fresh (reload, log in through the stored refresh token, replay the setup clicks), then one control is pressed and the page is compared before and after. Screenshots: `docs/audit/<state>-<nn>.jpg`.

### home — `/`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | はじめる | shows 「ログインはこちら」; shows 「アカウントを作る」 / 「ログインはこちら」 | OK |
| 2 | ログイン | shows 「アカウントを作る」; shows 「アカウントを作る」 | OK |

### home-login — `/` after ログイン

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | ログイン | with the fields empty the browser's required-field check stops the submit; with nao / password it logs in (e2e step login-form → my-page) | OK |
| 2 | アカウントを作る | shows 「はじめる」, 「ログインはこちら」; shows 「はじめる」 / 「ログインはこちら」 | OK |

### home-register — `/` after はじめる

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | はじめる | with the fields empty the browser's required-field check stops the submit; registering is checked by req_check FR-07 | OK |
| 2 | ログインはこちら | shows 「アカウントを作る」, 「ログイン」; shows 「ログイン」 / 「ログイン」 | OK |

### me-albums — `/me`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 名前を変える | shows 「やめる」, 「保存」; shows 「やめる」 / 「保存」 | OK |
| 2 | アルバム | opens `/me?tab=albums` | OK |
| 3 | カプセル | opens `/me?tab=capsules`; sets アルバム selected=false, カプセル selected=true | OK |
| 4 | 友達 | opens `/me?tab=friends`; sets アルバム selected=false, 友達 selected=true | OK |
| 5 | 彩り豊かな日 2026.09.27 | opens `/album/30` | OK |
| 6 | 桜と花火の一日 2026.09.27 | opens `/album/18` | OK |
| 7 | 青空と笑顔 2026.09.27 | opens `/album/14` | OK |
| 8 | 青空と笑顔 2026.09.27 | opens `/album/13` | OK |
| 9 | 青空と笑顔の日 2026.09.27 | opens `/album/12` | OK |
| 10 | 友達と青空日和 2026.09.27 | opens `/album/11` | OK |
| 11 | 青空と笑顔の日 2026.09.27 | opens `/album/10` | OK |
| 12 | 季節をこえた思い出 2026.09.27 | opens `/album/9` | OK |
| 13 | 友との特別な日 2026.09.27 | opens `/album/6` | OK |
| 14 | 青春の一日 2026.09.25 | opens `/album/8` | OK |
| 15 | 仲間と過ごした一日 2026.09.25 | opens `/album/5` | OK |
| 16 | お昼のピース 2026.09.25 | opens `/album/2` | OK |
| 17 | 秋晴れと笑顔の一日 2026.09.24 | opens `/album/7` | OK |
| 18 | 青空とはしゃぐ午後 2026.09.24 | opens `/album/4` | OK |
| 19 | 2026.09.24 のアルバム 2026.09.24 | opens `/album/3` | OK |
| 20 | 最高の1日 2026.09.24 | opens `/album/1` | OK |
| 21 | 2026.09.22 のアルバム 2026.09.22 | opens `/album/24` | OK |
| 22 | 2026.09.22 のアルバム 2026.09.22 | opens `/album/21` | OK |
| 23 | 友達との思い出 2026.09.22 | opens `/album/16` | OK |
| 24 | 特別な一日 2026.09.21 | opens `/album/28` | OK |
| 25 | 青空と笑顔の日 2026.09.21 | opens `/album/27` | OK |
| 26 | 友達と特別な一日 2026.09.21 | opens `/album/26` | OK |
| 27 | 秋晴れの遠足日和 2026.09.21 | opens `/album/25` | OK |
| 28 | 友達との思い出 2026.09.21 | opens `/album/23` | OK |
| 29 | 青空と笑顔の遠足 2026.09.21 | opens `/album/22` | OK |
| 30 | 友達との時間 2026.09.21 | opens `/album/20` | OK |
| 31 | 青空とはしゃぐ休日 2026.09.21 | opens `/album/19` | OK |
| 32 | 夕焼けと青空の一日 2026.09.21 | opens `/album/17` | OK |
| 33 | 友達と青空日和 2026.09.21 | opens `/album/15` | OK |
| 34 | 夕焼けと青春 2026.09.20 | opens `/album/29` | OK |
| 35 | 写真を撮る | opens `/camera` | OK |

### me-capsules — `/me?tab=capsules`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 名前を変える | shows 「やめる」, 「保存」; shows 「やめる」 / 「保存」 | OK |
| 2 | アルバム | opens `/me?tab=albums`; sets アルバム selected=true, カプセル selected=false | OK |
| 3 | カプセル | already selected here; pressing it again keeps it selected | OK |
| 4 | 友達 | opens `/me?tab=friends`; sets カプセル selected=false, 友達 selected=true | OK |
| 5 | 青空と笑顔の日 2026.09.27 に開けた | opens `/album/10?capsule=1` | OK |
| 6 | 友達と青空日和 2026.09.27 に開けた | opens `/album/11?capsule=2` | OK |
| 7 | 青空と笑顔の日 2026.09.27 に開けた | opens `/album/12?capsule=3` | OK |
| 8 | 青空と笑顔 2026.09.27 に開けた | opens `/album/13?capsule=4` | OK |
| 9 | 青空と笑顔 2026.09.27 に開けた | opens `/album/14?capsule=5` | OK |
| 10 | あと365日 2027.09.27 に開けられる | opens `/capsule/6` | OK |
| 11 | あと365日 2027.09.27 に開けられる | opens `/capsule/7` | OK |
| 12 | 写真を撮る | opens `/camera` | OK |

### me-friends — `/me?tab=friends`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 名前を変える | shows 「やめる」, 「保存」; shows 「やめる」 / 「保存」 | OK |
| 2 | アルバム | opens `/me?tab=albums`; sets アルバム selected=true, 友達 selected=false | OK |
| 3 | カプセル | opens `/me?tab=capsules`; sets カプセル selected=true, 友達 selected=false | OK |
| 4 | 友達 | already selected here; pressing it again keeps it selected | OK |
| 5 | QRで友達を追加 | opens `/friends/qr` | OK |
| 6 | さがす | disabled while the search box is empty; used in me-friends-search | OK |
| 7 | あやか @ayaka | opens `/reunion/2` | OK |
| 8 | みき @miki | opens `/reunion/3` | OK |
| 9 | りん @rin | opens `/reunion/4` | OK |
| 10 | 監査 @auda0cad6 | opens `/reunion/5` | OK |
| 11 | 監査 @auda565d4 | opens `/reunion/6` | OK |
| 12 | 監査 @auddf24e0 | opens `/reunion/7` | OK |
| 13 | 監査 @aud58b21d | opens `/reunion/8` | OK |
| 14 | 写真を撮る | opens `/camera` | OK |

### me-name-edit — `/me` after 名前を変える

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | やめる | shows 「名前を変える」; shows 「わたし」 / 「@nao」 | OK |
| 2 | 保存 | shows 「名前を変える」; shows 「わたし」 / 「@nao」 | OK |
| 3 | アルバム | opens `/me?tab=albums` | OK |
| 4 | カプセル | opens `/me?tab=capsules`; sets アルバム selected=false, カプセル selected=true | OK |
| 5 | 友達 | opens `/me?tab=friends`; sets アルバム selected=false, 友達 selected=true | OK |
| 6 | 桜と花火の一日 2026.09.27 | opens `/album/18` | OK |
| 7 | 青空と笑顔 2026.09.27 | opens `/album/14` | OK |
| 8 | 青空と笑顔 2026.09.27 | opens `/album/13` | OK |
| 9 | 青空と笑顔の日 2026.09.27 | opens `/album/12` | OK |
| 10 | 友達と青空日和 2026.09.27 | opens `/album/11` | OK |
| 11 | 青空と笑顔の日 2026.09.27 | opens `/album/10` | OK |
| 12 | 季節をこえた思い出 2026.09.27 | opens `/album/9` | OK |
| 13 | 友との特別な日 2026.09.27 | opens `/album/6` | OK |
| 14 | 青春の一日 2026.09.25 | opens `/album/8` | OK |
| 15 | 仲間と過ごした一日 2026.09.25 | opens `/album/5` | OK |
| 16 | お昼のピース 2026.09.25 | opens `/album/2` | OK |
| 17 | 秋晴れと笑顔の一日 2026.09.24 | opens `/album/7` | OK |
| 18 | 青空とはしゃぐ午後 2026.09.24 | opens `/album/4` | OK |
| 19 | 2026.09.24 のアルバム 2026.09.24 | opens `/album/3` | OK |
| 20 | 最高の1日 2026.09.24 | opens `/album/1` | OK |
| 21 | 2026.09.22 のアルバム 2026.09.22 | opens `/album/24` | OK |
| 22 | 2026.09.22 のアルバム 2026.09.22 | opens `/album/21` | OK |
| 23 | 友達との思い出 2026.09.22 | opens `/album/16` | OK |
| 24 | 特別な一日 2026.09.21 | opens `/album/28` | OK |
| 25 | 青空と笑顔の日 2026.09.21 | opens `/album/27` | OK |
| 26 | 友達と特別な一日 2026.09.21 | opens `/album/26` | OK |
| 27 | 秋晴れの遠足日和 2026.09.21 | opens `/album/25` | OK |
| 28 | 友達との思い出 2026.09.21 | opens `/album/23` | OK |
| 29 | 青空と笑顔の遠足 2026.09.21 | opens `/album/22` | OK |
| 30 | 友達との時間 2026.09.21 | opens `/album/20` | OK |
| 31 | 青空とはしゃぐ休日 2026.09.21 | opens `/album/19` | OK |
| 32 | 夕焼けと青空の一日 2026.09.21 | opens `/album/17` | OK |
| 33 | 友達と青空日和 2026.09.21 | opens `/album/15` | OK |
| 34 | 写真を撮る | opens `/camera` | OK |

### friend-qr-show — `/friends/qr?mode=show`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/me?tab=friends` | OK |
| 2 | 見せる | shows 「あと 9:57 で新しいQRに変わります」 | OK |
| 3 | 読み取る | opens `/friends/qr?mode=scan`; sets 見せる selected=false, 読み取る selected=true | OK |

### friend-qr-scan — `/friends/qr?mode=scan`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/me?tab=friends` | OK |
| 2 | 見せる | opens `/friends/qr?mode=show`; sets 見せる selected=true, 読み取る selected=false | OK |
| 3 | 読み取る | already selected here; pressing it again keeps it selected | OK |
| 4 | 追加 | disabled until a code is scanned or typed | OK |

### reunion — `/reunion/2`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 閉じる | opens `/me?tab=friends` | OK |
| 2 | 次の写真 | shows 「青空と笑顔の日 2026.09.21 ・ 屋台グルメ」; shows 「2026.09.21 ・ 屋台グルメ」 | OK |
| 3 | 青空と笑顔の日 2026.09.21 ・ 青空セルフィー | opens `/album/27` | OK |
| 4 | 止める | shows 「再生」 | OK |

### camera — `/camera`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 閉じる | opens `/me` | OK |
| 2 | 写真ライブラリから選ぶ | opens the photo picker | OK |
| 3 | AIに撮り方を聞く | shows 「1人増やす」, 「1人減らす」, 「かっこよく」; shows 「何人？」 / 「3人」 | OK |
| 4 | アルバムを作成 | opens `/album/new` | OK |
| 5 | 写真を撮る | opens the photo picker | OK |
| 6 | カメラを切り替える | switches between the front and back camera; the fake device on the desktop has one camera, so the picture stays the same (phone: 09-handoff-human.md) | OK |

### camera-hint — `/camera` after AIに撮り方を聞く

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 閉じる | covered by 「AIに撮り方を聞く」 in this state (reachable again once that is closed) | OK |
| 2 | 写真ライブラリから選ぶ | covered by 「AIに撮り方を聞く」 in this state (reachable again once that is closed) | OK |
| 3 | AIに撮り方を聞く | covered by 「AIに撮り方を聞く (open sheet)」 in this state (reachable again once that is closed) | OK |
| 4 | アルバムを作成 | covered by 「AIに撮り方を聞く (open sheet)」 in this state (reachable again once that is closed) | OK |
| 5 | 写真を撮る | covered by 「AIに撮り方を聞く (open sheet)」 in this state (reachable again once that is closed) | OK |
| 6 | カメラを切り替える | covered by 「AIに撮り方を聞く (open sheet)」 in this state (reachable again once that is closed) | OK |
| 7 | 閉じる | removes 「AIに撮り方を聞く」 / 「何人？」 | OK |
| 8 | 1人減らす | shows 「2人」 | OK |
| 9 | 1人増やす | shows 「4人」 | OK |
| 10 | わちゃわちゃ | already selected here; pressing it again keeps it selected | OK |
| 11 | エモく | sets わちゃわちゃ checked=false, エモく checked=true | OK |
| 12 | かわいく | sets わちゃわちゃ checked=false, かわいく checked=true | OK |
| 13 | かっこよく | sets わちゃわちゃ checked=false, かっこよく checked=true | OK |
| 14 | 聞いてみる | shows 「考え中…」; shows 「考え中…」 | OK |
| 15 | みんなで一斉にジャンプ！ | shows 「これで撮る」, 「ほかの案」; shows 「3、2、1でジャンプ」 / 「スロー再生すると青春っぽい」 | OK |
| 16 | 今度は一人ずつ増えてみよう！ | shows 「これで撮る」, 「ほかの案」; shows 「最初は一人だけ」 / 「1枚撮るごとに一人ずつ増える」 | OK |
| 17 | 空中で止まろう！ | shows 「これで撮る」, 「ほかの案」; shows 「ジャンプした瞬間を連写」 / 「つなげると空中に浮いて見える」 | OK |
| 18 | みんなで瞬間移動！ | shows 「これで撮る」, 「ほかの案」; shows 「同じポーズのまま」 / 「場所を少しずつずらして撮る」 | OK |
| 19 | 全員で変なことして！ | shows 「これで撮る」, 「ほかの案」; shows 「全員で変な動き」 / 「数年後に見返すと絶対笑う」 | OK |
| 20 | カメラを順番にのぞき込んで！ | shows 「これで撮る」, 「ほかの案」; shows 「一人ずつ画面に顔を出す」 / 「最後に全員集合」 | OK |
| 21 | 秘密の合言葉を決めよう！ | shows 「これで撮る」, 「ほかの案」; shows 「その場で謎の言葉を決める」 / 「全員で一緒に叫ぶ」 | OK |
| 22 | この続きを未来で撮ろう！ | shows 「これで撮る」, 「ほかの案」; shows 「一人が手を差し出すところまで撮る」 / 「再会したら相手がその手を取る」 | OK |
| 23 | みんなで文字を作ってみよう！ | shows 「これで撮る」, 「ほかの案」; shows 「1枚ごとに少しずつ移動」 / 「最後にハートなどの形を作る」 | OK |
| 24 | 未来で完成させる動画を撮ろう！ | shows 「これで撮る」, 「ほかの案」; shows 「全員で「せーの……」まで撮って終了」 / 「再会した日に続きを撮る」 | OK |
| 25 | 違う場所で撮ってみよう！ | shows 「これで撮る」, 「ほかの案」; shows 「いろんな場所で」 / 「同じポーズ・同じ構図で撮る」 | OK |
| 26 | みんなで歩こう！ | shows 「これで撮る」, 「ほかの案」; shows 「少しずつ前に進みながら撮る」 / 「最後に大きな一歩！」 | OK |

### camera-hint-result — `/camera` after AIに撮り方を聞く → 聞いてみる

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 閉じる | covered by 「AIに撮り方を聞く」 in this state (reachable again once that is closed) | OK |
| 2 | 写真ライブラリから選ぶ | covered by 「AIに撮り方を聞く」 in this state (reachable again once that is closed) | OK |
| 3 | AIに撮り方を聞く | covered by 「AIに撮り方を聞く (open sheet)」 in this state (reachable again once that is closed) | OK |
| 4 | アルバムを作成 | covered by 「AIに撮り方を聞く (open sheet)」 in this state (reachable again once that is closed) | OK |
| 5 | 写真を撮る | covered by 「AIに撮り方を聞く (open sheet)」 in this state (reachable again once that is closed) | OK |
| 6 | カメラを切り替える | covered by 「AIに撮り方を聞く (open sheet)」 in this state (reachable again once that is closed) | OK |
| 7 | 閉じる | removes 「AIに撮り方を聞く」 / 「3人でくっついてわちゃわちゃ感を全開に出そう」 | OK |
| 8 | ほかの案 | shows 「考え中…」; shows 「考え中…」 | OK |
| 9 | これで撮る | shows 「撮り方を消す」 | OK |

### album-create — `/album/new`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/camera` | OK |
| 2 | その他のオプション | sets その他のオプション expanded=true; shows 「マイページ」, 「ログアウト」; shows 「マイページ」 / 「ログアウト」 | OK |
| 3 | 17:30の写真を外す | shows 「17:30の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 4 | 17:30の写真を外す | shows 「17:30の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 5 | 17:30の写真を外す | shows 「17:30の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 6 | 17:30の写真を外す | shows 「17:30の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 7 | 17:31の写真を外す | shows 「17:31の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 8 | 17:31の写真を外す | shows 「17:31の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 9 | 17:31の写真を外す | shows 「17:31の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 10 | 17:31の写真を外す | shows 「17:31の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 11 | 02:04の写真を外す | shows 「02:04の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 12 | 02:04の写真を外す | shows 「02:04の写真を入れる」, 「AIでアルバムにまとめる（9枚）」; shows 「9/10」 / 「AIでアルバムにまとめる（9枚）」 | OK |
| 13 | AIでアルバムにまとめる（10枚） | opens `/album/generating/17` | OK |

### detail — `/album/18`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/me` | OK |
| 2 | その他のオプション | sets その他のオプション expanded=true; shows 「マイページ」, 「ログアウト」; shows 「マイページ」 / 「ログアウト」 | OK |
| 3 | タイトルを編集 | shows 「やめる」, 「保存」; shows 「やめる」 / 「保存」 | OK |
| 4 | 編集 | shows 「やめる」, 「保存」, 「閉じる」; shows 「閉じる」 / 「やめる」 | OK |
| 5 | 桜並木 | already selected here; pressing it again keeps it selected | OK |
| 6 | 花火と笑顔 | sets 花火と笑顔 pressed=true; shows 「花火バックにみんなで撮れて最高の一枚」 | OK |
| 7 | 桜並木 | shows 「この道歩くだけで春を感じたよね」 | OK |
| 8 | 花火と笑顔 | sets 花火と笑顔 pressed=true; shows 「みんなの笑顔が花火より輝いてた」 | OK |
| 9 | 桜並木 | shows 「2026.09.27 01:48」 / 「何回見ても飽きない桜のトンネル」 | OK |
| 10 | 花火と笑顔 | sets 花火と笑顔 pressed=true; shows 「2026.09.27 01:48」 / 「この瞬間ずっと覚えておきたいな」 | OK |
| 11 | この写真を飾る | opens `/album/18/decorate/61` | OK |
| 12 | シェア | opens `/album/18/share` | OK |

### detail-more — `/album/18` after その他のオプション

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 2 | その他のオプション | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 3 | マイページ | opens `/me` | OK |
| 4 | ログアウト | opens `/` | OK |
| 5 | タイトルを編集 | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 6 | 編集 | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 7 | 桜並木 | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 8 | 花火と笑顔 | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 9 | 桜並木 | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 10 | 花火と笑顔 | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 11 | 桜並木 | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 12 | 花火と笑顔 | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 13 | この写真を飾る | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |
| 14 | シェア | sets その他のオプション expanded=false; removes 「マイページ」 / 「ログアウト」 | OK |

### detail-title-edit — `/album/18` after タイトルを編集

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/me` | OK |
| 2 | その他のオプション | sets その他のオプション expanded=true; shows 「マイページ」, 「ログアウト」; shows 「マイページ」 / 「ログアウト」 | OK |
| 3 | やめる | shows 「タイトルを編集」; shows 「桜と花火の一日」 | OK |
| 4 | 保存 | shows 「タイトルを編集」; shows 「桜と花火の一日」 | OK |
| 5 | 編集 | shows 「閉じる」; shows 「閉じる」 | OK |
| 6 | 桜並木 | already selected here; pressing it again keeps it selected | OK |
| 7 | 花火と笑顔 | sets 花火と笑顔 pressed=true; shows 「花火バックにみんなで撮れて最高の一枚」 | OK |
| 8 | 桜並木 | shows 「この道歩くだけで春を感じたよね」 | OK |
| 9 | 花火と笑顔 | sets 花火と笑顔 pressed=true; shows 「みんなの笑顔が花火より輝いてた」 | OK |
| 10 | 桜並木 | shows 「2026.09.27 01:48」 / 「何回見ても飽きない桜のトンネル」 | OK |
| 11 | 花火と笑顔 | sets 花火と笑顔 pressed=true; shows 「2026.09.27 01:48」 / 「この瞬間ずっと覚えておきたいな」 | OK |
| 12 | この写真を飾る | opens `/album/18/decorate/61` | OK |
| 13 | シェア | opens `/album/18/share` | OK |

### detail-meta-edit — `/album/18` after 編集

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/me` | OK |
| 2 | その他のオプション | sets その他のオプション expanded=true; shows 「マイページ」, 「ログアウト」; shows 「マイページ」 / 「ログアウト」 | OK |
| 3 | タイトルを編集 | removes 「桜と花火の一日」 | OK |
| 4 | 閉じる | shows 「編集」; shows 「編集」 | OK |
| 5 | 桜並木 | already selected here; pressing it again keeps it selected | OK |
| 6 | 花火と笑顔 | sets 花火と笑顔 pressed=true; shows 「花火バックにみんなで撮れて最高の一枚」 | OK |
| 7 | 桜並木 | shows 「この道歩くだけで春を感じたよね」 | OK |
| 8 | 花火と笑顔 | sets 花火と笑顔 pressed=true; shows 「みんなの笑顔が花火より輝いてた」 | OK |
| 9 | 桜並木 | shows 「2026.09.27 01:48」 / 「何回見ても飽きない桜のトンネル」 | OK |
| 10 | 花火と笑顔 | sets 花火と笑顔 pressed=true; shows 「2026.09.27 01:48」 / 「この瞬間ずっと覚えておきたいな」 | OK |
| 11 | この写真を飾る | opens `/album/18/decorate/61` | OK |
| 12 | シェア | opens `/album/18/share` | OK |
| 13 | やめる | shows 「編集」; shows 「編集」 | OK |
| 14 | 保存 | shows 「編集」; shows 「編集」 | OK |

### decorate — `/album/30/decorate/105`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/album/30` | OK |
| 2 | 保存 | opens `/album/30/share` | OK |
| 3 | 花火大会を編集する | opens `/album/30/decorate/106`; sets ひとつ戻す disabled | OK |
| 4 | ペン | sets ペン pressed=false | OK |
| 5 | ハートのスタンプ | sets ペン pressed=false, ハートのスタンプ pressed=true | OK |
| 6 | 文字 | sets 文字 pressed=true; shows 「追加」; shows 「追加」 | OK |
| 7 | ひとつ戻す | changes the drawing | OK |
| 8 | 色 #ff7fb0 | already selected here; pressing it again keeps it selected | OK |
| 9 | 色 #ffffff | sets 色 #ff7fb0 pressed=false, 色 #ffffff pressed=true | OK |
| 10 | 色 #14264d | sets 色 #ff7fb0 pressed=false, 色 #14264d pressed=true | OK |
| 11 | 色 #ffd166 | sets 色 #ff7fb0 pressed=false, 色 #ffd166 pressed=true | OK |
| 12 | 保存して友達とシェア | opens `/album/30/share` | OK |

### decorate-text — `/album/30/decorate/105` after 文字

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/album/30` | OK |
| 2 | 保存 | opens `/album/30/share` | OK |
| 3 | 花火大会を編集する | opens `/album/30/decorate/106`; sets ひとつ戻す disabled | OK |
| 4 | ペン | sets ペン pressed=false | OK |
| 5 | ハートのスタンプ | sets ペン pressed=false, ハートのスタンプ pressed=true | OK |
| 6 | 文字 | sets 文字 pressed=false; removes 「追加」 | OK |
| 7 | ひとつ戻す | changes the drawing | OK |
| 8 | 色 #ff7fb0 | already selected here; pressing it again keeps it selected | OK |
| 9 | 色 #ffffff | sets 色 #ff7fb0 pressed=false, 色 #ffffff pressed=true | OK |
| 10 | 色 #14264d | sets 色 #ff7fb0 pressed=false, 色 #14264d pressed=true | OK |
| 11 | 色 #ffd166 | sets 色 #ff7fb0 pressed=false, 色 #ffd166 pressed=true | OK |
| 12 | 追加 | changes the drawing; sets 文字 pressed=false; removes 「追加」 | OK |
| 13 | 保存して友達とシェア | opens `/album/30/share` | OK |

### decorate-stamped — `/album/30/decorate/105` after ハートのスタンプ → tap 落書きキャンバス

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/album/30` | OK |
| 2 | 保存 | opens `/album/30/share` | OK |
| 3 | 花火大会を編集する | opens `/album/30/decorate/106`; sets ひとつ戻す disabled | OK |
| 4 | ペン | sets ペン pressed=true, ハートのスタンプ pressed=false | OK |
| 5 | ハートのスタンプ | sets ハートのスタンプ pressed=false | OK |
| 6 | 文字 | sets 文字 pressed=true; shows 「追加」; shows 「追加」 | OK |
| 7 | ひとつ戻す | changes the drawing | OK |
| 8 | 色 #ff7fb0 | already selected here; pressing it again keeps it selected | OK |
| 9 | 色 #ffffff | sets 色 #ff7fb0 pressed=false, 色 #ffffff pressed=true | OK |
| 10 | 色 #14264d | sets 色 #ff7fb0 pressed=false, 色 #14264d pressed=true | OK |
| 11 | 色 #ffd166 | sets 色 #ff7fb0 pressed=false, 色 #ffd166 pressed=true | OK |
| 12 | 保存して友達とシェア | opens `/album/30/share` | OK |

### share — `/album/18/share`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/album/18/decorate/61` | OK |
| 2 | その他のオプション | sets その他のオプション expanded=true; shows 「マイページ」, 「ログアウト」; shows 「マイページ」 / 「ログアウト」 | OK |
| 3 | あやかを見せる相手にする | sets あやかを見せる相手にする pressed=true; shows 「2人」 | OK |
| 4 | みきを見せる相手にする | sets みきを見せる相手にする pressed=true; shows 「2人」 | OK |
| 5 | りんを見せる相手にする | sets りんを見せる相手にする pressed=true; shows 「2人」 | OK |
| 6 | 監査を見せる相手にする | sets 監査を見せる相手にする pressed=true; shows 「2人」 | OK |
| 7 | 監査を見せる相手にする | sets 監査を見せる相手にする pressed=true; shows 「2人」 | OK |
| 8 | 監査を見せる相手にする | sets 監査を見せる相手にする pressed=true; shows 「2人」 | OK |
| 9 | 監査を見せる相手にする | sets 監査を見せる相手にする pressed=true; shows 「2人」 | OK |
| 10 | 友達と共有する | shows 「http://localhost:8080/s/5rQBASFSi9z01bd0F14jJBVHvECnGwNs」, 「タイムカプセルを作成する」; shows 「http://localhost:8080/s/」 / 「タイムカプセルを作成する」 | OK |

### share-sent — `/album/30/share` after 友達と共有する

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/album/30/decorate/105` | OK |
| 2 | その他のオプション | sets その他のオプション expanded=true; shows 「マイページ」, 「ログアウト」; shows 「マイページ」 / 「ログアウト」 | OK |
| 3 | あやかを見せる相手にする | sets あやかを見せる相手にする pressed=true; shows 「あやか」 / 「2人」 | OK |
| 4 | みきを見せる相手にする | sets みきを見せる相手にする pressed=true; shows 「みき」 / 「2人」 | OK |
| 5 | りんを見せる相手にする | sets りんを見せる相手にする pressed=true; shows 「りん」 / 「2人」 | OK |
| 6 | 監査を見せる相手にする | sets 監査を見せる相手にする pressed=true; shows 「監査」 / 「2人」 | OK |
| 7 | 監査を見せる相手にする | sets 監査を見せる相手にする pressed=true; shows 「監査」 / 「2人」 | OK |
| 8 | 監査を見せる相手にする | sets 監査を見せる相手にする pressed=true; shows 「監査」 / 「2人」 | OK |
| 9 | 監査を見せる相手にする | sets 監査を見せる相手にする pressed=true; shows 「監査」 / 「2人」 | OK |
| 10 | 友達と共有する | busy while the share sheet from the first press is open; headless Chrome never closes that sheet. With the sheet closing after 0.5 s the button is enabled again and the link stays shown | OK |
| 11 | http://localhost:8080/s/ww70FEzOSL_I4P7ipaEtsRwZKYLbcm6R | opens `/s/ww70FEzOSL_I4P7ipaEtsRwZKYLbcm6R` in a new tab | OK |
| 12 | タイムカプセルを作成する | opens `/album/30/capsule/new` | OK |

### me-friends-search — `/me?tab=friends` after type 「r」 in 友達をさがす → さがす

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 名前を変える | shows 「やめる」, 「保存」; shows 「やめる」 / 「保存」 | OK |
| 2 | アルバム | opens `/me?tab=albums`; sets アルバム selected=true, 友達 selected=false | OK |
| 3 | カプセル | opens `/me?tab=capsules`; sets カプセル selected=true, 友達 selected=false | OK |
| 4 | 友達 | already selected here; pressing it again keeps it selected | OK |
| 5 | QRで友達を追加 | opens `/friends/qr` | OK |
| 6 | さがす | searches again for the same word, so the same results stay | OK |
| 7 | 登録に友達申請 | shows 「申請しました」 | OK |
| 8 | あやか @ayaka | opens `/reunion/2` | OK |
| 9 | みき @miki | opens `/reunion/3` | OK |
| 10 | りん @rin | opens `/reunion/4` | OK |
| 11 | 監査 @auda0cad6 | opens `/reunion/5` | OK |
| 12 | 監査 @auda565d4 | opens `/reunion/6` | OK |
| 13 | 監査 @auddf24e0 | opens `/reunion/7` | OK |
| 14 | 監査 @aud58b21d | opens `/reunion/8` | OK |
| 15 | 写真を撮る | opens `/camera` | OK |

### capsule-create — `/album/18/capsule/new`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/album/18/share` | OK |
| 2 | その他のオプション | sets その他のオプション expanded=true; shows 「マイページ」, 「ログアウト」; shows 「マイページ」 / 「ログアウト」 | OK |
| 3 | 1年後 | already selected here; pressing it again keeps it selected | OK |
| 4 | 卒業式（半年後） | sets 1年後 checked=false, 卒業式（半年後） checked=true; shows 「卒業式（半年後）の自分へ」 / 「2027.03.28」 | OK |
| 5 | タイムカプセルを作成する | opens `/capsule/8` | OK |

### capsule-done — `/capsule/6`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 戻る | opens `/me?tab=capsules` | OK |
| 2 | その他のオプション | sets その他のオプション expanded=true; shows 「マイページ」, 「ログアウト」; shows 「マイページ」 / 「ログアウト」 | OK |
| 3 | タイムカプセルを開ける | console error: Failed to load resource: the server responded with a status of 409 () — the 409 CAPSULE_NOT_YET_OPEN is expected: the capsule opens in a year, so the screen then calls the dev-only unseal-now and shows the opened album | OK |

### generating-failed — `/album/generating/999999999`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 1 | 写真を選び直す | opens `/album/new` | OK |

### public-share — `/s/ww70FEzOSL_I4P7ipaEtsRwZKYLbcm6R`

| # | Control | Does what | OK |
| --- | --- | --- | --- |
| 0 | (none) | the page has no buttons or links | OK |

## Notes

- Browser automation here is Playwright, not `C:\agents\tools\bh.py`: these runs need a headless Chrome with a fake camera device and one isolated profile per run, which the shared Chrome lease of bh.py does not give.
- The camera is a fake device fed from `frontend/public/bg/friends.jpg`; there is one camera, so switching cameras changes nothing on the desktop.
- The one console error in the e2e runs is the expected `409 CAPSULE_NOT_YET_OPEN` when the capsule is opened before its date; the screen then calls the dev-only `unseal-now` so the demo can show the opened capsule.
- Phone-only behaviour (real camera, share sheet, LINE card, QR between two phones) is in `09-handoff-human.md`.
