import styles from './Logo.module.css'

type Props = { size?: number; color?: string; className?: string }

/** Eye + handwritten "ai". Placeholder until the generated logo image arrives. */
export function Logo({ size = 96, color = 'currentColor', className }: Props) {
  return (
    <div className={[styles.logo, className].filter(Boolean).join(' ')} style={{ color, fontSize: size }}>
      <svg className={styles.eye} viewBox="0 0 64 36" aria-hidden="true">
        <g fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round">
          <path d="M32 3v6M20 6l3 5M44 6l-3 5M10 12l4 3M54 12l-4 3" />
          <path d="M6 26c8-9 44-9 52 0c-8 9-44 9-52 0z" />
        </g>
        <circle cx="32" cy="26" r="5" fill="currentColor" />
      </svg>
      <span className={styles.word}>ai</span>
    </div>
  )
}
