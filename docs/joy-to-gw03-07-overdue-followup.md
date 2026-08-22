# joy → gw03 · document 07 · overdue follow-up, with a limited number of extensions

**Date:** 2026-08-22 · **App build:** `0.1.4 (104)`
**Status:** design + contract ask. The app half is **built and merged**, running
against fakes, and dark until §3 exists. Nothing is blocked on implementation
order — ship the pieces in whatever sequence suits you.

The one part of this that needed nothing from you — the Overdue *section* in the
task list — shipped in `0.1.3`. §4a is its remaining rough edge.

---

## 1. What this is

Today the app only ever speaks when spoken to. Every message in every session
starts with the user. This makes the agent **initiate**: when a deadline passes,
it asks whether the thing got done, and offers a bounded number of second
chances before recording that it lapsed.

> **Jarvis:** "Apply for jobs" was due at 6:40 pm. Have you done it?
> `[ Yes, done ]  [ +15 min ]  [ +30 min ]  [ +1 hour ]`
> *You can push this back one more time.*

Three rules:

1. **Three questions, two extensions.** The first nudge fires at the deadline.
   Each extension buys 15, 30 or 60 minutes and costs one of two allowances.
2. **The agent says how many are left**, every time. "One more" is the
   difference between a deadline and a suggestion.
3. **When the allowance is gone and it still is not done, the task becomes
   `incomplete`.** Not `cancelled` — this is a lapse, not a decision, and the
   two are deliberately distinct (parent plan §2.5).

`incomplete` being **non-terminal** (§4.3) is what makes this humane rather than
punitive: the task keeps its full mutation set, so "I did mean to do that" is
still one message away. Nothing is lost by letting it lapse.

## 2. Why the state has to be yours

The count cannot live on the device. Parent plan §2.1: the gateway owns all
state; §2.8: the server owns schedule and state, the device mirrors a window.
Concretely, if the phone held the counter then a reinstall, a second device or a
flat battery would hand out fresh extensions, and the cap would mean nothing.

The **policy** should be yours too — `extensions_allowed`, not a `2` compiled
into the app. Same reasoning as §3.1 and §3.3: the app does not own rules, and a
cap that can only change by shipping an APK is a cap nobody will tune.

## 3. Contract

### 3.1 Occurrences gain two fields

```jsonc
{
  "occurrence_id": 88,
  "task_id": 12,
  "title": "Apply for jobs",
  "scheduled_for": "2026-08-22T08:40:00Z",
  "is_priority": false,
  "extensions_used": 1,        // NEW
  "extensions_allowed": 2      // NEW — policy, from you
}
```

On `GET /occurrences/upcoming` as well, so the notification can say "one more"
while the phone is offline and cannot ask.

### 3.2 Two endpoints

```
POST /occurrences/{id}/extend    {minutes: 15|30|60}
  200 -> { occurrence, task }
  409 -> when extensions_used == extensions_allowed   (error code: "no_extensions_left")

POST /occurrences/{id}/complete
  200 -> { occurrence, task }
```

**Occurrence-scoped, not task-scoped**, and this is the whole reason occurrences
exist (parent §2.5): finishing Monday's gym session must not close the weekly
rule. `POST /tasks/{id}/complete` would be the wrong shape for exactly the case
recurrence was built for.

Note there is currently **no completion endpoint at all** — `propose_complete`
exists as a tool, and `/tasks/{id}/cancel` as a route, but nothing completes.
This needs one regardless of the rest of this document.

### 3.3 A new component: the nudge lives in the session

When a deadline passes, post an **agent message into that task's session**
carrying:

```jsonc
{ "type": "overdue",
  "occurrence_id": 88,
  "task_id": 12,
  "deadline": "2026-08-22T08:40:00Z",
  "extensions_used": 0,
  "extensions_allowed": 2 }
```

Why a message and not only a push: one session per task is the architecture
(parent §2.2), and scrolling back should show the agent asking and what you
answered — exactly as a resolved confirm card does (§5.3). A nudge that exists
only as a notification vanishes when the notification does, and then there is no
record that you were asked.

The app already tolerates unknown component types (§4.5), so shipping this
before the app renders it is safe.

### 3.4 State transitions

| event | from | to |
|---|---|---|
| deadline passes, extensions remain | `active` | `awaiting` + nudge |
| `extend` | `awaiting` | `active`, `scheduled_for += minutes`, `extensions_used += 1` |
| `complete` | any live | `completed` |
| deadline passes, no extensions left | `awaiting` | **`incomplete`** + final message |

The last row is the one to get right. Our proposal: **flip to `incomplete` at
that moment and say so**, rather than nudging a fourth time and waiting. The
final message offers only "Mark done" — and that still works, because
`incomplete` is not terminal. The alternative (ask again, wait for silence) just
moves the same decision later and leaves the task in `awaiting` indefinitely,
which is the state the scheduler can least afford to leak.

## 4. What the app does

- Fires the notification from the local alarm mirror, as now (§7.2) — so the
  nudge is on time with the phone offline.
- Renders the four actions on the notification **and** in the session, so it can
  be answered from the lock screen without opening the app.
- Shows the remaining count in both places.
- Dedups the local alarm against your push by `(task_id, fire_at)`, as §7.2
  already requires.

**Offline is the sharp edge.** The alarm fires without a network, but every
action here is a write, and §3.5 forbids an offline queue — reconciling a queued
"done" against an agent that has since moved the task is the problem that rule
exists to avoid. So a tapped action with no gateway will surface the failure
rather than pretend, and we will make that legible rather than silent. Worth
knowing on your side, because it means an extension can be *attempted* and not
land, and the deadline you see may be the one the user thought they moved.

## 4a. One more ask, cheap and separate

**`/tasks/sections` should carry an `overdue` array.**

The app now shows an Overdue section at the top of the task list — `awaiting`,
plus `active` whose `due_at` has passed, which covers the gap between a deadline
going by and your scheduler noticing. It needs nothing from you to work, and it
already shipped.

But it is derived from the tasks the app has loaded, and `all` is paged 20 at a
time. Priority and recurring arrive whole; everything else does not. So an
overdue task deep in the list is invisible until it is paged in — which is the
wrong failure for the one section whose entire job is "you have missed
something".

```jsonc
{ "overdue":   [Task],   // NEW: status = awaiting, or active with due_at < now
  "priority":  [Task],
  "recurring": [Task],
  "all": { "tasks": [Task], "page": 1, "has_more": true } }
```

Same shape as the sections beside it, and the query is one you already run for
the scheduler. Independent of everything else in this document — worth doing
even if the follow-up loop is not.

## 5. Open, for you

1. **Extensions are per occurrence, not per task** — so a recurring task gets a
   fresh two every firing. We think that is obviously right (a rule you have
   pushed back twice this Monday should not arrive pre-exhausted next Monday),
   but it is your schema, so confirm.
2. **Does an extension re-notify at the new time via push, or do we rely on the
   local alarm?** Our mirror only covers ~48h; a +1 hour extension is always
   inside it, so the local alarm is sufficient and the answer is probably "do
   nothing". Flagging it so it is a decision rather than an assumption.
3. **`extensions_allowed` per task, or global policy?** We would start global.
   Per-task is a setting nobody will set.
