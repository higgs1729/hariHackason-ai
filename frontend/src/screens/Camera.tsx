import { IconBolt, IconCameraRotate, IconPhoto } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import styles from './Camera.module.css'

export function Camera() {
  const navigate = useNavigate()

  return (
    <Screen className={styles.screen}>
      <StatusBar tone="light" />
      <TopBar
        left="close"
        to={routes.home}
        tone="light"
        right={
          <button type="button" className={styles.flash} aria-label="フラッシュ">
            <IconBolt size={22} stroke={1.7} />
          </button>
        }
      />

      <Photo asset="friendsSunset" className={styles.viewfinder} label="夕暮れに友達と撮る写真">
        <button type="button" className={styles.libraryIcon} aria-label="写真ライブラリ">
          <IconPhoto size={18} stroke={1.8} />
        </button>
      </Photo>

      <div className={styles.modes} aria-label="撮影モード">
        <span>ビデオ</span>
        <span className={styles.activeMode}>写真</span>
      </div>

      <div className={styles.controls}>
        <button type="button" className={styles.thumbnail} aria-label="アルバムを作成" onClick={() => navigate(routes.albumCreate)}>
          <Photo asset="tile2" className={styles.thumbnailPhoto} />
        </button>
        <button type="button" className={styles.shutter} aria-label="写真を撮る" onClick={() => navigate(routes.albumCreate)}>
          <span className={styles.shutterCenter} />
        </button>
        <button type="button" className={styles.flip} aria-label="カメラを切り替える">
          <IconCameraRotate size={30} stroke={1.6} />
        </button>
      </div>

      <div className={styles.homeIndicator} aria-hidden="true" />
    </Screen>
  )
}
