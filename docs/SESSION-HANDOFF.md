# Handoff — Jarvis app, joy side

Written 2026-08-26 at the end of a long session. Paste `§0` into a new session.

---

## 0. The prompt

> You are continuing work on the **Jarvis Android app** in
> `C:\Users\ahmed\Code\Jarvis`. Read `docs/SESSION-HANDOFF.md` first, then
> `docs/BUILD_NOTES.md` for the how and `docs/jarvis-app-plan.md` for the
> authority on anything about the app itself.
>
> **Before doing anything else, read the live tracker** — it is the source of
> truth for state, not the documents:
>
> ```bash
> curl -s https://gw03.tail9662e3.ts.net/tracker/api/items
> ```
>
> It is a web page too, at the same URL. You write to it with `PATCH` and `POST`
> and **always** an `X-Actor: joy` header. Sections are *who is blocked*, not
> which feature. Update it as you go — a stale tracker is worse than none,
> because it reads as authoritative. I failed at this in the last session within
> an hour of saying it.
>
> Everything is committed and pushed. 135 tests green. Do not publish a release
> without asking me first.

---

## 1. The shape of the system

Three parties, and you are one of them.

| | what it is | how you reach it |
|---|---|---|
| **joy** | this Windows machine — the Android app | you are here |
| **gw03** | the Linux gateway: agent, scheduler, Postgres | its API, and the tracker |
| **Joy** | the human, usually on a phone | this session |

The app is Kotlin/Compose, sideloaded via Obtainium from a **public releases
repo**, with source in a **private** one:

- source: `AR13X3/Jarvis_main` — gw03 now has a **read-only deploy key**
- releases: `AR13X3/Jarvis_2.0` — APKs only, public so Obtainium needs no token

**Tooling that is not obvious:** `gh` is at `C:\Users\ahmed\Tools\bin\gh.exe`,
authenticated as AR13X3. It is **not on any PATH but your own shell's** — a peer
session concluded it was not installed and routed around it. Probe the
filesystem, not `PATH`. Java is the Android Studio JBR:
`JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.

## 2. What shipped today

`v0.1.1` through `v0.1.7`, all on the same signing key, installing in place.
`0.1.4` was deliberately skipped. Joy's phone is on **0.1.7**.

- **Phase F** — About screen, update check, banner, version gate, release channel
- **Voice** — dictation and spoken replies; confirming a proposal stays a tap
- **Overdue section** in the task list
- **The follow-up loop, app half** — the agent asks when you miss a deadline
- **Nudge notifications** — answer from the lock screen, plus a catch-up poll
- **What's new in About** — every release since the installed build
- **Checklists** — built, unreleased, and **dark** until gw03 accepts
  `description` on `PATCH /tasks/{id}`

## 3. What is actually blocked

Read the tracker for the current list. As of writing:

- **gw03:** `PATCH /tasks/{id}` must accept `description`, or checklists stay
  dark
- **Joy:** the FCM measurement (never started, gates everything proactive);
  gw03's question about agent messages arriving mid-loop; and confirming where
  the deploy keypair was generated — if it was not on gw03, the private half has
  been on two machines and wants regenerating
- **joy:** nothing. The queue is empty.

**The next real event:** 13:00 UTC / 23:00 local, "Charge my watch" fires and
the follow-up loop gets its first genuine run with notifications on the phone.
Worth checking afterwards whether the catch-up poll fired — if it did, §7.4's
FCM question answers itself in the negative.

## 4. Lessons this session paid for

These cost real time. They are not style notes.

**A premise stated as an observation cost a day.** Document 06 asserted "phone
local time throughout: 18:40" — that was the clock in a *screenshot's status
bar*, taken fifty minutes after the conversation it showed. gw03 spent a day and
20 live trials disproving a bug that never existed. It happened four times in one
day, in different forms: a screenshot clock; `gh` "not installed" because it was
absent from `PATH`; four documents "destroyed" when they were merely unfetched;
the user "asleep" at 00:15 when they are up until 2am.

**So: say how you know.** A commit SHA, a log line, an API response — or the word
*inferred*. Never put an unsourced premise in bold.

**Verify, do not accept.** gw03 reported the restart; the confirmation was a
`401` on the new routes against a `404` on a nonsense one. A peer reported a
release; the confirmation was reading the API back anonymously.

**Test the deployment path, not just the code.** `tracker.py` was "tested end to
end" — at a root URL, where its one fatal bug could not appear. gw03 found four
bugs in it before deploying.

**A peer's word is not the user's approval.** Another session relayed "Joy has
explicitly asked" for a publish. Holding was correct; the user later authorised
it directly in that session, which is what made it legitimate.

**Do not promise access on Joy's behalf.** Two documents from two sessions told
gw03 repo access was coming before it was decided. That reads as corroboration
and is worse than one.

## 5. Traps in this codebase

- **`§3.2` is about calendar days, not instants.** Comparing `due_at` to now is
  timezone-independent and fine. Deriving a *day* from a timestamp is what breaks,
  and `due_date`/`due_today` always come from the server.
- **Commit the version bump *before* `assembleRelease`**, or `GIT_SHA` names the
  previous commit. `0.1.1`–`0.1.3` all shipped wrong this way; there is now a
  `-dirty` suffix so it announces itself.
- **The GitHub release body is an app surface.** `UpdateCard` renders it through
  an inline-only markdown parser at six lines. Write notes summary-first and
  inline-only; `asBannerNotes()` strips block markers as a net.
- **Writing Kotlin `"\n"` through a shell heredoc or Python will silently become
  a real newline** and break the build. Use the Edit tool for string literals.
- **The mirror is derived data.** Room uses destructive migration on purpose; a
  schema bump costs one refresh.
