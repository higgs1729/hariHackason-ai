import type { CSSProperties, ReactNode } from 'react'
import { assets, type AssetKey } from '../assets'

type Props = {
  asset: AssetKey
  className?: string
  style?: CSSProperties
  /** overlay content (doodles, captions, controls) */
  children?: ReactNode
  /** decorative by default; pass a label when the image carries meaning */
  label?: string
}

/** A photo slot. Renders the asset as a background so gradients and real images are interchangeable. */
export function Photo({ asset, className, style, children, label }: Props) {
  return (
    <div
      className={className}
      role={label ? 'img' : undefined}
      aria-label={label}
      style={{
        background: assets[asset],
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
