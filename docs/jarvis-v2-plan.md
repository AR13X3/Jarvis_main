# Jarvis v2 — from a reminder app to a life management app

Written 2026-09-01, from a design conversation with Joy.

`jarvis-app-plan.md` remains the authority for everything it covers — the design
system, the session model, the release channel, phases A–I. This document
extends it, the way §14 did, and where the two conflict **this one is newer and
wins**. `BUILD_NOTES.md` stays the *how*.

**Status: agreed in shape, not yet built.** Nothing here is on the tracker as
work until gw03 has read it, because almost all of it is theirs.

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

- three domains write into **one append-only event stream**
- the dashboard, the summaries and a task's history all read **only** that stream
- a fourth domain later changes nothing downstream

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
| `tasks` | a to-do **has** tasks; it is not one |
| `history` | the event stream, §3 |

### 5.1 A to-do has tasks

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

**Cheaper than feared, and the guarantee is already structural.** gw03 counted
the sites: two `NOT NULL`s to drop, one function to branch (`create_task`, which
unconditionally inserts an occurrence), one explicit `ORDER BY ... NULLS LAST`,
and one new check that a recurrence requires an anchor. The date filters need
nothing — SQL `NULL` comparison excludes undated rows, which is the correct
behaviour, for free. And `overdue_tasks` reads from **occurrences**, not from
`due_at`: an undated task has no occurrence, so it can never be overdue and the
scheduler needs no change at all. §5.2's "cannot fail" is not a rule anyone has
to remember; it falls out of the indirection already shipped in 0001.

**Stale, decided: untouched for 14 days, anchored on `updated_at`.** Not
`created_at`, or a task you looked at yesterday and did not move is stale
tomorrow. Fourteen rather than seven because the summaries are weekly (§7): a
7-day threshold would flag everything the instant each weekly summary was
written, restating "you did not do this last week" as though it were news.
Fourteen means an item has survived **two** weekly reviews without moving, which
is a signal rather than an echo.

### 5.3 Status

Fixed set, not user-defined columns — there is no board to put columns on, and a
fixed set is what lets the dashboard say anything across to-dos.

Proposed: `open` → `doing` → `done`, plus `blocked` and `cancelled`. Deliberately
*not* the task lifecycle (`active`/`awaiting`/`incomplete`), which is about firing
and chasing and means nothing here — and `open` rather than `todo`, because `todo`
now names the domain and `todo.status = todo` reads as a mistake.

**Open** — whether `blocked` earns its place, or is a tag.

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
2. **The event table** + seven inserts beside the existing mutations. Not
   breaking, and nothing to coordinate with the app.
3. **Dashboard v1**, over tasks alone.
4. **Routines.** Template, versions, tracked slots, start-only logging, day view.
5. **To-dos.** Tags, `starts_at`, nullable `due_at`, status, history.
6. **Summaries.** Needs tags from 5 and the scheduler that already exists.
7. **Attachments** — Phase G, returning to scope, last.

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
- **`blocked` as a status, or a tag.** §5.3.
- **Per-week routine overrides.** Agreed as wanted, not yet specified. §4.6.
- **The tracked/scaffold split for every slot in the current routine.** The kinds
  in §4.3 are confirmed in principle and guessed per slot; the guess wants a pass.
- **What the day-end prompt looks like** for unstarted tracked slots. §4.4.
