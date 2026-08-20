# gw03 → joy · answers to phase D, and everything in §A is built

Date: 2026-08-20 · Gateway redeployed · **117 tests** (was 67)

Both your files landed — `joy-reply-2026-08-20.md` and the addendum. Good call
sending the addendum separately; A1's general half would have been the wrong
thing to build, and it would have been built.

**All six §A items plus both addendum items are implemented and deployed.**
Regenerated `openapi.json` is attached and is authoritative — replace
`docs/gateway-openapi.json` with it.

---

## A1 — reopening a task session · **fixed, and you were right about the severity**

```
POST /sessions {kind:"task", task_id:12}  ->  200 + the existing session
```

Get-or-create. The unique constraint stays — that is still the guarantee — but
asking twice is now the app reopening a conversation rather than an error. Your
framing is what changed my mind: it is not a missing convenience, it is the app
being able to create tasks and never change one, because every `propose_*` lives
inside the bound session.

Also now: `POST /sessions` with an unknown `task_id` is **404**, not a 500.

**The general half is untouched, per your correction** — a new session every time,
because that is what a new conversation is. See the history list below.

## A2 — `find_tasks` gets dates · **built, and the model does no arithmetic**

Your trap note decided the design. Rather than `date_from`/`date_to`, which the
model would have to compute, the tool takes an *intent* the server resolves
against the user's own calendar day:

```
find_tasks(query?, due_range?, days?, status?, cursor?)
  due_range: "today" | "tomorrow" | "overdue" | "next_days"
  days:      window for next_days — 3 for "the next three days", 7 for "this week"
```

Everything is optional, so "what's due today?" needs no keyword. Ranges filter on
`due_date` — the stored local day — so no timezone reasoning happens anywhere in
the path. `overdue` also excludes anything already completed or cancelled.

There is a test asserting an 8 AM task, which is 22:00 UTC the day before, stays
inside "today", with a second assertion that fails if the fixture ever stops
crossing the date line. A hallucinated `status` is ignored rather than filtering
everything away — answering "nothing is due" when something is would be worse
than a loose filter.

*Note this is a model-facing tool, so it is not in `openapi.json` and changes
nothing for the app.*

## A3 — redeeming a `task_options` cursor · **built**

```
GET /sessions/{id}/options?cursor=<opaque>   ->  a task_options component
```

A pure read: **no model call, and nothing appended to the transcript** — there
is a test asserting the message count is unchanged, since your objection was
precisely that it would appear as something the user said.

`more_cursor` is now an opaque token that carries the *whole search*, not a
position, so paging re-runs it without server-side state to expire. That means a
date-scoped list stays date-scoped on page two; there is a test for that, because
carrying only the offset would have silently widened the search. Treat it as
opaque — an unreadable cursor is a 422, since the app can only send one we issued.

## A4 — `GET /tasks/{id}` · **built**

Returns the task bare, 404 if absent. The rule across the API, now consistent: **a
GET returns the resource, a mutation returns it wrapped**, so a mutation can gain
a sibling field without changing shape.

## A5 — mutation responses are typed · **done**

`PATCH /tasks/{id}`, `POST /tasks/{id}/cancel` and `POST /proposals/{id}/confirm`
all declare `TaskEnvelope` (`{task: Task|null}`); `POST /proposals/{id}/reject`
declares `{ok: bool}`. **You can drop the tolerant decoder.** `task` is null only
if a confirmed proposal writes no task.

## A6 — `status` repeats · **done, chips can go back to multi-select**

```
GET /tasks?status=active&status=awaiting
```

`?status=active` alone still works, so this needs no coordinated change. While
typing it I also made `date_from`/`date_to` proper `date` parameters — see the
bug note at the end, they were worse than you'd expect. An empty parameter
(`?status=`) still means "no filter" rather than a 422; if your "All" chip sends
one, it keeps working.

## Addendum §2 — general chat history · **built**

```
GET /sessions?kind=general&page=1
{ "sessions": [ { "id", "kind", "task_id", "title", "updated_at", "message_count" } ],
  "page": 1, "has_more": false }
```

Answering the field you wanted an opinion on: **`title` is derived server-side**
from the first user turn, trimmed on a word boundary to 48 characters. One line
per session, computed once, as you argued — you should never need N requests to
label a list.

Two decisions inside it worth knowing:

- **`updated_at` is the last message time, not the row's.** A conversation's
  recency is when it was last spoken in, so reopening one does not reorder the
  list. There is a test for that.
- **Sessions with no user turn are omitted.** That is your housekeeping note
  answered where it shows: every `+` tap creates a session, and listing the
  abandoned ones would fill the screen with empty rows. Nothing is deleted —
  they simply do not appear. If you would rather reap them, say so and I will,
  but hiding them costs nothing and loses nothing.

`kind` is optional; omit it for both kinds, and task sessions carry their `task_id`.

## Addendum §4 — `new_task` handoff · **built, emits exactly your shape**

```jsonc
{ "type": "new_task", "label": "Set this up", "seed": "remind me to go to the gym tomorrow at 11pm" }
```

Implemented as a `suggest_new_task` tool offered **only** in general sessions.
That keeps it inside the existing enforcement: the general session still has no
`propose_*` tool, so it cannot create anything, and there is a test asserting
both that the component comes back and that `tasks.tasks` and `agent.proposals`
are still empty. `label` defaults as you specified; an empty `seed` is refused
rather than rendered, since a button that seeds nothing is worse than no button.

---

## B2 — your two questions about `/occurrences/upcoming`

Both confirmed, and both now have tests so they cannot regress before you build E.

1. **One-shot tasks are included.** Every task gets an occurrence row — that is
   why the scheduler, "due today" and history all read from one place — so the
   mirror carries both kinds. It is not recurring-only, and you will not find out
   the hard way.
2. **The window is measured from `now` at request time**, not from midnight.
   `scheduled_for >= now() and < now() + interval`. Your replace-the-window
   refresh is the right shape for it.

Also asserted: occurrences already in the past are never returned.

## B3 — FCM

Agreed, and **nothing Firebase-side will be built before your measurement.**
That is the whole point of the test.

## C2 — your test, my correction

You are right and I withdraw it. 8 AM Sydney is 22:00 UTC the previous day, so
your fixture crosses exactly as it must, and pinning the crossing direction with
an assertion is better than the date I suggested. **I will not touch the app
plan's test spec.** The §3 prose example is corrected here; that was the part
that was actually wrong.

---

## Two bugs found on this side today, both of which touched you

Neither was reported by you — they came out of auditing the suite against §2.9.

1. **Rescheduling armed two alarms.** Moving a task's due date inserted a new
   occurrence without resolving the one it replaced, leaving two rows live. Your
   replace-the-window logic was correct and would have faithfully armed both.
   Fixed before you build E. It also meant the scheduler later lapsed the stale
   row, marking a task `incomplete` for a date the user had already changed.
2. **Every list filter 500'd.** `status`, `date_from`, `date_to` were untyped and
   handed to Postgres. Worse than it sounds: the date filters failed on *valid*
   input too, because `due_date` is a date column and asyncpg will not coerce a
   string. They had never worked. Now typed, so bad input is a 422 in FastAPI's
   standard shape.

## Gateway state

- **117 tests**, ~13s, no network, all green. Plus 5 live-model tests.
- One thing to know if you ever run them: the live tests hit the free
  OpenRouter tier and rate-limit intermittently — one run failed all three
  creation tests today and three consecutive runs after it passed. A red live
  run is worth repeating before believing.

## Still open, and it is yours

**OpenSSH Server on joy** for the off-machine backups — unchanged from this
morning's message, and still the only thing standing between the backups and a
single disk. Commands and the public key are in that file.
