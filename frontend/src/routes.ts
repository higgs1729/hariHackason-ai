/** Route table. Order matches the flow on the poster (section 07). */
export const routes = {
  home: '/',
  camera: '/camera',
  albumCreate: '/album/new',
  decorate: '/album/decorate',
  share: '/album/share',
  capsuleCreate: '/capsule/new',
  capsuleDone: '/capsule/done',
  detail: '/album/detail',
} as const

export type RouteKey = keyof typeof routes
