"""Poster crop next to the app screenshot, one image per screen plus an overview.

The app side comes from an e2e run (tools/e2e.py), so every screen is the real
build on :8080, logged in, at phone size:

    python tools/side_by_side.py docs/e2e/run-2

Writes docs/reference/compare/<stem>.jpg and docs/reference/compare/all.jpg.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
POSTER = ROOT / 'docs/reference/poster'
OUT = ROOT / 'docs/reference/compare'
# poster crop stem -> e2e step name
PAIRS = {
    '01-home': 'home',
    '02-camera': 'camera',
    '03-album-create': 'album-create',
    '04-decorate': 'decorate',
    '05-share': 'share',
    '06-capsule-create': 'capsule-create',
    '07-capsule-done': 'capsule-done',
    '08-detail': 'album-detail',
}
H = 900
LABEL = 44


def font(size):
    for name in ('meiryo.ttc', 'YuGothM.ttc', 'arial.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def fit(img):
    img = img.convert('RGB')
    return img.resize((round(img.width * H / img.height), H), Image.LANCZOS)


def pair(stem, run: Path):
    shot = next(run.glob(f'[0-9][0-9]-{PAIRS[stem]}.jpg'), None)
    if shot is None:
        raise SystemExit(f'no {PAIRS[stem]} screenshot in {run}')
    left, right = fit(Image.open(POSTER / f'{stem}.png')), fit(Image.open(shot))
    gap = 24
    sheet = Image.new('RGB', (left.width + right.width + gap * 3, H + LABEL + gap), 'white')
    draw = ImageDraw.Draw(sheet)
    f = font(26)
    draw.text((gap, 8), f'{stem}  poster', fill='#14264d', font=f)
    draw.text((left.width + gap * 2, 8), f'app ({run.name}/{shot.name})', fill='#14264d', font=f)
    sheet.paste(left, (gap, LABEL))
    sheet.paste(right, (left.width + gap * 2, LABEL))
    return sheet


def main():
    run = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'docs/e2e/run-2'
    OUT.mkdir(parents=True, exist_ok=True)
    sheets = []
    for stem in PAIRS:
        s = pair(stem, run)
        s.save(OUT / f'{stem}.jpg', quality=82)
        sheets.append(s)
    # overview: two rows of four pairs, scaled down
    small = [s.resize((s.width // 2, s.height // 2)) for s in sheets]
    w, h = max(s.width for s in small), max(s.height for s in small)
    overview = Image.new('RGB', (w * 4, h * 2), 'white')
    for i, s in enumerate(small):
        overview.paste(s, ((i % 4) * w, (i // 4) * h))
    overview.save(OUT / 'all.jpg', quality=80)
    print(f'wrote {len(sheets)} pairs and all.jpg to {OUT}')


if __name__ == '__main__':
    main()
