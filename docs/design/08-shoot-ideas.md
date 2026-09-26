# 08 — Shoot ideas (prikura prompts)

> Twelve prompts from the design lead (okinn), 2026-09-26. The frontend ships
> them in `frontend/src/shootIdeas.ts`. They may be replaced: okinn noted they
> are hard to follow without pictures, and the feature may be dropped if time
> runs out.

## Where they are used

- **Shoot hint sheet** (Camera → 「AIに撮り方を聞く」): a row of 「お題」 chips
  under the form. Tapping one shows it as a hint with no API call, so the demo
  works when the AI is down. Ideas that need more people than the member count
  are hidden (`minMembers`).
- **Mock `hints.shoot`**: returns the ideas in turn instead of a fixed text.
- **Backend (ask)**: use them as few-shot examples in the `ClaudeShootHinter`
  prompt. Map `title` → `hint`, `steps` → `poses`. Tone: short, a shout at
  the group, one ending with 「！」.

## The list

| title (`hint`) | steps (`poses`) | min people |
| --- | --- | --- |
| みんなで一斉にジャンプ！ | 3、2、1でジャンプ / スロー再生すると青春っぽい | 1 |
| 今度は一人ずつ増えてみよう！ | 最初は一人だけ / 1枚撮るごとに一人ずつ増える / 最後は全員集合 | 2 |
| 空中で止まろう！ | ジャンプした瞬間を連写 / つなげると空中に浮いて見える | 1 |
| みんなで瞬間移動！ | 同じポーズのまま / 場所を少しずつずらして撮る / つなげるとスーッと移動して見える | 1 |
| 全員で変なことして！ | 全員で変な動き / 数年後に見返すと絶対笑う | 2 |
| カメラを順番にのぞき込んで！ | 一人ずつ画面に顔を出す / 最後に全員集合 | 2 |
| 秘密の合言葉を決めよう！ | その場で謎の言葉を決める / 全員で一緒に叫ぶ / 未来で見て「何これ？」ってなる | 2 |
| この続きを未来で撮ろう！ | 一人が手を差し出すところまで撮る / 再会したら相手がその手を取る / 2本つないで完成 | 2 |
| みんなで文字を作ってみよう！ | 1枚ごとに少しずつ移動 / 最後にハートなどの形を作る | 3 |
| 未来で完成させる動画を撮ろう！ | 全員で「せーの……」まで撮って終了 / 再会した日に続きを撮る | 2 |
| 違う場所で撮ってみよう！ | いろんな場所で / 同じポーズ・同じ構図で撮る | 1 |
| みんなで歩こう！ | 少しずつ前に進みながら撮る / 最後に大きな一歩！ | 1 |

Several ideas (frame-by-frame, "finish it at the reunion") need video or
multi-shot capture the app does not have; today they are prompts only.
