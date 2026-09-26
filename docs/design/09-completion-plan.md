# 09 — Completion plan

> Agreed with the frontend owner on 2026-09-27. Work runs unattended; anything
> that needs a person is parked in `09-handoff-human.md` and the rest goes on.

## Decisions

| # | Item | Decision |
| --- | --- | --- |
| 1 | Server | This PC. `scripts/demo.ps1` brings up Docker (moving stale socket dirs aside), MySQL, the frontend build, Spring, the seed and a cloudflared quick tunnel, then prints the phone URL. |
| 2 | AI | Claude Code on this PC: the backend calls `claude -p` for album titles/captions and shoot hints; photos are passed as file paths. Failure falls back to the fixed text as today. The API-key implementation stays, chosen by a property. |
| 3 | Audit | Every P0/P1 in 01-requirements checked in `http` mode on :8080. Every button and link on every screen recorded in `09-audit.md` (does what / OK). Dead controls are made to work or removed. The §8 five-minute demo is driven end to end twice by browser automation, with screenshots. |
| 4 | Visuals | Poster-style anime backgrounds (Shinkai-like sky, sunset river and skyline, clouds) generated with local ComfyUI (Illustrious-XL): Home, capsule create, capsule done, capsule illustration, cloud/watercolor texture, camera placeholder. Each of the 8 poster screens is compared side by side with the app. Album photos stay the sample cells. |
| 5 | Backend ownership | The frontend owner's agent may change the backend directly (Claude CLI, QR friend endpoints, `memberId` filter, idempotent seed), one feature branch each. |
| 6 | Tunnel | May be started unattended. Anyone with the URL can open the app. |
| 7 | Merging | A feature branch that passes build, lint and the e2e run is fast-forwarded into `main` and pushed without asking. |

## Done when

- The §8 demo runs to the end twice on :8080 by automation.
- Every row of `09-audit.md` is OK.
- Album generation and shoot hints come from the Claude CLI (`aiGenerated = 1`).
- The 8 screens use generated backgrounds, with poster comparison images.
- `scripts/demo.ps1` alone starts everything.
- Whatever needs a person is listed in `09-handoff-human.md`.
