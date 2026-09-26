import { useState } from 'react'
import { IconDots, IconLogout, IconUserCircle } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { routes } from '../routes'
import { useAuth } from '../state/auth'
import styles from './MoreMenu.module.css'

/**
 * The "…" in a screen's top-right corner (it is on the poster on five screens).
 * It used to be a button that did nothing; it now opens the two things there
 * is otherwise no way to reach from inside the flow: My page and log out.
 */
export function MoreMenu({ className }: { className?: string }) {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [open, setOpen] = useState(false)

  return (
    <div className={styles.wrap}>
      <button type="button" className={className} aria-label="その他のオプション" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <IconDots size={24} stroke={1.8} />
      </button>
      {open && (
        <>
          <div className={styles.backdrop} onClick={() => setOpen(false)} aria-hidden="true" />
          <div className={styles.menu} role="menu">
            <button type="button" role="menuitem" onClick={() => navigate(routes.me())}>
              <IconUserCircle size={18} stroke={1.8} aria-hidden="true" /> マイページ
            </button>
            <button
              type="button"
              role="menuitem"
              onClick={async () => {
                await logout()
                navigate(routes.home())
              }}
            >
              <IconLogout size={18} stroke={1.8} aria-hidden="true" /> ログアウト
            </button>
          </div>
        </>
      )}
    </div>
  )
}
