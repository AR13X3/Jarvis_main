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
proposes a reminder, you confirm, it fires on time and chases you if you ignore
it. That is a **reminder** app, and the object at the centre of it is called a
task, which is the source of most of the confusion below.

v2 adds three things and renames one:

| | what it is | fails when |
|---|---|---|
| **Reminders** | today's `task`, renamed. A point in time that fires and chases. | past due, extensions spent, not done |
| **Routines** | a repeating weekly plan that partitions the day. Time tracking, not nagging. | a tracked slot is never started |
| **Tasks** | *new*. Start and deadline as separate things, tags, status, checklists, attachments, history. Optionally undated. | dated: as reminders. **undated: cannot fail** |
| **Dashboard** | what got done, what is slipping, what is being avoided | — |
| **Summaries** | daily and weekly, overall and per group, written by the agent | — |

The fourth column is not decoration. Joy's definition of failing is *"not
completing on due time, or even after extensions"* — which is deadline-bound, so
an object with no deadline has nothing to fail against. An undated task is
**stale**, never failed. Getting this wrong puts every undated task permanently
in "what am I missing".

---

## 2. Rename `task` → `reminder`, and do it first

The gateway's `task` is a reminder: one `due_at`, a recurrence, an occurrence
per firing, a follow-up loop with extensions. When real Tasks arrive there will
be two things called task in the routes, the schemas, the database, the agent's
tool names and the app's models.

Do it now, while there is one client, one user and ten releases of history. The
app is the only consumer and it has a serialisation layer, so its half is
mechanical. After real Tasks exist this becomes a migration that has to
disambiguate two meanings of the same word, and it never gets cheaper.

`GET /tasks` → `GET /reminders`, `Task` → `Reminder`, `PatchTaskBody` →
`PatchReminderBody`, and the agent's `find_tasks` / `new_task` tools likewise.
Occurrences keep their name; they are already correct.

**This is the one change that must happen before anything else in this document.**

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

An event is roughly: `at`, `source` (reminder | routine | task), `source_id`,
`verb` (started, completed, missed, lapsed, created, rescheduled, ticked,
cancelled), `category_or_tag`, and a small payload.

**Most of this already exists and is invisible.** The occurrence table has
carried per-instance `status` — `completed`, `incomplete`, `cancelled`,
`awaiting` — since Phase E, and every reminder that has fired since has left a
row. The only read route is `GET /occurrences/upcoming`. There is no way to read
the past.

So the first real step after the rename is small: **expose history**. One route,
plus `completed_at` on the occurrence — today it records *that* something was
completed but not *when*, so completing yesterday's reminder this morning buckets
into the wrong day. That alone is enough for a real dashboard over reminders,
shipped before routines or tasks exist.

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

Thursday runs 10:00–1:00; Friday to Sunday run 8:00–2:00. **A routine day is a
logical day — wake to sleep — and its boundary differs per weekday.** It is
declared on the routine, not derived.

This is `jarvis-app-plan.md` §3.2 in a new costume: deriving a calendar day from
a timestamp is the mistake, and the answer is the same — the server owns the
boundary and the app never computes it.

### 4.3 Slots have a kind, and most are not tickable

Twelve slots a day is roughly eighty a week. Ticking "Lunch" and "Get ready to
leave" daily is noise that buries the five that matter.

- **tracked** — Reskill block, Web dev, Gym, Uni, Reskill meetings, batch cook.
  Startable, counted, this is the adherence data. About five a day.
- **scaffold** — wake, lunch, dinner, shower. Drawn so the day reads as
  continuous. Never startable, never counted.
- **buffer** — Wednesday 5:30–8:30, "keep empty". **The success condition
  inverts**: the win is having left it alone. Tracked like everything else,
  every honest Wednesday would score as a failure.

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

## 5. Tasks

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
| `reminders` | a task **has** reminders; it is not one |
| `history` | the event stream, §3 |

### 5.1 A task has reminders

Joy: *"yes, tasks can have reminders, but we can create tasks without any due
time or date as well."*

So the relationship is one task to many reminders, and a reminder stays the
object that already works — it fires, it chases, it extends, it lapses. A task
does not acquire its own nagging machinery; if two systems chase the same
deadline the user gets buzzed twice for one thing.

### 5.2 Undated tasks

A task with no `due_at` is legal and ordinary. It cannot be overdue and it
cannot fail. It needs its own place in the UI — a backlog — and its own word in
the dashboard. **Stale**, on some threshold of untouched days, is the honest one.

This is the single most likely thing to be got wrong, because every existing
query in the gateway assumes a due date exists.

### 5.3 Status

Fixed set, not user-defined columns — there is no board to put columns on, and a
fixed set is what lets the dashboard say anything across tasks.

Proposed: `todo` → `doing` → `done`, plus `blocked` and `cancelled`. Deliberately
*not* the reminder lifecycle (`active`/`awaiting`/`incomplete`), which is about
firing and chasing and means nothing here.

**Open** — whether `blocked` earns its place, or is a tag.

---

## 6. Dashboard

Reads the event stream and nothing else.

Four words, and they are not synonyms:

- **failed** — had a deadline, extensions spent, not done. Reminders, dated tasks.
- **missed** — a tracked routine slot never started.
- **drifting** — a routine slot started consistently, but not near its plan.
- **stale** — an undated task untouched past a threshold. Cannot fail.

The app renders; the gateway computes every number. No streaks, percentages or
day-bucketing on the client — see §4.2 for why.

Sequenced deliberately so it is useful early: a dashboard over **reminders only**
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

1. **Rename** `task` → `reminder`. Everything else is cheaper afterwards.
2. **Expose occurrence history** + `completed_at`. One route.
3. **Dashboard v1**, over reminders alone. Proves the spine, ships early.
4. **Routines.** Template, versions, tracked slots, start-only logging, day view.
5. **Tasks.** Tags, `starts_at`, nullable `due_at`, status, history.
6. **Summaries.** Needs tags from 5 and the scheduler that already exists.
7. **Attachments** — Phase G, returning to scope, last.

Steps 1, 2, 4, 5, 6 are overwhelmingly **gw03's**: the gateway owns state and the
app renders. joy's share is navigation, the dashboard, the routine day view and
the task detail screen. Real work, but the smaller half.

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
