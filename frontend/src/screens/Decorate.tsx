import { IconDeviceFloppy, IconHeart, IconSparkles, IconSticker } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import styles from './Decorate.module.css'

export function Decorate() {
  const navigate = useNavigate()

  return (
    <Screen className={styles.screen}>
      <StatusBar />
      <TopBar
        to={routes.albumCreate}
        right={
          <button type="button" className={styles.save} aria-label="保存" onClick={() => navigate(routes.share)}>
            <IconDeviceFloppy size={24} stroke={1.7} />
          </button>
        }
      />

      <main className={styles.paper}>
        <h1 className={styles.heading}>
          <IconHeart className={styles.headingHeart} size={24} stroke={1.5} aria-hidden="true" />
          最高の1日 <span>♡</span>
        </h1>

        <div className={styles.canvas}>
          <IconSparkles className={styles.sparkleLeft} size={36} stroke={1.3} aria-hidden="true" />
          <IconHeart className={styles.heartRight} size={39} stroke={1.6} aria-hidden="true" />

          <div className={`${styles.polaroid} ${styles.firstPolaroid}`}>
            <div className={styles.collage}>
              <Photo asset="tile1" className={styles.collagePhoto} label="放課後の写真" />
              <Photo asset="friendsSunset" className={styles.collagePhoto} label="夕暮れの友達の写真" />
            </div>
            <span className={styles.sticker} aria-hidden="true"><IconSticker size={20} stroke={1.7} /></span>
          </div>

          <div className={styles.notes}>
            <IconHeart className={styles.noteHeart} size={31} stroke={1.7} aria-hidden="true" />
            <span className={styles.best}>BEST</span>
            <span className={styles.date}>2025.09.20</span>
            <span className={styles.scribble} aria-hidden="true" />
          </div>

          <div className={`${styles.polaroid} ${styles.secondPolaroid}`}>
            <div className={styles.collage}>
              <Photo asset="tile3" className={styles.collagePhoto} label="みんなで撮った写真" />
              <Photo asset="tile4" className={styles.collagePhoto} label="夕日の写真" />
            </div>
          </div>

          <IconHeart className={styles.heartLeft} size={40} stroke={1.5} aria-hidden="true" />
          <IconSparkles className={styles.sparkleRight} size={30} stroke={1.5} aria-hidden="true" />
        </div>
      </main>

      <div className={styles.footer}>
        <button type="button" className={styles.primary} onClick={() => navigate(routes.share)}>
          保存して友達とシェア
        </button>
      </div>
    </Screen>
  )
}
