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
> **§9 is your brief.** Joy has asked for four changes before the next release.
> Read it, then confirm the scope of the Jira-style work before building all of
> it — that one is the only open-ended item.
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

Given 2026-09-02, after `v0.1.12`. Four changes, then carry on.

**Confirm scope on the third before building all of it.** The other three are
well-defined; that one is a direction, not a specification.

### 9.1 Five tabs, icons only

Dashboard and To-dos become **their own tabs**. Today both are nested screens —
`TodoList` hangs off the Tasks graph via `onTodos`, and `DashboardRoute` off
`onOpenDashboard`. Joy wants them at the top level.

That makes five: **Tasks, To-dos, Routine, Dashboard, Chat.** Five labelled tabs
will not fit, so the bar goes **icon-only** — Joy's words: "to make sure all of
the tabs fit, use only icons".

`JarvisBottomBar` already iterates `JarvisTab.entries` and picks an icon per tab,
so the enum and the icon `when` are most of it. Dropping the label means the
`contentDescription` becomes the only name a screen reader gets — keep it, and
keep the touch target at its current size rather than shrinking to match the
smaller cell.

### 9.2 A to-do's deadline is the deadline — delete the reminder link

Joy: *"To-dos can have deadline, remove the reminder pointing thing, it is doing
work twice."*

**Read that precisely.** To-dos already carry `starts_at`, `due_at`, `starts_on`
and `due_date` — the deadline exists and works. The thing to remove is the
**to-do → reminder link** shipped in `395df72`, where a to-do could point at
Tasks that chase it. Two mechanisms now answer "when is this due", they have to
be kept consistent, and Joy is right that it is duplicate work.

Do **not** add a deadline field. It is already there.

This reverses **v2 plan §5.1**, which argued a to-do *has* tasks so that only one
domain owns nagging. That argument was about not building a second nagging
engine, and deleting the link does not rebuild one — a to-do with a `due_at`
simply is not chased. Update §5.1 rather than leaving the plan contradicting the
code, and tell gw03: the link has a gateway table (`todos.todo_tasks`) and they
should not keep serving something nothing reads.

### 9.3 Labels, the way Jira and Trello do them

Joy: *"The labelling system is not proper, make it like jira-trello. we also need
more jira-trello functionalities in to-do."*

Today `Todo.tags` is `List<String>` — free text, typed fresh each time, no
colour, no reuse, nothing stopping `uni` and `Uni` being different labels. That
is the "not proper" part.

What Jira and Trello actually do, and what makes them feel different: a label is
a **first-class object** with a name and a colour, defined once on a board and
**picked from a set** rather than typed. That alone fixes the drift and gives the
dashboard something it can group by reliably.

**Beyond labels, this is a direction and not a spec.** Ask Joy which of these
matter before building any of them — the whole list is a large amount of work
and some of it may not be wanted:

- priority, as a field rather than the existing boolean star
- ordering within a status, so a column has a top
- sub-tasks, distinct from the checklist that already exists in the description
- a parent or epic, for grouping several to-dos under one piece of work
- comments or an activity trail on a to-do
- due-soon and overdue treatment on the list

Joy was explicit earlier that there is **no dragging and no board columns**
(v2 plan §5). Check whether that still holds before designing anything that
assumes a board — "more jira-trello functionalities" may have changed it.

### 9.4 Routine notifications

Joy: *"I am not getting any notifications for routines, we need those as well."*

There is no routine notification path at all. Nothing fires when a tracked slot
begins, so the day view only works if you are already looking at it — which
defeats the point of tracking adherence.

The machinery exists and should be reused rather than rebuilt: `AlarmScheduler`
arms exact alarms, `Notifier` posts them, `BootReceiver` re-arms after a reboot
or an app update. What is new is *what* to arm — the tracked slots of the current
routine day — and it is the same shape as the occurrence mirror.

Two things to get right, both of which this codebase has already paid for once:

- **`BUILD_NOTES` §14.** `PendingIntent` identity goes in the data URI, never in
  extras and never in arithmetic on a request code. A routine slot alarm needs
  its own key that cannot collide with an occurrence alarm.
- **A routine day is not a calendar day** (v2 plan §4.2). Arm against the logical
  day, and remember that all seven of Joy's days cross midnight.

Only **tracked** slots — about 3.7 a day. Scaffold, buffer and free must not
notify, or the feature becomes eighty interruptions a week and gets turned off.

### 9.5 Then keep going

Test the app on the phone. Every bug that has mattered in this project was found
by using it, not by a test — an empty turn rendering as nothing, a silent
push-back, a lost conversation. Then carry on with `jarvis-v2-plan.md` §8.
