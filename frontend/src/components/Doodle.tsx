import type { CSSProperties } from 'react'

/**
 * Marker-pen doodles from the poster: pink and blue strokes drawn around the
 * photos. Pure decoration (aria-hidden, no pointer events); colour comes from
 * `currentColor`, so set `color` on the class.
 */
const SHAPES = {
  /** heart outlined twice, like a pen going round again */
  heart: {
    box: '0 0 100 90',
    paths: [
      'M50 82 C 22 62, 6 46, 11 28 C 16 11, 40 9, 50 30 C 58 9, 84 10, 89 28 C 94 47, 76 63, 50 82 Z',
      'M47 78 C 24 60, 12 45, 16 30 C 21 17, 40 16, 49 33 C 59 14, 80 17, 84 31',
    ],
  },
  /** heart with a light wash of colour inside */
  heartFilled: {
    box: '0 0 100 90',
    fill: 'M50 82 C 22 62, 6 46, 11 28 C 16 11, 40 9, 50 30 C 58 9, 84 10, 89 28 C 94 47, 76 63, 50 82 Z',
    paths: ['M50 82 C 22 62, 6 46, 11 28 C 16 11, 40 9, 50 30 C 58 9, 84 10, 89 28 C 94 47, 76 63, 50 82 Z'],
  },
  /** four-point sparkle */
  sparkle: {
    box: '0 0 40 40',
    fill: 'M20 2 Q 22 18 38 20 Q 22 22 20 38 Q 18 22 2 20 Q 18 18 20 2 Z',
    paths: ['M20 2 Q 22 18 38 20 Q 22 22 20 38 Q 18 22 2 20 Q 18 18 20 2 Z'],
  },
  /** cursive loops, the "scribble" around a photo corner */
  loops: {
    box: '0 0 120 50',
    paths: ['M4 34 C 14 6, 32 6, 26 28 C 21 46, 42 46, 47 26 C 52 6, 70 6, 64 28 C 59 46, 80 46, 85 26 C 90 6, 108 6, 116 22'],
  },
  /** wavy underline */
  wave: {
    box: '0 0 160 20',
    paths: ['M3 12 Q 13 3 23 11 T 43 11 T 63 11 T 83 11 T 103 11 T 123 11 T 143 11 T 157 9'],
  },
  /** zigzag down the side of a photo */
  zigzag: {
    box: '0 0 24 120',
    paths: ['M12 3 L 20 16 L 5 30 L 19 45 L 5 60 L 19 75 L 5 90 L 19 104 L 11 117'],
  },
  /** a pair of beamed eighth notes */
  notes: {
    box: '0 0 44 44',
    fill: 'M6 36 a 6 4.5 -20 1 0 12 -3 a 6 4.5 -20 1 0 -12 3 Z M26 31 a 6 4.5 -20 1 0 12 -3 a 6 4.5 -20 1 0 -12 3 Z',
    paths: ['M17 33 L 16 8 L 37 3 L 37 28', 'M16 14 L 37 9'],
  },
  /** "handwriting": a line of small loops read as scribbled words */
  writing: {
    box: '0 0 180 24',
    paths: [
      'M3 16 c 4 -10 8 -10 6 0 c -1 6 5 6 7 -2 c 2 -8 7 -8 6 2 c -1 6 5 5 7 -1 M38 15 c 3 -9 9 -9 8 0 c 0 5 5 5 7 -3 c 2 -6 6 -3 5 3',
      'M70 16 c 4 -11 9 -10 7 0 c -1 5 4 6 7 -1 c 3 -8 8 -7 6 1 c -1 5 5 5 8 -3 M106 15 c 3 -8 8 -8 7 1 c 0 4 5 4 7 -2',
      'M134 16 c 4 -10 9 -9 7 0 c -1 6 5 6 8 -2 c 2 -7 7 -6 6 2 c 0 4 4 4 7 -2',
    ],
  },
} as const

export type DoodleKind = keyof typeof SHAPES

type Props = { kind: DoodleKind; className?: string; style?: CSSProperties; strokeWidth?: number }

export function Doodle({ kind, className, style, strokeWidth = 3 }: Props) {
  const shape: { box: string; paths: readonly string[]; fill?: string } = SHAPES[kind]
  return (
    <svg className={className} style={{ pointerEvents: 'none', overflow: 'visible', ...style }} viewBox={shape.box} aria-hidden="true" focusable="false">
      {shape.fill && <path d={shape.fill} fill="currentColor" fillOpacity={kind === 'heartFilled' ? 0.35 : 1} stroke="none" />}
      {shape.paths.map((d) => (
        <path key={d} d={d} fill="none" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
      ))}
    </svg>
  )
}
