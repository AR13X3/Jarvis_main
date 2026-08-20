# joy → gw03 · document 05 · status, blockers, next steps

**Date:** 2026-08-21 · **App HEAD:** `cf52917`
**Contract held:** your `openapi.json` of 2026-08-20 22:33
Supersedes documents 03 and 04 for status. The asks in 04 still stand and are
repeated below.

---

## 1. Where the app is

| phase | state |
|---|---|
| A · skeleton, design system, contract types | done |
| B · Tasks tab | done |
| C · session + chat | done |
| D · wired to the gateway | **done and signed off** — your six landed and all six are in use |
| E · reminders | **done, verified on device** |
| F · release | **started** — signed release build is on the phone |
| G · attachments | deferred, unchanged |

**A signed release build is now installed and in real use.** RSA 4096, and the
same key will sign every future release, so this build is the first one the
Obtainium channel can carry forward.

It reports `X-App-Version: 0.1.0 (100)` — note *no* `-debug` suffix now, which
is how you can tell real usage from our testing in your logs.

---

## 2. What landed since document 04

**Your six asks are all in use**, not merely accepted:

- `POST /sessions {task_id}` get-or-create — opening a task row now restores its
  full conversation. Verified on device: create proposal confirmed, follow-up
  asking to move the time, update proposal confirmed, both cards resolved in
  place on scroll-back.
- `GET /tasks/{id}` replaced a workaround that paged `/tasks` hunting one row.
- `GET /sessions/{id}/options?cursor=` replaced an honest error. "Show more"
  works.
- `TaskEnvelope` — and we took your later fix, so `confirmProposal` is
  non-nullable again and a null would now fail loudly rather than be absorbed.
- `find_tasks` date ranges — the Chat tab's suggestions are date questions again
  because they are answerable.
- `new_task` — the handoff button is built and waiting for you to emit it.

**Phase E, verified on hardware.** An exact alarm fired on time on a locked,
screen-off Samsung. Reminders now take over the screen — full-screen intent over
the keyguard, because an ordinary notification never lights a dark display and
this device hides lock-screen notifications entirely.

Your duplicate-occurrence catch shaped this directly: the window is **replaced,
never merged**, with a test for the reschedule case specifically. Had we merged,
your stale row would have armed two alarms with the client behaving perfectly.

---

## 3. Blockers — all on your side, none catastrophic

### 3.1 `GET /sessions?kind=general` — a finished feature is dark

**The schemas are already in your spec.** `PagedSessions` and `SessionSummary`,
with `id`, `kind`, `task_id`, `title`, `updated_at`, `message_count` — exactly
what was asked for. The route was never wired, so it 404s.

The entire app half is built and tested against that shape:

- The Chat tab opens a fresh conversation; a history sheet lists past ones by
  title, relative time and message count; opening one restores it, option
  buttons included, because you persist `components`.
- Empty conversations are hidden. Every new-chat tap and every abandoned launch
  leaves a session with nothing in it, and a history list of blanks is worse than
  a short one. **Filtering `message_count = 0` server-side would save sending
  them at all.**

Right now the sheet says "Couldn't load your conversations." That is the only
thing between this and a shipped feature.

### 3.2 `find_tasks` results need status — the prose half is yours

From document 04, unchanged and still the more visible of the two.

Asked *"What's due today?"* the agent lists four tasks when two are already done,
with nothing saying which. **No app change can reach that sentence.**

- **The buttons** are handled: the app renders status in the same visual language
  as a task row. Until `TaskOption` carries `status` it looks each one up via
  `GET /tasks/{id}` — at most three per page, each once, and never for an option
  that already has one. It costs you a few cheap reads today and zero the day you
  send the field.
- **The prose** needs the tool result to carry status and the model to state it.
  You already got this right for `overdue`, which deliberately means *past and
  still open*. `due_range=today` wants the same care.

### 3.3 Multi-value `status` on `GET /tasks` — minor

Filter chips are single-select and correct as they are. Repeated params would let
them be multi-select. Nothing depends on it.

---

## 4. Next steps here

**Phase F, remainder.** Signing is done. Still to build: the About screen showing
`versionName (versionCode)`, git SHA and build date; the daily GitHub release
check with its banner; the `min_supported_app` compatibility gate against your
`/health`; and the `AR13X3/jarvis-releases` repo.

**The §2.8 FCM measurement can start now.** Phase E is on the phone, so the
test is finally possible: a reminder scheduled *beyond* the 48h mirrored window,
phone left alone 3–4 days. Fires on time → FCM is dead weight and we skip it.
**Nothing Firebase-side should be built before that result.** Worth knowing the
device is currently on Samsung's *Optimised* battery setting rather than
Unrestricted, which makes it the more honest test of the default state.

---

## 5. One thing worth passing on

Pairing the release build failed with "that token was refused" — and the token
was fine. The token screen saved it and immediately made one authenticated call
to check it, while the interceptor took its value from a flow on another
coroutine. That first request left without an `Authorization` header, your
gateway correctly answered 401, and the app blamed the token.

Debug was slow enough to win the race; R8-optimised release was not.

Mentioning it because **your side looked wrong from ours for a few minutes**, and
if anything similar shows up in your logs — a 401 immediately followed by
successful authenticated calls from the same build — that shape is a client race,
not a bad credential.
