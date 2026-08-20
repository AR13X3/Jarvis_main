# joy → gw03 · document 04 · `find_tasks` results need status

**Date:** 2026-08-21 · **App HEAD:** `e0b706d`
Follows document 03. Everything there still stands; this adds one item and is
the only thing in it.

---

## The problem, from real use

Asked *"What's due today?"* the agent answered:

```
You have 4 tasks due today (Sydney time):

1. Check the alarm  – due 01:56 AM
2. Start work       – due 02:06 AM
3. Test the alarm   – due 09:00 AM
4. Go to the gym    – due 11:30 PM
```

Two of those were **already completed**. Nothing in the answer says so.

The user's words: *"it mentions all tasks today even if it's completed. It should
specify which are complete and which are left."*

That is the right instinct — the question *"what's due today"* is really *"what
do I still have to do today"*, and an answer that silently mixes finished work
into the list is worse than no answer, because it reads as authoritative.

---

## Two halves. One is ours and is done.

### 1. The option buttons — ours, fixed, but it needs your field

`TaskOption` carries `task_id`, `title`, `due_at` and nothing else, so the
buttons under that answer had no status to show either.

The app now renders status on them in the same visual language as a task row —
muted for done, struck through for cancelled, an outlined ring for missed, with
the label beside the date (`Completed · Today, 1:56 am`).

**Please add `status` to `TaskOption`:**

```jsonc
{ "task_id": 12, "title": "Go to the gym",
  "due_at": "2026-08-21T13:30:00Z",
  "status": "active" }
```

Until it arrives, the app looks each one up via `GET /tasks/{id}` — at most three
per page, each fetched once, and **never for an option that already carries a
status**. So it costs you a handful of cheap reads today and exactly zero the day
you send the field. Nothing needs removing on our side when you do.

Absent is treated as *unknown*, not as `active`: guessing "outstanding" for a
task that might be finished is the error being fixed.

### 2. The prose — yours, and the part that actually matters

The numbered list above is the model's own text. **No app change can reach it.**
Whatever the buttons show, the sentence still says four tasks are due today when
two are done.

Worth noting you already got this exactly right one layer down: `overdue`
deliberately means *past and still open*, so a task completed last week is not
overdue. The same care applied to `due_range=today` is what is missing.

Three ways, in our order of preference:

1. **Return status in the tool result and tell the model to state it.** Most
   flexible — it can then answer "two left, two already done" naturally, which is
   what a person would say.
2. **Split the result** into outstanding and completed, so the model has the
   grouping handed to it rather than having to notice.
3. **Default `due_range` queries to open tasks**, with completed ones only on
   request. Cleanest answer, but it loses the "you already did this" signal that
   is genuinely useful at the end of a day.

We would take 1 or 2. Either way the tool result needs to carry status — which is
the same field as item 1, so both halves are one change on your side.

---

## Nothing else has changed

Still outstanding from document 03: `GET /sessions?kind=general` (chat history)
and multi-value `status` on `GET /tasks`. Neither blocks anything.

Phase E is built and verified on the device — an exact alarm fired on time on a
locked, screen-off Samsung, and reminders now take over the screen rather than
waiting to be noticed. The §2.8 FCM measurement can start whenever the owner is
ready; nothing Firebase-side should be built before it.
