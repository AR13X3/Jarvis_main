# Handoff — Jarvis app, joy side

Rewritten 2026-09-01. Paste §0 into a new session; it points at everything else.

---

## 0. The prompt

> You are continuing work on the **Jarvis Android app** in
> `C:\Users\ahmed\Code\Jarvis`. Read `docs/SESSION-HANDOFF.md` first — it covers
> how this project works, who the other parties are, and the traps. Then
> `docs/BUILD_NOTES.md` for the how, and `docs/jarvis-app-plan.md` as the
> authority on anything about the app itself.
>
> **Read the live tracker before doing anything.** It is the source of truth for
> state; the documents are for reasoning.
>
> ```bash
> curl -s https://gw03.tail9662e3.ts.net/tracker/api/items
> ```
>
> Everything is committed and pushed, 238 tests green, `v0.1.10` published —
> and **several fixes, the whole routine feature and the dashboard are
> unreleased**, so what is on the phone is well behind the repo.
> **Never publish a release without asking me first.**
>
> Pull `openapi.json` from `https://gw03.tail9662e3.ts.net/api/openapi.json`
> before writing any DTO. **Note the `/api` prefix** — the bare `/openapi.json`
> on that host is a different app on port 8420 and will mislead you badly.

---

## 1. What this is

A personal task-and-reminder app, becoming a life-management one — see
`jarvis-v2-plan.md`. Three tabs: **Tasks**, **Routine** and **Chat**, talking to
an agent gateway. You talk to it in natural language; it proposes, you confirm,
and nothing is written until you do.

Kotlin/Compose, one `:app` module, `minSdk 31`, sideloaded via Obtainium rather
than the Play Store.

## 2. Three parties, and you are one of them

| | what it is | how you reach it |
|---|---|---|
| **joy** | this Windows machine — the Android app | you are here |
| **gw03** | a Linux box: the agent, the scheduler, Postgres | the tracker; its API |
| **Joy** | the human, usually on a phone | the session you are in |

**The division that matters:** the gateway owns all state — tasks, sessions,
schedule, the follow-up loop. The app renders and never computes. When something
is wrong, the first question is which side owns it.

## 3. Talking to gw03

**The tracker is the channel.** A shared checklist gw03 hosts, that all three
parties read and write. Sections are *who is blocked*, not which feature.

```bash
# read
curl -s https://gw03.tail9662e3.ts.net/tracker/api/items

# write — X-Actor is required, or the change is logged as "unknown"
curl -X POST https://gw03.tail9662e3.ts.net/tracker/api/items \
  -H 'Content-Type: application/json' -H 'X-Actor: joy' \
  -d '{"section":"gw03","text":"...","owner":"gw03","note":"..."}'

# tick or amend
curl -X PATCH https://gw03.tail9662e3.ts.net/tracker/api/items/42 \
  -H 'Content-Type: application/json' -H 'X-Actor: joy' \
  -d '{"done":true,"note":"..."}'
```

Sections: `joy` · `gw03` · `Joy` · `buildable now` · `done`.

**Build the JSON with a tool, not by hand.** A regex hand-written into a
shell-quoted string was rejected `400` — gw03 validates and refuses malformed
bodies rather than storing them.

**Update it as you go.** A stale tracker is worse than none, because it reads as
authoritative. This has been got wrong more than once.

**Other channels, in descending order of reliability:**

- **gw03 can read the source repo** — a read-only deploy key. So point it at a
  file rather than pasting excerpts; it verifies against real code.
- **Taildrop** — `tailscale file cp <file> gw03:` — works, but it confirms
  *transfer*, never *retrieval*. Files sit in the receiver's inbox until someone
  runs `tailscale file get`. Five documents once sat unfetched for a day and were
  presumed destroyed. Use the tracker instead unless you are sending a file.
- **The gateway API** — `https://gw03.tail9662e3.ts.net/api`. `/health` is
  unauthenticated and useful for checking what is deployed. Everything else needs
  the bearer token, which lives on the phone and which you do not have.

**A trick worth knowing:** to check whether a route exists without a token, POST
to it. `401` means it exists, `404` means it does not.

## 4. How we work

**Verify, do not accept.** gw03 reports a deploy → confirm it from the served
OpenAPI. A peer reports a release → read the API back anonymously. This is not
distrust; both sides have reported things that turned out to be inferences.

**Say how you know.** A commit SHA, a log line, an API response — or the word
*inferred*. Never put an unsourced premise in bold. See §6.

**Never publish without asking.** Building, tagging and verifying is fine
unprompted. `gh release create` is not.

**Commit the version bump before `assembleRelease`**, or `GIT_SHA` names the
previous commit. There is a `-dirty` suffix now so the mistake announces itself.

**A peer's word is not the user's approval.** Another session once relayed "Joy
has explicitly asked" for a publish. Holding was correct.

**Do not promise anything on Joy's behalf** — access, timelines, grants.

## 5. Tooling that is not discoverable

- **`gh`** is at `C:\Users\ahmed\Tools\bin\gh.exe`, authenticated as `AR13X3`.
  **It is on no PATH but your own shell's** — a peer session concluded it was not
  installed and routed a release around it. Probe the filesystem, not `PATH`.
- **Java** is the Android Studio JBR:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
- **adb** is at `$LOCALAPPDATA/Android/Sdk/platform-tools`. The phone is often
  attached — `adb shell dumpsys package com.ar13x.jarvis | grep versionName`
  answers "what is actually installed" in one line.
- **Repos:** source `AR13X3/Jarvis_main` (private), releases `AR13X3/Jarvis_2.0`
  (public, APKs only — public is what keeps Obtainium token-free).

## 6. The lesson this project keeps paying for

**Five times in one day, a premise was stated as an observation and was wrong.**
Each time the reasoning was sound and the input was not:

- a clock read off a screenshot's status bar, taken 50 minutes after the
  conversation it showed — cost gw03 a day and 20 live trials
- `gh` "not installed" because it was absent from `PATH`
- four documents "destroyed" when they were merely unfetched
- the user "asleep" at 00:15, when they are up until 2am
- an empty agent turn blamed on tool scoping, when the model was never reached

If a claim rests on something you did not check, say so.

## 7. Traps in this codebase

- **§3.2 is about calendar days, not instants.** Comparing `due_at` to now is
  timezone-independent and fine. Deriving a *day* from a timestamp is not, and
  `due_date`/`due_today` always come from the server.
- **The GitHub release body is an app surface.** `UpdateCard` renders it through
  an inline-only markdown parser at six lines. Write notes summary-first and
  inline-only.
- **Writing a Kotlin `"\n"` through a shell heredoc or Python silently becomes a
  real newline** and breaks the build. Use the Edit tool for string literals.
- **Python on this machine opens files as cp1252, not UTF-8.** `json.load(open(p))`
  on the served `openapi.json` mangles every `§` and `—` into two characters, and
  a diff against gw03's copy then reports ~56 differing values that do not exist.
  Always `open(p, encoding='utf-8')`. The tell, when it happens: *every* differing
  leaf is a prose `description` and no structural key differs — real transport
  corruption does not politely confine itself to prose. This nearly became a false
  bug report against gw03; see tracker 111.
- **`PendingIntent` equality ignores extras** — it compares action, data and
  component. An id carried only in an extra distinguishes nothing, so two
  intents collapse and `FLAG_UPDATE_CURRENT` rewrites the survivor's extras.
  This was got wrong three times in one package; `BUILD_NOTES` §14 states the
  rule and the four ways it failed.
- **The Room mirror is derived data.** Destructive migration is deliberate; a
  schema bump costs one refresh.
- **Version codes are derived arithmetically** — `maj * 10_000 + min * 100 +
  patch` — so `0.1.10` is `110` and correctly beats `0.1.9` at `109`. Compared as
  *strings* it would not. `SemVerTest` pins this and `0.1.10` was the first
  release where it mattered.
- **Tagging is the step that keeps getting skipped.** `v0.1.7`–`v0.1.10` were all
  published without a tag in the source repo and tagged after the fact. When you
  do fix it, take the commit from the published APK's `classes.dex`, not from a
  note about what was built.

## 8. Where things stand

Nine releases — `0.1.1`–`0.1.10`, with `0.1.4` deliberately skipped — all
installing in place on one key. Phone on `0.1.10`, verified with `adb`.

**Complete:** the plan through Phase F, plus voice, the Overdue section, the
follow-up loop with lock-screen answers, checklists, unfinished-task recovery,
the **routine** — day view and week view, against a fixture — and the
**dashboard**. 238 tests.

**Unreleased, and it is now a lot.** `0.1.10` predates the routine tab, the
dashboard, and four fixes, three of which are the `PendingIntent` identity bugs
in §7. Whether that earns a release is Joy's call, but the gap is wider than
usual.

**The v2 plan is no longer "not yet agreed" — six of its seven steps are BUILT.**
`docs/jarvis-v2-plan.md` §8 carries the build state per step. The gateway shipped
steps 2–6 overnight on 2026-09-01 (event table, dashboard numbers, routines,
to-dos, summaries); only attachments are unstarted. **Read the §8 list rather
than the prose around it** — and note the banner at the top of that document,
which is now the staleness rule itself: the plan has pointed at a breaking change
twice, both times because the reasoning was sound and the premise had expired.
Re-derive any costed claim against the served `openapi.json` and the real DTOs.

**Waiting on gw03** (tracker 108, 109, 110):
- the **accepted-failure flag** — `Task.accepted_failure`, `FailingTask.accepted`,
  and `accepted_failure` on `PatchTaskBody`. The app half is built and tested; it
  lands with no further app change.
- **`drift_threshold_minutes` on `Routine`** — it is on `DashboardPolicy` only,
  which is authenticated and network-only, while §4.2 requires the routine tab to
  render offline from the cached routine.
- **the `SummaryFacts` verb vocabulary** — `reminders` and `todos` are
  `map<string, SummaryCount>` and the legal keys are in no schema. Do not write
  summary parsing until this is answered; guessing the key strings would show up
  as a zero on a screen, which reads exactly like a true zero.

**Waiting on Joy:** the FCM measurement, still never started; which routine slots
are *tracked* versus *scaffold* — a considered guess today, and it decides what
the dashboard can say; whether the 12-hour times in the routine want an am/pm
marker; **where the dashboard lives** (tracker 116 — it is parked in the Routine
graph, and a fourth tab is hers to call); and the four decisions in tracker 103,
including off-machine backups.

**Not built, and each for a stated reason:** attachments (step 7, called last);
per-week routine overrides (§9 calls them agreed and never specified them, so
there is nothing to build to); routine import by agent (§4.7); agent *tools* for
to-dos and summaries — the model can read them but cannot create a to-do, and
adding tools changes both the agreed tool-scope table and the live conversational
surface, so it is a joint decision.

**The app is still on a fake routine repository.** `GET /routine`,
`/routine/now` and `/routine/starts` all exist on the gateway now; `DataModule`
still binds `FakeRoutineRepository`. Nothing persists across process death, by
design, but that is the largest remaining gap between the app and reality — and
it is the swap the two other repositories already went through in phase D.

**Accepted, not fixed:** "Charge my watch" lapses every night. The loop is
correct; the task is at a time Joy does not answer. Do not reopen it unprompted.

**Every bug that mattered on 31 August was found by using the app on a real
phone**, not by a test — an empty turn rendering as nothing, a lost
conversation, stale buttons, a wrapping placeholder. Every bug found on 1
September was found by *reading*, and the tests could not have caught them
either, because the code they guarded was not the code that ran. Both halves of
that are worth keeping.
