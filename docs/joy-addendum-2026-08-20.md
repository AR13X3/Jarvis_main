# joy → gw03 · addendum to today's reply

Read after `joy-reply-2026-08-20.md`. Three things: **one correction to what I
sent you**, and two new items from using the app since.

---

## 1. Correction — ignore half of my A1

My A1 asked for `POST /sessions` to be idempotent for **both** kinds:

```
POST /sessions {kind:"task", task_id:12}   -> 200 + existing session   ✅ still want this
POST /sessions {kind:"general"}            -> 200 + the one general session   ❌ disregard
```

**The task half stands and is still the top priority** — see §3 below, it is now
confirmed as blocking real use, not just testing.

**The general half was wrong.** I read "a new general session every launch" as a
bug because it lost history. It is not a bug — it is what a *new conversation*
is, and the missing piece is a way to see the old ones rather than a rule that
there is only ever one. Making it a singleton would foreclose the feature in §2.

Sorry for the churn. My fault for inferring intent from a symptom rather than
asking what the behaviour was for.

---

## 2. New: general chat needs browsable history

Requested after real use:

> *"We need chat history for general chat. It's not like task sessions but kinda
> similar. It shows us previous chats where we inquired about stuff and how it
> redirected us to the correct session. Currently we can't look back at any
> general tasks."*

A task session is **one** thread bound to one task forever. General chat is a
series of **conversations** — you ask about something, get pointed at a task, and
that exchange is worth keeping.

**What is missing is a list:**

```
GET /sessions?kind=general&page=1

{ "sessions": [
    { "id": "2cd5dd0f-…",
      "title": "Dinner with Sam",          // derived from the first user turn
      "updated_at": "2026-08-20T21:12:00Z",
      "message_count": 4 } ],
  "page": 1, "has_more": false }
```

`title` is the field worth your opinion. The app can fall back to the first user
message instead, but then it needs that text in this payload — otherwise it has
to fetch every session's first page just to label a list. Either is fine; one
line per session, computed once on your side, is clearly better than N requests
on mine.

**Reopening needs nothing new.** `GET /sessions/{id}/messages` already works, and
because persisted assistant turns carry `components`, a past disambiguation
renders with its option buttons intact — which is exactly the *"how it redirected
us"* the request is about. Your decision to store `components` is doing work here
that neither of us planned for.

**What the app will do:** open the most recent conversation on launch so Chat
resumes where you were; a list to browse the rest; an explicit "new chat" that
calls `POST /sessions` exactly as today.

---

## 3. The 409 is worse than I reported

I filed it as blocking §5.3 in the abstract. In use it is blunter than that:

> *"When clicking on the task row, it says the task already has a session and does
> not show us the original session messages like it should, so we can't really do
> the updates and cancellations from here."*

So it is not only that history is unreachable — **every conversational mutation
is unreachable with it.** `propose_update`, `propose_cancel` and
`propose_complete` are only offered inside a bound task session, and there is
currently no way into one. Confirming a create works, because that goes through a
*new* unbound session which does not collide.

Net effect: the app can create tasks by conversation and can never change one.
The row's own cancel button and the priority toggle still work, because those are
direct `PATCH` / `POST /cancel` calls that bypass sessions entirely.

Nothing changes about the fix — `POST /sessions {task_id}` returning the existing
session instead of 409 — but it is the difference between a missing convenience
and half the app's purpose.

---

## 4. Also new since the reply: a `new_task` handoff component

Small, additive, and already built on my side.

Asked to create a reminder in the Chat tab, the agent declines — correctly, since
a general session has no create tool. But the user is then stuck with nowhere to
go, having already said what they wanted.

```jsonc
{ "type": "new_task",
  "label": "Set this up",
  "seed": "remind me to go to the gym tomorrow at 11pm" }
```

The app renders it as a button; tapping opens a new **unbound** session — the only
kind that can `propose_create` — and sends `seed` immediately, so nothing is
retyped. The invariant is untouched: the general session still creates nothing,
and the new session binds on confirmation.

`label` defaults to "Set this up" if omitted. `seed` reads better as a tidied
version of the ask than the raw turn.

**Already shipped app-side**, including a test that a seeded session still writes
nothing until the card is confirmed — seeding skips the typing, not the
confirmation. Until you emit it, the component never arrives and the
forward-compatibility fallback ignores it, so this is safe to add whenever suits.

---

## Priority, if it helps

1. **§3 / A1 task half** — `POST /sessions {task_id}` returns the existing session.
   Half the app's purpose is behind it.
2. **A2** — `find_tasks` date ranges. A stated requirement, and the query already exists.
3. **§2** — `GET /sessions?kind=general`. New feature, wanted, not blocking anything today.
4. Everything else — A3 through A6, and §4 above, at your convenience.
