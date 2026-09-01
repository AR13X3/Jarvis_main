# Handoff — Jarvis app, joy side

Rewritten 2026-09-01. Paste §0 into a new session; it points at everything else.

---

## 0. The prompt

> You are continuing work on the **Jarvis Android app** in
> `C:\Users\ahmed\Code\Jarvis`. Read `docs/SESSION-HANDOFF.md` first — it covers
> how this project works, who the other parties are, and the traps. Then
> `docs/BUILD_NOTES.md` for the how, `docs/jarvis-app-plan.md` as the authority
> on the app itself, and `docs/jarvis-v2-plan.md` for where it is going.
>
> **Read the live tracker before doing anything.** It is the source of truth for
> state; the documents are for reasoning.
>
> ```bash
> curl -s https://gw03.tail9662e3.ts.net/tracker/api/items
> ```
>
> **§9 is your brief.** Five changes before the next release, and **every
> decision in it is already made** — Joy asked for the calls to be taken, not
> returned as questions. Build it as written; raise something only if it turns
> out to be wrong. §9.1 first: a to-do cannot currently be given a deadline at
> all, which is a bug and not a feature.
>
> As of 2026-09-02: `v0.1.12` published, 290 tests green, tree clean. **Verify
> that rather than believing it** — sessions overlap here and this line goes
> stale within the hour.
>
> **Never publish a release without asking me first.** Building, tagging and
> verifying unprompted is fine; `gh release create` is not. Use
> `bash tools/release.sh`, which refuses a dirty tree, a duplicate tag, a
> versionCode that does not beat the published one, a changed signing cert, and
> an APK that does not embed the commit it names.
>
> Pull `openapi.json` from `https://gw03.tail9662e3.ts.net/api/openapi.json`
> before writing any DTO. **Note the `/api` prefix** — the bare `/openapi.json`
> on that host is a different app and will mislead you badly.
>
> After §9, keep going: test the app on the phone for bugs, and carry on with
> `jarvis-v2-plan.md` §8. Bugs found by using the app have consistently been the
> ones that mattered.

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
- **`JarvisJson` sets `explicitNulls = false`, so a Kotlin `null` is OMITTED,
  not sent.** Right for every body except `PATCH /todos/{id}`, which reads with
  `exclude_unset`: there, an omitted key means "leave alone" and an explicit
  `null` means "clear". A nullable data-class field could only ever send the
  first, so clearing a deadline would silently do nothing. `todoPatchBody` builds
  a `JsonObject` from a `Patch<T>` tri-state instead — do not "simplify" it back
  into a data class; `TodoPatchTest` is what will catch you.
- **An enum the app spells differently from the gateway fails SILENTLY.**
  `JarvisJson` sets `coerceInputValues`, so an unrecognised value becomes the
  field's default — `null` on a nullable one — rather than throwing. This cost a
  real bug: `OverdueResolution` shared one value out of four with the contract,
  so answered overdue cards decoded as *unanswered* and kept live buttons until
  the whole extension allowance was spent. `docs/STATES.md` is every state in the
  system, checked against the contract; keep it current.
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

Eleven releases — `0.1.1`–`0.1.12`, with `0.1.4` deliberately skipped — all
installing in place on one key. **`v0.1.12` is the current release**, built from
`4a149cb` and tagged. Whether the phone has taken it has not been checked — no
device was attached to `adb`.

`tools/release.sh` does the whole thing except publishing: it refuses a dirty
tree, checks the versionCode beats what is published, builds, verifies the
signing cert against the one every prior release used, reads the versionName and
the embedded commit **back out of the APK**, then tags and pushes. It stops
before `gh release create` on purpose. Do not add a `--publish` flag.

**Complete:** the plan through Phase F, plus voice, the Overdue section, the
follow-up loop with lock-screen answers, checklists, unfinished-task recovery,
the **routine** — day view and week view, against a fixture — the
**dashboard**, and **to-dos** (list, backlog, detail, capture, linking). 290 tests.

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

**To-dos are feature-complete for now** — list, backlog filter, detail, capture
and linking. The one thing deliberately absent is a **date picker in the capture
sheet**: §5.2 makes an undated to-do the ordinary case, so capture is for the
backlog and setting a date happens on the detail screen, where the consequence
(*this leaves the backlog*) is visible.

**Summaries are deliberately unstarted**, blocked on tracker 110: `SummaryFacts`
carries verb-keyed maps whose key vocabulary is in no schema. Writing the parser
against guessed strings would surface as a zero on screen, which reads exactly
like a true zero.

**Not built, and each for a stated reason:** attachments (step 7, called last);
per-week routine overrides (§9 calls them agreed and never specified them, so
there is nothing to build to); routine import by agent (§4.7); agent *tools* for
to-dos and summaries — the model can read them but cannot create a to-do, and
adding tools changes both the agreed tool-scope table and the live conversational
surface, so it is a joint decision.

**The routine's remote half is written and NOT bound — one live read away.**
`RemoteRoutineRepository` and its DTOs are done and tested (15 tests); the swap
is the single `@Binds` line in `DataModule`, which explains at the binding site
why it has not been flipped. Two reasons, both real:

1. **`RoutineDay.weekday` has no base in the contract** — a bare `integer`, and
   Python's `weekday()` (0 = Mon) and `isoweekday()` (1 = Mon) differ by one
   character. Wrong choice shifts the whole week by a day with every slot and
   time still correct, so it reads as bad data rather than a client bug.
   `RoutineDto.toDomain` *measures* the base from the payload instead of
   guessing — a complete week is either `{0..6}` or `{1..7}` and those sets do
   not overlap — and throws on anything else. Tracker 117 asks gw03 to put the
   range in the schema, at which point that function collapses to a line.
2. **No routine payload this code has seen came from the gateway.** No bearer
   token on this machine; everything was built from the schema.

So: get one live `GET /routine` (a token, or run it from the phone), check the
weekday base and that the tab renders, then flip the binding. Flipping it blind
trades a working routine tab for an unrun one.

**Accepted, not fixed:** "Charge my watch" lapses every night. The loop is
correct; the task is at a time Joy does not answer. Do not reopen it unprompted.

**Every bug that mattered on 31 August was found by using the app on a real
phone**, not by a test — an empty turn rendering as nothing, a lost
conversation, stale buttons, a wrapping placeholder. Every bug found on 1
September was found by *reading*, and the tests could not have caught them
either, because the code they guarded was not the code that ran. Both halves of
that are worth keeping.

---

## 9. Joy's brief for the next session

Given 2026-09-02, after `v0.1.12`. Five changes, then carry on. **Every decision
in here is made** — Joy asked for the calls to be taken rather than returned as
questions. Build it as written; raise something only if it turns out to be wrong.

### 9.1 A to-do cannot be given a deadline. Fix that first.

**This is a bug, not a feature, and it is the most embarrassing thing in the
app.** `Todo` carries `starts_at`, `due_at`, `starts_on` and `due_date`, and the
detail screen offers exactly one deadline control: **"Clear deadline"**. There is
no date picker anywhere in the codebase — `grep -rl DatePicker app/src/main`
returns nothing. Capture has no date field either, deliberately (`41f4756`).

So a to-do can only acquire a deadline if the agent sets one through chat, and
the only thing the UI can then do is take it away. Joy's words: *"literally no
way to add deadlines at the moment on todos."*

Build both directions:

- **On the detail screen**, a deadline row that opens a date picker — and a time
  picker only if a time is wanted, since most to-dos want a day and not an hour.
  Keep "Clear deadline" where it is.
- **On capture**, an optional deadline. `41f4756` left it out on purpose so the
  `+` stayed one field, and that instinct was right — keep the field optional and
  secondary, not a required second step.

**Send the instant, never the local day.** `due_date` and `starts_on` are the
server's to compute (§3.2). The app sends `due_at` and reads both back. There is
no `DatePicker` precedent in this codebase, so this is also the place that
establishes one — write it as a reusable component, because §9.3 needs it too.

### 9.2 Five tabs, icons only

Dashboard and To-dos become **their own tabs**. Today both are nested screens —
`TodoList` hangs off the Tasks graph via `onTodos`, `DashboardRoute` off
`onOpenDashboard`.

That makes five: **Tasks, To-dos, Routine, Dashboard, Chat**, and the bar goes
**icon-only** so they fit. Joy asked directly for both as tabs, so all five stay
— none gets demoted back to a nested screen.

`JarvisBottomBar` already iterates `JarvisTab.entries` and picks an icon per tab,
so the enum and the icon `when` are most of the work. Dropping the label makes
`contentDescription` the only name a screen reader gets — keep it, and keep the
touch target at its present height rather than shrinking it to match the narrower
cell.

### 9.3 Labels, the way Jira and Trello do them

Today `Todo.tags` is `List<String>`: free text, retyped every time, no colour,
nothing stopping `uni` and `Uni` becoming two labels. That is the "not proper"
part.

**A label becomes a first-class object** — an id, a name, a colour — defined once
and **picked from a set** rather than typed. That alone ends the drift and gives
the dashboard something it can group by without normalising strings.

- a label picker on the to-do detail: existing labels first, "create new" last
- labels shown as coloured chips on the list row, not as prose
- filter the list by label
- renaming or recolouring a label changes it everywhere, because it is one object

This needs a gateway contract. Put the shape on the tracker before building the
app half, the way §4 asks — `labels(id, name, colour)` plus a join to to-dos, and
the existing free-text tags migrate into it as one label each.

### 9.4 The Jira-style features, chosen

Joy: *"Add some solid jira-trello features, pick for me."* These four, in this
order. They are chosen to be **solid on a phone**, which rules out most of what
makes Jira feel like Jira on a desktop.

1. **Priority as a real scale**, not the boolean star. Four levels — highest,
   high, normal, low — shown as a coloured glyph on the row. The star is a
   two-state field pretending to be a priority, and a backlog of forty to-dos
   cannot be ordered by a boolean.
2. **Sub-tasks**: real child to-dos with their own status and deadline, distinct
   from the description checklist. A checklist item cannot be scheduled or
   assigned a label; a sub-task is the thing you actually want when a piece of
   work has parts. The parent shows `3 of 5`.
3. **An activity trail on each to-do** — what changed and when. This is nearly
   free: `tasks.occurrence_events` already exists and the same pattern applies,
   and it is what makes "why is this still open" answerable three weeks later.
4. **Status grouping on the list**, with counts per status. It reads like a board
   without being one.

**Still no dragging and no board columns.** Joy set that in v2 plan §5 and nothing
since has changed it: drag-to-reorder on a phone is a fight with the scroll
gesture, and a board needs horizontal space this screen does not have. Grouping
by status gets most of the legibility for none of the cost.

**Deliberately not built:** assignees (one user), sprints and epics (no team, no
cadence), time tracking (that is what Routine is for), and custom fields.

### 9.5 The to-do → reminder link is deleted

Joy: *"remove the reminder pointing thing, it is doing work twice."*

Remove the link shipped in `395df72`, where a to-do could point at Tasks chasing
it. Once §9.1 lands, a to-do's own deadline answers "when is this due", and two
mechanisms answering it means keeping them consistent forever.

This reverses **v2 plan §5.1**, which argued a to-do *has* tasks so only one
domain owns nagging. That argument was about not building a second nagging
engine, and deleting the link does not build one — a to-do with a `due_at` simply
is not chased. **Update §5.1** rather than leaving the plan contradicting the
code.

gw03 has been told (tracker #131). Once the app ships without it, ask them to
**retire `todos.todo_tasks` rather than leave it serving nothing** — dead state
that still answers is how this project has been misled twice.

### 9.6 Routine notifications

Joy: *"I am not getting any notifications for routines, we need those as well."*

There is no routine notification path at all, so the day view only works if you
are already looking at it — which defeats tracking adherence.

Reuse the machinery rather than rebuilding it: `AlarmScheduler` arms exact
alarms, `Notifier` posts them, `BootReceiver` re-arms after a reboot or an app
update. What is new is *what* to arm — the tracked slots of the current routine
day — and it is the same shape as the occurrence mirror.

Three things this codebase has already paid for once:

- **`BUILD_NOTES` §14.** `PendingIntent` identity goes in the data URI, never in
  extras and never in arithmetic on a request code. A slot alarm needs a key that
  cannot collide with an occurrence alarm.
- **A routine day is not a calendar day** (v2 plan §4.2). Arm against the logical
  day; all seven of Joy's days cross midnight.
- **Tracked slots only** — about 3.7 a day. Scaffold, buffer and free must never
  notify, or this becomes eighty interruptions a week and gets switched off,
  which is worse than not having it.

The notification's action is **start** — the same one tap the day view uses, so
the routine can be driven from the lock screen exactly as reminders are.

### 9.7 Then keep going

Test the app on the phone. Every bug that has mattered in this project was found
by using it, not by a test — an empty turn rendering as nothing, a silent
push-back, a lost conversation, and §9.1 above, which no test would ever have
caught because the model was right the whole time. Then carry on with
`jarvis-v2-plan.md` §8.
