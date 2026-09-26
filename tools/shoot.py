"""Screenshot every mock route at phone size (390x844) with headless Chrome.

Builds nothing: run `npm run build` in frontend/ first. Serves frontend/dist on a
local port, opens each route at a 1000x920 window (headless Chrome refuses windows
narrower than 500 px, which would trigger the desktop PhoneFrame media query anyway)
and crops the phone's inner screen out of the page.

Usage:
    python tools/shoot.py [--out docs/mock] [--routes home,camera,...] [--sheet]

Output: <out>/<nn>-<name>.png (390x844 each) and, with --sheet, <out>/all-screens.png.
"""
import argparse
import os
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "frontend" / "dist"
CHROME = os.environ.get(
    "CHROME", r"C:\Program Files\Google\Chrome\Application\chrome.exe"
)

# (file stem, route). Order matches the poster's flow.
ROUTES = [
    ("01-home", "/"),
    ("02-camera", "/camera"),
    ("03-album-create", "/album/new"),
    ("04-decorate", "/album/decorate"),
    ("05-share", "/album/share"),
    ("06-capsule-create", "/capsule/new"),
    ("07-capsule-done", "/capsule/done"),
    ("08-detail", "/album/detail"),
]

WINDOW = (1000, 920)
PHONE_W, PHONE_H = 390, 844
FRAME_BORDER, STAGE_PAD_TOP = 8, 32  # PhoneFrame.module.css desktop layout


def serve(port: int) -> None:
    import http.server

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(DIST), **kwargs)

        def send_head(self):
            if not os.path.exists(self.translate_path(self.path)):
                self.path = "/index.html"
            return super().send_head()

        def log_message(self, *args):
            pass

    http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()


def shoot(url: str, png: Path) -> None:
    profile = tempfile.mkdtemp(prefix="shoot-")
    subprocess.run(
        [
            CHROME,
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            f"--window-size={WINDOW[0]},{WINDOW[1]}",
            "--virtual-time-budget=4000",
            f"--user-data-dir={profile}",
            f"--screenshot={png}",
            url,
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        timeout=60,
    )


def crop_phone(png: Path) -> Image.Image:
    full = Image.open(png).convert("RGB")
    outer_w = PHONE_W + 2 * FRAME_BORDER
    x0 = (WINDOW[0] - outer_w) // 2 + FRAME_BORDER
    y0 = STAGE_PAD_TOP + FRAME_BORDER
    return full.crop((x0, y0, x0 + PHONE_W, y0 + PHONE_H))


def contact_sheet(images: list[Image.Image], path: Path, scale: float = 0.5) -> None:
    w, h = int(PHONE_W * scale), int(PHONE_H * scale)
    gap, cols = 12, 4
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * w + (cols + 1) * gap, rows * h + (rows + 1) * gap), "white")
    for i, im in enumerate(images):
        r, c = divmod(i, cols)
        sheet.paste(im.resize((w, h)), (gap + c * (w + gap), gap + r * (h + gap)))
    sheet.save(path, optimize=True)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / "docs" / "mock"))
    ap.add_argument("--routes", default="", help="comma-separated stems, e.g. 01-home,05-share")
    ap.add_argument("--port", type=int, default=5181)
    ap.add_argument("--sheet", action="store_true", help="also write all-screens.png")
    args = ap.parse_args()

    if not (DIST / "index.html").exists():
        print("frontend/dist missing: run `npm run build` in frontend/ first", file=sys.stderr)
        return 1
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    wanted = {s for s in args.routes.split(",") if s} or {stem for stem, _ in ROUTES}

    threading.Thread(target=serve, args=(args.port,), daemon=True).start()
    time.sleep(0.5)

    shots: list[Image.Image] = []
    with tempfile.TemporaryDirectory() as tmp:
        for stem, route in ROUTES:
            if stem not in wanted:
                continue
            raw = Path(tmp) / f"{stem}.png"
            shoot(f"http://127.0.0.1:{args.port}{route}", raw)
            im = crop_phone(raw)
            im.save(out / f"{stem}.png", optimize=True)
            shots.append(im)
            print(f"{stem}: {out / f'{stem}.png'}")
    if args.sheet and len(shots) == len(ROUTES):
        contact_sheet(shots, out / "all-screens.png")
        print(f"sheet: {out / 'all-screens.png'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
