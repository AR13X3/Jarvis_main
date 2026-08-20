# Jarvis app — build notes

Companion to `jarvis-app-plan.md`. Records what the plan deliberately refused to
pin (§2: "do not pin numbers from this document"), plus anything discovered
while building that the plan could not have known.

---

## 1. Resolved toolchain

Machine: `joy` (Windows 11). JDK comes from the Android Studio bundle —
there is no separate JDK on PATH.

```
JAVA_HOME = C:\Program Files\Android\Android Studio\jbr    (OpenJDK 21)
Android SDK = C:\Users\ahmed\AppData\Local\Android\Sdk
```

| component | version | note |
|---|---|---|
| Gradle | 9.7.1 | wrapper, distribution SHA-256 pinned in `gradle-wrapper.properties` |
| AGP | 9.3.1 | latest stable; 9.4.0 was still rc at the time |
| Kotlin | 2.4.10 | see §2 — **not** applied as a plugin |
| KSP | 2.3.11 | KSP now versions independently of Kotlin |
| Compose BOM | 2026.08.00 | every Compose artifact takes its version from here |
| Hilt | 2.60.1 | works with AGP 9 + KSP2 |
| `compileSdk` | 37 | forced: current AndroidX refuses to compile against 36 |
| `targetSdk` | 36 | behaviour opt-in, moved deliberately and separately |
| `minSdk` | 31 | per plan §2 — `canScheduleExactAlarms()` exists from 31 |

`compileSdk = 37` was not a preference. `androidx.core:core-ktx:1.19.0` and
`androidx.compose.ui:ui-android:1.12.0` both hard-fail below it. AGP downloaded
the platform itself; there are no command-line tools installed on this machine.

Build from the shell with:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```

## 2. AGP 9 has built-in Kotlin — the one real migration surprise

AGP 9.0 applies the Kotlin compiler itself. Applying
`org.jetbrains.kotlin.android` **fails the build outright** with "no longer
required for Kotlin support since AGP 9.0".

Two consequences worth knowing before touching `build.gradle.kts`:

- The `kotlin-android` plugin is absent from `app/build.gradle.kts` on purpose.
  Do not add it back. The other Kotlin plugins — `plugin.compose`,
  `plugin.serialization`, KSP — are still applied normally.
- AGP pins its own KGP version, which is **older** than the catalog's. The
  `buildscript { }` block in the root `build.gradle.kts` raises that pin. Delete
  it and the project silently compiles against AGP's bundled Kotlin instead of
  2.4.10, which is the kind of drift that shows up as an inexplicable compiler
  error six weeks later.

## 3. Contract items for the gw03 side

§13 of the plan already lists the open questions. Phase A added two, both
discovered by writing the types out in full:

### 3.1 `Confirm` needs a `status` (blocking for §5.3)

§4.5 defines the confirm component as `proposal_id` + `summary`. That is enough
for a **live** card and not enough for **history**: §5.3 requires a resolved card
to stay visible in the message stream in its resolved state, and with only those
two fields the app cannot tell a card it already accepted from one still waiting.

`agent.proposals.status` already holds exactly this
(`pending|confirmed|rejected|superseded`), so it costs the gateway nothing.

```jsonc
{ "type": "confirm", "proposal_id": "9f2c", "status": "confirmed", "summary": { … } }
```

The app defaults it to `pending` when absent, so a gateway that has not added it
yet still parses — it just renders every historical card as live.

### 3.2 `GET /sessions/{id}/messages` has no defined message shape

§4.4 names the endpoint and its paging parameters but never says what a message
looks like. The app assumes:

```jsonc
{ "messages": [ { "id": 41, "role": "assistant", "text": "…",
                  "components": [ … ], "created_at": "2026-08-19T22:00:00Z" } ],
  "has_more": true }
```

`components` on a persisted assistant message is the load-bearing part — it is
the same array `AgentResponse` carries, and it is what makes scrolling back show
the cards rather than bare text.

### 3.4 `cancel` needs a scope for recurring tasks

§4.4 defines `POST /tasks/{id}/cancel {confirm:true}`. For a recurring task that
is ambiguous, and destructively so: "skip this Friday" and "stop doing this
every Friday" are different intentions and the endpoint cannot tell them apart.

The parent plan already models the distinction — `tasks.occurrences` exists
precisely so a firing can resolve without closing the rule (§2.5). The API is
the only place it went missing.

```jsonc
POST /tasks/{id}/cancel  { "confirm": true, "scope": "occurrence" | "series" }
```

`occurrence` resolves the current occurrence and advances `next_fire_at`;
`series` sets the task to `cancelled` as today. Defaulting an absent `scope` to
`series` keeps existing behaviour, though the app always sends one.

Resolving it server-side rather than passing an occurrence id is deliberate: the
app has no occurrence id for a row (`/tasks` does not carry one), and giving it
one would mean the client tracking which firing is current — a scheduling
concern it has no business holding.

The app already asks the question. A one-shot task gets a single confirm; a
recurring task gets two labelled choices.

### 3.9 `POST /sessions` cannot reopen a session — **blocks the core loop**

Found by running phase D against the live gateway. This is the most important
item on the list; everything else here is a nuisance by comparison.

`POST /sessions` is create-only, and nothing reads an existing session back. The
two session kinds therefore fail in opposite directions:

| | what happens | consequence |
|---|---|---|
| **Task session** | `409` — *"that task already has a session"* | Tapping a task row **cannot open its session at all**. §5.3's core loop is dead. |
| **General session** | `200`, but a **new** session every time | The Chat tab loses its entire history on every app restart. Verified: two launches, two ids, second one empty. |

Both break acceptance item §12.8 — "kill the app mid-refinement; reopen; session
resumes with history intact."

The app cannot work around either. There is no `GET /sessions`, no
`GET /tasks/{id}/session`, and the 409 body carries no session id, so once a
session exists the app has no way to name it again.

**Simplest fix — make `POST /sessions` idempotent**, which is what
"one session per task" already implies:

```
POST /sessions {kind:"task", task_id:12}   -> 200 + the existing session, not 409
POST /sessions {kind:"general"}            -> 200 + the one general session
```

`agent.sessions.task_id` is already unique, so for task sessions this is an
upsert-and-return rather than an insert-or-conflict. The general session needs a
rule for what "the" general session is — most recent, or a singleton row.

A `GET /sessions?kind=&task_id=` lookup would work equally well; the app is happy
with either.

### 3.10 `find_tasks` needs date ranges — a stated requirement, not a nicety

The general session's tools are `find_tasks` and `get_task`, and `find_tasks`
matches **titles by keyword**. Asked "what's due today?" live, the agent replied
that it has no way to list tasks or filter by due date, and offered a keyword
search instead.

These are confirmed real use cases, in the user's words:

- *"What's due today?"*
- *"What's due this week?"*
- *"What's due in the next 3 days?"*

**The query already exists.** `GET /tasks?date_from=&date_to=&status=` is what
the Tasks tab pages through. Nothing new is needed in the schema or the data —
the same predicate just needs exposing as a tool the model can call in a general
session:

```
find_tasks(query?, date_from?, date_to?, status?)
```

Note this is not something a filter chip can replace. The Tasks tab offers
Today / Next 7 days / Overdue; "the next three days" is not on that list and
cannot be, because a chip row cannot enumerate every window somebody might ask
for. Open-ended ranges are exactly what chat is *for*.

#### One trap worth stating explicitly

**The model must not compute the dates itself.** "Today" is the user's local
calendar day, the server runs UTC, and a model asked to work out "the next three
days" will produce whatever it thinks the date is — reintroducing §3.2's bug
through the side door, in the one place there is no `due_date` field to fall
back on.

Either resolve the relative range server-side inside the tool, or inject the
user's local `today` into the prompt so the model has a true anchor to offset
from. The app deliberately has no say here: it never computes a task's day, and
it should not start doing so for chat either.

### 3.11 `new_task` component — the general-chat handoff

Asked to create a reminder in the Chat tab, the agent correctly declines: a
general session is offered `find_tasks` and `get_task` and no create tool
(parent plan §2.3), because a general session can never become bound and one
that created tasks would accumulate them, breaking the one-session-per-task
invariant §2.2 rests on.

That rule is right. The dead end is not — the user asked for something ordinary
and got a refusal with nowhere to go.

**Proposed component**, so the decline can hand over instead:

```jsonc
{ "type": "new_task",
  "label": "Set this up",
  "seed": "remind me to go to the gym tomorrow at 11pm" }
```

The app renders it as a button. Tapping opens a new **unbound** session — the
only kind that can `propose_create` — and sends `seed` immediately, so the user
never retypes what they already said. The invariant is untouched: the general
session still creates nothing, and the new session binds to the task the moment
the proposal is confirmed.

`label` defaults to "Set this up" if omitted, and `seed` should be a tidied
version of the request rather than the raw turn where that reads better.

**Already built and tested app-side**, including a test that the seeded session
still writes nothing until the card is confirmed — seeding skips the typing, not
the confirmation. Until the gateway emits this, the component simply never
arrives and the forward-compatibility fallback ignores it, so shipping it is
safe on your schedule.

### 3.5 `GET /tasks` — param names and single status

Two differences from plan §4.4, both taken from `gateway-openapi.json` and now
matched by the app:

- The date params are **`date_from` / `date_to`**, not `from` / `to`.
- **`status` takes one value, not a list.** The filter chip row was multi-select
  and is now single-select, because a chip row that offers "Active + Missed"
  while the server can only answer one of them is a filter that silently means
  something other than it shows.

If repeated `status` params are cheap on your side (`status: list[str] | None`),
the chips would go back to multi-select. Not blocking.

### 3.6 Mutation responses are untyped in the schema

`PATCH /tasks/{id}`, `POST /tasks/{id}/cancel` and
`POST /proposals/{id}/confirm` are declared as bare objects with no schema —
FastAPI has no return annotation on them — while §4.4 says they return `{task}`.

The app reads either an envelope or a bare task so a later annotation cannot
break it, but that is a guess wearing a seatbelt. **Annotating the return types
would settle it**, and would also let the app stop carrying the tolerant decoder.

### 3.7 There is no `GET /tasks/{id}`

Opening a task session needs that one task, to know whether it is terminal and
therefore whether the composer appears. With no single-task route the app pages
`GET /tasks` until it finds the id — twenty rows fetched to read one, and worse
for a task deep in the list.

A plain `GET /tasks/{id}` would remove it entirely.

### 3.8 No endpoint redeems a `task_options` cursor — blocks "Show more"

`task_options` carries `more_cursor`, and §5.4 specifies buttons three at a time
with "Show more" paging the rest. **No route accepts that cursor.** There is no
`GET /sessions/{id}/options`, and redeeming it through `POST /messages` would
mean inventing a fake user turn — it would appear in the transcript as something
the user said, and spend a model call to page a list the gateway already has.

The app currently fails that tap with "Showing more matches needs a gateway
update." The disambiguation flow works up to three candidates; beyond that it is
blocked on you. This is the only §5.4 behaviour not working against the live
gateway.

### 3.3 Still open from §13

- `GET /occurrences/upcoming` does not exist in the parent plan's API list. No
  reminders without it.
- `min_supported_app` / `current_app` from the gateway (§10.3).
- Off-tailnet: everything here assumes Tailscale-only.

## 4. Deviations from the plan's package layout

§2 lists `core/{network,data,di,time}`. Two additions:

- `core/model` — the wire types. They are shared by `data` and `network` and
  belong to neither.
- `core/ui` — `LoadState` only. It is UI vocabulary, not data.
- `feature/conversation` — the message list, composer, confirmation card and
  `ConversationViewModel`. The plan lists `feature/tasks/session` and
  `feature/chat` as separate leaves, but §5.3 and §5.4 describe the *same*
  screen with different framing: same stream, same composer, same cards. Two
  copies would drift, and putting the shared half under `feature/tasks` would
  make the Chat tab depend on the Tasks package for its own body. The two
  feature packages survive as thin wrappers that supply a header, an empty
  state and a target.

`core/time` exists and is empty until phase E needs it.

## 5. Fonts

`res/font/outfit_variable.ttf` (Outfit) and `res/font/inter_variable.ttf`
(Inter), both SIL Open Font License — licences in `docs/licenses/`. Variable
fonts, so one file covers every weight; minSdk 31 is well above the API 26 floor
for font variation settings.

Bundled rather than downloadable, per §2: downloadable fonts need Play Services
and can silently fall back, and a brand face that sometimes does not load is
worse than no brand face.

Inter's file is ~877 KB because it ships every script it supports. Not worth
subsetting for a single-user sideloaded app; revisit only if APK size ever
becomes a real complaint.

## 6. Device verification

Target device is attached and phase A is verified on it:

```
SM-S918B (Galaxy S23 Ultra) · Android 16 · API 36 · adb RFCW20Z4AND
```

Install and drive it from the shell:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug
adb shell am start -n com.ar13x.jarvis.debug/com.ar13x.jarvis.MainActivity
adb shell cmd uimode night yes    # or: no
adb exec-out screencap -p > shot.png
```

Note the debug build installs as `com.ar13x.jarvis.debug` — `applicationIdSuffix`
keeps it side-by-side with a release build rather than replacing it.

Three defects were found this way and could not have been found any other way:

1. The `awaiting` indicator drew `rowFill` rather than the accent, so the one
   status meant to draw attention had a near-invisible dot in both themes.
2. Cards vanished into the ground in dark. A drop shadow needs something darker
   to cast onto; on a near-black ground it does nothing. Dark now uses a
   hairline, light keeps the shadow.
3. The noise dither was clipped to the wash, leaving a visible horizontal seam
   where the texture stopped against flat ground.

Still worth an eye on a real panel as the app grows: gradient banding in dark
(the wash now carries more dither than light for exactly this reason), and
whether `BrandTintDark` stays readable as the awaiting wash once real rows sit
in it.

## 7. Judgement calls the plan does not make

Recorded because they are decisions, not details, and the next person reading
§5.2 will wonder why the code does not match it literally.

### 7.1 No Paging 3 for the All-tasks list

§2's stack table specifies Paging 3 with a network-only `PagingSource`. The list
uses plain state-held pagination instead.

The reason is `/tasks/sections`. That endpoint exists so the whole tab's first
paint is **one** round trip, and it returns page 1 of All alongside the other two
sections. A `PagingSource` insists on owning page 1, so combining them means
either fetching page 1 twice on every cold start or bypassing the endpoint built
for this. Neither is worth Paging 3's benefits at 20 rows a page in a list that
also carries two non-paged sections and section headers.

What was actually needed — append on scroll, an append spinner, a retryable
append failure — is about forty lines in the ViewModel and composes with
`animateItem()` without fighting it.

Revisit if the All list ever needs placeholders or a Room-backed cache. It does
not, and per §0 it must not.

### 7.2 Filters collapse the sections

§5.2 describes three sections and a filter chip row without saying how they
interact. Showing both produces a self-contradicting screen: filter to
"Completed" and the Priority section still shows active tasks, because Priority
*is* a filter (`is_priority && status in (active, awaiting)`).

So an active filter hides Priority and Recurring and leaves one result list.
Clearing the filters brings them back.

### 7.3 Priority + recurring — **resolved**

A task that is both appears **once, under Recurring**, carrying its star.

The subtlety is in how Recurring is defined. §5.2's table says
`recurrence is not null and status = 'active'`. Deduplicating against that put a
repeating task that had *fired* — status `awaiting` — into Priority, still
showing "Every Sunday". That is the same duplication the split was meant to
remove, just relocated, and on the device it read as the fix having failed.

**Recurring is a property of the rule, not of the current occurrence.** Only a
terminal status ends it, because then the rule really is over:

```sql
-- recurring: every live repeating task
recurrence is not null and status not in ('completed','cancelled')

-- priority: starred, live, and not a repeating rule
is_priority and status in ('active','awaiting') and recurrence is null
```

`/tasks/sections` computes these server-side, so this belongs on the gateway.
`TaskSectionsTest` covers it, including the `awaiting` case that caused the
confusion.

### 7.4 Row composition

§5.2 lists the row's contents but not their arrangement. Three separate controls
(overflow, cancel, expand) placed independently read as clutter, so they are
grouped: overflow and cancel are one cluster overlaid at the top-right, the
expand chevron is centred on the bottom edge. The actions change the task; the
chevron only changes what you can see of it, and keeping them apart is what stops
a mis-tap on "show details" from landing on "cancel".

The actions are *overlaid* rather than laid out beside the content so only the
title yields width to them — otherwise every metadata line is silently 68dp
narrower than the card, which is what clipped "Every Monday, Wednesday and
Friday" to "…and…".
