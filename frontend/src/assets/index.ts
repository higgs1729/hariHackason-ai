/**
 * Every photo / illustration slot in the UI resolves through this map.
 * Values are CSS `background` strings today (gradient placeholders).
 * When generated images arrive, replace a value with `url(...)` and nothing
 * else needs to change.
 */
export const assets = {
  /** sunset over the river, city skyline (home / capsule create background) */
  skySunset: 'linear-gradient(180deg, #8fb8ec 0%, #c7d9f3 35%, #f0c9b8 62%, #4a6ea6 100%)',
  /** deep night sky (time capsule complete) */
  skyNight: 'linear-gradient(180deg, #0f1f4a 0%, #1c3a78 55%, #2b4f93 100%)',
  /** friends at sunset, viewed from behind (camera preview / album hero) */
  friendsSunset: 'linear-gradient(180deg, #7fb0ea 0%, #e6b8a6 45%, #b8825f 62%, #2a3550 100%)',
  /** generic album photo tiles */
  tile1: 'linear-gradient(160deg, #6fa3e6, #f2c2a8 60%, #2f4a7a)',
  tile2: 'linear-gradient(200deg, #4d7ec4, #d6b3c7 55%, #1f3560)',
  tile3: 'linear-gradient(140deg, #8fc0f0, #f7d1b0 50%, #35507f)',
  tile4: 'linear-gradient(180deg, #5f8fd2, #edc0a4 58%, #26406f)',
  tile5: 'linear-gradient(220deg, #7aa8e8, #e8bfb5 50%, #2c4577)',
  tile6: 'linear-gradient(120deg, #9ac5f2, #f4c9a9 60%, #3b5a8c)',
  /** avatars */
  avatarMe: 'linear-gradient(135deg, #f7c9d9, #9dc3f2)',
  avatarA: 'linear-gradient(135deg, #f9d7b5, #9dc3f2)',
  avatarB: 'linear-gradient(135deg, #cfe3f9, #f5b8c8)',
  avatarC: 'linear-gradient(135deg, #b5d5f6, #f9dcb3)',
} as const

export type AssetKey = keyof typeof assets
