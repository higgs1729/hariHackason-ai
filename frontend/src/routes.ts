/**
 * Route table. Order matches the flow on the poster (section 07).
 *
 * Screens that show one album carry its id in the path so a reload (or a
 * shared link) lands on the same content. `paths` are the patterns given to
 * <Route>; `routes` build concrete hrefs.
 */
export const paths = {
  home: '/',
  /** hub that is not on the poster (07-screens-beyond-poster) */
  me: '/me',
  camera: '/camera',
  albumCreate: '/album/new',
  albumGenerating: '/album/generating/:jobId',
  decorate: '/album/:albumId/decorate/:albumPhotoId',
  share: '/album/:albumId/share',
  capsuleCreate: '/album/:albumId/capsule/new',
  capsuleDone: '/capsule/:capsuleId',
  detail: '/album/:albumId',
} as const

export const routes = {
  home: () => paths.home,
  me: (tab?: 'albums' | 'capsules' | 'friends') => (tab ? `${paths.me}?tab=${tab}` : paths.me),
  camera: () => paths.camera,
  albumCreate: () => paths.albumCreate,
  albumGenerating: (jobId: number) => `/album/generating/${jobId}`,
  decorate: (albumId: number, albumPhotoId: number) => `/album/${albumId}/decorate/${albumPhotoId}`,
  share: (albumId: number) => `/album/${albumId}/share`,
  capsuleCreate: (albumId: number) => `/album/${albumId}/capsule/new`,
  capsuleDone: (capsuleId: number) => `/capsule/${capsuleId}`,
  /** `capsuleId` shows that opened capsule's message above the album; it survives a reload */
  detail: (albumId: number, capsuleId?: number) => `/album/${albumId}${capsuleId ? `?capsule=${capsuleId}` : ''}`,
} as const

export type RouteKey = keyof typeof routes

/** Parse a numeric route param; NaN → null so screens can show "not found". */
export function numParam(value: string | undefined): number | null {
  const n = Number(value)
  return value !== undefined && Number.isInteger(n) && n > 0 ? n : null
}
