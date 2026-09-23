/**
 * Wire types for the backend API.
 *
 * Source of truth: `docs/design/03-detailed-design.md` §3–§4 on the `yuu` branch.
 * Field names are copied verbatim (camelCase, numeric ids, ISO-8601 with offset).
 * Do not rename anything here without changing the backend spec first.
 */

/** ISO-8601 with offset, e.g. `2026-09-20T17:30:00+09:00`. */
export type IsoDateTime = string
/** `YYYY-MM-DD`. */
export type IsoDate = string

// ---------------------------------------------------------------------------
// Errors (§3.2)
// ---------------------------------------------------------------------------

export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'UNSUPPORTED_MEDIA'
  | 'IDEMPOTENCY_KEY_REQUIRED'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_INVALID'
  | 'CREDENTIALS_INVALID'
  | 'ACCOUNT_BANNED'
  | 'ALBUM_FORBIDDEN'
  | 'ROLE_INSUFFICIENT'
  | 'NOT_FRIENDS'
  | 'USER_BLOCKED'
  | 'USER_NOT_FOUND'
  | 'PHOTO_NOT_FOUND'
  | 'ALBUM_NOT_FOUND'
  | 'CAPSULE_NOT_FOUND'
  | 'JOB_NOT_FOUND'
  | 'ACCOUNT_EXISTS'
  | 'VERSION_CONFLICT'
  | 'FRIEND_REQUEST_EXISTS'
  | 'CAPSULE_NOT_YET_OPEN'
  | 'JOB_ALREADY_RUNNING'
  | 'SHARE_REVOKED'
  | 'FILE_TOO_LARGE'
  | 'NO_VALID_PHOTOS'
  | 'IF_MATCH_REQUIRED'
  | 'RATE_LIMITED'
  | 'NETWORK_ERROR' // client-side only: fetch threw before a response arrived

export interface ApiErrorBody {
  code: ErrorCode
  message: string
  details?: Record<string, unknown> | null
  traceId?: string
}

/** Thrown by every API call that fails. `status` is the HTTP status (0 for network errors). */
export class ApiError extends Error {
  readonly status: number
  readonly code: ErrorCode
  readonly details: Record<string, unknown> | null
  readonly traceId: string | undefined

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.details = body.details ?? null
    this.traceId = body.traceId
  }
}

// ---------------------------------------------------------------------------
// Auth / User (§4.1, §4.2)
// ---------------------------------------------------------------------------

export type UserRole = 'user' | 'admin' | 'ban'

export interface User {
  id: number
  userAccount: string
  userName: string | null
  userAvatar: string | null
  userProfile: string | null
  userRole: UserRole
}

export interface RegisterRequest {
  /** 4+ chars */
  userAccount: string
  /** 8+ chars */
  userPassword: string
  userName: string
}

export interface LoginRequest {
  userAccount: string
  userPassword: string
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  user: User
}

// ---------------------------------------------------------------------------
// Photos (§4.4)
// ---------------------------------------------------------------------------

export interface UploadedPhoto {
  id: number
  url: string
  picWidth: number
  picHeight: number
  takenTime: IsoDateTime
  latitude: number | null
  longitude: number | null
}

export interface RejectedUpload {
  filename: string
  code: ErrorCode
}

export interface UploadResult {
  uploaded: UploadedPhoto[]
  rejected: RejectedUpload[]
}

/** Row of `GET /api/photos`. Shape follows the `photo` table; only the columns the UI reads. */
export interface Photo {
  id: number
  url: string
  thumbUrl: string | null
  picWidth: number
  picHeight: number
  picScale: number
  takenTime: IsoDateTime
  takenTimeSource: 'EXIF' | 'UPLOAD'
  latitude: number | null
  longitude: number | null
  albumNum: number
}

export interface Page<T> {
  items: T[]
  nextCursor: string | null
  total: number
}

export interface ListPhotosParams {
  unassigned?: boolean
  from?: IsoDateTime
  to?: IsoDateTime
  limit?: number
  cursor?: string
}

// ---------------------------------------------------------------------------
// Albums (§4.5)
// ---------------------------------------------------------------------------

export type MemberRole = 'owner' | 'editor'

export interface AlbumMember {
  userId: number
  userName: string | null
  userAvatar: string | null
  memberRole: MemberRole
}

export type Weather = '晴れ' | '曇り' | '雨' | '雪'

/** One `album_photo` row. `id` is the album_photo id; decoration and metadata are keyed by it. */
export interface AlbumPhoto {
  id: number
  photoId: number
  version: number
  position: number
  photoUrl: string
  thumbUrl: string
  picWidth: number
  picHeight: number
  picScale: number
  takenTime: IsoDateTime
  caption: string | null
  place: string | null
  weather: Weather | null
  photoComment: string | null
  music: string | null
  hasOverlay: boolean
  overlayUserName: string | null
  compositeUrl: string | null
}

export interface Album {
  id: number
  /** ETag value. Send back as `If-Match` on writes. */
  version: number
  title: string
  summary: string | null
  albumDate: IsoDate
  place: string | null
  /** 1 when Claude wrote the copy, 0 for the rule-based fallback. Never surface "AI failed". */
  aiGenerated: 0 | 1
  aiModel: string | null
  coverPhotoId: number
  coverThumbUrl: string
  userId: number
  userName: string | null
  photoNum: number
  memberNum: number
  viewNum: number
  /** null until `POST /share` has been called. */
  shareToken: string | null
  myRole: MemberRole
  members: AlbumMember[]
  photos: AlbumPhoto[]
}

/** Row of `GET /api/albums` (list). Same as Album minus `photos`/`members`. */
export type AlbumSummary = Omit<Album, 'photos' | 'members'>

export type JobStatus = 'PENDING' | 'CLUSTERING' | 'ENRICHING' | 'READY' | 'FAILED'

export interface GenerateJob {
  id: number
  status: JobStatus
  /** 0–100 */
  progress: number
  albumIds: number[]
  errorMsg: string | null
  finishTime: IsoDateTime | null
}

export interface AlbumPatch {
  title?: string
  coverPhotoId?: number
  summary?: string
}

export interface AlbumPhotoPatch {
  caption?: string
  place?: string
  weather?: Weather | null
  photoComment?: string
  music?: string | null
}

// ---------------------------------------------------------------------------
// Decoration (§4.6) — coordinates are 0..1 normalised to the photo box
// ---------------------------------------------------------------------------

export interface StrokeElement {
  id: string
  type: 'stroke'
  color: string
  /** line width as a fraction of the photo width */
  width: number
  points: [number, number][]
}

export interface TextElement {
  id: string
  type: 'text'
  text: string
  x: number
  y: number
  font: 'hand' | 'sans'
  /** font size as a fraction of the photo height */
  size: number
  color: string
  rotation: number
}

export interface StickerElement {
  id: string
  type: 'sticker'
  assetId: string
  x: number
  y: number
  scale: number
  rotation: number
}

export interface FilterElement {
  id: string
  type: 'filter'
  name: string
  intensity: number
}

export type DecorationElement = StrokeElement | TextElement | StickerElement | FilterElement

export interface Decoration {
  elements: DecorationElement[]
  overlayUserId: number | null
  overlayUpdateTime: IsoDateTime | null
}

/** GET /decoration returns the body plus the album_photo `version` as ETag. */
export interface DecorationWithVersion extends Decoration {
  version: number
}

// ---------------------------------------------------------------------------
// Share (§4.7)
// ---------------------------------------------------------------------------

export interface ShareLink {
  shareToken: string
  /** Absolute URL built from the request Host. Pass straight to `navigator.share`. */
  shareUrl: string
  viewCount?: number
}

// ---------------------------------------------------------------------------
// Capsule (§4.8) — sealed and opened are different types on purpose
// ---------------------------------------------------------------------------

export interface CapsuleSealed {
  id: number
  status: 'SEALED'
  openTime: IsoDateTime
  daysRemaining: number
}

export interface CapsuleOpened {
  id: number
  status: 'OPENED'
  openTime: IsoDateTime
  openedTime: IsoDateTime
  capsuleMsg: string | null
  album: Album
}

export type Capsule = CapsuleSealed | CapsuleOpened

export interface CreateCapsuleRequest {
  albumId: number
  openTime: IsoDateTime
  capsuleMsg: string
}

// ---------------------------------------------------------------------------
// Shoot hint — POST /api/hints/shoot (05-backend-answers §8)
// ---------------------------------------------------------------------------

export interface ShootHintRequest {
  memberCount?: number
  memberNames?: string[]
  place?: string
  mood?: string
}

export interface ShootHint {
  hint: string
  poses: string[]
  /** 0 = fixed fallback text. For logs only: never branch the UI on it (§8.1). */
  aiGenerated: 0 | 1
}

// ---------------------------------------------------------------------------
// Friends (§4.3)
// ---------------------------------------------------------------------------

export interface FriendRequests {
  incoming: User[]
  outgoing: User[]
}
