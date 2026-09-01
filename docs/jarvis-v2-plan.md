# Jarvis v2 — from a reminder app to a life management app

Written 2026-09-01, from a design conversation with Joy.

`jarvis-app-plan.md` remains the authority for everything it covers — the design
system, the session model, the release channel, phases A–I. This document
extends it, the way §14 did, and where the two conflict **this one is newer and
wins**. `BUILD_NOTES.md` stays the *how*.

**Status, 2026-09-01 09:13 UTC: six of §8's seven steps are BUILT and deployed.**
Only step 7 (attachments) is unstarted. The gateway half of steps 1–6 is live
behind `https://gw03.tail9662e3.ts.net/api`; the app half — the dashboard, the
to-do screens, summaries — is not. The tracker is the state; this file is the
reasoning.

**This document goes stale, and it has now pointed at a breaking change twice.**
Both times the reasoning was sound and the premise had expired: §5.2's cost
count (see the correction there), and this banner, which still said "not yet
built" after six steps had shipped. Older sections lose to newer ones, and both
lose to the served `openapi.json` and the code. **Re-derive any costed claim
against the real DTOs before building on it.** Where a paragraph is superseded it
is marked in place rather than deleted — a deleted paragraph is one somebody
re-derives from scratch, and the trap with it.

---

## 1. What is actually changing

Today the app does one thing well: you say something in natural language, it
proposes a task, you confirm, it fires on time and chases you if you ignore it.
That is a **reminder** app whose central object is called a task — slightly the
wrong word, and the one already in the API, the database, the agent's tools and
Joy's head.

v2 adds three things and renames nothing:

| | what it is | fails when |
|---|---|---|
| **Tasks** | unchanged. A point in time that fires and chases. | past due, extensions spent, not done |
| **Routines** | a repeating weekly plan that partitions the day. Time tracking, not nagging. | a tracked slot is never started |
| **To-dos** | *new*. Start and deadline as separate things, tags, status, checklists, attachments, history. Optionally undated. | dated: as tasks. **undated: cannot fail** |
| **Dashboard** | what got done, what is slipping, what is being avoided | — |
| **Summaries** | daily and weekly, overall and per group, written by the agent | — |

The fourth column is not decoration. Joy's definition of failing is *"not
completing on due time, or even after extensions"* — which is deadline-bound, so
an object with no deadline has nothing to fail against. An undated task is
**stale**, never failed. Getting this wrong puts every undated task permanently
in "what am I missing".

---

## 2. No rename. The new domain is **to-dos**

An earlier draft opened with a rename: `task` → `reminder`, freeing the word for
the new domain. gw03 costed it — ~1090 identifiers, 5 routes, 10 wire models, the
schema, the `task_status` enum, 4 indexes, a trigger, and a cutover coupling two
repos with a phone in the field.

**Decided: don't.** The existing thing stays **Tasks**, everywhere — wire,
database, tools, tab. The new domain is **To-dos**.

That deletes the single largest item in the plan and every risk attached to it.
Nothing is deploy-coupled, no aliases are needed, and the phone and the gateway
stay independent.

**What it costs, stated rather than glossed.** "Task" for a timed nudge that
chases you is still slightly the wrong word, and gw03 made a real argument the
first draft had missed: `tasks.task_status` is shared by tasks and occurrences,
and its values are the reminder lifecycle — so the new domain's statuses (§5.3)
need a separate enum with a less obvious name. That is a database-internal name.
It never reaches the app, the API's consumers, or Joy. It is gw03's ugliness to
live with, and it is far cheaper than the rename.

**Naming, so both sides write it the same way.** "To-do" and "to-dos" in prose
and on screen; `todo` and `todos` in identifiers, routes, tables and tool names.
No collision with `tasks` anywhere.

---

## 3. The spine: one event stream

Reminders, routines and tasks are genuinely different shapes and should stay
three models. Forcing them into one table with a `kind` column produces a table
where two thirds of the columns are null for any given row.

But their *completions* are identical, and completions are all the dashboard and
the summaries ever read. So:

- three domains write into an append-only event record
- the dashboard, the summaries and a to-do's history all read **only** that record
- a fourth domain later changes nothing downstream

**One table or one per domain — settled, and not the way this section first said.**
gw03 built `tasks.occurrence_events`: task-scoped, typed columns, real foreign
keys, a domain verb enum. The argument that won is that a single table cannot
hold a foreign key across three domains, and putting `prev_scheduled_for`,
`extensions_used`, `slot_id` and `clamped` into untyped `jsonb` reproduces the
very two-thirds-null problem this section rejects for the domain tables —
relocated into the stream, and worse for having lost the types.

The load-bearing half: this section's benefit was for *downstream readers*, and
§6 makes the gateway the only downstream reader there will ever be. An
abstraction whose sole beneficiary prefers the other shape has no beneficiary.

**The condition, and it is not optional.** A view unioning the per-domain tables
into `(at, source, source_id, verb)` lands **with the second domain, not after
it**. The moment two event tables exist and no view does, every reader grows a
branch — which is the entire thing this section was written to prevent. It is
cheapest to write when there are exactly two tables and dearer with each one.
When it lands, the dashboard's internals change and the API does not.

An event is roughly: `at`, `source` (task | routine | todo), `source_id`,
`verb` (started, completed, missed, lapsed, created, rescheduled, ticked,
cancelled), `category_or_tag`, and a small payload.

**Corrected by gw03, and this is the one thing the first draft got wrong.**

The occurrence table is a **current-state projection, not an event stream**.
Every transition is an `UPDATE` in place: extending *overwrites* `scheduled_for`
so the original deadline is gone, a reschedule can revive a cancelled row, and
`awaiting_since` is cleared on every extension. For a firing due 09:00, asked at
09:02, auto-extended twice and completed at 11:40, the row afterwards says
`scheduled_for 11:00, completed, resolved_at 11:40, extensions_used 2`. The
outcome is readable. The deadline it missed, when it was asked, and when either
extension happened are not.

So "most of this already exists" is true of **completions** and false of
**history** — and §6's *drifting*, which is planned versus actual, cannot be
computed from it, because for a task the planned time is precisely the field
extend overwrites. The stream is a genuinely new append-only table. Still cheap:
seven sites mutate occurrence state, one `INSERT` beside each, no behaviour
change. Backfilled rows are outcome-only and must be marked `backfilled`, so no
dashboard presents a reconstruction as an observation.

**`completed_at` already exists.** It is `tasks.occurrences.resolved_at`, since
migration 0001, written on completion, cancellation, supersession and lapse. The
first draft said the occurrence "records *that* something was completed but not
*when*" — true of the API, false of the database, and inferred from the served
OpenAPI rather than checked. Exposing it costs one field on the model, one line
in the mapper and one read route. **No migration.** Do not rename it: it also
stamps cancelled and incomplete, and `resolved_at` is the honest name.

**Bucketing, decided.** `scheduled_date` is the day something was *due*;
`local_day(resolved_at)` is the day it was *answered*. Both columns exist and
they answer different questions, so the dashboard uses both deliberately:
**"what did I get done" is answered-day, "what did I miss" is due-day.**

Per-task history and the dashboard are the same feature. Build the stream once.

---

## 4. Routines

Modelled from Joy's own weekly plan (`the-week.html`), which is the input this
section exists to serve. Read it before changing anything here.

### 4.1 It is a partition, not a checklist

Every minute from wake to sleep is allocated, back to back, no gaps. That is why
the weekly totals and the 26 / 19 / 17 percentage split mean anything — they are
only computable because the slots tile the whole day. Reminders are points and
tasks are ranges; a routine is a **budget**. This is why it is its own model and
not tasks with a start time.

### 4.2 The day does not end at midnight

Friday's Speedway slot runs 1:45pm–12:15am, then shower 12:15–1:00, then
wind-down 1:00–2:00. Bucket those by calendar date and three hours of Friday
land on Saturday, and every weekend statistic is quietly wrong.

**All seven days cross midnight**, not only the ones that look unusual. Mon,
Tue and Wed run 08:00–01:00; Thursday 10:00–01:00; Fri, Sat and Sun 08:00–02:00.
Naming only Thursday and the weekend, as the first draft did, invites someone to
implement "declare the boundary where it differs, default the rest to midnight"
— which silently deletes Monday and Tuesday's 23:00–01:00 slot and one of
Wednesday's two buffers. **Every day is declared. There is no default and no
branch**, which is simpler than the first draft read, not harder.

**48 hours a week belong to no logical day.** 120 waking hours out of 168; the
gaps are 01:00→08:00 four times, 01:00→10:00 on Wednesday night, and
02:00→08:00 three times. §4.1's partition is of *waking* hours, not of the
clock, so resolving an instant to a day is a **partial** function.

**Decided: a tap in a gap is attributed to the day that just ended, and recorded
as clamped.** A 3am tap is a late finish, not a new day, and rejecting it loses
the one honest signal about overrun — which is the entire reason for tracking
three consecutive Speedway nights. No threshold: "within N hours of the end"
needs an N nobody can justify, and it puts a discontinuity in the middle of the
night, exactly where the interesting data lives.

**Resolution rule**, so both sides agree: the logical day is the date `d` where
`start(d) <= t < end(d)`; resolve by scanning **backwards** to the most recent
declared start, never forwards from midnight.

This is `jarvis-app-plan.md` §3.2 in a new costume, but **the two are not the
same rule and the difference matters.**

§3.2 forbids deriving a calendar day from a timestamp, because the answer
depends on a timezone the phone and the server disagree about. Resolving an
instant against boundaries the **server declared** and the app has already
fetched is arithmetic on server-owned data — nothing is invented, `starts_at`
and `ends_at` came from the routine version.

**The app must resolve it locally**, because the routine tab has to render off
the tailnet. The whole reminders subsystem is built on a local mirror for that
reason: alarms fire on a plane. A tab that needed a round trip to know what day
it is would be blank exactly when someone is checking whether they did the gym.

An earlier draft of this section said "the server owns the boundary and the app
never computes it", which is too strong — and gw03 read it literally and designed
a `/routine/now` route as the only source of truth. That route is worth having as
a **cross-check**, and as the answer before a version has been fetched. It is not
the only source. If the two ever disagree, that is a bug worth finding, and it is
only findable because both exist.

### 4.3 Slots have a kind, and most are not tickable

Twelve slots a day is roughly eighty a week. Ticking "Lunch" and "Get ready to
leave" daily is noise that buries the five that matter.

- **tracked** — Reskill block, Web dev, Gym, Uni, Reskill meetings, batch cook.
  Startable, counted, this is the adherence data. About five a day.
- **scaffold** — wake, lunch, dinner, shower. Drawn so the day reads as
  continuous. Never startable, never counted.
- **buffer** — **exactly two slots**, both on Wednesday, both named by the
  footer: 17:30–20:30 and 23:00–01:00. **The success condition inverts** — the
  win is having left them alone.
- **free** — everything else that is neither a commitment nor scaffolding:
  Breather, Free, Calls, Wind-down, Thursday's football. Not startable, not
  counted, and **not scored either way**. Rest is neither an achievement nor a
  failure.

The fourth kind exists because the first draft had *buffer* doing two jobs.
Counted against the fixture: buffer was on fourteen slots and only two of them
invert. The other twelve are ordinary free time, so the dashboard would have
congratulated Joy for keeping Friday's wind-down empty, and diluted the one
number that carries signal by seven to one.

**Speedway is tracked** — 31.5h, 26% of the week, the largest committed block by
half again. It was missing from the first draft's list, which is the kind of
omission that becomes load-bearing the day someone re-derives the list from this
document.

**Kind never derives from category.** The fixture is the evidence: it has to
break that correspondence fourteen times to be right — twice for the cooks,
twelve times for free. Kind is per-slot data on the routine version, set by the
agent at import (§4.7) and hand-correctable.

The week is **67 slots: 26 tracked, 27 scaffold, 2 buffer, 12 free** — a mean of
3.7 tracked a day, not the "about five" and "roughly eighty a week" the first
draft quoted from a guess.

**Calls is free, not tracked.** The one genuine judgement call rather than a
countable fact, and Joy's own category is named "Free, buffer, calls" — that is
Joy classifying it. An unmade call at 11pm on a Tuesday is not a failure.

### 4.4 Start-only logging

One tap: *I am starting this*. There is no stop button.

- Starting any slot **closes whichever slot is currently open**. It does not
  assume you follow the order — doing gym after uni simply records itself, which
  is the variation Joy wants to see.
- Actual duration of a slot is `start(n)` to `start(n+1)`.
- **Skipping is implicit** — a slot you never started. Clean, but it cannot
  distinguish "I skipped gym" from "I did gym and forgot to tap". One prompt at
  day end over unstarted tracked slots keeps the data honest.
- **Day end closes the last open slot.** Without it, one missed tap at 11pm
  produces a fourteen-hour Reskill block.
- **A start record pins the routine version it was measured against**, not only
  the logical day. The day boundary is declared *on* the version (§4.6), so if a
  later version moves Thursday's wake to 09:00, every stored Thursday start
  silently re-buckets — rewriting exactly the history §4.6 exists to protect.
  The log is `(routine_version_id, logical_day, slot_id, started_at, clamped)`,
  version first.

Variation therefore falls out for free: planned 2:15–3:45, actual 2:48–4:10, and
out of sequence.

### 4.5 Categories are routine-local

Speedway, Reskill/CBAI, Uni, Web dev, Gym, Life, Free. Each has a colour and a
**class** — committed, upkeep, or free — which is what produces the top-line
76.5 / 20.25 / 23.25 split.

Joy's decision: these are **not** the summary groups. Summaries are for tasks;
the routine is for seeing where the hours go. Reskill and CBAI stay merged here.

*Consequence, recorded rather than argued:* routine categories and task tags are
separate vocabularies, so "20h on Reskill this week" and "9 Reskill tasks done"
cannot appear in the same dashboard row. That is defensible — time spent and
output produced are different questions — but it is a choice, not an accident.

### 4.6 Versioning, because editing must not rewrite history

A routine can be edited. If editing the template changed the past, moving gym to
4pm today would silently rewrite three months of adherence and every "you are
failing at this" would become fiction.

So a routine has **versions with effective dates**. An edit takes effect from
tomorrow; days already measured keep the plan they were measured against.

That covers the permanent case. The temporary one — *this week only, uni
assessment eats Wednesday's buffer* — is a **per-week override** layered on top
of a version. Worth having, second to build.

**An override may change times. It may never change slot kinds.** "This week is
different" is not "this week I measure different things": if a week could change
what is tracked, adherence stops being comparable week to week, which is the
entire point of the dashboard. Changing a kind is a change of intent, and that is
an edit — a new version, with an effective date.

**Category identity is not category classification, and only one of them is
versioned.** A category's `key` and `label` live on the *routine*, because that
is what a cross-version rollup joins on and "20h on Reskill this week versus
last" has to survive an edit. Its `cls` — committed, upkeep or free — lives on
the **version**, because that is what moves the headline split and is therefore a
property of the plan. Put `cls` on the routine and reclassifying Reskill silently
restates last month's totals, which is the one thing this section exists to
prevent.

**The start log is a projection; the events are the history.** Recording a start
upserts by `(version, slot, logical_day)` — a mis-tap corrects in place. The
append-only record is a `started` / `cleared` event in §3's stream. One table
doing both is the mistake `occurrences` already made.

### 4.7 Getting a routine in

Nobody is typing eighty slots on a phone. Paste the week into chat and let the
agent propose the routine — the propose-and-confirm flow already exists and this
is just a new object at the end of it.

### 4.8 The prose in the footer is not decoration

*"Spend Wednesday's buffer on overruns, not new work." "Freeze the Sat and Sun
portions on Thursday." "Speedway nights give about six hours' sleep, three in a
row."* Those are the operating rules and the reason the shape is what it is.
They survive as routine-level and per-day notes, or the reasoning is rebuilt from
scratch in three months.

### 4.9 The day view

Joy's three asks, directly: **upcoming first, not day start**; **one day at a
time, not one long scroll**; **tickable**.

- day pager across the week, not a single scrolling page
- earlier slots collapsed to one line — "5 earlier · 2 done, 1 skipped"
- a now line, with the current slot given weight
- upcoming below it
- only tracked slots carry a start target; scaffold is dimmed, buffer is marked
- keep the stacked day bar from the HTML. It is the best thing in that file and
  reads at a glance.

---

## 5. To-dos

The one genuinely new domain. Trello-like in *capability*, not in interface —
Joy was explicit: **no dragging, no board columns**.

| field | note |
|---|---|
| `title`, `description` | description already carries checklists as of 0.1.8 |
| `starts_at` | **new** — separate from the deadline |
| `due_at` | **nullable** — today it is required on the gateway |
| `tags` | many per task. The summary groups (Reskill, CBAI, Uni) are tags. |
| `status` | see below |
| `checklists` | already built and shipping |
| `attachments` | this is Phase G, which returns to scope |
| ~~`tasks`~~ | ~~a to-do **has** tasks; it is not one~~ — **link deleted 2026-09-02, see §5.1** |
| `history` | the event stream, §3 |

### 5.1 A to-do has tasks

> **SUPERSEDED 2026-09-02 — the link is deleted, on Joy's instruction.**
> *"Remove the reminder pointing thing, it is doing work twice."*
>
> **A to-do's own deadline is the deadline.** `todos.todo_tasks`, the join that
> let a to-do point at the reminders chasing it, is gone from the app: no link
> and unlink on `TodoRepository`, no routes on `JarvisApi`, no `task_ids` on the
> `Todo` DTO, and no Reminders section on the detail screen. gw03 has agreed to
> retire the table now that nothing reads it (tracker 131 and 132) rather than
> leave it answering — dead state that still answers is how this project has been
> misled twice.
>
> **The paragraph below is not wrong about what it was arguing.** Its point was
> that a to-do must not grow a *second nagging engine*, and that is still true
> and still enforced: deleting the link does not build one, because a to-do with
> a `due_at` is simply **not chased**. Nothing about it fires, extends or lapses.
> What the link actually produced was two mechanisms answering *when is this
> due* — the to-do's own `due_at` and whatever the linked task's deadline said —
> which then had to be kept agreeing with each other forever, by hand, with
> nothing detecting a divergence.
>
> **The half that survives:** a thing that should interrupt you is a *reminder*,
> made in the Tasks tab, and it fires and chases exactly as it always has. That
> was §5.1's real content. What is dropped is only the pointer between them.
>
> **One consequence worth stating rather than discovering.** §7's summaries group
> by tag, and the reminders half of a tagged summary was partly populated through
> this link. With it gone that half goes permanently empty for to-do-derived
> reminders — and an empty count reads exactly like a true zero. joy raised this
> on tracker 132; it is gw03's to answer, and it is a reason to check a summary
> after this ships rather than a reason not to ship it.

Joy: *"yes, tasks can have reminders, but we can create tasks without any due
time or date as well."*

So the relationship is **one to-do to many tasks**, and a task stays the object
that already works — it fires, it chases, it extends, it lapses. A to-do does not
acquire its own nagging machinery; if two systems chase the same deadline the
user gets buzzed twice for one thing.

The wording is a little unlovely — a to-do *has tasks* — and that is the price of
§2. It is also only ever written here and in gw03's schema: on screen a to-do
simply has reminders set on it, which is what a person would call them.

### 5.2 Undated to-dos

A to-do with no `due_at` is legal and ordinary. It cannot be overdue and it
cannot fail. It needs its own place in the UI — a backlog — and its own word in
the dashboard. **Stale**, on some threshold of untouched days, is the honest one.

> **SUPERSEDED 2026-09-01 — and following it would have broken the app.**
> The paragraph that stood here counted the cost of making **`tasks.due_at`
> nullable**: two `NOT NULL`s to drop, `create_task` to branch, one explicit
> `ORDER BY ... NULLS LAST`, one recurrence-needs-an-anchor check. That count was
> gw03's and it was *correct when written* — back when to-dos and tasks were
> going to be the same table behind the rename. **§2 cancelled the rename and
> §5.1 makes a to-do the _owner_ of tasks rather than a kind of one, so none of
> it applies.** The paragraph survived the edit.
>
> It matters rather than being untidy because it describes a **breaking** change
> dressed as a cheap one. The app's `Task` DTO declares `due_at` and `due_date`
> **non-null** (`core/model/Task.kt`). The first undated task the gateway served
> would have failed to deserialise and taken the whole task list down with it —
> while §8 says "nothing here is breaking, nothing is deploy-coupled". True of
> what was built; not true of what this paragraph described. gw03 read the DTO
> rather than assuming, which is why it was caught before it was built.

**What is actually true, as built (migration 0009, 2026-09-01).**
`todos.due_at` is nullable **from birth**. `tasks.due_at` is not touched and
stays `NOT NULL`; the app's `Task` DTO needs no change and there is no deploy
coupling. To-dos live in their own `todos` schema, and `todos.todo_tasks` holds
the link — there is deliberately **no `todo_id` column on `tasks.tasks`**, so the
domain that works does not depend on the domain that is new.

The one durable half of the old paragraph is worth keeping, because it is the
reason "cannot fail" needs no enforcement: `overdue_tasks` reads from
**occurrences**, not from `due_at`. A to-do has no occurrences of its own — only
the tasks linked to it do, and those chase their own deadlines. So §5.2's "cannot
fail" is not a rule anyone has to remember; it falls out of the indirection
already shipped in 0001.

**Stale, decided: untouched for 14 days, anchored on `updated_at`.** Not
`created_at`, or a task you looked at yesterday and did not move is stale
tomorrow. Fourteen rather than seven because the summaries are weekly (§7): a
7-day threshold would flag everything the instant each weekly summary was
written, restating "you did not do this last week" as though it were news.
Fourteen means an item has survived **two** weekly reviews without moving, which
is a signal rather than an echo.

**`stale` deliberately ignores `date_from`/`date_to`, and the app must say so.**
"Untouched for fourteen days" is a fact about *now*, not about the window being
looked at; windowing it would make last month's dashboard claim things went stale
in a month that had not happened yet. The consequence is a UI requirement, not a
note: **when the dashboard window ends before today, the stale section is the one
section that is still current, and it carries a caption saying so.** Without the
caption it reads as a bug — a "historical" list that keeps changing. When the
window ends today the caption is omitted, because then it is saying nothing.

### 5.3 Status

Fixed set, not user-defined columns — there is no board to put columns on, and a
fixed set is what lets the dashboard say anything across to-dos.

**Decided: `open` → `doing` → `done`, plus `cancelled`. Four, shipped in 0009.**
Deliberately *not* the task lifecycle (`active`/`awaiting`/`incomplete`), which is
about firing and chasing and means nothing here — and `open` rather than `todo`,
because `todo` now names the domain and `todo.status = todo` reads as a mistake.

**`blocked` is NOT a status — ruled 2026-09-01, closing §9's open question.**
gw03's argument is the cheap one and it holds: adding an enum value later is one
statement, removing one means rewriting every row that used it, so shipping an
undecided value is the expensive direction. But the deciding argument is about
meaning rather than cost.

The other four all answer **where the item is**. `blocked` answers **why it is not
moving**, which is a different question, and putting the answer in the same field
destroys the first one: a to-do that goes `doing` → `blocked` → unblocked cannot
say whether to return to `doing` or to `open`. It also cannot carry the only part
anyone actually needs — *what* is blocking — because a status has no room for a
referent. "Blocked" without "on what" is a feeling, not a state.

So if it earns its place later it arrives **orthogonal to status**: a nullable
`blocked_on` — free text, or a link to the to-do or task doing the blocking —
which composes with `doing` instead of overwriting it. That shape survives; the
enum value would have had to be migrated away from.

---

## 6. Dashboard

Reads the event stream and nothing else.

Four words, and they are not synonyms:

- **failed** — had a deadline, extensions spent, not done. Tasks, and dated to-dos.
- **missed** — a tracked routine slot never started.
- **drifting** — started consistently, but not near its plan. Computable for
  **routines** from the day they ship, because the plan lives on the routine
  version and the start log is append-only. **Not computable for tasks** until
  §3's event table exists, because extending a task overwrites the planned time
  it would be compared against.
- **stale** — an undated to-do untouched past a threshold. Cannot fail.

The app renders; the gateway computes every number. No streaks, percentages or
day-bucketing on the client — see §4.2 for why.

**An empty drift list is not the same as nothing drifting, and rendering it as
though it were would be a lie.** The response carries `provenance`, and it is not
a footnote: `firings_without_history` counts firings the event stream cannot
speak for because they predate it. Measured the day the route shipped, that was
27 of them, and `drifting` was consequently empty.

So: whenever `firings_without_history` is non-zero the drift section says **"not
enough history yet — N firings predate the record"**, and an empty state is shown
**only** when the stream can actually account for the period. This is a
requirement, not a nicety. The failure mode is silent, it lasts about a week, and
it errs in the direction that reassures.

**The same trap is in `missed`, and it is worse there.** Before anyone taps
anything, every tracked slot of every finished day has no start — so a naive
`missed` reports the whole week as missed, which is indistinguishable from "the
routine tab has not been used yet". gw03 built the counting half correctly:
**missed is counted only within days that carry at least one start**, and a day
with no starts is excluded as no evidence either way. `routine_provenance`
reports `days_elapsed` against `days_measured` so the gap is visible.

That leaves the rendering half, and it is joy's. **Three rules, all required:**

1. `routine_provenance.days_measured == 0` renders as **"not measured yet"** and
   never as a clean week. An empty `missed` with nothing measured is the mirror
   image of an empty `drifting` with no history — it is the screen congratulating
   Joy for a week nobody recorded.
2. Whenever `days_elapsed > days_measured` the section shows **"measured on N of
   M days"** — always, not only when the gap is large. A caveat that appears only
   above some threshold teaches the reader that its absence means zero.
3. Every `missed` row shows its own denominator: **"missed 3 of 5 measured
   days"**, never a bare "3". `MissedSlot.days_measured` is on the wire per row
   precisely so the row can say it, and a bare count invites reading it against
   `days_elapsed`, which is the wrong number.

**Accepted failures are shown, not hidden — ruled 2026-09-01.** "Charge my watch"
has failed 9 of 12 and Joy has already decided the reminder is right and the time
is one she does not answer (tracker 40). Left alone it sits at the top of
`failing` every day forever, and a dashboard that leads with something you chose
to live with teaches you to skip the top row.

Excluding it from `failing` entirely was the other option and it is **wrong for
the reason this whole section exists**: an absent row and a row that stopped
failing read identically. If "Charge my watch" ever *did* start succeeding,
nothing on the screen would change. So the flag is carried, not filtered — and
because gw03 computes and the app renders, **the ranking is the app's job**:
accepted rows sort below unaccepted ones, under their own quiet heading, with the
real numbers still on them. Nothing is hidden and nothing is shouted about.

**Drift thresholds are derived, per domain, and live in `policy`.** For tasks
gw03 derived `GRACE_MINUTES + EXTENSIONS_ALLOWED * AUTO_EXTEND_MINUTES` — the
longest the gateway ever said it would wait, so anything later is later than its
own declared tolerance. It self-adjusts when either constant is tuned, and all
three inputs ride on the response so the derivation is checkable rather than a
magic number that can only be repeated.

Routines need their own: a gym slot started thirty minutes late is drift, and 135
minutes would never fire on a ninety-minute slot. `SlotRow.drifted` currently
hardcodes 15 minutes, which is the right *reasoning* — nobody taps at the second,
and a routine crying wolf over four minutes gets ignored — and the wrong *place*.
It moves into `policy` when the routine tables land, and the constant is deleted.

**Built 2026-09-01 as `policy.routine_drift_threshold_minutes`, derived from
`GRACE_MINUTES` alone** — this system's already-declared unit of "a delay that
does not count for anything". It lands on 15 with a reason instead of a guess,
gw03 pins it with a test, and it is absolute, so an early start counts, matching
`SlotRow.drifted`'s `|actual − planned|`. Accepted as-is.

> **But `policy` alone cannot carry it, and this is the app half of the same
> mistake gw03 caught in `grace_minutes`.** `policy` rides on `/dashboard`, which
> is authenticated and network-only. §4.2 requires the **routine tab to render
> off the tailnet** from the cached `Routine`, and `Routine` carries no threshold
> (verified against the served contract, sha `e398ff18e4aa6b33`: it is on
> `DashboardPolicy` and nowhere else). Deleting the constant with the number
> reachable only through `/dashboard` means the routine tab either keeps a
> client-side 15 under another name, or cannot answer offline at all.
>
> **The fix is one additive optional field: carry
> `drift_threshold_minutes` on `Routine` too** — the fetch-once-and-keep object
> the app is already told to cache. Then the number arrives with the thing it
> describes and is cached by the same code path.
>
> **Until it does, the app does not guess.** `SlotRow.drifted` becomes
> *unknown* rather than *false* when no threshold has been received, and the row
> renders with no drift verdict. That is §6's own provenance rule — never render
> "nothing is wrong" when the honest answer is "nothing was measured" — applied
> to the threshold instead of the history. A fallback 15 would be a client
> constant wearing a server's name, which is what this paragraph exists to end.

Sequenced deliberately so it is useful early: a dashboard over **tasks only**
is possible as soon as §3's history route exists, before routines or tasks are
built. Each domain then lights up more of the same screen.

---

## 7. Summaries

Daily and weekly, overall and per tag — Reskill, CBAI, Uni as separate groups.

Scheduled agent runs on gw03's existing scheduler, which already runs the
follow-up loop. Each writes a **stored** summary record rather than generating on
view: stable, cheap to render, and *"what did I get done last Tuesday"* stays
answerable after the fact.

A group is a tag with a summary subscription attached. One taxonomy, not two.

---

## 8. Order

1. **Expose `resolved_at`** + one history route. No migration, not breaking.
   — **DONE** (gateway).
2. **The event table** + inserts beside the existing mutations. Not breaking, and
   nothing to coordinate with the app. — **DONE** (gateway), migration 0006–0008.
   *The count here said "seven inserts". It was **ten** — three `INSERT`s were
   missed in the original survey. gw03 recounted against the code before
   building. A miscount in a plan is a miscount you build to.*
3. **Dashboard v1**, over tasks alone. — gateway **DONE** (`GET /dashboard`);
   **app half is joy's and is the current work**.
4. **Routines.** Template, versions, tracked slots, start-only logging, day view.
   — gateway **DONE**; app day view and week view built.
5. **To-dos.** Tags, `starts_at`, nullable `due_at`, status, history. — gateway
   **DONE**, migration 0009. *"Nullable `due_at`" means **`todos.due_at`** — see
   the correction in §5.2. `tasks.due_at` is untouched and stays `NOT NULL`.*
   **App screens not built.**
6. **Summaries.** Needs tags from 5 and the scheduler that already exists. —
   gateway **DONE**, migrations 0010–0011. **App screen not built.**
7. **Attachments** — Phase G, returning to scope, last. — **NOT STARTED.**

The first draft had a rename at the front, then at position three. §2 removed it
altogether, which is why this list is shorter than the discussion that produced
it. Nothing here is breaking, nothing is deploy-coupled, and steps 1 and 2 can
ship the moment gw03 next restarts for a reason of its own.

Steps 1, 2, 4, 5, 6 are overwhelmingly **gw03's**: the gateway owns state and the
app renders. joy's share is navigation, the dashboard, the routine day view and
the to-do detail screen. Real work, but the smaller half — and the routine half
is already built and running on a fixture.

---

## 9. Open

- **Navigation.** The app is two tabs. This is four surfaces — Reminders, Tasks,
  Routine, Dashboard — plus Chat. That is a design-system decision, not a
  routing one, and `jarvis-app-plan.md` §6 is the authority.
- ~~**`blocked` as a status, or a tag.**~~ **Closed 2026-09-01: not a status.**
  See §5.3 — it is orthogonal to status, so if it arrives it arrives as
  `blocked_on`, not as a fifth enum value.
- **Per-week routine overrides.** Agreed as wanted, not yet specified. §4.6.
  Still unbuilt, and *deliberately* — §9 called it agreed and never said what it
  was, so there is nothing to build to. It needs a spec before it needs code.
- **Agent tools for to-dos and summaries.** Not built, deliberately: the model
  can read them but cannot create a to-do, only the app can. Adding tools changes
  the agreed tool-scope table *and* the live conversational surface, so it is a
  joint decision rather than gw03's alone. §2.3.
- **The tracked/scaffold split for every slot in the current routine.** The kinds
  in §4.3 are confirmed in principle and guessed per slot; the guess wants a pass.
- **What the day-end prompt looks like** for unstarted tracked slots. §4.4.
