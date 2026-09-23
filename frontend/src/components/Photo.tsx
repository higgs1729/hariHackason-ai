import type { CSSProperties, ReactNode } from 'react'
import { useImageSrc } from '../api'
import { assets, type AssetKey } from '../assets'

type Props = {
  /** placeholder from the asset map; used when `src` is absent */
  asset?: AssetKey
  /** real image URL from the API (photo.url / thumbUrl / compositeUrl) */
  src?: string | null
  className?: string
  style?: CSSProperties
  /** overlay content (doodles, captions, controls) */
  children?: ReactNode
  /** decorative by default; pass a label when the image carries meaning */
  label?: string
}

/** A photo slot. Renders the image as a background so gradients and real images are interchangeable. */
export function Photo({ asset = 'tile1', src, className, style, children, label }: Props) {
  // backgroundImage, not the `background` shorthand: React warns when a shorthand
  // and its longhands (backgroundSize/Position) are updated in the same render.
  // `/api/...` images need the Bearer token; show the gradient until the blob is ready.
  const resolved = useImageSrc(src)
  const backgroundImage = resolved ? `url("${resolved}")` : assets[asset]
  return (
    <div
      className={className}
      role={label ? 'img' : undefined}
      aria-label={label}
      style={{
        backgroundImage,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
        position: 'relative',
        overflow: 'hidden',
        ...style,
      }}
    >
      {children}
    </div>
  )
}
