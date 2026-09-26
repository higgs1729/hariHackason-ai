# 09 — What needs a person

> Parked here during the unattended run of `09-completion-plan.md`. Everything
> below was left because a script cannot do it or should not decide it.
> Tick a box when done.

## Before the demo (by 12:00)

- [ ] **Rehearse once on a real phone through the tunnel.** Run
      `scripts/demo.ps1`, open the printed `https://….trycloudflare.com` URL on the
      phone, and walk §8 of 01-requirements: log in as `nao` / `password`, shoot 3,
      AI album, decorate, share to あやか, capsule, open. The e2e runs covered this on
      desktop Chrome with a fake camera (`docs/e2e/run-1`, `run-2`), not on a phone.
- [ ] **Camera permission on the phone.** The camera needs HTTPS, which the tunnel
      gives. First visit asks for permission; allow it before going on stage.
- [ ] **Keep this PC awake and logged in** for the whole event: power plan "never
      sleep", lid open or external display, Windows Update paused. The server, MySQL,
      the tunnel and Claude Code all run on it.
- [ ] **Decide whether to reset the data.** The automated runs left their albums,
      capsules and throwaway accounts (`aud…`, `reg…`) in MySQL, so nao's My page
      shows many albums. Resetting deletes everything, so it was not done
      unattended. To start clean: `demo.ps1 -Stop`, then
      `docker compose down -v` in the repo folder, then `demo.ps1` (the seed runs
      again on the empty database).
- [ ] **The tunnel URL changes every time `demo.ps1` starts the tunnel.** Send the
      URL to the demo phone again after any restart. (A named Cloudflare tunnel
      would fix this but needs a Cloudflare login and a domain.)

## Things only a phone can show

- [ ] **LINE preview card.** Paste a share link (`/s/…`) into a LINE chat and check
      the card shows the album title and the decorated photo. The page serves
      `og:title` / `og:image` (checked by e2e, `og-image.jpg` in each run folder),
      but LINE's crawler and cache are outside what we can test.
- [ ] **Native share sheet.** The share screen calls `navigator.share` and also
      shows the link on the page. Check on the phone that the sheet opens with the
      link and that LINE is in it. (Chrome on Windows opens the Windows share
      dialog for the same call; close it and the button is usable again.)
- [ ] **QR friend scan.** My page → 友達 → QR: show the code on one phone, scan it
      with the other (logged in as another seeded user, e.g. `ayaka` / `password`).
      The endpoints were checked by API (issue, accept, own code 400, reuse 410);
      the camera scan itself was not.

## Risks you should know about

- **AI runs on the Claude Code subscription on this PC** (`claude -p`, model
  sonnet). Measured: shoot hint ~4 s, album of 3 photos 9–14 s, 12 photos in 3
  groups ~21 s. If the plan hits its rate limit the app does not break: albums
  get the rule-based title and captions, hints get fixed text (`aiGenerated = 0`).
  To switch to the API key instead, set `AI_PROVIDER=api` and `ANTHROPIC_API_KEY`
  before starting Spring (`demo.ps1` passes the environment through).
- **Anyone with the tunnel URL can use the app** (decision 6). Stop the tunnel
  after the event (close the cloudflared window or re-run `demo.ps1 -Stop`).
- `/api/dev/*` (seed, open a capsule now) is on because the `dev` profile is
  active. That is what makes "タイムカプセルを開ける" work on stage for a capsule
  sealed for a year. Do not leave this running as a public service.
