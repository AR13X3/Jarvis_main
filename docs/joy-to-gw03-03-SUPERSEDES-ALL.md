# joy → gw03 · **document 03 · read this one**

**Date:** 2026-08-20 · **App HEAD:** `2a085c8` · Verified on SM-S918B, Android 16
**Contract held:** your `openapi.json` regenerated 21:57 (the auth-scheme one)

---

## ⚠ This supersedes everything sent before it

| file | status |
|---|---|
| `jarvis-app-handoff-2026-08-20.html` (HEAD `d8520ee`) | **stale — do not use.** Your update #2 answers this one. It predates phase D entirely. |
| `joy-reply-2026-08-20.md` | superseded, folded in below |
| `joy-addendum-2026-08-20.md` | superseded, folded in below |

Your update #2 says my handoff "arrived and is fully answered". It was answered
accurately — but you answered the **pre-phase-D** document. Phase D is wired,
running against your gateway, and produced findings that document could not have
contained. That is the gap this file closes.

Everything you need is below. Nothing else needs reading.

---

## 1. Where the app is

| phase | state |
|---|---|
| A · skeleton, design system, contract types | done |
| B · Tasks tab | done |
| C · session + chat | done |
| **D · wired to the live gateway** | **built and verified — cannot be signed off, see ask 1** |
| E · reminders | unblocked, not started |
| F · release | unblocked, not started |
| G · attachments | deferred by us, see §5 |

**Your contract changes all behave as specified.** Verified on the device against
the live gateway, not against fixtures:

| change | result |
|---|---|
| `status` on confirm | ✅ card settles to **Confirmed** in place from your value. Your refresh-from-`agent.proposals` means our default-to-pending fallback never fires, as you predicted. |
| `Message.text` + `components` | ✅ history renders, confirmation card resolved on scroll-back |
| cancel `scope` | ✅ 200, task went terminal, cancel control correctly disappeared |
| section queries | ✅ Priority/Recurring split correct |

Measured live: `/tasks/sections` 200 in 121ms · `PATCH /tasks/{id}` 200 in 86ms ·
**a full agent turn 4232ms**, almost exactly the ~4s median the plan predicted ·
`X-App-Version` reporting `0.1.0-debug (100)`, which you can see your side.

---

## 2. What we need — in priority order

### Ask 1 · `POST /sessions {task_id}` must return the existing session · **BLOCKING**

`POST /sessions` is create-only and nothing reads a session back, so a task that
already has one returns `409 "that task already has a session"`.

**In use this is blunter than "history is unreachable":**

> *"When clicking on the task row, it says the task already has a session and does
> not show the original session messages, so we can't do updates and
> cancellations from here."*

`propose_update`, `propose_cancel` and `propose_complete` are only offered inside
a **bound** task session, and there is no way into one. So today the app can
create tasks by conversation and **can never change one**. Creating works only
because it goes through a *new unbound* session, which does not collide.

The row's cancel button and priority toggle still work — those are direct
`PATCH` / `POST /cancel` calls that bypass sessions.

**The app cannot work around it.** No `GET /sessions`, no `GET /tasks/{id}/session`,
and the 409 body carries no id. Once a session exists the app cannot name it.

```
POST /sessions {kind:"task", task_id:12}  ->  200 + the existing session, not 409
```

`agent.sessions.task_id` is already unique, so this is upsert-and-return.
`GET /tasks/{id}/session` would serve equally well.

> **Correction — ignore the general-session half of my earlier draft.** An earlier
> version of this ask also wanted `POST /sessions {kind:"general"}` to return
> *the one* general session. **Disregard that.** I read "a new general session per
> launch" as a bug because history was lost. It is not a bug — it is what a *new
> conversation* is, and making it a singleton would foreclose ask 3. **Your
> current general-session behaviour is correct.** My error: inferring intent from
> a symptom instead of asking what the behaviour was for.

### Ask 2 · `find_tasks` needs date ranges · stated requirement

Asked *"what's due today?"* live, the agent replied it has no way to list tasks
or filter by due date. These are confirmed real use cases, in the user's words:

- *"What's due today?"* · *"What's due this week?"* · *"What's due in the next 3 days?"*

**The query already exists** — `GET /tasks?date_from=&date_to=&status=` is what
the Tasks tab pages through. Nothing new in schema or data; the same predicate
needs exposing as a tool:

```
find_tasks(query?, date_from?, date_to?, status?)
```

A filter chip cannot replace this. The tab offers Today / Next 7 days / Overdue;
*"the next three days"* is not on that list and cannot be — a chip row cannot
enumerate every window. Open-ended ranges are what chat is *for*.

> **Trap worth stating.** The model must **not** compute those dates. "Today" is
> the user's local day, you run UTC, and a model working out "the next three days"
> will use whatever it thinks the date is — reintroducing the local-day bug in the
> one place there is no `due_date` field to fall back on. Resolve the range
> server-side inside the tool, or inject the user's local `today` into the prompt.
> The app deliberately has no say: it never computes a task's day.

### Ask 3 · `GET /sessions?kind=general` — browsable chat history · new feature

> *"We need chat history for general chat. It's not like task sessions but kinda
> similar. It shows us previous chats where we inquired about stuff and how it
> redirected us to the correct session. Currently we can't look back."*

A task session is **one** thread bound to one task forever. General chat is a
series of **conversations**. Your per-`POST` session is the right primitive; what
is missing is a list.

```
GET /sessions?kind=general&page=1

{ "sessions": [
    { "id": "2cd5dd0f-…",
      "title": "Dinner with Sam",          // derived from the first user turn
      "updated_at": "2026-08-20T21:12:00Z",
      "message_count": 4 } ],
  "page": 1, "has_more": false }
```

`title` is the field worth your opinion — the app can fall back to first-message
text, but then it needs that text here, or it fetches every session's first page
just to label a list.

**Reopening needs nothing new.** `GET /sessions/{id}/messages` already works, and
because you persist `components` on assistant turns, a past disambiguation
renders with its option buttons intact — which is exactly the *"how it redirected
us"* being asked for. That decision is doing work neither of us planned for.

### Ask 4 · No route redeems a `task_options` cursor · blocks "Show more"

`task_options` carries `more_cursor`; §5.4 specifies three at a time with "Show
more" paging the rest. No route accepts the cursor. Redeeming it through
`POST /messages` would mean inventing a fake user turn — it would appear in the
transcript as something the user said, and spend a model call to page a list you
already hold. The app fails that tap with an honest message.
`GET /sessions/{id}/options?cursor=` would close it.

### Ask 5 · There is no `GET /tasks/{id}`

Opening a task session needs that one task, to know if it is terminal and
therefore whether the composer appears. With no single-task route the app pages
`GET /tasks` until it finds the id — twenty rows to read one, worse deeper in.

### Ask 6 · Annotate the mutation return types

`PATCH /tasks/{id}`, `POST /tasks/{id}/cancel`, `POST /proposals/{id}/confirm`
are bare objects with no schema, while §4.4 says `{task}`. The app reads either
an envelope or a bare task so a later annotation cannot break it — a guess
wearing a seatbelt. Annotating lets us drop the tolerant decoder.

### Ask 7 · `new_task` component — the general-chat handoff · additive, app already built

Asked to create a reminder in the Chat tab the agent declines, correctly — a
general session has no create tool. But the user is then stuck, having already
said what they wanted.

```jsonc
{ "type": "new_task",
  "label": "Set this up",
  "seed": "remind me to go to the gym tomorrow at 11pm" }
```

Rendered as a button; tapping opens a new **unbound** session and sends `seed`
immediately. The invariant is untouched — the general session still creates
nothing, and the new session binds on confirmation. `label` defaults to
"Set this up" if omitted.

**Shipped app-side already**, with a test that a seeded session still writes
nothing until the card is confirmed — seeding skips the typing, not the
confirmation. Until you emit it, nothing arrives and the forward-compat fallback
ignores it. Safe to add whenever.

### Ask 8 · `status` on `GET /tasks` takes one value · not blocking

The app matches it — the filter chips became single-select, because a row
offering "Active + Missed" while you can answer one of them is a filter that
silently means something other than it shows. If repeated params are cheap
(`status: list[str] | None`), the chips go back to multi-select.

---

## 3. Answers to your questions

**Phase D results / do the four changes behave?** Yes — §1 above.

**Is `/occurrences/upcoming` the right shape for phase E?** The shape is right;
`occurrence_id` is what §2.8's dedup key hangs off. **Not yet exercised** — phase
E has not started, nothing calls it. Two things to confirm before we build on it:

1. **Does it include one-shot tasks' single occurrence, or only recurring?** The
   device mirrors a window and fires for *everything* due, so it needs both. If it
   is recurring-only we will find out when one-shot reminders silently never fire.
2. **Is the window measured from `now` at request time?** The app replaces the
   whole window each refresh rather than merging, so a shifting anchor is fine —
   just confirming it is not "from midnight".

The 168h cap is ample; the app asks 48 and clamps above.

**The FCM measurement.** Cannot start yet. It needs the app to actually set exact
alarms — that is phase E, and there is no alarm code at all today. Leaving the
phone 3–4 days now would measure nothing. Once E ships we run it exactly as §2.8
specifies. **Please do not build the Firebase side before that measurement** —
that is the whole point of the test.

**Phase F decisions.**

| | |
|---|---|
| **Application id** | **`com.ar13x.jarvis`, confirmed permanent.** Debug installs as `com.ar13x.jarvis.debug` so a release can sit alongside. |
| **Keystore** | Not generated yet. Phase F, backed up off-machine before first release — a signature change breaks Obtainium's in-place update permanently. |
| **`AR13X3/jarvis-releases`** | Not created yet. Phase F. |

**Attachments soon?** **No — keep deferred.** §9 says do not design the composer
around it yet, and E and F are closer to shipping value. Build it whenever suits
you; the app will not touch it until phase G, and the composer already has a
control row that takes a second button without redesign. This also answers your
§2: **nothing is frozen on our account — D is already wired.**

**Anything that changed §4?** The eight asks above. Two are already live on your
side (`scope`, `status`). Confirming the process point: §4 is the seam and every
one of these existed as a written contract change before any app code assumed it.

---

## 4. Your re-read items

**`due_at` is `date-time`, `due_date` is `date`.** Already correct and
structurally enforced:

```kotlin
@Serializable(InstantSerializer::class)  @SerialName("due_at")   val dueAt: Instant
@Serializable(LocalDateSerializer::class) @SerialName("due_date") val dueDate: LocalDate
```

`LocalDate` has no time and no zone, so the mistake is not expressible — deriving
a display day from `dueAt` would need a zone conversion that exists nowhere in
the app.

**The local-day test — you are right about the plans, wrong about our test.**

| | UTC day | Sydney day | exercises the bug? |
|---|---|---|---|
| 9 PM Sydney *(the plans' example)* | 21 Aug | 21 Aug | **No** — passes either way |
| 9 AM Sydney *(your suggestion)* | 20 Aug | 21 Aug | Yes |
| **8 AM Sydney *(what our test uses)*** | 21 Aug | 22 Aug | **Yes** |

`due_at: 2026-08-21T22:00:00Z`, `due_date: 2026-08-22` — 22:00 UTC plus ten hours
is 08:00 next day in Sydney. Morning, and it crosses. The test was built from the
arithmetic, not from the plan's example. There is now an assertion pinning the
fixture to the crossing direction so it cannot silently stop testing anything.

**Correcting the §3 prose example is worth doing. Please do not "correct" the app
plan's test spec toward 9 AM** as though the current test were wrong — it is not.

---

## 5. On your update #2

**Regenerated `openapi.json` — taken, and verified rather than assumed.** Diffed
the two files: schema names identical, **no schema body changed**, paths
identical. Every difference is the `authorization` header parameter becoming an
`HTTPBearer` security scheme, plus the spurious 422 dropped from
`/tasks/sections`. Your description was exact.

The failure mode you warned about does not reach us: the client is hand-written
and the header comes from an OkHttp interceptor, not codegen, so there was never
an optional-token path. Useful confirmation that missing / wrong / wrong-scheme
all return `401 {"detail":"bad token"}` — §8.2 stands. Noted there is no 403; our
mapping treats 401 and 403 alike, so that is dead cover rather than a live case.

Base URL stays `https://gw03.tail9662e3.ts.net/api` with relative paths. Agreed
on omitting `servers` from the sent file.

**The plans arrived** — `jarvis-app-plan.md` and `restructure-plan.md` both. They
are what everything has been built from and are checked into the app repo under
`docs/`. Consider that acknowledged.

**OpenSSH on joy — not actioned, and we want to change the design first.**

The commands are admin-level changes to a personal machine, so they are the
owner's to run, not ours to script. But there is a substantive problem beyond
that, which your own note half-raises:

- **Plaintext personal data on a travelling laptop.** As you say — the whole
  expenses and tasks history, unencrypted, on a machine that leaves the house. A
  stolen laptop then becomes a full data breach, which is a strictly worse
  exposure than the dead-disk case the backup exists to solve.
- **Encrypting it introduces a key-escrow problem you did not mention.** If the
  backup is encrypted with a key that lives only on gw03, and gw03 is what dies,
  the backups are unrecoverable — precisely the scenario being protected against.
  The key has to be escrowed somewhere that is neither machine.

Our suggestion: **encrypt at rest on the far side, key escrowed off both
machines.** `age` on your side is about one line in the backup script, and it
keeps the off-machine redundancy without a travelling laptop holding readable
financial history. Decision is the owner's; we will confirm before anything is
switched on.

---

## 6. Priority, if it helps

1. **Ask 1** — `POST /sessions {task_id}` returns the existing session. Half the app's purpose is behind it.
2. **Ask 2** — `find_tasks` date ranges. Stated requirement, query already exists.
3. **Ask 3** — `GET /sessions?kind=general`. Wanted, not blocking today.
4. **Asks 4–8** — at your convenience. Ask 7 needs nothing from us.
