/**
 * Every photo / illustration slot in the UI resolves through this map.
 * Values are CSS `background-image` strings: photos from `public/photos/`
 * (cells cut from the sample contact sheets, about 295x245 each).
 * Full-screen slots put a gradient over the photo: it keeps white text
 * readable and hides the upscaling of a small cell.
 */
const photo = (cell: string) => `url("/photos/${cell}.jpg")`

export const assets = {
  /** sunset over the river, city skyline (home / capsule create background) */
  skySunset: `linear-gradient(180deg, rgba(20, 38, 77, 0.35) 0%, rgba(20, 38, 77, 0) 40%, rgba(20, 38, 77, 0.55) 100%), ${photo('s2r2c3')}`,
  /** deep night sky (time capsule complete) */
  skyNight: `linear-gradient(180deg, rgba(15, 31, 74, 0.55) 0%, rgba(15, 31, 74, 0.25) 55%, rgba(15, 31, 74, 0.7) 100%), ${photo('s1r4c5')}`,
  /** friends at sunset, viewed from behind (camera preview / album hero) */
  friendsSunset: `linear-gradient(180deg, rgba(20, 38, 77, 0.25) 0%, rgba(20, 38, 77, 0) 45%, rgba(20, 38, 77, 0.4) 100%), ${photo('s1r1c2')}`,
  /** generic album photo tiles */
  tile1: photo('s1r2c3'),
  tile2: photo('s2r4c3'),
  tile3: photo('s1r3c1'),
  tile4: photo('s2r1c3'),
  tile5: photo('s3r2c4'),
  tile6: photo('s1r4c2'),
  /** avatars */
  avatarMe: photo('s1r2c4'),
  avatarA: photo('s2r3c1'),
  avatarB: photo('s2r1c4'),
  avatarC: photo('s2r3c2'),
} as const

export type AssetKey = keyof typeof assets
