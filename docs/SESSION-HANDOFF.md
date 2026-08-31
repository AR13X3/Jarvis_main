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
> Everything is committed and pushed, 144 tests green, `v0.1.10` published.
> **Never publish a release without asking me first.**

---

## 1. What this is

A personal task-and-reminder app. Two tabs — **Tasks** and **Chat** — talking to
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
- **The Room mirror is derived data.** Destructive migration is deliberate; a
  schema bump costs one refresh.
- **Version codes are derived arithmetically**, so `0.1.10` (10010… rendered
  `110`) correctly beats `0.1.9` (109). As *strings* it would not. `SemVerTest`
  pins this and `0.1.10` was the first release where it mattered.

## 8. Where things stand

Ten releases, all installing in place on one key. Phone on `0.1.9`; `0.1.10` is
published and not yet installed.

**Complete:** the plan through Phase F, plus voice, the Overdue section, the
follow-up loop with lock-screen answers, checklists, and unfinished-task
recovery. 144 tests.

**Waiting on gw03:** teach the model to write checklists as `- [ ]` lines — a
prompt change, no contract change. Until then the format works but is
undiscoverable, because the natural phrasing produces a numbered list the parser
does not accept.

**Accepted, not fixed:** "Charge my watch" lapses every night. The loop is
correct; the task is at a time Joy does not answer. Joy has decided that is fine.
Do not reopen it unprompted.

**Every bug that mattered today was found by using the app on a real phone**, not
by a test — an empty turn rendering as nothing, a lost conversation, stale
buttons, a wrapping placeholder. The tests are worth having and have caught real
things, but they do not find these.
