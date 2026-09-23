/**
 * The API contract the frontend consumes.
 *
 * One method per endpoint in `docs/design/03-detailed-design.md` §4 (P0 + capsule P1).
 * Two implementations exist: `http.ts` (real backend) and `mock.ts` (in-memory).
 * Screens only ever import `api` from `./index`; they never call fetch directly.
 */
import type {
  Album,
  AlbumPatch,
  AlbumPhoto,
  AlbumPhotoPatch,
  AlbumSummary,
  Capsule,
  CreateCapsuleRequest,
  DecorationElement,
  DecorationWithVersion,
  FriendQr,
  GenerateJob,
  ListPhotosParams,
  LoginRequest,
  Page,
  RegisterRequest,
  ShareLink,
  ShootHint,
  ShootHintRequest,
  TokenPair,
  UploadResult,
  User,
  Photo,
} from './types'

export interface AuthApi {
  register(body: RegisterRequest): Promise<TokenPair>
  login(body: LoginRequest): Promise<TokenPair>
  refresh(refreshToken: string): Promise<TokenPair>
  logout(refreshToken: string): Promise<void>
  me(): Promise<User>
}

export interface UsersApi {
  me(): Promise<User>
  patchMe(body: { userName?: string; userProfile?: string }): Promise<User>
  get(id: number): Promise<User>
  search(q: string): Promise<User[]>
}

export interface FriendsApi {
  list(): Promise<User[]>
  request(userId: number): Promise<void>
  accept(requestId: number): Promise<void>
  /** 05 §3.2 (backend P1). A fresh token each call. */
  qr(): Promise<FriendQr>
  /** Both sides become friends at once. Returns the QR owner. 410 when expired or used. */
  acceptQr(qrToken: string): Promise<User>
}

export interface PhotosApi {
  /** multipart `files`; 10MB each, 20 per call. One bad file does not fail the batch. */
  upload(files: File[]): Promise<UploadResult>
  list(params?: ListPhotosParams): Promise<Page<Photo>>
}

export interface AlbumsApi {
  /** 202 → job id. Caller supplies the Idempotency-Key so a retry replays the same job. */
  generate(photoIds: number[], idempotencyKey: string): Promise<{ jobId: number }>
  job(jobId: number): Promise<GenerateJob>
  /** `memberId` (05 §3.2, reunion mode) is ignored by backends that predate it; callers must not rely on it filtering. */
  list(params?: { limit?: number; cursor?: string; memberId?: number }): Promise<Page<AlbumSummary>>
  get(albumId: number): Promise<Album>
  patch(albumId: number, version: number, body: AlbumPatch): Promise<Album>
  patchPhoto(albumId: number, albumPhotoId: number, version: number, body: AlbumPhotoPatch): Promise<AlbumPhoto>
  addMember(albumId: number, userId: number): Promise<void>
}

export interface DecorationApi {
  get(albumId: number, albumPhotoId: number): Promise<DecorationWithVersion>
  /** Full replacement. `version` becomes `If-Match`. Returns the new version. */
  put(albumId: number, albumPhotoId: number, version: number, elements: DecorationElement[]): Promise<{ version: number }>
  /** Client-rendered cache images. Failure here must not lose the element data already PUT. */
  uploadRendered(albumId: number, albumPhotoId: number, overlay: Blob, composite: Blob): Promise<void>
}

export interface ShareApi {
  create(albumId: number): Promise<ShareLink>
  get(albumId: number): Promise<ShareLink>
}

export interface CapsulesApi {
  create(body: CreateCapsuleRequest): Promise<Capsule>
  list(): Promise<Capsule[]>
  get(capsuleId: number): Promise<Capsule>
  open(capsuleId: number): Promise<Capsule>
  /** dev profile only */
  unsealNow(capsuleId: number): Promise<Capsule>
}

export interface HintsApi {
  /** Always 200 with some text (AI or fixed fallback). Only 429 RATE_LIMITED is an error. */
  shoot(body: ShootHintRequest): Promise<ShootHint>
}

export interface Api {
  hints: HintsApi
  auth: AuthApi
  users: UsersApi
  friends: FriendsApi
  photos: PhotosApi
  albums: AlbumsApi
  decoration: DecorationApi
  share: ShareApi
  capsules: CapsulesApi
}
