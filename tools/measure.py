"""Pixel measurements of the poster's inner phone, in percent of its screen.

Usage: python tools/measure.py 05-share [--screen X,Y,W,H] [--mock]
The box config gives a search neighborhood and scan line, never a returned box edge.
Reported edges come from the strongest actual RGB step in that neighborhood.
Photos and generated art are measured for context, but excluded from pass/fail.
"""
import argparse
import json
import os
from pathlib import Path

os.environ.setdefault('OPENBLAS_NUM_THREADS', '1')
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
POSTER = ROOT / 'docs/reference/poster'
MOCK = ROOT / 'docs/mock'
ORDER = ('05-share', '08-detail', '02-camera', '01-home', '03-album-create',
         '04-decorate', '06-capsule-create', '07-capsule-done')

# Region coordinates (x, y, width, height) are percentages of the inner screen.
# Small central patches exclude captions, borders and antialiased corners.
REGIONS = {
 '05-share': {'header_top': (49, 3, 4, 2), 'header_bottom': (48, 7, 4, 1),
  'page': (95, 23, 2, 2), 'card_album': (85, 51, 2, 2),
  'card_recipient': (88, 70, 2, 2), 'chip': (9, 32, 2, 2),
  'fab': (74, 88, 2, 1), 'album_photo': (36, 42, 5, 3, True),
  'recipient_photo': (19, 73, 5, 3, True)},
 '08-detail': {'header_top': (49, 3, 4, 2), 'header_bottom': (49, 7, 4, 1),
  'page': (93, 81, 3, 2), 'icon_box': (10, 53, 2, 2),
  'title_round': (88, 20, 2, 2), 'thumbnail': (13, 41, 5, 3, True)},
 '02-camera': {'surround_top': (47, 11, 4, 2), 'surround_bottom': (47, 92, 4, 2),
  'mode_bg': (10, 74, 3, 2), 'shutter': (49, 84, 2, 2),
  'viewfinder_photo': (43, 42, 5, 3, True), 'thumbnail': (13, 83, 4, 2, True)},
 '01-home': {'primary': (47, 86, 4, 2), 'photo_sky': (47, 37, 4, 3, True),
  'photo_bottom': (45, 79, 4, 3, True)},
 '03-album-create': {'page': (95, 66, 2, 2), 'tool_fill': (15, 55, 3, 2),
  'date_bg': (91, 65, 3, 2), 'primary': (48, 94, 3, 2),
  'hero_photo': (40, 40, 5, 3, True), 'tile_photo': (14, 76, 4, 2, True)},
 '04-decorate': {'paper_top': (94, 15, 2, 2), 'paper_bottom': (94, 88, 2, 2),
  'polaroid_paper': (13, 32, 2, 2), 'primary': (48, 94, 3, 2),
  'collage_photo': (34, 40, 4, 3, True)},
 '06-capsule-create': {'header': (49, 7, 3, 2), 'page': (92, 45, 3, 2, True),
  'card': (20, 37, 3, 2), 'primary': (48, 61, 3, 2),
  'sky_photo': (80, 30, 3, 2, True)},
 '07-capsule-done': {'night_top': (47, 12, 4, 2),
  'night_mid': (12, 55, 3, 2), 'night_bottom': (12, 85, 3, 2),
  'primary': (48, 88, 4, 2), 'capsule_art': (48, 47, 4, 2, True)},
}

# Approximate ROI (x0,y0,x1,y1), plus y for left/right scans and x for top/bottom.
# ROI endpoints are SEARCH CENTERS ONLY; output always comes from pixel gradients.
# 'photo' skips color/geometry grading for generated imagery.
BOXES = {
 '05-share': {'header': ((0,0,100,11),50,50),
  'avatar_strip': ((0,11,100,20),15,50),
  'album_card': ((7,30,94,58),8,32),
  'recipient_card': ((7,65,94,94),48,80),
  'fab': ((67,82,90,92),77,87)},
 '08-detail': {'header': ((0,0,100,11),50,50),
  'title_area': ((0,11,100,34),50,20),
  'row_1': ((0,34,100,49),50,43),
  'row_2': ((0,49,100,59),50,54),
  'icon_box': ((8,50,20,56),14,53)},
 '02-camera': {'viewfinder': ((3,13,97,71),50,45,True),
  'modes': ((23,71,77,79),50,75),
  'shutter': ((39,79,61,90),50,85),
  'thumbnail': ((4,79,26,89),15,84,True)},
 '01-home': {'primary_button': ((10,82,90,90),50,86),
  'login_link': ((29,91,71,96),50,93)},
 '03-album-create': {'hero': ((4,11,96,61),50,42,True),
  'tool_row': ((5,51,71,61),37,56),
  'tile_grid': ((4,68,96,92),50,80,True),
  'primary_button': ((4,91,96,99),50,95)},
 '04-decorate': {'first_polaroid': ((8,26,83,59),48,42,True),
  'second_polaroid': ((16,65,92,94),55,79,True),
  'primary_button': ((4,91,96,99),50,95)},
 '06-capsule-create': {'header': ((0,0,100,12),50,50),
  'sky_card': ((5,14,95,82),50,47,True),
  'message_card': ((12,29,88,70),50,48),
  'primary_button': ((18,57,82,67),50,62)},
 '07-capsule-done': {'heading': ((15,24,85,36),50,30),
  'illustration': ((17,37,83,67),50,51,True),
  'primary_button': ((7,83,93,94),50,88)},
}


def rectangle(image, stem):
    """Locate bezel-to-content transitions from RGB scans, with dark fallback."""
    if stem in ('02-camera', '07-capsule-done'):
        return rectangle(np.asarray(Image.open(POSTER / '05-share.png').convert('RGB')), '05-share')
    h, w = image.shape[:2]
    if stem == '06-capsule-create':
        guesses = (38, 42, 500, None)
    else:
        guesses = (25, 22, w-56, h-28)
    def locate(axis, fixed, center, span, direction=1):
        line = image[fixed,:,:].astype(float) if axis == 0 else image[:,fixed,:].astype(float)
        n = line.shape[0]
        lo, hi = max(4,center-span), min(n-5,center+span)
        # Average colors on both sides of every candidate edge, ignoring single-pixel noise.
        scores = [(direction*(line[i+2:i+5].mean()-line[i-4:i-1].mean()),i)
                  for i in range(lo,hi)]
        return max(scores)[1]
    x0 = locate(0, round(h*.48), guesses[0], 6, 1)
    x1 = locate(0, round(h*.48), guesses[2], 7, -1)
    y0 = locate(1, round(w*.50), guesses[1], 6, 1)
    if stem == '06-capsule-create':
        return [int(x0),int(y0),int(x1-x0),int(round((x1-x0)/.445))]
    y1 = locate(1, round(w*.31), guesses[3], 8, -1)
    y1 = min(y1,h-15)
    return [int(x0),int(y0),int(x1-x0),int(y1-y0)]


def crop_screen(pixels, screen):
    x,y,w,h = screen
    # 06 is intentionally clipped. It remains at full-scale; absent pixels are never sampled.
    return pixels[max(0,y):min(pixels.shape[0],y+h),max(0,x):min(pixels.shape[1],x+w)]


def sample(pixels, screen, r):
    x,y,w,h = screen
    px,py,pw,ph = r[:4]
    a,b = round(x+w*px/100),round(y+h*py/100)
    c,d = round(x+w*(px+pw)/100),round(y+h*(py+ph)/100)
    patch = pixels[max(0,b):min(pixels.shape[0],d),max(0,a):min(pixels.shape[1],c)]
    if not patch.size: return None
    return [int(round(t)) for t in patch.reshape(-1,3).mean(axis=0)]


def edge(pixels, screen, axis, anchor, center, width=8):
    """Measured edge = maximum local multi-pixel RGB step in a search interval."""
    x,y,w,h = screen
    scale = w if axis == 0 else h
    offset = x if axis == 0 else y
    at = int(round((y+h*anchor/100) if axis == 0 else (x+w*anchor/100)))
    if at < 0 or at >= pixels.shape[0 if axis == 0 else 1]: return None
    line = pixels[at,:,:] if axis == 0 else pixels[:,at,:]
    # average five adjacent rows/columns to reject small glyphs/noise
    lo_at = max(0,at-2); hi_at = min(pixels.shape[0 if axis == 0 else 1],at+3)
    line = pixels[lo_at:hi_at,:,:].mean(axis=0) if axis == 0 else pixels[:,lo_at:hi_at,:].mean(axis=1)
    line = line.astype(float)
    center_px = offset+scale*center/100
    lo=max(4,int(center_px-scale*width/100)); hi=min(len(line)-5,int(center_px+scale*width/100)+1)
    if hi<=lo: return None
    delta=np.asarray([np.linalg.norm(line[i+2:i+5].mean(axis=0)-line[i-4:i-1].mean(axis=0)) for i in range(lo,hi)])
    # Include strength so invisible edges cannot masquerade as a valid measurement.
    idx=int(delta.argmax())+lo
    return [round(100*(idx-offset)/scale,2),round(float(delta.max()),2)]


def box(pixels, screen, spec):
    approx, scan_x, scan_y, *rest = spec
    x0,y0,x1,y1=approx
    # Boundaries touching canvas are pixel-derived screen boundaries.
    values=[]; strengths=[]
    for axis,anchor,center in [(0,scan_y,x0),(1,scan_x,y0),(0,scan_y,x1),(1,scan_x,y1)]:
        if center in (0,100):
            values.append(float(center)); strengths.append(None); continue
        detected=edge(pixels,screen,axis,anchor,center,2.5 if axis==0 else 2.5)
        values.append(detected[0] if detected else None)
        strengths.append(detected[1] if detected else None)
    return {'box':values,'edge_strengths':strengths,'excluded':bool(rest and rest[0]),
            'detected':all(v is not None for v in values) and values[0]<values[2] and values[1]<values[3]}


def measure(stem, mock=False, override=None):
    path=(MOCK if mock else POSTER)/f'{stem}.png'
    pixels=np.asarray(Image.open(path).convert('RGB'))
    screen=[0,0,pixels.shape[1],pixels.shape[0]] if mock else (override or rectangle(pixels,stem))
    colors={name:{'region':list(r[:4]),'rgb':sample(pixels,screen,r),'excluded':len(r)>4 and r[4]}
            for name,r in REGIONS[stem].items()}
    boxes={name:box(pixels,screen,spec) for name,spec in BOXES[stem].items()}
    return {'stem':stem,'source':str(path.relative_to(ROOT)),'screen':screen,'colors':colors,'boxes':boxes}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('stem',choices=ORDER)
    p.add_argument('--screen',help='override measured poster screen as x,y,w,h')
    p.add_argument('--mock',action='store_true')
    args=p.parse_args()
    override=[int(v) for v in args.screen.split(',')] if args.screen else None
    print(json.dumps(measure(args.stem,args.mock,override),ensure_ascii=False,indent=2))

if __name__=='__main__': main()
