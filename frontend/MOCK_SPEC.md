# UI mock spec — "ai"

Source of truth: `../image-1.png` (presentation poster), section 04 "アプリの画面イメージ" (7 phones) and section 05 (time-capsule create phone). Nothing else is a reference.

The poster's phone screens are about 130 px wide, so much of their copy is unreadable. Every visible string below is tagged:

- **[legible]** — read directly from the poster; use verbatim.
- **[inferred]** — unreadable in the poster; filled from the poster's own section text (03 主な機能, 05, 06) or from context. Free to revise.

Visual language (all screens): indigo/navy (藍) brand, white surfaces, rounded 16–24 px corners, soft sky/sunset photo backgrounds, thin hand-drawn doodles (hearts, sparkles) in pink/white, iOS status bar "9:41". One theme only. No bottom tab bar (the poster has none). Flow follows section 07: 写真を撮る → AIが自動でアルバム作成 → デコレーション → 友達と共有 → タイムカプセル.

## Shared building blocks (already implemented)

| File | Role |
| --- | --- |
| `src/styles/tokens.css` | colors, radii, fonts (`--font-ui`, `--font-hand`) |
| `src/assets/index.ts` | keyed placeholder backgrounds; every photo goes through `Photo` |
| `src/components/Photo.tsx` | `<Photo asset="tile1">` photo slot |
| `src/components/Logo.tsx` | eye + handwritten "ai" |
| `src/components/StatusBar.tsx` | 9:41 bar; `tone="light"` for white on dark |
| `src/components/TopBar.tsx` | back/close + title + right slot; `to` overrides history-back |
| `src/components/Screen.tsx` | full-height column |
| `src/components/PhoneFrame.tsx` | 390×844 frame on desktop only |
| `src/routes.ts` | route table |

Image slots that will later be replaced by generated images: all `Photo` assets, the `Logo` word "ai", the capsule illustration on screen 7.

## Screens

### 1. Home — `/` — `screens/Home.tsx`

Full-bleed background `skySunset` (blue sky, clouds, river, city skyline at the bottom). Everything centered, white text.

- StatusBar (light), TopBar left = back chevron only (poster shows a faint `<`), no title. **[legible]**
- `Logo` large (~110 px), white. **[legible]**
- Subtitle under the logo: 「あの時の、最高を、ずっと。」 **[inferred]** — the poster shows a garbled line "ホーム 最して"; the app tagline from the header is the safest fill.
- Bottom: white pill button 「はじめる」 (full width minus 32 px margins, navy text, bold) → `/camera`. **[legible]**
- Under it a small white text link 「ログイン」 (no destination; stays on home). **[legible]**

### 2. Camera — `/camera` — `screens/Camera.tsx`

Dark navy screen (`--navy-900`).

- StatusBar (light). Top row: `✕` left → `/`; right a small round outline icon (flash/flip) — use `IconBolt`. **[legible]**
- Viewfinder: `Photo asset="friendsSunset"` filling the width, ~62% of height, rounded 12 px, small margin. Top-left corner a tiny white icon (photo-library) **[legible, icon only]**.
- Below the viewfinder, two small labels centered above the controls: left 「ビデオ」, center 「写真」 (active, brighter) **[inferred]** — poster shows two short garbled labels.
- Controls row: left square thumbnail button (rounded 10 px, white outline, `Photo asset="tile2"`) ; center big white shutter circle (72 px, 4 px navy inset ring) → `/album/new`; right camera-flip outline icon button (`IconCameraRotate`). **[legible]**
- Home indicator bar at the bottom. **[legible]**

### 3. Album create — `/album/new` — `screens/AlbumCreate.tsx`

White screen with a hero photo.

- StatusBar (light on the hero) and TopBar left = back chevron → `/camera`, right = `IconDots` (light). **[legible]** The header overlays the hero.
- Hero `Photo asset="friendsSunset"`, full width, ~55% height. Overlays: handwritten 「Best Friends ♡」 in white script (`--font-hand`, ~26 px, slight rotation), a few white sparkles/hearts, and a small white doodle scribble top-left. **[legible]**
- Over the bottom edge of the hero, a row of 4 small round white-translucent icon buttons: sticker (`IconSticker`), text (`IconTypography`), pen (`IconPencil`), filter (`IconWand`). **[legible, icons only — poster shows 4 round icons]** They do nothing.
- Below: date line 「2025.09.20」 in navy, bold 18 px, left; right a small outline photo icon (`IconPhoto`). **[legible]**
- Then a 3×2 grid of photo tiles (`tile1`…`tile6`, rounded 12 px, 4:3). Each tile has a tiny white icon badge top-right (`IconHeart`) and a short caption under it 「放課後」「梅田」「みんなで」「夕日」「帰り道」「ピース」 **[inferred]** — poster shows a tiny label under each tile, unreadable. Tapping the first tile → `/album/decorate`.
- Bottom fixed primary button (navy pill) 「AIでアルバムにまとめる」 → `/album/decorate`. **[inferred]** — the poster caption says "AIが自動でアルバムにまとめてくれる!"; the poster screen itself is cut off below the grid.

### 4. Decorate — `/album/decorate` — `screens/Decorate.tsx`

Very light blue paper background (`--blue-50`) covered with pink/blue doodles.

- StatusBar, TopBar left = back chevron → `/album/new`, right = a small navy save icon (`IconDeviceFloppy`) **[legible: something small top-right]**.
- Handwritten heading 「最高の1日 ♡」 in navy script (~24 px, `--font-hand` for the heart, UI font bold for the words) with pink hand-drawn hearts around it. **[legible]**
- Two tilted polaroid cards (white padding 8 px, bottom padding 24 px, slight rotation −3° / +2°, soft shadow) stacked: top one holds a 2-photo collage (`tile1` + `friendsSunset`), the lower one `tile3` + `tile4`. Pink hearts, a small sticker chip (`IconSticker` in a white pill) and a scribble of small navy text 「BEST」「2025.09.20」 between them. **[legible: layout and hearts; text inferred]**
- Bottom fixed primary button 「保存して友達とシェア」 → `/album/share`. **[inferred]** — poster is cut off; next step in the flow.

### 5. Share — `/album/share` — `screens/Share.tsx`

White list screen.

- StatusBar, TopBar left = back chevron → `/album/decorate`, right = `IconDots`. **[legible]**
- Row of 4 round avatars (`avatarMe`, `avatarA`, `avatarB`, `avatarC`, 40 px) with tiny names under them 「わたし」「あやか」「みき」「りん」 **[inferred: names]**, a `>` chevron at the row end. First avatar has a navy ring (selected). **[legible: avatars + chevron]**
- Section title 「放課後プリクラ」 bold 18 px, with a `>` chevron right. **[legible]**
- Card 1 (white, border 1 px `--border`, rounded 16 px): top row small pill chips 「2025.09.20」「梅田」「3人」 **[inferred]**; body: a wide photo (`friendsSunset`, rounded 12 px) with two small round avatars stacked at its right (`avatarA`, `avatarB`); under the photo a small navy line 「AIがまとめました ✨」 **[inferred]**. **[legible: layout]**
- Section title 「見せる相手」 **[inferred]** with a `>` chevron. **[legible: second heading + chevron]**
- Card 2: left a photo tile (`tile5`, 100×100, rounded 12 px), right a stack of 3 small text lines 「あやか」「みき」「りん」 each with a tiny check icon **[inferred]**; bottom-right a round navy FAB with `IconSend`, 52 px → `/capsule/new`. **[legible: layout + FAB]**
- Small caption under card 2: 「友達と一緒に思い出を作れる！」 **[inferred, from the poster caption]**.

### 6. Time capsule create — `/capsule/new` — `screens/CapsuleCreate.tsx`

From poster section 05.

- Navy header block: StatusBar (light), TopBar title 「タイムカプセル」 (light), left back chevron → `/album/share`, right `IconDots`. **[legible]**
- Background below the header: `Photo asset="skySunset"` filling the rest (sunset sky, city skyline at the bottom). **[legible]**
- Centered translucent white card (rgba(255,255,255,.88), rounded 20 px, padding 24 px, width minus 40 px margins):
  - 「1年後の自分へ」 bold 18 px navy. **[legible]**
  - 「この思い出を、」 / 「未来の自分に届けよう。」 two lines, 14 px, navy. **[legible]**
  - thin divider
  - 「2026.09.20」 14 px. **[legible]**
  - navy pill button 「タイムカプセルを作成する」 → `/capsule/done`. **[legible]**

### 7. Time capsule done — `/capsule/done` — `screens/CapsuleDone.tsx`

Full-bleed `skyNight` (deep navy sky with faint stars — add a few 2 px white dots with CSS).

- StatusBar (light), TopBar left = back chevron (light) → `/capsule/new`, right `IconDots` (light). **[legible]**
- Centered heading, two lines, white 18 px bold: 「タイムカプセルが」 / 「完成しました！」 **[legible]**
- Capsule illustration: a rounded pill outline (white 3 px stroke, ~110×60 px, rotated −35°) with a diagonal split line, plus 4 white four-point sparkles around it (CSS or inline SVG). **[legible]** Placeholder until the generated illustration arrives.
- Bottom white pill button 「タイムカプセルを開ける」 → `/album/detail`. **[inferred]** — the poster button reads "タイムカプセルを○○する" with the verb unreadable; after creation, "開ける" (open) is the only action that makes sense and leads to the detail screen.

### 8. Detail — `/album/detail` — `screens/Detail.tsx`

White list screen (feature 6 「写真情報の保存: 場所・時間・音楽・コメントを一緒に保存する！」 is the content source).

- StatusBar, TopBar left = back chevron → `/capsule/done`, right `IconDots`. **[legible]**
- Header block: title 「2025 プリクラ」 bold 20 px **[legible-ish: "202x プリクラ"]**, right a round light-blue chip with `IconUser`; under the title a muted line 「2025.09.20」 **[inferred: poster shows a garbled date]** and a tiny right-aligned 「編集」 text link **[inferred]**.
- Divider, then a list of rows (each 56 px, icon in a light-blue rounded square 36 px, label, optional chevron):
  1. thumbnail (`tile1`, 44 px) + small caption 「場所」 above 「梅田」 bold, chevron right. **[legible: 梅田 + chevron]**
  2. `IconClock` 「時間」 → 「2025.09.20 17:30」 **[inferred]**
  3. `IconMusic` 「音楽」 → 「I'm yours / Jason Mraz」 **[inferred]**
  4. `IconMessage` 「コメント」 → 「テスト終わりの放課後、最高だった♡」, chevron right. **[inferred]**
  5. `IconUsers` 「メンバー」 → 「あやか、みき、りん」 **[inferred]**
  6. `IconCloud` 「天気」 → 「晴れ」 **[inferred]** — poster shows a 6th row cut off.

## Navigation summary

```
/ ──はじめる──▶ /camera ──shutter──▶ /album/new ──AIでまとめる──▶ /album/decorate
 ──保存してシェア──▶ /album/share ──FAB──▶ /capsule/new ──作成──▶ /capsule/done ──開ける──▶ /album/detail
```

Back chevrons go one step left in this chain.
