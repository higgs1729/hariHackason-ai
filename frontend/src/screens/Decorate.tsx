import { useEffect, useState } from 'react'
import { IconArrowBackUp, IconDeviceFloppy, IconHeart, IconPencil, IconSparkles, IconTypography } from '@tabler/icons-react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, type ApiError, type DecorationElement } from '../api'
import { DoodleCanvas, renderImages } from '../components/DoodleCanvas'
import { ErrorNote, Loading } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { numParam, routes } from '../routes'
import { toApiError, useAsync } from '../state/useAsync'
import styles from './Decorate.module.css'

const COLORS = ['#ff7fb0', '#ffffff', '#14264d', '#ffd166']

/**
 * Screen 04: doodle on one album photo.
 * GET /decoration → edit locally → PUT /decoration (If-Match) → POST /decoration/rendered.
 * The element list is the truth; the rendered images are a cache and may fail without losing work.
 */
export function Decorate() {
  const navigate = useNavigate()
  const params = useParams()
  const albumId = numParam(params.albumId)
  const albumPhotoId = numParam(params.albumPhotoId)

  const album = useAsync(() => (albumId ? api.albums.get(albumId) : Promise.reject(new Error('no album'))), [albumId])
  const deco = useAsync(
    () => (albumId && albumPhotoId ? api.decoration.get(albumId, albumPhotoId) : Promise.reject(new Error('no photo'))),
    [albumId, albumPhotoId],
  )

  const [elements, setElements] = useState<DecorationElement[]>([])
  const [tool, setTool] = useState<'pen' | 'none'>('pen')
  const [color, setColor] = useState(COLORS[0])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    if (deco.data) setElements(deco.data.elements)
  }, [deco.data])

  const target = album.data?.photos.find((p) => p.id === albumPhotoId) ?? null
  const others = album.data?.photos.filter((p) => p.id !== albumPhotoId).slice(0, 2) ?? []

  // Inline text entry instead of window.prompt(): prompt() is blocked in some
  // WebViews and looks foreign on iOS.
  const [textDraft, setTextDraft] = useState<string | null>(null)
  const addText = () => {
    const text = textDraft?.trim()
    setTextDraft(null)
    if (!text) return
    setElements((els) => [
      ...els,
      { id: `t${Date.now().toString(36)}`, type: 'text', text, x: 0.5, y: 0.85, font: 'hand', size: 0.09, color, rotation: -4 },
    ])
  }

  const undo = () => setElements((els) => els.slice(0, -1))

  const save = async () => {
    if (!albumId || !albumPhotoId || !target || !deco.data) return
    setBusy(true)
    setError(null)
    try {
      let version = deco.data.version
      try {
        version = (await api.decoration.put(albumId, albumPhotoId, version, elements)).version
      } catch (err) {
        const e = toApiError(err)
        if (e.code !== 'VERSION_CONFLICT') throw e
        // someone else saved first: take their version number, keep our elements, retry once
        const current = Number(e.details?.current ?? (await api.decoration.get(albumId, albumPhotoId)).version)
        version = (await api.decoration.put(albumId, albumPhotoId, current, elements)).version
      }
      try {
        const { overlay, composite } = await renderImages(target.photoUrl, elements)
        await api.decoration.uploadRendered(albumId, albumPhotoId, overlay, composite)
      } catch {
        // cache images are optional; GET /composite falls back to the original photo
      }
      navigate(routes.share(albumId))
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  if (!albumId || !albumPhotoId) return <Loading label="アルバムが見つかりません" />

  return (
    <Screen className={styles.screen}>
      <StatusBar />
      <TopBar
        to={routes.albumCreate()}
        right={
          <button type="button" className={styles.save} aria-label="保存" disabled={busy} onClick={save}>
            <IconDeviceFloppy size={24} stroke={1.7} />
          </button>
        }
      />

      <main className={styles.paper}>
        <h1 className={styles.heading}>
          <IconHeart className={styles.headingHeart} size={24} stroke={1.5} aria-hidden="true" />
          {album.data?.title ?? '…'} <span>♡</span>
        </h1>

        {album.loading || deco.loading ? (
          <Loading />
        ) : (
          <div className={styles.canvas}>
            <IconSparkles className={styles.sparkleLeft} size={36} stroke={1.3} aria-hidden="true" />
            <IconHeart className={styles.heartRight} size={39} stroke={1.6} aria-hidden="true" />

            <div className={`${styles.polaroid} ${styles.firstPolaroid}`}>
              <div className={styles.target}>
                <Photo src={target?.photoUrl} className={styles.targetPhoto} label={target?.caption ?? '写真'} />
                {target && (
                  <DoodleCanvas
                    photoUrl={target.photoUrl}
                    elements={elements}
                    onElementsChange={setElements}
                    tool={tool}
                    color={color}
                    className={styles.overlay}
                  />
                )}
              </div>
              <span className={styles.captionLine}>{target?.caption}</span>
            </div>

            <div className={styles.notes}>
              <IconHeart className={styles.noteHeart} size={31} stroke={1.7} aria-hidden="true" />
              <span className={styles.best}>BEST</span>
              <span className={styles.date}>{album.data?.albumDate.replace(/-/g, '.')}</span>
              <span className={styles.scribble} aria-hidden="true" />
            </div>

            {others.length > 0 && (
              <div className={`${styles.polaroid} ${styles.secondPolaroid}`}>
                <div className={styles.collage}>
                  {others.map((p) => (
                    <button key={p.id} type="button" className={styles.collageButton} onClick={() => navigate(routes.decorate(albumId, p.id))} aria-label={`${p.caption ?? '写真'}を編集する`}>
                      <Photo src={p.thumbUrl} className={styles.collagePhoto} />
                    </button>
                  ))}
                </div>
              </div>
            )}

            <IconHeart className={styles.heartLeft} size={40} stroke={1.5} aria-hidden="true" />
            <IconSparkles className={styles.sparkleRight} size={30} stroke={1.5} aria-hidden="true" />
          </div>
        )}
        <ErrorNote error={album.error ?? deco.error ?? error} />
      </main>

      <div className={styles.toolbar} aria-label="編集ツール">
        <button type="button" className={tool === 'pen' ? styles.toolOn : ''} aria-label="ペン" aria-pressed={tool === 'pen'} onClick={() => setTool(tool === 'pen' ? 'none' : 'pen')}>
          <IconPencil size={20} stroke={1.8} />
        </button>
        <button type="button" aria-label="文字" aria-pressed={textDraft !== null} className={textDraft !== null ? styles.toolOn : ''} onClick={() => setTextDraft(textDraft === null ? 'Best Friends ♡' : null)}>
          <IconTypography size={20} stroke={1.8} />
        </button>
        <button type="button" aria-label="ひとつ戻す" onClick={undo} disabled={elements.length === 0}>
          <IconArrowBackUp size={20} stroke={1.8} />
        </button>
        <span className={styles.colors}>
          {COLORS.map((c) => (
            <button key={c} type="button" className={`${styles.swatch} ${c === color ? styles.swatchOn : ''}`} style={{ background: c }} aria-label={`色 ${c}`} onClick={() => setColor(c)} />
          ))}
        </span>
      </div>

      {textDraft !== null && (
        <form
          className={styles.textEntry}
          onSubmit={(e) => {
            e.preventDefault()
            addText()
          }}
        >
          <input type="text" value={textDraft} onChange={(e) => setTextDraft(e.target.value)} maxLength={40} aria-label="入れる文字" autoFocus />
          <button type="submit">追加</button>
        </form>
      )}

      <div className={styles.footer}>
        <button type="button" className={styles.primary} disabled={busy} onClick={save}>
          {busy ? '保存中…' : '保存して友達とシェア'}
        </button>
      </div>
    </Screen>
  )
}
