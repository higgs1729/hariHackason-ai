import { useEffect, useRef, type PointerEvent as ReactPointerEvent } from 'react'
import { resolveImage, type DecorationElement, type StickerElement, type StrokeElement, type TextElement } from '../api'

type Props = {
  /** photo under the overlay; drawn only when rendering the composite */
  photoUrl: string
  elements: DecorationElement[]
  onElementsChange: (next: DecorationElement[]) => void
  /** pen draws; stamp drops a heart where you tap */
  tool: 'pen' | 'stamp' | 'none'
  color: string
  className?: string
}

/**
 * Transparent overlay on top of a photo. Coordinates are stored 0..1 so the
 * same element list renders at 390px on the phone and 1200px in the OG image
 * (03-detailed-design §5.4). Strokes, text and the heart sticker draw;
 * filter elements are kept in the list untouched.
 */
export function DoodleCanvas({ photoUrl, elements, onElementsChange, tool, color, className }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const drawing = useRef<StrokeElement | null>(null)

  // keep the bitmap in sync with the element list
  useEffect(() => {
    const c = canvasRef.current
    if (!c) return
    const rect = c.getBoundingClientRect()
    const dpr = window.devicePixelRatio || 1
    c.width = Math.max(1, Math.round(rect.width * dpr))
    c.height = Math.max(1, Math.round(rect.height * dpr))
    const ctx = c.getContext('2d')!
    ctx.clearRect(0, 0, c.width, c.height)
    paint(ctx, elements, c.width, c.height)
  }, [elements])

  const toNorm = (e: ReactPointerEvent<HTMLCanvasElement>): [number, number] => {
    const r = e.currentTarget.getBoundingClientRect()
    return [clamp((e.clientX - r.left) / r.width), clamp((e.clientY - r.top) / r.height)]
  }

  const down = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (tool === 'stamp') {
      const [x, y] = toNorm(e)
      const rotation = Math.round((Math.random() - 0.5) * 30)
      onElementsChange([...elements, { id: `k${Date.now().toString(36)}`, type: 'sticker', assetId: 'heart', x, y, scale: 0.16, rotation }])
      return
    }
    if (tool !== 'pen') return
    e.currentTarget.setPointerCapture(e.pointerId)
    drawing.current = { id: `s${Date.now().toString(36)}`, type: 'stroke', color, width: 0.012, points: [toNorm(e)] }
    onElementsChange([...elements, drawing.current])
  }
  const move = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    const s = drawing.current
    if (!s) return
    s.points.push(toNorm(e))
    onElementsChange([...elements.filter((el) => el.id !== s.id), { ...s, points: [...s.points] }])
  }
  const up = () => {
    drawing.current = null
  }

  return (
    <canvas
      ref={canvasRef}
      className={className}
      style={{ touchAction: tool === 'none' ? 'auto' : 'none', cursor: tool === 'none' ? 'default' : 'crosshair' }}
      onPointerDown={down}
      onPointerMove={move}
      onPointerUp={up}
      onPointerCancel={up}
      data-photo={photoUrl}
      aria-label="落書きキャンバス"
    />
  )
}

const clamp = (v: number) => Math.min(1, Math.max(0, v))

/** Draws stroke and text elements onto a context of size w×h (pixels). */
export function paint(ctx: CanvasRenderingContext2D, elements: DecorationElement[], w: number, h: number) {
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  for (const el of elements) {
    if (el.type === 'stroke') {
      ctx.strokeStyle = el.color
      ctx.lineWidth = el.width * w
      ctx.beginPath()
      el.points.forEach(([x, y], i) => (i === 0 ? ctx.moveTo(x * w, y * h) : ctx.lineTo(x * w, y * h)))
      ctx.stroke()
    } else if (el.type === 'text') {
      drawText(ctx, el, w, h)
    } else if (el.type === 'sticker' && el.assetId === 'heart') {
      drawHeart(ctx, el, w, h)
    }
  }
}

/** A filled heart with a white rim, sized as a fraction of the photo width. */
function drawHeart(ctx: CanvasRenderingContext2D, el: StickerElement, w: number, h: number) {
  const s = el.scale * w
  ctx.save()
  ctx.translate(el.x * w, el.y * h)
  ctx.rotate((el.rotation * Math.PI) / 180)
  ctx.beginPath()
  ctx.moveTo(0, s * 0.35)
  ctx.bezierCurveTo(-s * 0.55, -s * 0.05, -s * 0.35, -s * 0.5, 0, -s * 0.2)
  ctx.bezierCurveTo(s * 0.35, -s * 0.5, s * 0.55, -s * 0.05, 0, s * 0.35)
  ctx.closePath()
  ctx.shadowColor = 'rgba(20,38,77,0.3)'
  ctx.shadowBlur = 4
  ctx.fillStyle = '#ff7fb0'
  ctx.fill()
  ctx.shadowBlur = 0
  ctx.lineWidth = Math.max(2, s * 0.06)
  ctx.strokeStyle = '#ffffff'
  ctx.stroke()
  ctx.restore()
}

function drawText(ctx: CanvasRenderingContext2D, el: TextElement, w: number, h: number) {
  ctx.save()
  ctx.translate(el.x * w, el.y * h)
  ctx.rotate((el.rotation * Math.PI) / 180)
  ctx.fillStyle = el.color
  ctx.font = `${el.font === 'hand' ? '' : 'bold '}${el.size * h}px ${el.font === 'hand' ? "'Segoe Script','Segoe Print',cursive" : 'sans-serif'}`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.shadowColor = 'rgba(20,38,77,0.35)'
  ctx.shadowBlur = 4
  ctx.fillText(el.text, 0, 0)
  ctx.restore()
}

/**
 * Renders the two cache images the backend stores (§4.6 POST /decoration/rendered):
 * a transparent overlay PNG and a composite JPEG (photo + overlay).
 */
export async function renderImages(photoUrl: string, elements: DecorationElement[], width = 1200): Promise<{ overlay: Blob; composite: Blob }> {
  const img = await loadImage(await resolveImage(photoUrl))
  const w = width
  const h = Math.round((img.naturalHeight / img.naturalWidth) * w) || Math.round(w * 0.75)

  const overlayCanvas = document.createElement('canvas')
  overlayCanvas.width = w
  overlayCanvas.height = h
  paint(overlayCanvas.getContext('2d')!, elements, w, h)

  const compositeCanvas = document.createElement('canvas')
  compositeCanvas.width = w
  compositeCanvas.height = h
  const cctx = compositeCanvas.getContext('2d')!
  cctx.drawImage(img, 0, 0, w, h)
  cctx.drawImage(overlayCanvas, 0, 0)

  const [overlay, composite] = await Promise.all([toBlob(overlayCanvas, 'image/png'), toBlob(compositeCanvas, 'image/jpeg', 0.9)])
  return { overlay, composite }
}

const loadImage = (src: string) =>
  new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('image load failed'))
    img.src = src
  })

const toBlob = (c: HTMLCanvasElement, type: string, quality?: number) =>
  new Promise<Blob>((resolve, reject) => c.toBlob((b) => (b ? resolve(b) : reject(new Error('toBlob failed'))), type, quality))
