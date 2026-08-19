# Jarvis App — Android build plan

> Handoff document for building the **Jarvis Android app** on the `joy` machine,
> in parallel with the agent gateway on `gw03`. Self-contained: assumes no prior
> conversation context.
>
> Parent: `restructure-plan.md` Part 2 — read §2.1–§2.11 there for *why* the
> system is shaped this way. This document is the *how* for the app half only.
> The gateway half is not covered here beyond the contract they share.
>
> Companions: `ecosystem-plan.md`, `jarvis/BUILD_NOTES.md`.

---

## 0. The one thing that makes parallel work possible

The app cannot talk to a gateway that does not exist yet. So **§4 (the API
contract) is the seam**, and the app is built against an in-memory fake that
implements it. Phases A–C below produce a fully interactive app — every screen,
every animation, every state — with zero network code running.

That means:

- **§4 is a contract, not a sketch.** Whoever builds the gateway implements
  exactly these shapes. If either side needs a change, it changes *here first*.
- The `TaskRepository` / `AgentRepository` interfaces have two implementations
  from day one: `FakeTaskRepository` (fixtures) and `RemoteTaskRepository`
  (Retrofit). A build flag or a Hilt module swap picks one.
- The fakes are not throwaway. They stay as the backing for UI tests and for
  working on the app while off-tailnet.

**Do not build a local task database as a source of truth.** The gateway owns
all state (§2.1 of the parent plan). The only thing that persists on-device is
the alarm mirror (§7) and a thin display cache. Everything else is fetched.

---

## 1. What the app is

Two-tab bottom nav — **Tasks** and **Chat** — over an agent gateway on `gw03`.

- **Tasks tab.** Three sections over one table: Priority, Recurring, All tasks
  (20/page). Tapping a row opens *that task's* persistent chat session.
- **Chat tab.** General agent chat, plus task lookup with a disambiguation flow.
- Every AI-initiated mutation arrives as a **confirmation card** the user
  accepts or rejects. Nothing is written until confirmed.
- Reminders fire as notifications, from on-device exact alarms.

Target device: **Samsung Galaxy S23 Ultra**, single user, single device.
Distribution is sideload via Obtainium (§10) — not Play Store, which removes a
whole class of policy constraints.

---

## 2. Stack

Chosen for design/animation/UX quality first, with "boring where boring is
better" for the plumbing.

| concern | choice | why this one |
|---|---|---|
| UI | **Jetpack Compose** | Non-negotiable for the animation work below. Views cannot do shared-element task→session transitions cleanly. |
| Design system | **Material 3 skeleton, custom brand scheme.** **No dynamic color.** | The app has a designed identity (§6). Monet would repaint it from the user's wallpaper — incompatible with a brand gradient. M3 is kept for component behaviour, spacing and a11y, not for its palette. |
| Motion | **Compose animation** — spring-first, via named motion tokens (§6.5) | The Framer-Motion feel is spring physics + orchestration + interruptible gestures. Compose does all three natively; the discipline is tokens, not one-off `tween`s. |
| Type | **Bundled font files** in `res/font` | Not downloadable fonts — those need Play Services at runtime and can silently fall back. A brand face that sometimes doesn't load is worse than no brand face. |
| Navigation | **Navigation Compose**, type-safe routes via `@Serializable` objects | Compile-time-checked arguments; no string route parsing. |
| State | **ViewModel + StateFlow**, immutable UI state data classes, unidirectional data flow | One `UiState` per screen, one `sealed interface` of events upward. |
| DI | **Hilt** | Makes the fake↔real repository swap a one-module change. |
| Network | **Retrofit + OkHttp + kotlinx.serialization** | kotlinx.serialization is required — the `components` array is polymorphic (§4.5) and Moshi/Gson handle that far worse. |
| Paging | **Paging 3**, network-only `PagingSource` (no `RemoteMediator`) | The All-tasks list is 20/page and endless. Network-only keeps it simple — no Room sync layer. |
| Local store | **Room** | Alarm mirror only (§7). Not a task cache-of-record. |
| Background | **WorkManager** (daily alarm refresh) + **AlarmManager** (exact firing) | Per §2.8 of the parent plan. |
| Images | **Coil** | For attachments (§9), later phase. |
| Secure storage | **DataStore** + Jetpack Security for the gateway token | One shared bearer token (§4.1). |

**Versions:** do not pin numbers from this document. Use a Gradle **version
catalog** (`gradle/libs.versions.toml`) and the **Compose BOM**, and resolve to
the latest stable that the installed Android Studio supports. Anything written
here would be stale by the time you read it.

**`minSdk = 31` (Android 12).** Deliberately high. `SCHEDULE_EXACT_ALARM` and
`canScheduleExactAlarms()` only exist from 31, so this deletes an entire
compatibility branch from the most delicate code in the app. There is exactly
one target device and it is on 14+; there is no user base to lose.
`targetSdk` / `compileSdk`: latest stable available.

**Module layout: one `:app` module**, with strict package boundaries:

```
com.ar13x.jarvis
  ├── core/           network/  data/  di/  time/
  ├── designsystem/   theme/  component/  motion/
  ├── feature/tasks/  list/  session/
  ├── feature/chat/
  └── reminders/      alarm/  work/  notification/
```

Multi-module is the "correct" answer for a team and the wrong tradeoff for one
person — it buys build-time parallelism you do not need and costs indirection
you will feel every day. If the app outgrows this, the package boundaries above
are already the module seams. (Change `com.ar13x.jarvis` if you prefer another
application id — but change it **before** the first signed release, because it
is the app's permanent identity to Obtainium.)

---

## 3. Non-negotiable guardrails

These exist because the parent plan's whole design collapses if the app
duplicates server logic. State them to anyone (or anything) writing this code.

1. **Never parse or generate an RRULE in the app.** The server sends
   `recurrence_text`, already rendered by `describe_recurrence()`. Display it.
   The app has no recurrence library and needs none.
2. **Never compute "due today" from a timestamp.** The server sends `due_date`
   (a bare local calendar day) and `due_today` (a boolean). Deriving either from
   `due_at` reintroduces the exact timezone bug the parent plan calls its
   highest-risk defect (§2.5 note there): the server is UTC, a 9 PM Sydney
   reminder is the next UTC day.
3. **Never decide which tools the agent may use.** Tool scoping is enforced
   server-side by session state (§2.3 of the parent plan). The app does not send
   a tool list and does not reason about one.
4. **Never write to a task without a proposal** — except the priority toggle
   (§5.4), which is the one deliberate exception and goes through `PATCH`.
5. **No offline mutations.** No local queue of pending writes. If the gateway is
   unreachable, mutations fail with a clear message (§8.2). Reconciling an
   offline write queue against an agent that may have moved the task on is a
   genuinely hard problem and there is no reason to have it.
6. **The app never sees the OpenRouter key.** It talks only to the gateway.

---

## 4. API contract

Base URL: `https://gw03.tail9662e3.ts.net` (Tailscale MagicDNS).

**TLS is real, not self-signed.** Tailscale issues a genuine cert for the
`*.ts.net` name, so standard OkHttp works with no `networkSecurityConfig`
exception, no cert pinning, no trust-manager hacks. If you find yourself
disabling certificate validation, stop — something else is wrong.

### 4.1 Auth

Single shared bearer token, sent on every request:

```
Authorization: Bearer <token>
```

Set once in a first-run screen, stored via DataStore + Jetpack Security,
attached by an OkHttp `Interceptor`. Tailscale-only is the real perimeter; the
token guards against another device on the tailnet. A `401` should surface the
token screen again, not a generic error.

### 4.2 Conventions

- All timestamps are **ISO-8601 instants in UTC** (`2026-08-21T09:00:00Z`) and
  parse to `java.time.Instant`.
- `due_date` / `scheduled_date` are **bare local calendar days** (`2026-08-21`)
  and parse to `java.time.LocalDate`. They are *not* derived from the instants.
- Errors: non-2xx with `{"error": {"code": "...", "message": "..."}}`.
  `message` is safe to show the user.

### 4.3 Task

```jsonc
{
  "id": 12,
  "title": "Call the dentist",
  "description": "",
  "due_at": "2026-08-21T09:00:00Z",
  "due_date": "2026-08-21",          // LOCAL day — display key, never computed client-side
  "is_priority": false,
  "recurrence": "FREQ=WEEKLY;BYDAY=TU",  // null => one-shot; app never parses this
  "recurrence_text": "Every Tuesday",    // server-rendered; this is what you display
  "next_fire_at": "2026-08-25T09:00:00Z",
  "status": "active",                // active|awaiting|completed|cancelled|incomplete
  "due_today": false,                // server-computed against the user's local day
  "created_at": "...", "updated_at": "...",
  "completed_at": null, "cancelled_at": null
}
```

`status` maps to a sealed enum. **`incomplete` is not terminal** — it keeps its
full mutation set and is the one you most want to reschedule. Only `completed`
and `cancelled` lock a task. Getting this wrong makes lapsed tasks
unreschedulable, which is the opposite of what they are for.

### 4.4 Endpoints

```
GET   /tasks/sections                       -> SectionsResponse
GET   /tasks?status=&from=&to=&page=        -> PagedTasks        (20/page)
PATCH /tasks/{id}          {is_priority}    -> {task}
POST  /tasks/{id}/cancel   {confirm:true}   -> {task}

POST  /sessions            {kind, task_id?} -> Session
GET   /sessions/{id}/messages ?before=&limit=  -> PagedMessages
POST  /sessions/{id}/messages {text}        -> AgentResponse
POST  /sessions/{id}/attachments  multipart -> {attachment_id}    (phase G)

POST  /proposals/{id}/confirm               -> {task}
POST  /proposals/{id}/reject                -> {ok:true}

GET   /occurrences/upcoming?within_hours=48 -> UpcomingOccurrences
```

```jsonc
// SectionsResponse — one round trip for the whole Tasks tab first paint
{ "priority":  [Task],
  "recurring": [Task],
  "all": { "tasks": [Task], "page": 1, "has_more": true } }
```

> **`GET /occurrences/upcoming` is an addition.** §2.6 of the parent plan does
> not list it, but §2.8 requires the device to mirror a ~48h window of
> occurrences to set alarms, and there is no endpoint that serves them. Flag
> this to whoever builds the gateway — the app cannot do reminders without it.

```jsonc
// UpcomingOccurrences
{ "occurrences": [
    { "occurrence_id": 88, "task_id": 12, "title": "Call the dentist",
      "scheduled_for": "2026-08-21T09:00:00Z", "is_priority": false } ],
  "window_hours": 48,
  "generated_at": "2026-08-19T22:00:00Z" }
```

### 4.5 AgentResponse — the polymorphic bit

Responses are **structured, never bare text** — the app renders cards and
buttons, not markdown.

```jsonc
{
  "text": "Just to confirm — 'Call the dentist', Thursday 21 Aug, 9:00 AM.",
  "components": [
    { "type": "confirm",
      "proposal_id": "9f2c…",
      "summary": { "action": "create", "title": "Call the dentist",
                   "due_at": "2026-08-21T09:00:00Z", "due_date": "2026-08-21",
                   "is_priority": false, "recurrence_text": null,
                   "description": "" } },
    { "type": "task_options",
      "options": [ { "task_id": 12, "title": "…", "due_at": "…" } ],
      "more_cursor": "eyJvZmZzZXQiOjN9" }
  ]
}
```

In Kotlin:

```kotlin
@Serializable
data class AgentResponse(
    val text: String,
    val components: List<AgentComponent> = emptyList(),
)

@Serializable
sealed interface AgentComponent {
    @Serializable @SerialName("confirm")
    data class Confirm(
        @SerialName("proposal_id") val proposalId: String,
        val summary: ProposalSummary,
    ) : AgentComponent

    @Serializable @SerialName("task_options")
    data class TaskOptions(
        val options: List<TaskOption>,
        @SerialName("more_cursor") val moreCursor: String? = null,
    ) : AgentComponent
}
```

Configure the Json instance with `classDiscriminator = "type"` and
**`ignoreUnknownKeys = true`**. The second one matters more than it looks: the
gateway will grow component types (attachments, task cards), and an app that
throws on an unknown `type` cannot be forward-compatible. Add a fallback branch
that renders unknown components as their `text` and nothing else, rather than
crashing the message list.

**`recurrence_text` in the confirmation summary is load-bearing.** The parent
plan (§2.5) notes the model emits malformed tool arguments roughly 1 in 5 on the
"last Friday of every month" pattern. A wrong day set is only catchable by a
human reading it *before* confirming — so render it prominently, never collapse
it behind a "details" affordance.

---

## 5. Screens

### 5.1 Scaffold

`NavigationBar` with two destinations. Tasks is the start destination. Each tab
keeps its own back stack; switching tabs preserves scroll position and any open
session.

### 5.2 Tasks list

Three sections in one scrolling `LazyColumn`, in order:

| section | contents | empty state |
|---|---|---|
| **Priority** | `is_priority && status in (active, awaiting)`, by `due_at` | hidden entirely |
| **Recurring** | `recurrence != null && status == active`, by `next_fire_at` | hidden entirely |
| **All tasks** | everything, newest first, paged 20 | "No tasks yet — tap + to start" |

Sections with no rows are **hidden, not shown empty** — three empty headers on
first launch is noise. The All-tasks section always renders, since it carries
the primary empty state.

> **Open question inherited from the parent plan (§3):** a task that is both
> priority *and* recurring appears in both sections. Showing it twice is
> arguably correct — both statements are true — but reads as a bug. **Build it
> duplicated first**, look at it on the device, and decide from there. Do not
> spend design time on it before it is on a screen.

**Row anatomy:** status indicator · title · due date · due-today dot ·
recurrence text (if any) · overflow menu · cancel button.

- **Due-today indicator** comes from `due_today` on the task. Never computed.
- **Cancel asks for confirmation** — a mis-tap is as destructive as a wrong
  model call. Use an `AlertDialog` naming the task. On confirm, the row *stays*
  with `status = cancelled`; rows are never deleted.
- **Overflow (⋮)** → *Set as priority* / *Remove priority*. Applies immediately,
  no dialog, with an **undo snackbar**. This is the parent plan's rule made
  concrete: AI-initiated mutations confirm, direct manipulation of a cheap
  reversible flag does not. Undo is what makes "no confirmation" defensible.
- **Filters** by status and date range, as a filter chip row that collapses on
  scroll.
- **`+` (top-left per the parent plan)** creates a new unbound session and
  navigates straight into it.

### 5.3 Task session

Tapping a row opens that task's persistent session, with full history.

- Message list: user right, agent left, `reverseLayout = true`, paged backwards
  via `before=`.
- **Confirmation card** rendered inline in the stream, not as a dialog. Accept /
  Reject. Once resolved it stays visible in history in its resolved state —
  scrolling back should show what you agreed to, not a blank.
- **Optimistic send:** the user's message appears immediately; a pending agent
  bubble shows a thinking indicator until the response lands. Median latency is
  ~4s, so this state is seen constantly and deserves real design attention — it
  is not a spinner.
- **Terminal tasks are read-only.** For `completed` / `cancelled`, the composer
  is replaced by an explanatory strip ("This task is completed — you can still
  ask about it"). Do not just disable the field with no reason given. Questions
  still work; the server simply offers no mutation tools.

### 5.4 Chat tab

General session, same message UI. Adds the lookup flow:

1. Ask about a task in natural language.
2. If ambiguous, the agent asks for a date.
3. `task_options` renders as **buttons, 3 at a time**; `more_cursor` drives a
   "Show more" that appends the next 3.
4. Tapping a button navigates into that task's session — cross-tab, into the
   Tasks stack.

### 5.5 First run

Gateway URL (pre-filled) + token, then a connectivity probe with a real
diagnosis on failure (§8.2). Then the notification and exact-alarm permission
requests (§7.3) — asked *in context*, not as a wall of dialogs on first launch.

---

## 6. Design direction

**This app is designed, not assembled from defaults.** The reference direction is
brand-led: a saturated hero gradient bleeding into near-white, an oversized
friendly headline, generous rounded surfaces floating on a soft ground, and one
confident accent doing all the work.

Everything in this section is a constraint on implementation, not a suggestion.

### 6.1 Revised from the first draft — no dynamic color

An earlier version of this plan recommended Material 3 **dynamic color (Monet)**.
That is **dropped**. Dynamic color derives the scheme from the user's wallpaper,
which is precisely the opposite of a committed identity — you cannot own a brand
gradient and simultaneously let the OS repaint it every time the wallpaper
changes. Fixed scheme, both themes hand-tuned.

Material 3 stays for component *behaviour*, touch targets, spacing rhythm and
accessibility. Its default palette does not.

### 6.2 Palette

One saturated brand hue, a near-neutral ground with a slight warm bias toward
that hue, near-black ink. The hue below is a starting point — swap it in one
place if you want a different identity, but keep the *structure*.

```kotlin
// designsystem/theme/Palette.kt — light
val BrandCore    = Color(0xFFD8203C)   // primary actions, active states
val BrandDeep    = Color(0xFF9E0F26)   // gradient far stop, pressed
val BrandTint    = Color(0xFFFDE7EA)   // selected chip fill, subtle wash
val Ground       = Color(0xFFFAF7F8)   // page background — warm-biased, not pure white
val Surface      = Color(0xFFFFFFFF)   // cards, composer, sheets
val SurfaceSunk  = Color(0xFFF1EDEF)   // input wells, inactive chips
val Ink          = Color(0xFF15100F)   // headlines
val InkMuted     = Color(0xFF6B6164)   // secondary copy
val Hairline     = Color(0xFFE7E0E2)
```

Dark theme is **not an inversion.** The gradient goes deeper and less saturated
(a bright crimson wash on a dark ground vibrates); the ground is a warm-biased
near-black (`0xFF141011`), surfaces lift to `0xFF1E1819`, and `BrandCore`
brightens to roughly `0xFFF04156` to hold contrast against it. Tune both by eye
on the actual device, not in a preview.

> **The brand hue cannot also mean "bad".** This is the one real trap in adopting
> a red identity for an app with a `cancelled` status. If red is both the primary
> button and the failure signal, every CTA reads as a warning and every error
> reads as a CTA. **Status colours therefore live on a separate scale that
> contains no red:**
>
> | status | treatment | why |
> |---|---|---|
> | `active` | neutral ink, no fill | the default; needs no decoration |
> | `awaiting` | brand tint fill | it wants you — the one status allowed to borrow the brand |
> | `completed` | green check, muted row | resolved and quiet |
> | `cancelled` | grey, title struck through | a *decision*, calm, not an error |
> | `incomplete` | amber, outlined | a *lapse* — needs attention, is not a failure |
>
> `cancelled` and `incomplete` must stay distinguishable at a glance in both
> themes — the parent plan (§2.5) keeps both statuses precisely because a
> decision and a lapse are different things, and that distinction is only worth
> anything if you can see it. **Encode it in form as well as colour** (strike vs
> outline), so it survives colour-blindness and a dark theme.

### 6.3 The gradient — and the one bug it will give you

The hero gradient is the signature. It appears behind the Chat header, the
onboarding screen, and the Tasks tab header in a shorter form.

```kotlin
Brush.verticalGradient(
    0f    to BrandCore,
    0.55f to BrandCore.copy(alpha = 0.35f),
    1f    to Ground,
)
```

**Large Android gradients band visibly** — you will get stepped stripes across
that wash, especially on an OLED panel in dark mode. Two fixes, apply both:

1. Draw into a layer with `Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)`.
2. Overlay very low-alpha noise (a small tiling bitmap at ~2–3% alpha) via
   `drawWithCache`. Noise dither is what removes banding; blur does not.

Do this once in a `BrandGradient` composable and never hand-roll it again.

### 6.4 Form

- **Corner radius scale:** `xs 8 · sm 12 · md 20 · lg 28 · pill 999`. The
  reference's generosity comes from `lg` on cards and `pill` on chips/CTAs — do
  not compromise these down to 4dp Material defaults.
- **Spacing scale:** 4 · 8 · 12 · 16 · 24 · 32 · 48. Screen gutter 20dp.
- **Elevation:** Compose's default shadows are hard and grey. For cards use a
  soft double shadow drawn manually — one tight low-alpha, one wide very-low —
  or `Modifier.shadow` with a large radius and a *tinted* `ambientColor` /
  `spotColor` pulled toward the brand hue. Untinted grey shadows are the single
  fastest way to make this look generic.
- **Headline:** oversized, tight leading (~1.05), `text-wrap: balance` equivalent
  via manual line breaking where it matters. This is the reference's loudest
  move and it costs nothing.
- **Composer:** floats above the ground on `Surface` with `lg` radius and a soft
  shadow — never a full-width bar pinned edge-to-edge with a top divider.

### 6.5 Motion tokens — the Framer-Motion equivalent

Framer Motion has no Android port. What transfers is the *approach*: spring
physics by default, layout animations, orchestrated stagger, and gestures that
stay interruptible. Compose does all of it. The discipline is defining tokens
once instead of scattering `tween(300)` everywhere.

```kotlin
// designsystem/motion/Motion.kt
object Motion {
    val Snappy  = spring<Float>(dampingRatio = 0.80f, stiffness = 620f)  // taps, chips
    val Standard= spring<Float>(dampingRatio = 0.85f, stiffness = 380f)  // most transitions
    val Gentle  = spring<Float>(dampingRatio = 0.90f, stiffness = 190f)  // sheets, large surfaces
    val Expressive = spring<Float>(dampingRatio = 0.62f, stiffness = 300f) // arrival with a little overshoot
    // IntOffset / Size variants needed too — spring<T> is per-type.
}
```

| Framer Motion | Compose |
|---|---|
| `transition={{type:"spring"}}` | `spring(dampingRatio, stiffness)` |
| `layout` prop | `Modifier.animateBounds`, `LookaheadScope` |
| `layoutId` shared element | `SharedTransitionLayout` + `sharedElement()` |
| `AnimatePresence` | `AnimatedVisibility` / `AnimatedContent` |
| `staggerChildren` | per-index `delayMillis`, or sequential `Animatable` |
| drag + inertia | `draggable` + `Animatable.animateDecay/animateTo` with velocity |

**Spend the budget in four places. Keep everything else quiet.**

1. **Task row → session, shared element.** The signature move, and the one the
   architecture is built around (one session per task). The title morphs from row
   to session header. Build this in phase C, not as polish later.
2. **Message arrival.** Agent bubbles enter with `Expressive` — slight overshoot,
   fade + 8dp rise. Stagger nothing here; one bubble at a time.
3. **Confirmation card.** The most consequential moment in the app — it is where
   writes happen. Springs in on arrival; on accept, settles into its resolved
   state in place rather than vanishing.
4. **List mutation.** `Modifier.animateItem()` so a cancelled or promoted task
   visibly *moves* between sections. The user needs to see where it went.

**Interruptibility is not optional.** Every one of these must survive being
interrupted mid-flight — that is most of what makes Framer Motion feel good, and
it is why these are springs targeting `Animatable` rather than fire-and-forget
`tween`s. A transition that must finish before it accepts the next input feels
broken no matter how pretty the curve.

Respect `Settings.Global.ANIMATOR_DURATION_SCALE`: when the user has reduced
animations, collapse to cross-fades rather than ignoring the setting.

### 6.6 Type

Bundle the font files in `res/font` (see §2 — downloadable fonts need Play
Services and can silently fall back, which is worse than not having a brand
face). Two roles:

- **Display** — headlines and task titles. Something with warmth and a tight
  geometric feel; this carries the personality.
- **UI/body** — a highly legible neutral sans for everything else.

Tabular numerals wherever dates, times or counts align in a column
(`FontFeature "tnum"`).

### 6.7 Applying this to the Tasks tab

The reference only covers chat. The Tasks tab is the app's other half and has no
reference, so it is specified here explicitly — otherwise it will drift into a
stock Material list and the app will feel like two different products.

- Short brand gradient behind the header, fading to `Ground` by the first row.
- Rows are **cards on `Surface` with `md` radius and soft shadow**, separated by
  spacing — not full-bleed list items with divider hairlines.
- Section headers: small, uppercase, letter-spaced, `InkMuted`.
- Filter chips: pill, `SurfaceSunk` inactive, `BrandTint` + brand text active.
- The `+` is a brand-filled circle, and it is the only saturated element in the
  list — so the eye lands on it immediately.

## 7. Reminders

Per parent plan §2.8. **Server owns schedule and state; the device mirrors a
window and fires precisely.**

### 7.1 Mirror

- Room table `upcoming_occurrence`: `occurrence_id` (PK), `task_id`, `title`,
  `scheduled_for`, `is_priority`.
- Refreshed from `GET /occurrences/upcoming?within_hours=48` on app foreground,
  and daily via WorkManager.
- Refresh is **replace-the-window**, not merge: delete rows outside the window,
  upsert what came back, then reconcile alarms against the table. A merge
  strategy leaves alarms for occurrences the server has since cancelled.

### 7.2 Alarms

- `AlarmManager.setExactAndAllowWhileIdle()` — required to fire under Doze.
- **Re-arm on `BOOT_COMPLETED`.** Alarms do not survive reboot. This is the
  single most commonly forgotten line in this whole document.
- Also re-arm after the daily WorkManager refresh and after any app update.
- **Dedup by `(task_id, fire_at)`**: derive a stable notification id from that
  pair so a push and a local alarm for the same occurrence collapse into one
  notification rather than two.

### 7.3 Permissions

| permission | API | notes |
|---|---|---|
| `POST_NOTIFICATIONS` | 13+ | Runtime request. Ask in context, not at launch. |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 12+ | Check `canScheduleExactAlarms()`; if false, send the user to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Handle the false case — do not assume. |
| `RECEIVE_BOOT_COMPLETED` | — | For §7.2. |
| Battery optimization exemption | — | See below. |

**Samsung specifically.** Samsung's battery management is aggressive with
periodic background work — it is the reason the parent plan's §2.8 test exists.
Add a settings shortcut that walks the user to exempt Jarvis from battery
optimization. Sideloaded via Obtainium, so the Play policy restrictions on
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` do not apply.

### 7.4 FCM — do not build it yet

The parent plan is explicit: **decide by measurement, not principle.** Ship
local alarms only, then leave the phone alone for 3–4 days with a reminder
scheduled *beyond* the 48h mirrored window.

- Fires on time → FCM is dead weight. Skip it. Delete this section.
- Does not fire → that is precisely the failure FCM exists to fix, and it gets
  added knowing why.

With one user, one device, and the Telegram bot retired, there is no second
writer whose changes need pushing. Building FCM first means a Firebase project,
`google-services.json`, and a service-account key on `gw03` for a problem that
may not exist.

---

## 8. Cross-cutting

### 8.1 State and error model

One `UiState` data class per screen, exposed as `StateFlow`. Model loading and
failure as *states*, not as booleans scattered across the class:

```kotlin
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Failed(val reason: FailureReason) : LoadState<Nothing>
}
```

### 8.2 Failure messages that name the actual cause

The most likely failure by a wide margin is **the phone being off the tailnet**.
A generic "Something went wrong" is close to useless when the fix is one toggle:

| condition | message |
|---|---|
| DNS/connect failure to `*.ts.net` | "Can't reach Jarvis. Is Tailscale connected?" — with a button that opens Tailscale. |
| `401` | "This device isn't authorised any more." → token screen. |
| `5xx` | "Jarvis is having trouble. Try again in a moment." |
| Timeout mid-turn | "That took too long. Your message was sent — check the session." *(Only say this if the send actually reached the server; otherwise offer retry.)* |

### 8.3 Testing

- **Serialization tests first.** The polymorphic `components` array is where a
  contract mismatch will bite. Round-trip every component type, plus an unknown
  `type` to prove forward-compatibility.
- **Compose UI tests** on the fakes for: confirm→task appears, reject→nothing
  written, cancel→dialog→row persists as cancelled, terminal task→no composer.
- **A `LocalDate` boundary test.** Feed a task due 9 PM Sydney and assert it
  shows on the Sydney day, not the UTC one. This is the highest-risk bug class
  in the whole system and it is one assertion.

---

## 9. Attachments (later)

Resolved in the parent plan: **pictures get uploaded through the chat app.** The
gateway schema lands in its phase 2, so this is additive.

App side, when it happens: image picker → `POST /sessions/{id}/attachments`
(multipart) → then send the message referencing the returned `attachment_id`.
Upload first, message second — that ordering is why `message_id` is nullable
server-side. Show upload progress and let the user cancel.

Not in phases A–F. Do not design the composer around it now, but do not paint
yourself into a corner where a second row of controls cannot be added.

---

## 10. Release channel

Per parent plan §2.11 — **Obtainium tracking a separate public releases repo**,
`AR13X3/jarvis-releases`, holding no code. The source repo stays private, and
Obtainium needs no GitHub token (its token setting is global across all sources,
so using one would make it a standing credential for every private repo on the
account).

Mechanics that actually matter:

- **Stable signing key.** Obtainium installs updates in place; a signature
  change fails the install outright. Generate the keystore once, **back it up
  off-machine before the first release**, never regenerate. Blast radius is mild
  — the gateway owns all state, so a forced uninstall costs a cache, not data —
  but it breaks the update channel permanently.
- **Monotonic `versionCode`**; git tag matches `versionName` (`v1.4.0`) so
  Obtainium's version detection has something unambiguous to read.
- **One APK asset per release**, named consistently (`jarvis-v1.4.0.apk`).
  Multiple assets force an Obtainium regex filter for no benefit.
- Build in Android Studio on `joy`, publish with `gh release create` against the
  releases repo. No CI at this size — adding Actions later means putting the
  keystore in secrets, which is a real decision, not a chore.
- Obtainium needs "install unknown apps" granted once.

### 10.1 Version identity — one number, everywhere

Every release must be identifiable from the app, from the gateway logs, and from
the repo, without guessing. Four things move together and must never disagree:

| artefact | example | rule |
|---|---|---|
| `versionName` | `1.4.0` | semver, user-visible |
| `versionCode` | `10400` | **monotonic**; derive it, never hand-edit |
| git tag | `v1.4.0` | `v` + `versionName` exactly — Obtainium reads this |
| APK asset | `jarvis-v1.4.0.apk` | one asset per release |

Derive the code from the name so they cannot drift:

```kotlin
// app/build.gradle.kts
fun gitSha(): String = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim()

val appVersionName = "1.4.0"
val appVersionCode = appVersionName.split(".")
    .map { it.toInt() }
    .let { (maj, min, patch) -> maj * 10_000 + min * 100 + patch }   // 1.4.0 -> 10400

android {
    defaultConfig {
        versionName = appVersionName
        versionCode = appVersionCode
        buildConfigField("String", "GIT_SHA", "\"${gitSha()}\"")
        buildConfigField("long",   "BUILD_TIME", "${System.currentTimeMillis()}L")
    }
}
```

This caps at 99 minor / 99 patch per major, which is not a real constraint. It
does mean **`1.4.0` → `1.10.0` is a valid increase** (10400 → 11000) but
`1.4.0` → `1.4.10` is fine too (10410). Never release a version whose computed
code is not strictly greater than the last — Obtainium will refuse the install.

**Surface it in an About screen**: `versionName (versionCode)`, git SHA, build
date. When something misbehaves on the phone, the first question is always
"which build is that?" and it should take one tap to answer.

**Send it to the gateway** on every request:

```
X-App-Version: 1.4.0 (10400)
```

Costs nothing and means gateway logs attribute a bad turn to a specific build.

### 10.2 In-app update check

**Play's In-App Updates API is unavailable** — that library only works for apps
installed by the Play Store, and this one is sideloaded. So the app checks for
itself. Obtainium still performs the actual install; the app only *notices* and
*explains*.

Worth building even though Obtainium already notifies, because the app can say
things Obtainium cannot: what changed, and whether the current build is still
compatible with the gateway.

**Flow:**

1. At most **once per day** (WorkManager, or on foreground with a timestamp
   guard), `GET https://api.github.com/repos/AR13X3/jarvis-releases/releases/latest`.
2. Read `tag_name` (`v1.5.0`) and `body` (release notes).
3. Compare against `BuildConfig.VERSION_NAME` with a real semver comparison —
   **not string comparison**, or `1.10.0` sorts below `1.9.0`.
4. If newer: a dismissible banner plus a badge on the About screen, showing the
   new version and its release notes.
5. The action **opens Obtainium** (or the release page as a fallback). The app
   does **not** download or install the APK itself.

Deliberately not self-installing: that needs `REQUEST_INSTALL_PACKAGES`, a
download manager, signature verification, and an install-session flow — all to
duplicate a job Obtainium already does, and does off-tailnet.

Two properties that make this robust:

- **It works off-tailnet.** api.github.com is public, unlike the gateway. The
  update check is the one network call that succeeds when Tailscale is off.
- **Rate limits are irrelevant.** Unauthenticated GitHub is 60 req/hr per IP; one
  check per day uses 0.07% of that. No token needed, so nothing to leak.

Fail **silently**. A failed update check is not worth an error to the user —
log it, try again tomorrow.

### 10.3 Gateway compatibility

Client and server update out of band here: the gateway is redeployed on `gw03`
whenever you like, and the phone updates whenever you open Obtainium. They
*will* drift. Handle it explicitly rather than debugging a mystery later.

The gateway returns, in every response header or an `/health` payload:

```jsonc
{ "min_supported_app": "1.2.0",     // below this, refuse and tell the user
  "current_app": "1.5.0" }          // latest known good
```

- App below `min_supported_app` → a **blocking** screen: "This version of Jarvis
  is too old to talk to the server. Update to continue." with the Obtainium
  action. Blocking is correct here — a client sending a retired request shape
  produces confusing failures, not clean ones.
- App below `current_app` but at or above the minimum → the ordinary dismissible
  banner from §10.2.

Add `min_supported_app` to the gateway's contract (§13).

### 10.4 Release checklist

Run this every time; it is short precisely so it actually gets run.

1. Bump `appVersionName` in `app/build.gradle.kts`.
2. Update `CHANGELOG.md` in the **releases** repo — the entry becomes the GitHub
   release body, which becomes the text the in-app banner shows. Write it for
   the person holding the phone.
3. `./gradlew assembleRelease` — signed with the **same keystore as every prior
   release** (§10, stable signing key).
4. Verify: `versionCode` strictly greater than last; APK signature matches
   (`apksigner verify --print-certs`) — a mismatch here fails the install on
   device with a message that does not explain itself.
5. `gh release create v1.4.0 --repo AR13X3/jarvis-releases -F notes.md jarvis-v1.4.0.apk`
6. Confirm Obtainium sees it and installs **in place** over the previous build.
   An install that asks to uninstall first means the signature changed — stop
   and fix it, do not accept the uninstall.

---

## 11. Phases

Phases **A–C need no gateway** and are the parallel work. D is the join point.

| phase | scope | done when |
|---|---|---|
| **A. Skeleton** | Project, catalog, Hilt, nav shell, two tabs, theme + semantic palette, motion primitives, fake repos + fixtures | App runs, both tabs navigate, dark/light both correct |
| **B. Tasks tab** | Three sections, row, status/priority/due-today indicators, filters, cancel dialog, overflow + undo, paging | Every §5.2 behaviour works against fakes |
| **C. Session + chat** | Message list, composer, optimistic send, pending state, confirm card, task_options buttons + show more, read-only terminal, **shared-element transition** | §5.3/§5.4 work against fakes; the row→session transition feels right |
| **D. Wire up** | Retrofit impls, token screen, error taxonomy, swap Hilt module. **Requires the gateway.** | Same flows, real data |
| **E. Reminders** | Room mirror, WorkManager refresh, exact alarms, boot re-arm, permissions, Samsung exemption | Reminder fires once, on time, phone offline |
| **F. Release + updates** | Keystore, signing config, derived versioning (§10.1), About screen, `X-App-Version` header, GitHub update check + banner (§10.2), gateway compat gate (§10.3), releases repo, Obtainium | Update installs **in place** over a prior build, and the older build shows the update banner before you install it |
| **G. Attachments** | §9 | Deferred |

**Suggested order within A** — do the theme and the shared-element transition
*early*, not last. They are the two things that are painful to retrofit and the
two things that decide whether the app feels good.

---

## 12. Acceptance — app-side slice of parent §2.9

1. A session bound to a task cannot create a second task. *(App side: verify the
   UI never offers it. The real enforcement is server-side and not the app's
   job to test.)*
2. A terminal task's session shows no composer.
3. Multi-turn refinement: a vague request reaches a correct task over several
   turns, nothing written until confirmation.
4. Reject a proposal → no task appears.
5. Cancel from the row → dialog → row persists as `cancelled`, not removed.
6. `cancelled` and `incomplete` are visibly distinct, in both themes.
7. Disambiguation: three same-titled tasks on different dates resolve through
   the date → buttons flow, including "show more".
8. Kill the app mid-refinement; reopen; session resumes with history intact.
9. Reminder fires once, on time, with the phone offline.
10. An `incomplete` task can still be rescheduled and returns to `active`.

---

## 13. Open — needs a decision from the gw03 side

- **`GET /occurrences/upcoming` does not exist in the parent plan's API list**
  (§4.4 above). The app cannot do reminders without it.
- **`min_supported_app` / `current_app`** must come from the gateway (§10.3), or
  the app cannot tell a drifted client from a broken one.
- **Off-tailnet.** The parent plan leaves this open. Everything here assumes
  Tailscale-only. If the app must work off-tailnet that is a public ingress and
  a different security conversation — and it changes §4.1 and §8.2 materially.
- **Completing a recurring occurrence from its session.** One session spans
  every occurrence, so "done" resolves the *current* one. Confirm that is the
  intent versus resolving only from the notification.
- **Priority + recurring overlap** (§5.2). Build duplicated, decide on device.
- **Attachment semantics.** Whether an image may *drive a proposal* is a Budget
  domain question, out of scope here. Until answered, chat accepts and displays
  images with no tool able to act on them.
