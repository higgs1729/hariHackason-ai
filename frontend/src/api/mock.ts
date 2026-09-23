/**
 * In-memory implementation of the API contract.
 *
 * Runs the whole app without a backend (`VITE_API_MODE=mock`, the default in dev
 * until Spring is up). Seeded the way `POST /api/dev/seed` is specified:
 * 4 users, friends between them, a handful of photos, 2 albums.
 *
 * Behaviour mirrors the spec where the UI can tell the difference:
 * - generate → job goes PENDING → CLUSTERING → ENRICHING → READY over ~3s with progress
 * - If-Match mismatch → 409 VERSION_CONFLICT
 * - sealed capsule → SEALED DTO without capsuleMsg / album
 * - one running job per user → 409 JOB_ALREADY_RUNNING
 */
import type { Api } from './contract'
import { tokens } from './tokens'
import {
  ApiError,
  type Album,
  type AlbumPhoto,
  type Capsule,
  type CapsuleOpened,
  type CapsuleSealed,
  type DecorationElement,
  type GenerateJob,
  type Photo,
  type TokenPair,
  type User,
} from './types'

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms))
const iso = (d: Date) => d.toISOString().replace('Z', '+00:00')
const fail = (status: number, code: ApiError['code'], message: string = code): never => {
  throw new ApiError(status, { code, message })
}

/** Gradient placeholder as a data URL so <img src> works without a server. */
function gradientImage(a: string, b: string, w = 1200, h = 900): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs><rect width="100%" height="100%" fill="url(#g)"/></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

const palette: [string, string][] = [
  ['#6fa3e6', '#f2c2a8'],
  ['#4d7ec4', '#d6b3c7'],
  ['#8fc0f0', '#f7d1b0'],
  ['#5f8fd2', '#edc0a4'],
  ['#7aa8e8', '#e8bfb5'],
  ['#9ac5f2', '#f4c9a9'],
]

// ---------------------------------------------------------------------------
// state
// ---------------------------------------------------------------------------

let nextId = 1000
const id = () => nextId++

const users: User[] = [
  { id: 12, userAccount: 'nao', userName: 'わたし', userAvatar: gradientImage('#f7c9d9', '#9dc3f2', 200, 200), userProfile: null, userRole: 'user' },
  { id: 13, userAccount: 'ayaka', userName: 'あやか', userAvatar: gradientImage('#f9d7b5', '#9dc3f2', 200, 200), userProfile: null, userRole: 'user' },
  { id: 14, userAccount: 'miki', userName: 'みき', userAvatar: gradientImage('#cfe3f9', '#f5b8c8', 200, 200), userProfile: null, userRole: 'user' },
  { id: 15, userAccount: 'rin', userName: 'りん', userAvatar: gradientImage('#b5d5f6', '#f9dcb3', 200, 200), userProfile: null, userRole: 'user' },
]
const passwords = new Map<string, string>([
  ['nao', 'password'],
  ['ayaka', 'password'],
  ['miki', 'password'],
  ['rin', 'password'],
])
/** userId → friend userIds (both directions kept) */
const friends = new Map<number, Set<number>>([
  [12, new Set([13, 14, 15])],
  [13, new Set([12])],
  [14, new Set([12])],
  [15, new Set([12])],
])

const photos: (Photo & { ownerId: number })[] = []
const albums: Album[] = []
const decorations = new Map<number, DecorationElement[]>() // albumPhotoId → elements
const jobs = new Map<number, GenerateJob & { ownerId: number }>()
const capsules: (Capsule & { ownerId: number; albumId: number; capsuleMsgStored: string })[] = []
const shares = new Map<number, string>() // albumId → token

let me: User | null = null

function seedPhotos(ownerId: number, count: number, base: Date, gapMin: number) {
  for (let i = 0; i < count; i++) {
    const [a, b] = palette[i % palette.length]
    const taken = new Date(base.getTime() + i * gapMin * 60_000)
    const pid = id()
    photos.push({
      id: pid,
      ownerId,
      url: gradientImage(a, b),
      thumbUrl: gradientImage(a, b, 400, 300),
      picWidth: 1200,
      picHeight: 900,
      picScale: 1.333,
      takenTime: iso(taken),
      takenTimeSource: 'EXIF',
      latitude: 34.7025,
      longitude: 135.4959,
      albumNum: 0,
    })
  }
}

function buildAlbum(ownerId: number, photoRows: Photo[], title: string, summary: string, place: string): Album {
  const owner = users.find((u) => u.id === ownerId)!
  const albumId = id()
  const captions = ['放課後', '梅田', 'みんなで', '夕日', '帰り道', 'ピース']
  const albumPhotos: AlbumPhoto[] = photoRows.map((p, i) => ({
    id: id(),
    photoId: p.id,
    version: 0,
    position: i,
    photoUrl: p.url,
    thumbUrl: p.thumbUrl ?? p.url,
    picWidth: p.picWidth,
    picHeight: p.picHeight,
    picScale: p.picScale,
    takenTime: p.takenTime,
    caption: captions[i % captions.length],
    place,
    weather: '晴れ',
    photoComment: i === 0 ? 'テスト終わりの放課後、最高だった♡' : null,
    music: null,
    hasOverlay: false,
    overlayUserName: null,
    compositeUrl: null,
  }))
  for (const p of photoRows) {
    const row = photos.find((x) => x.id === p.id)
    if (row) row.albumNum += 1
  }
  const first = photoRows[0]
  return {
    id: albumId,
    version: 0,
    title,
    summary,
    albumDate: first.takenTime.slice(0, 10),
    place,
    aiGenerated: 1,
    aiModel: 'mock',
    coverPhotoId: first.id,
    coverThumbUrl: first.thumbUrl ?? first.url,
    userId: ownerId,
    userName: owner.userName,
    photoNum: albumPhotos.length,
    memberNum: 1,
    viewNum: 0,
    shareToken: null,
    myRole: 'owner',
    members: [{ userId: ownerId, userName: owner.userName, userAvatar: owner.userAvatar, memberRole: 'owner' }],
    photos: albumPhotos,
  }
}

function seed() {
  const day = new Date('2026-09-20T07:30:00Z') // 16:30 JST
  seedPhotos(12, 6, day, 20) // one cluster → album
  albums.push(buildAlbum(12, photos.slice(0, 6), '最高の1日', 'テスト終わりの放課後、みんなで梅田へ。', '梅田'))
  seedPhotos(12, 4, new Date('2026-09-13T05:00:00Z'), 15)
  albums.push(buildAlbum(12, photos.slice(6, 10), '土曜の部活のあと', '練習終わりにみんなでアイス。', '天王寺'))
  seedPhotos(12, 5, new Date('2026-09-21T08:00:00Z'), 10) // unassigned
}
seed()

const requireMe = (): User => me ?? fail(401, 'TOKEN_INVALID')
const pair = (u: User): TokenPair => ({ accessToken: `mock-access-${u.id}-${Date.now()}`, refreshToken: `mock-refresh-${u.id}`, user: u })

function findAlbum(albumId: number): Album {
  const u = requireMe()
  const a = albums.find((x) => x.id === albumId) ?? fail(404, 'ALBUM_NOT_FOUND')
  if (!a.members.some((m) => m.userId === u.id)) fail(403, 'ALBUM_FORBIDDEN')
  return a
}

function toCapsuleDto(c: (typeof capsules)[number]): Capsule {
  if (c.status === 'SEALED') {
    const days = Math.max(0, Math.ceil((new Date(c.openTime).getTime() - Date.now()) / 86_400_000))
    const dto: CapsuleSealed = { id: c.id, status: 'SEALED', openTime: c.openTime, daysRemaining: days }
    return dto
  }
  const dto: CapsuleOpened = {
    id: c.id,
    status: 'OPENED',
    openTime: c.openTime,
    openedTime: c.openedTime,
    capsuleMsg: c.capsuleMsgStored,
    album: albums.find((a) => a.id === c.albumId)!,
  }
  return dto
}

// ---------------------------------------------------------------------------
// api
// ---------------------------------------------------------------------------

export const mockApi: Api = {
  auth: {
    async register(body) {
      await delay(200)
      if (body.userAccount.length < 4 || body.userPassword.length < 8) fail(400, 'VALIDATION_FAILED', '入力を確認してください')
      if (users.some((u) => u.userAccount === body.userAccount)) fail(409, 'ACCOUNT_EXISTS')
      const u: User = { id: id(), userAccount: body.userAccount, userName: body.userName, userAvatar: null, userProfile: null, userRole: 'user' }
      users.push(u)
      passwords.set(u.userAccount, body.userPassword)
      friends.set(u.id, new Set())
      me = u
      return pair(u)
    },
    async login(body) {
      await delay(200)
      const u = users.find((x) => x.userAccount === body.userAccount)
      if (!u || passwords.get(u.userAccount) !== body.userPassword) return fail(401, 'CREDENTIALS_INVALID', 'IDかパスワードが違います')
      me = u
      return pair(u)
    },
    async refresh(refreshToken) {
      const uid = Number(refreshToken.replace('mock-refresh-', ''))
      const u = users.find((x) => x.id === uid) ?? fail(401, 'TOKEN_INVALID')
      me = u
      return pair(u)
    },
    async logout() {
      me = null
    },
    async me() {
      return requireMe()
    },
  },

  users: {
    async me() {
      return requireMe()
    },
    async patchMe(body) {
      const u = requireMe()
      Object.assign(u, body)
      tokens.setUser(u)
      return u
    },
    async get(userId) {
      return users.find((u) => u.id === userId) ?? fail(404, 'USER_NOT_FOUND')
    },
    async search(q) {
      const u = requireMe()
      const mine = friends.get(u.id) ?? new Set()
      return users.filter((x) => x.id !== u.id && !mine.has(x.id) && (x.userAccount.startsWith(q) || (x.userName ?? '').startsWith(q)))
    },
  },

  friends: {
    async list() {
      const u = requireMe()
      const ids = friends.get(u.id) ?? new Set()
      return users.filter((x) => ids.has(x.id))
    },
    async request(userId) {
      const u = requireMe()
      // mock: auto-accept so the demo does not need a second phone
      friends.get(u.id)?.add(userId)
      if (!friends.has(userId)) friends.set(userId, new Set())
      friends.get(userId)!.add(u.id)
    },
    async accept() {},
  },

  photos: {
    async upload(files) {
      const u = requireMe()
      await delay(300)
      const uploaded = []
      const rejected = []
      for (const f of files) {
        if (!/^image\/(jpeg|png)$/.test(f.type)) {
          rejected.push({ filename: f.name, code: 'UNSUPPORTED_MEDIA' as const })
          continue
        }
        const url = URL.createObjectURL(f)
        const row: Photo & { ownerId: number } = {
          id: id(),
          ownerId: u.id,
          url,
          thumbUrl: url,
          picWidth: 0,
          picHeight: 0,
          picScale: 1,
          takenTime: iso(new Date(f.lastModified || Date.now())),
          takenTimeSource: 'UPLOAD',
          latitude: null,
          longitude: null,
          albumNum: 0,
        }
        photos.push(row)
        uploaded.push({ id: row.id, url, picWidth: 0, picHeight: 0, takenTime: row.takenTime, latitude: null, longitude: null })
      }
      return { uploaded, rejected }
    },
    async list(params) {
      const u = requireMe()
      let rows = photos.filter((p) => p.ownerId === u.id)
      if (params?.unassigned) rows = rows.filter((p) => p.albumNum === 0)
      rows = [...rows].sort((a, b) => b.takenTime.localeCompare(a.takenTime))
      return { items: rows.slice(0, params?.limit ?? 100), nextCursor: null, total: rows.length }
    },
  },

  albums: {
    async generate(photoIds, idempotencyKey) {
      const u = requireMe()
      const existing = [...jobs.values()].find((j) => j.ownerId === u.id && (j as { key?: string }).key === idempotencyKey)
      if (existing) return { jobId: existing.id }
      if ([...jobs.values()].some((j) => j.ownerId === u.id && !['READY', 'FAILED'].includes(j.status))) {
        fail(409, 'JOB_ALREADY_RUNNING')
      }
      const rows = photos.filter((p) => photoIds.includes(p.id) && p.ownerId === u.id)
      if (rows.length === 0) fail(422, 'NO_VALID_PHOTOS')
      const job: GenerateJob & { ownerId: number; key: string } = {
        id: id(),
        ownerId: u.id,
        key: idempotencyKey,
        status: 'PENDING',
        progress: 0,
        albumIds: [],
        errorMsg: null,
        finishTime: null,
      }
      jobs.set(job.id, job)
      // simulate the pipeline
      void (async () => {
        await delay(400)
        job.status = 'CLUSTERING'
        job.progress = 15
        await delay(600)
        job.status = 'ENRICHING'
        for (let p = 30; p < 100; p += 10) {
          job.progress = p
          await delay(250)
        }
        const sorted = [...rows].sort((a, b) => a.takenTime.localeCompare(b.takenTime))
        const album = buildAlbum(u.id, sorted, `${sorted[0].takenTime.slice(0, 10).replace(/-/g, '.')} のアルバム`, 'AIがまとめました。', '梅田')
        albums.unshift(album)
        job.albumIds = [album.id]
        job.progress = 100
        job.status = 'READY'
        job.finishTime = iso(new Date())
      })()
      return { jobId: job.id }
    },
    async job(jobId) {
      const j = jobs.get(jobId) ?? fail(404, 'JOB_NOT_FOUND')
      const { ownerId: _o, ...dto } = j
      return dto
    },
    async list() {
      const u = requireMe()
      const items = albums.filter((a) => a.members.some((m) => m.userId === u.id)).map(({ photos: _p, members: _m, ...rest }) => rest)
      return { items, nextCursor: null, total: items.length }
    },
    async get(albumId) {
      await delay(100)
      return structuredClone(findAlbum(albumId))
    },
    async patch(albumId, version, body) {
      const a = findAlbum(albumId)
      if (a.version !== version) throw new ApiError(409, { code: 'VERSION_CONFLICT', message: 'conflict', details: { current: a.version } })
      Object.assign(a, body)
      a.version += 1
      return structuredClone(a)
    },
    async patchPhoto(albumId, albumPhotoId, version, body) {
      const a = findAlbum(albumId)
      const p = a.photos.find((x) => x.id === albumPhotoId) ?? fail(404, 'PHOTO_NOT_FOUND')
      if (p.version !== version) throw new ApiError(409, { code: 'VERSION_CONFLICT', message: 'conflict', details: { current: p.version } })
      Object.assign(p, body)
      p.version += 1
      return structuredClone(p)
    },
    async addMember(albumId, userId) {
      const u = requireMe()
      const a = findAlbum(albumId)
      if (!friends.get(u.id)?.has(userId)) fail(403, 'NOT_FRIENDS')
      if (a.members.some((m) => m.userId === userId)) return
      const f = users.find((x) => x.id === userId) ?? fail(404, 'USER_NOT_FOUND')
      a.members.push({ userId, userName: f.userName, userAvatar: f.userAvatar, memberRole: 'editor' })
      a.memberNum = a.members.length
    },
  },

  decoration: {
    async get(albumId, albumPhotoId) {
      const a = findAlbum(albumId)
      const p = a.photos.find((x) => x.id === albumPhotoId) ?? fail(404, 'PHOTO_NOT_FOUND')
      return { elements: decorations.get(albumPhotoId) ?? [], overlayUserId: null, overlayUpdateTime: null, version: p.version }
    },
    async put(albumId, albumPhotoId, version, elements) {
      const u = requireMe()
      const a = findAlbum(albumId)
      const p = a.photos.find((x) => x.id === albumPhotoId) ?? fail(404, 'PHOTO_NOT_FOUND')
      if (p.version !== version) throw new ApiError(409, { code: 'VERSION_CONFLICT', message: 'conflict', details: { current: p.version } })
      decorations.set(albumPhotoId, structuredClone(elements))
      p.version += 1
      p.hasOverlay = elements.length > 0
      p.overlayUserName = u.userName
      return { version: p.version }
    },
    async uploadRendered(albumId, albumPhotoId, _overlay, composite) {
      const a = findAlbum(albumId)
      const p = a.photos.find((x) => x.id === albumPhotoId) ?? fail(404, 'PHOTO_NOT_FOUND')
      p.compositeUrl = URL.createObjectURL(composite)
    },
  },

  share: {
    async create(albumId) {
      const a = findAlbum(albumId)
      const token = shares.get(albumId) ?? Math.random().toString(36).slice(2, 10) + Math.random().toString(36).slice(2, 10)
      shares.set(albumId, token)
      a.shareToken = token
      return { shareToken: token, shareUrl: `${location.origin}/s/${token}` }
    },
    async get(albumId) {
      const a = findAlbum(albumId)
      const token = shares.get(albumId) ?? fail(404, 'ALBUM_NOT_FOUND')
      return { shareToken: token, shareUrl: `${location.origin}/s/${token}`, viewCount: a.viewNum }
    },
  },

  capsules: {
    async create(body) {
      const u = requireMe()
      findAlbum(body.albumId)
      const c = {
        id: id(),
        ownerId: u.id,
        albumId: body.albumId,
        status: 'SEALED' as const,
        openTime: body.openTime,
        daysRemaining: 0,
        capsuleMsgStored: body.capsuleMsg,
      }
      capsules.push(c)
      return toCapsuleDto(c)
    },
    async list() {
      const u = requireMe()
      return capsules.filter((c) => c.ownerId === u.id).map(toCapsuleDto)
    },
    async get(capsuleId) {
      const c = capsules.find((x) => x.id === capsuleId) ?? fail(404, 'CAPSULE_NOT_FOUND')
      return toCapsuleDto(c)
    },
    async open(capsuleId) {
      const c = capsules.find((x) => x.id === capsuleId) ?? fail(404, 'CAPSULE_NOT_FOUND')
      if (new Date(c.openTime).getTime() > Date.now()) fail(409, 'CAPSULE_NOT_YET_OPEN')
      Object.assign(c, { status: 'OPENED', openedTime: iso(new Date()) })
      return toCapsuleDto(c)
    },
    async unsealNow(capsuleId) {
      const c = capsules.find((x) => x.id === capsuleId) ?? fail(404, 'CAPSULE_NOT_FOUND')
      Object.assign(c, { status: 'OPENED', openedTime: iso(new Date()) })
      return toCapsuleDto(c)
    },
  },

  hints: {
    async shoot(body) {
      requireMe()
      await delay(600)
      const n = body.memberCount ?? body.memberNames?.length ?? 2
      const who = body.memberNames?.length ? body.memberNames.join('と') : `${n}人`
      return {
        hint: `${who}で、ひとりずつカメラに向かって走ってきて最後に全員でジャンプ！`,
        poses: ['全員で指ハート', '背中合わせで振り向く', 'ひとりだけ変顔'],
        aiGenerated: 0,
      }
    },
  },
}
