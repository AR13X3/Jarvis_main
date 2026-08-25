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

> **Status as of 2026-08-20 evening — six of eight resolved and deployed.**
>
> | ask | state |
> |---|---|
> | §3.9 `POST /sessions {task_id}` get-or-create | ✅ **done** — verified on device, the core loop works |
> | §3.10 `find_tasks` date ranges | ✅ done — server resolves the range, model never does date arithmetic |
> | §3.8 `task_options` cursor route | ✅ done — `GET /sessions/{id}/options?cursor=` |
> | §3.7 `GET /tasks/{id}` | ✅ done — returns a bare `Task`, not an envelope |
> | §3.6 mutation return types | ✅ done — `TaskEnvelope`; `confirm` now 404s rather than returning a null task |
> | §3.11 `new_task` component | ✅ done — in the discriminator |
> | §3.12 `GET /sessions?kind=general` | ⏳ outstanding — the chat-history list |
> | §3.5 multi-value `status` | ⏳ outstanding — single-select chips stay correct meanwhile |
>
> The app now uses all six. The two workarounds they replaced are gone: paging
> `GET /tasks` to find one row, and failing "Show more" with an honest error.


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

**Fix for task sessions — make `POST /sessions` idempotent:**

```
POST /sessions {kind:"task", task_id:12}   -> 200 + the existing session, not 409
```

`agent.sessions.task_id` is already unique, so this is upsert-and-return rather
than insert-or-conflict. A `GET /tasks/{id}/session` lookup would serve equally
well.

> **Correction to the version of this sent on 2026-08-20.** That draft also asked
> for `POST /sessions {kind:"general"}` to return *the one* general session.
> **Disregard that half** — see §3.12. Collapsing general sessions to a singleton
> would foreclose browsable chat history, which is now a requirement. The
> gateway's current behaviour for general sessions is **correct**; what is
> missing is a way to list and reopen them.

### 3.12 General chat history — **app side built, route still missing**

The schemas are already in your spec — `PagedSessions` and `SessionSummary`,
with `id`, `kind`, `task_id`, `title`, `updated_at`, `message_count`. Exactly
what was asked for. **The route was never wired**, so `GET /sessions` 404s.

The whole app half is now built against that shape and tested on the fake:

- The Chat tab opens **empty** — a fresh conversation is the default entry, and
  the history button is how you go back. It reuses an existing *empty* session
  rather than creating one per visit, so glancing at the tab does not litter the
  server with rows nobody wrote in.
- A history sheet lists past conversations by title, relative time and message
  count, and opening one restores it — including its option buttons, because
  persisted assistant turns carry `components`.
- A `+` starts a new conversation.

```
GET /sessions?kind=general&page=1   ->  PagedSessions
```

One thing the app does that the endpoint should probably do too: **empty
conversations are hidden.** Every `+` and every abandoned launch leaves a session
with no messages, and a history list full of blanks is worse than a short one.
The app filters on `message_count == 0`; filtering server-side would save sending
them at all.

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

### 3.13 `TaskOption` needs `status`

Found in use: asking *"what's due today"* lists everything due today, with no way
to tell what is already done from what is left.

```jsonc
{ "task_id": 12, "title": "Go to the gym",
  "due_at": "2026-08-21T13:30:00Z",
  "status": "active" }          // <- please add
```

The app renders it in the same visual language as a task row — muted and ticked
for done, struck through for cancelled, an outlined ring for missed — so a status
learned in the list reads identically in a chat result. **Already built and
tested; the field is nullable, so nothing breaks until you send it.**

Absent is treated as unknown rather than defaulting to `active`: guessing
"outstanding" for a task that might be finished is exactly the error being fixed.

Worth considering on your side too: the model's prose should distinguish them
even when it does not render buttons. `overdue` already means *past and still
open* — "due today" arguably wants the same care, either by saying which are
done or by separating the two in the answer.

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

## 8. Phase F — release channel

Signing landed earlier (§10 of the plan). This section covers the rest.

### 8.1 About screen

`feature/about/AboutScreen.kt`. Everything on it comes from `BuildConfig`, so it
renders with the gateway down — which is the state it is most often opened in.
Reachable from the Tasks header's right shoulder, a ghost circle so it never
competes with the brand-filled `+` (§6.7).

It is also reachable from the **failure** screen, via a quiet "About this build"
under "Try again". The header is not on screen in that state, and "which build
is that?" is asked precisely when something is broken.

### 8.2 `<queries>` — a latent bug the update work surfaced

The manifest had no `<queries>` element. From Android 11 a package the app has
not declared is invisible to `getLaunchIntentForPackage`, **which returns null
rather than failing**, so §8.2's "Open Tailscale" button had never once appeared
on the device — the code was correct and the button silently did not exist.

Declared now: `com.tailscale.ipn`, both Obtainium package ids, and an
`ACTION_VIEW`/`https` intent for the browser fallback.

### 8.3 Update check (§10.2)

`core/update/`. Two sources, two cadences, and the split is deliberate:

| source | cadence | why |
|---|---|---|
| `GET /health` | every foreground | It decides whether to **block**, which is too consequential to answer from something stale. One cheap unauthenticated call. |
| `api.github.com` | at most once a day | A release does not appear more often than that. Rate limits are a non-issue — 0.07% of the anonymous allowance. |

Three things worth not undoing:

- **The GitHub client is a separate `OkHttpClient` with no `AuthInterceptor`.**
  Reusing the app's client would attach the gateway bearer token to every update
  check, handing github.com a tailnet credential — in the one request that by
  design succeeds when everything else fails.
- **`min_supported_app` is never persisted.** It is held in memory from a live
  read. A wall raised from a cached number while the gateway is unreachable
  would say "update to continue" when the real fix is turning Tailscale on,
  sending the user to fix the one thing that is not broken.
- **Dismissal is per version**, not a boolean. A global "don't tell me" would
  silence the next release, which is the one that might matter.

`SemVerTest` covers the trap §10.2 names: as strings, `"1.10.0" < "1.9.0"`, so a
string comparison would quietly stop offering updates after the ninth minor and
give no sign it had. The `-debug` suffix is stripped too — if it were not, the
check would be dead in exactly the build used to develop it, and would look like
it worked.

### 8.4 APK naming

`assembleRelease` is finalised by a `renameReleaseApk` copy task producing
`app/build/outputs/release/jarvis-v<versionName>.apk` — one asset per release,
named consistently (§10), so `gh release create` needs no manual rename.

Note for anyone editing it: the `rename {}` lambda must capture a **local**, not
`appVersionName` directly. A lambda reading a script-level property captures the
build script object, which the configuration cache cannot serialise, and the
build fails *after* producing a correct APK.

### 8.5 Repo split, as built

The plan's §10 names one repo, `AR13X3/jarvis-releases`. The names differ in
practice; the split does not:

| role | repo | visibility |
|---|---|---|
| source | `AR13X3/Jarvis_main` | **private** |
| releases — APK assets only, no code | `AR13X3/Jarvis_2.0` | **public** |

The visibility is the load-bearing part, not the names. A public releases repo
is what lets Obtainium work with **no GitHub token** — and its token setting is
global across every source, so pointing it at a private repo would put a
standing credential for every private repo on the account onto the phone.

`GithubReleasesApi.RELEASES_REPO` follows this. Changing it means shipping a
build, so it is worth getting right once rather than renaming later: an older
APK in the field keeps checking whatever repo it was compiled against.

### 8.6 Phase F, as it actually stands

Four releases are published: `v0.1.1`, `v0.1.2`, `v0.1.3`, `v0.1.5`. `0.1.4` was
built and deliberately **skipped** — `0.1.5` supersedes it, and spending an
install on a metadata-only build buys nothing.

| done-when | state |
|---|---|
| an update installs **in place** over a prior build | **verified** on device, `0.1.1` over `0.1.0` through Obtainium, no uninstall prompt |
| the older build shows the **banner** before you install it | **not yet seen** |

The second half has been testable since `0.1.5` shipped alongside `0.1.3` in the
field, and "Check now" on the About screen removes the 24h wait. It is the last
unproven step of the phase, and it is about ten seconds of work on a device.

### 8.7 Fixed — the pairing screen's URL field now does something

`TokenStore.gatewayUrl` was written by `save()` and **read by nothing**.
Retrofit's base URL was fixed at build time from `BuildConfig`, so a URL typed
at pairing was accepted, stored, and silently ignored while the app carried on
talking to the compiled-in host. Harmless with one gateway; it became real the
day it moved, and would have presented as "I changed the URL and nothing
happened".

`GatewayUrlInterceptor` now rewrites scheme, host, port and path prefix on the
way out, reading a `@Volatile` off `TokenStore` — the same shape `AuthInterceptor`
uses, and for the same reason: an interceptor is not a coroutine and must not
block on DataStore inside OkHttp's chain. `currentUrl` is set *before* `save()`
returns, closing the same race that made pairing fail in release (§8 of the
0.1.1 notes).

**The subtlety, which the tests caught and the first version got wrong:** the
rewrite is a *replacement*, not an addition. `tailscale serve` mounts the
gateway at `/api`, so Retrofit is constructed with that prefix and every
outgoing path already contains it. Prepending the target's prefix without
removing the old one produced `/api/api/tasks/sections`. `rebased(from, to)`
therefore takes both bases.

About shows the paired URL rather than the constant, via its own small
`AboutViewModel`. Showing the compiled-in value on the screen whose entire job
is "what is this build doing" would be a confident lie the moment the two
differ.

### 8.8 Commit the version bump *before* building

`0.1.1`, `0.1.2` and `0.1.3` each report the commit **before** their own bump,
because the bump was still uncommitted when `assembleRelease` ran. Check out the
SHA that `0.1.3` shows and `appVersionName` reads `0.1.2`.

Nothing broke, but it defeats §10.1's one stated purpose — identifying a release
from the app, the gateway logs and the repo without guessing — and a bare SHA
gives no hint that it is lying.

Two changes:

1. **Order.** Bump, commit, *then* `assembleRelease`, then tag. The checklist in
   §10.4 of the plan implies this; it did not survive contact.
2. **A `-dirty` suffix on `GIT_SHA`** when the tree has uncommitted changes, so
   the About screen says `abc1234-dirty` rather than presenting a SHA that does
   not contain the build. Cheap, and it makes the mistake self-announcing
   instead of needing to be remembered.

Releases before `0.1.4` predate both and carry the off-by-one.

### 8.9 The GitHub release body is an app surface

Easy to forget, because it is authored on a web page and reviewed on a web page.

`UpdateCard` renders `update.notes` through `toInlineMarkdown`, which
understands exactly four inline delimiters — `` ` ``, `**`, `~~`, `*`, `_` — and
nothing block-level, inside `maxLines = 6` with an ellipsis. So notes written
the way release notes are normally written arrive on the phone as literal
characters: `- ` in front of every bullet, `##` in front of a heading, a row of
dashes where a rule was. And the convention of putting a horizontal rule between
a summary and the detail places it in the *worst* spot, mid-banner.

Two halves, because a rule nobody remembers is not a fix:

1. **Write the first block short, inline-only, summary first.** The app
   truncates at six wrapped lines exactly where a web reader keeps going, so
   summary-then-rule-then-detail suits both surfaces at once.
2. **`ReleaseNotes.asBannerNotes()` strips block markers before rendering** —
   bullets, headings, blockquotes and rules. Conservative by design: it removes
   markers, never content, and leaves the inline emphasis `toInlineMarkdown`
   does understand untouched. A note written for the web still reads as prose on
   the phone.

The bullet pattern requires whitespace after the marker (`^[-*+]\s+`) on
purpose: `*emphasis*` also starts with an asterisk and has to survive.

Tables and code fences are handled too. A table's delimiter row is the one line
in markdown that is pure punctuation with no content beneath it, so it is
dropped; content rows become `Version · Change`, keeping every word. The
discriminator is a **leading** pipe — a sentence that merely contains one is
prose. A version-comparison table is the realistic case, because it reads well
on the web page and so tends to land in the first block, where the six-line cap
cannot hide it.

Found by the session that cut `0.1.6`, after writing `0.1.5`'s notes for a web
page — two headed sections, four bullets, a rule, a trailer — which would have
been six lines of ellipsised prose with stray dashes on the phone.

## 9. Voice — dictation in, replies out

Not in the plan at all: §11 has no voice phase. Added after Phase F, as its own
phase, on one condition that shaped every decision below.

**Both halves are entirely app-side. The gateway is untouched** — no endpoint,
no contract change, nothing to coordinate. `TextToSpeech` and `SpeechRecognizer`
are on-device Android APIs, and `AgentResponse.text` was already there to read.

### 9.1 The guardrail that shaped it

Voice makes "just say yes to confirm" very tempting, and it is exactly what
parent plan §2.2 deleted auto-confirm to prevent: committing because the user
did not look is wrong, and committing because the phone *misheard* is the same
failure with a worse cause.

So **dictation ends at the composer**. A transcript is a draft; the user reads
it and presses send. Confirming a proposal stays a tap, always.
`VoiceGuardrailTest` asserts a spoken "yes" while a card is live writes nothing,
because the erosion of this would arrive as a small reasonable-looking change.

### 9.2 Speaking a confirmation, not just the prose

Responses are structured, never bare text (§4.5). Speaking only `text` would
read "Just to confirm —" and stop, leaving the part that matters on screen.
`SpokenReply.toUtterance()` therefore speaks the proposal too, and **always the
recurrence**: the 1-in-5 malformed day set §4.5 warns about is only ever caught
by a human checking before confirming, and a listener has nothing else. It also
says "tap confirm to go ahead", so nobody is left thinking it is already done.

Options are deliberately *not* read out. Three titles with dates is a list
nobody can hold in their head, and tapping one is the real next step.

### 9.3 Choices worth keeping

- **`SpeechRecognizer`, not `ACTION_RECOGNIZE_SPEECH`.** The intent version puts
  Google's dialog over a screen §6 spends its whole length making deliberate.
  The API version also gives partial results, which is the difference between
  dictation that looks alive and a button that appears dead for four seconds.
- **On-device recognition preferred.** `minSdk = 31` already clears
  `createOnDeviceSpeechRecognizer`; nothing spoken leaves the phone, and it
  works with Tailscale off. Falls back when no model is installed.
- **Spoken replies default to off**, and go silent when the ringer is not on
  normal. An app that starts talking on a bus has made a decision that was not
  its to make.
- **`VoiceController` is `@ViewModelScoped`, not a singleton.** The recogniser
  holds one live callback; shared between the Chat tab and an open session,
  whichever tapped the mic last owns it while both render as listening — so a
  transcript could land in the conversation you are not looking at.
- **Barge-in everywhere.** Typing, dictating, sending or leaving the screen all
  stop speech. The mic also stops the speaker first: the phone talking into its
  own microphone is both comic and a real recognition failure.

### 9.4 Manifest

`RECORD_AUDIO`, requested on the first mic tap rather than at launch (§5.5), and
a `<queries>` entry for `android.speech.RecognitionService` — without it
`isRecognitionAvailable()` reports false on a device that has one, and the mic
would be hidden on hardware that supports it perfectly well.

### 9.5 "Check now"

Added to the About screen alongside voice. The update check's daily guard is
right for the background and wrong for a person standing in front of the screen
after a release — and without an override, verifying the §10.2 banner means
waiting a day. It forces one check and cannot become a poll, since only a
deliberate tap reaches it.

## 10. Overdue — the section, and the follow-up loop

Two related pieces, deliberately separable: one shipped and needs nothing from
gw03, the other is built and dark until the contract lands.

### 10.1 The Overdue section (shipped)

A task set for 6:40 looked identical to every other row at 6:45. There is now an
**Overdue** section above Priority, with a count, and the row's time turns amber
with a pill.

What lands in it:

| included | why |
|---|---|
| `status = awaiting` | the server's own word for "fired, waiting on you" (parent §2.8) — authoritative |
| `status = active` and its moment in the past | the gap between a deadline passing and the scheduler noticing |

"Its moment" is `due_at` for a one-shot task and `next_fire_at` for a repeating
one. Reading `due_at` for both was the defect in §10.4.

`incomplete` is **excluded**. It has already lapsed, so it is a record rather
than something demanding an answer, and a section mixing the two is one you
learn to ignore.

**On §3.2, because this looks exactly like the rule it does not break.**
Comparing two instants is timezone-independent. Deriving a calendar *day* from a
timestamp is not, which is why the server owns `due_date` and `due_today` and
still does. `OverdueSectionTest` pins the 08:40 UTC / 6:40 pm Sydney boundary
from the safe side.

**Known limit:** it is a view over tasks already loaded, and `all` is paged 20 at
a time, so an overdue task deep in the list is invisible until it is paged in.
Asked for as an `overdue` array on `/tasks/sections` in document 07 §4a.

### 10.2 The follow-up loop (built, dark)

The first thing in the app the agent says without being asked. At the deadline:
*"Have you done this?"* — done, or push it back 15/30/60 minutes. Two extensions,
then an unfinished task becomes `incomplete`.

Three decisions worth not undoing:

- **The count is the gateway's.** A device-held counter resets on reinstall and
  hands out fresh chances, which makes the cap decorative. So the third push is
  refused *by the server* and shown as a failure, rather than pre-empted by a
  disabled button. `OverdueAnswerTest` covers exactly that.
- **`extensions_allowed` is sent, not compiled in.** Same reasoning as §3.1 and
  §3.3 keeping rules server-side: a cap that needs an APK to change is a cap
  nobody tunes.
- **The nudge is a component in the session, not only a notification.** One
  session per task is the architecture (parent §2.2), so scrolling back shows
  the agent asking and what you answered, as a resolved confirm card does. A
  nudge living only in the shade leaves no record you were ever asked.

Everything is occurrence-scoped, which is why occurrences exist: completing
Monday's gym session must not close the weekly rule, and next Monday should
arrive with a fresh allowance.

**Not built:** answering from the notification. It needs `extensions_used` in
the Room mirror, which is a schema migration, and it is pointless before the
gateway sends the field. That is the remaining piece.

### 10.3 A past-due proposal is called out on the confirmation card

From a real failure: "remind me to apply for jobs in 2 hours", sent at 18:40,
came back as 17:40 — an hour *before* the message asking for it. It was
confirmed, because the sentence above the card read "2 hours from now" and only
the parenthesised time was wrong.

The card is the last human checkpoint (§4.5), so it now says when a proposed
reminder has already passed. The value itself is gw03's to fix and document 06
reports it — the arithmetic was right and the *now* it was added to was three
hours stale.

### 10.4 Every recurring task was permanently overdue

Shipped broken in `0.1.3` and live until now. Found by gw03 from the other side
of the contract: document 08 §4.3 records the server declining the same literal
reading of document 07 §4a, and flags that our side probably took it.

We had:

```kotlin
TaskStatus.Active -> dueAt.isBefore(now)
```

A recurring parent keeps its original `due_at` as the **series anchor** — it
never moves — and stays `active` between firings by design (parent §2.5 rule 1).
So the anchor is in the past for the entire life of the rule, and every
repeating task sat in Overdue forever. A daily task created in June would still
be there in December.

The moment that actually has a deadline is `next_fire_at`, which the app already
receives and already sorts the Recurring section by. So:

```kotlin
val Task.overdueMoment: Instant?
    get() = if (isRecurring) nextFireAt else dueAt
```

Three things worth not undoing:

- **`awaiting` stays authoritative and unconditional.** If the server fired it
  and is waiting, it is overdue whatever the anchor or the next firing says.
- **A recurring rule with no `next_fire_at` is not overdue.** There is no moment
  to have missed, and falling back to the anchor is exactly the bug.
- **The ordering reads the same property.** Judged on one moment and sorted by
  another is the same defect wearing a different hat — sorting on the anchor
  would pin every repeating task to the top regardless of when it is next due.
  `sortedBy { it.overdueMoment ?: it.dueAt }`.

Five cases added to `OverdueSectionTest`, including the one that fails against
the old code.

**This is a stopgap.** §4a's `overdue` array is now built server-side (gw03
document 10), and it is derived from occurrences rather than from either
timestamp — the server asks whether a live occurrence has passed, which is the
question we are approximating. When we consume it, this whole derivation goes,
along with the paging limit in §10.1.

### 10.5 The loop runs on timeouts — app-side work it creates

`joy-to-gw03-09` changes the follow-up loop from "the user taps" to "silence
extends": an unanswered nudge auto-extends by an hour after a 15-minute grace,
twice, and the third closes the task `incomplete`. Two consequences land on this
side, neither built yet, both gated on gw03.

**A second notification kind.** `Notifier` has exactly one: `show(occurrence)`,
the reminder itself, on channel `jarvis.reminders` with a full-screen intent.
An auto-extension and a lapse are not reminders — nothing is being asked, the
agent is reporting what it already did. They want their own channel so the two
can be tuned apart, and no full-screen intent: a task quietly closing does not
warrant taking over the screen.

**A poll timer after each nudge.** There is no push (gw03 document 10 §2), so a
server-originated transition is invisible until the phone asks. The alarm fires
the nudge; the grace expires on the server; nothing tells us. So `AlarmReceiver`
must also schedule a poll for `GRACE_MINUTES + small`, and on discovering the
transition raise the notification locally and let `AlarmScheduler.reconcile`
re-arm from the new occurrence list.

The ordering matters and is easy to get backwards: **the notification is raised
from what the poll learned**, not predicted from the timer. Predicting it means
announcing an extension the server may not have made — the user answered on
another device, the grace was reconfigured, the request failed. The timer says
when to look; the server says what happened.

`GRACE_MINUTES` therefore has to arrive from the server alongside
`EXTENSION_MINUTES` (document 09 §5), or the app is guessing when to poll.

### 10.4 Answering the agent from the lock screen

Without this the follow-up loop is **unanswerable at any hour**. The nudge exists
only as a card inside a task session, nothing surfaces it, and the deadline walks
its two auto-extensions and lapses to `incomplete` while the user is sitting
right there. Quiet hours turned out to be the smaller half of that problem —
being awake does not help if nothing asks.

**No new notification kind, and that was the wrong scoping to begin with.** The
reminder and the nudge are the same instant seen from two sides: the server says
"your deadline passed", the app already says "this is due now". So the
notification that already fires gains the answers. Two notifications for one
moment would be the duplicate §7.2 spends its dedup id preventing.

**The chain that removes the need for push.** An unanswered nudge is answered by
the server, which extends and moves the deadline — and the phone has no idea,
because the mirror refreshes only on foreground and daily. So `NudgeCatchUp`
schedules a look for `grace_minutes` plus 90 seconds, `refresh()` replaces the
window and reconciles, and the next alarm arms itself:

    alarm -> notification -> unanswered -> poll -> new deadline -> alarm

That runs entirely on local alarms, offline, reaching the gateway only when there
is something to say. It is a real data point for §7.4: the loop that most
obviously wanted push does not need it.

The catch-up alarm is **inexact** on purpose. Nothing user-visible happens at
that instant — it only re-reads state — so it has no business competing for the
exact-alarm budget with the reminders themselves.

`grace_minutes` comes from the server, like the chips. An app assuming 15 is
wrong the day gw03 tunes it, and wrong *silently*: the poll would run before the
extension existed, learn nothing, and look exactly like the feature not working.

**On §3.4.** A notification action writes without a proposal. That is not the
rule being broken — §3.4 governs *AI-initiated* mutations, and §5.4 draws the
line explicitly at direct manipulation. This is the user answering a question
they were asked. The proposal path is untouched.

**No offline queue** (§3.5). A tap with no gateway fails and says so in a toast,
rather than replacing the reminder with an error. Queueing a "done" is worse here
than the rule's usual case, because the server is running its own timer against
the same occurrence.

The Room bump to version 2 needed no migration: the module already builds with
`fallbackToDestructiveMigration`, and the mirror is derived data that one refresh
rebuilds. What looked like the risky part of this work was the cheapest.

### 10.5 The line that became a countdown

`OverdueCard`'s last line read "if this isn't done, it'll be marked incomplete" —
true, and open-ended, which was right when only a missed deadline could lapse a
task. Under the timeout loop the user has a hard `grace_minutes` from the card
appearing before the server answers for them. Wording that reads like a general
rule materially understates a running clock, so it now states the number.

### 8.10 About shows what changed, not just that something did

The release notes only ever existed in the banner, capped at six wrapped lines
with an ellipsis, and About said nothing about *what* was in an update.

It now lists **every release at or above the installed build**, newest first —
not just the latest. That distinction is the whole feature: a phone on `0.1.3`
when `0.1.7` lands has missed three releases, and `releases/latest` describes
only the last of them, so "what changed" would quietly hide two thirds of the
answer. It reads `GET /releases` instead.

The installed build is included and tagged, so the screen answers "what am I
running" as well as "what would I get" — two different questions people bring to
the same screen.

Notes go through `asBannerNotes()` for the block-marker stripping (§8.9) but
without the six-line cap. The banner has six lines; this has a screen, and
reading them is the reason you came.

Fetched on demand when About opens rather than kept warm: a changelog nobody is
looking at is not worth a background request. It is also the one call in the app
that works **off-tailnet**, so this section answers when the gateway cannot.

The filtering lives in `toReleaseNotes()`, separate from the fetch, so it is
testable without a network or an Android context — drafts, pre-releases,
unparseable tags, ordering, and a build ahead of the channel all have cases. The
assumed fields were also checked against the live payload rather than the
documentation: all five published releases carry `tag_name`, `body`,
`published_at`, `draft` and `prerelease`.
