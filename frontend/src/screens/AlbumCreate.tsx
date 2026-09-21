import { IconDots, IconHeart, IconPhoto, IconPencil, IconSparkles, IconSticker, IconTypography, IconWand } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import styles from './AlbumCreate.module.css'

const tiles = [
  { asset: 'tile1', caption: '放課後' },
  { asset: 'tile2', caption: '梅田' },
  { asset: 'tile3', caption: 'みんなで' },
  { asset: 'tile4', caption: '夕日' },
  { asset: 'tile5', caption: '帰り道' },
  { asset: 'tile6', caption: 'ピース' },
] as const

export function AlbumCreate() {
  const navigate = useNavigate()

  return (
    <Screen className={styles.screen}>
      <Photo asset="friendsSunset" className={styles.hero} label="夕暮れの友達との思い出">
        <StatusBar tone="light" />
        <TopBar
          to={routes.camera}
          tone="light"
          right={
            <button type="button" className={styles.more} aria-label="その他のオプション">
              <IconDots size={24} stroke={1.8} />
            </button>
          }
        />
        <span className={styles.scribble} aria-hidden="true" />
        <div className={styles.lettering}>
          Best Friends ♡
          <IconSparkles className={styles.letterSparkle} size={24} stroke={1.6} aria-hidden="true" />
        </div>
        <IconHeart className={styles.heroHeart} size={29} stroke={1.5} aria-hidden="true" />
        <IconSparkles className={styles.heroSparkle} size={24} stroke={1.3} aria-hidden="true" />
      </Photo>

      <div className={styles.editor}>
        <div className={styles.tools} aria-label="編集ツール">
          <button type="button" aria-label="スタンプ"><IconSticker size={22} stroke={1.7} /></button>
          <button type="button" aria-label="文字"><IconTypography size={22} stroke={1.7} /></button>
          <button type="button" aria-label="ペン"><IconPencil size={22} stroke={1.7} /></button>
          <button type="button" aria-label="フィルター"><IconWand size={22} stroke={1.7} /></button>
        </div>

        <div className={styles.dateRow}>
          <h1>2025.09.20</h1>
          <IconPhoto size={23} stroke={1.7} aria-hidden="true" />
        </div>

        <div className={styles.grid}>
          {tiles.map((tile) => (
            <button
              key={tile.asset}
              type="button"
              className={styles.tile}
              onClick={() => navigate(routes.decorate)}
              aria-label={`${tile.caption}の写真をデコレーションする`}
            >
              <Photo asset={tile.asset} className={styles.tilePhoto}>
                <span className={styles.heartBadge}><IconHeart size={13} stroke={1.7} aria-hidden="true" /></span>
              </Photo>
              <span className={styles.caption}>{tile.caption}</span>
            </button>
          ))}
        </div>
      </div>

      <div className={styles.footer}>
        <button type="button" className={styles.primary} onClick={() => navigate(routes.decorate)}>
          AIでアルバムにまとめる
        </button>
      </div>
    </Screen>
  )
}
