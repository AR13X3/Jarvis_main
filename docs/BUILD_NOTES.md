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

§5.2 asked for it to be built duplicated and judged on the device. Decided
2026-08-20: a task that is both appears **once, under Recurring**, carrying its
star so the priority is still legible. Showing it twice was defensible — both
statements are true — but read as a bug.

The de-duplication is against **what Recurring actually shows**, not against the
`recurrence` flag. Recurring is `recurrence != null && status == 'active'`, so
excluding every recurring task from Priority would drop a priority recurring task
in `awaiting` out of *both* sections — and that is exactly the task that wants
attention. `TaskSectionsTest` covers the case, and there is a fixture ("Take the
bins out") that sits in it.

**This is a gateway change too.** `/tasks/sections` computes these queries
server-side, so the same rule belongs there:

```sql
-- priority: starred, live, and not already shown under recurring
is_priority
  and status in ('active','awaiting')
  and not (recurrence is not null and status = 'active')
```

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
