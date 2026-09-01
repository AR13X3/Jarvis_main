# Every state in the system

Written 2026-09-02, from the **served contract** (`openapi.json`, sha256
`e398ff18e4aa6b33`) and checked against the app's enums one by one — not from
memory and not from the plan.

**Why this file exists.** `OverdueResolution` in the app read
`completed`/`extended`/`lapsed` while the gateway sent
`superseded`/`completed`/`cancelled`/`incomplete`. One value in common. The
three it did not recognise decoded to `null`, which the overdue card reads as
*unanswered* — so answered nudges came back with live buttons and a countdown
that had already run out. **Nothing threw and nothing was logged.** The only
symptom was a card that would not settle, and it was found by Joy using the app.

A vocabulary that lives in two places drifts. This is the list, in one place.

---

## Task status — 5

What has happened to a *reminder*. The thing that fires, chases and can be
extended.

| value | means | terminal? |
|---|---|---|
| `active` | scheduled, nothing wrong | no |
| `awaiting` | overdue and being chased — the follow-up loop is asking | no |
| `completed` | done | **yes** |
| `cancelled` | **you decided not to do it.** A decision, not a failure | **yes** |
| `incomplete` | **the allowance ran out and it never got answered** | no |

**`cancelled` and `incomplete` are the distinction this system exists to keep.**
One is a choice you made; the other is a thing that did not get done. Collapsing
them would make "how am I going" unanswerable — you cannot improve against a
number that counts your deliberate decisions as failures.

`incomplete` is **not terminal**, deliberately: it is the status you most want
to reschedule, and treating it as final makes lapsed reminders unrecoverable.
Only `completed` and `cancelled` lock a task.

*App:* `TaskStatus`. Rendered by `statusStyle()` — `cancelled` struck through
and grey, `incomplete` an outlined amber dot labelled **"Not completed"**.

## Occurrence status — 5

The same five words, one level down: a single *firing* of a task. A recurring
reminder has many occurrences and each resolves on its own — finishing Monday's
gym session must not close the weekly rule.

`active` · `awaiting` · `completed` · `cancelled` · `incomplete`

## Overdue resolution — 4

How one overdue *nudge card* ended. Not the same as the task's status: a task
can be `active` while last night's card reads `incomplete`.

| value | means |
|---|---|
| `superseded` | a later card replaced it — the deadline moved, by you or automatically |
| `completed` | you answered "yes, it's done" |
| `cancelled` | the task was cancelled |
| `incomplete` | the allowance ran out and nobody answered |

`null` means **still asking** — and only then does the card show buttons.

*App:* `OverdueResolution`. **This is the one that was wrong.**

## To-do status — 4

A *to-do* is the thing a reminder is about. It never fires and never chases.

| value | means |
|---|---|
| `open` | not started |
| `doing` | in progress |
| `done` | finished |
| `cancelled` | not doing it |

**`blocked` is deliberately absent** (ruled 2026-09-01, v2 plan §5.3). These
four say *where an item is*; "blocked" says *why it is not moving*, which is a
different question — and `doing → blocked → unblocked` cannot say which value to
return to. If it earns its place it arrives as a separate `blocked_on`.

## Routine slot kind — 4

| value | means | counted? |
|---|---|---|
| `tracked` | startable and measured — Reskill, gym, uni, the evening meetings | **yes** |
| `scaffold` | wake, lunch, shower. Drawn so the day reads as continuous | no |
| `buffer` | deliberately empty — **the win is leaving it alone** | inverted |
| `free` | rest. Not a commitment, not scaffolding, not scored either way | no |

## Routine category class — 3

`committed` · `upkeep` · `free` — the headline split for "where the hours go".

## The rest

| what | values |
|---|---|
| Proposal action | `create` `update` `cancel` `complete` |
| Proposal status | `pending` `confirmed` `rejected` `superseded` |
| Cancel scope | `occurrence` (this firing) · `series` (the whole rule) |
| Message role | `user` `assistant` `tool` |
| Session kind | `task` `general` |
| Summary period | `daily` `weekly` |

---

## Two vocabularies that deliberately do not meet

**`missed` is a routine word, not a task word.** A tracked slot that was never
started is `missed`; a reminder that ran out of chances is `incomplete`. The
dashboard keeps them in separate sections because they are counted differently —
`missed` only within days that carry at least one start, since before anything
is logged every slot looks missed.

The app's task row used to label `incomplete` as "Missed", which borrowed the
routine word for the task meaning. It says **"Not completed"** now.

**Routine categories are not task tags.** Categories (`reskill`, `gym`, `uni`…)
are routine-local and versioned; tags (`Reskill`, `CBAI`, `Uni`) are global and
unversioned, and drive summary subscriptions. "20h on Reskill this week" and
"9 Reskill tasks done" cannot share a dashboard row (§4.5).

---

## Checked

All eight wire enums in the app were compared against the contract on
2026-09-02. Seven matched exactly. `OverdueResolution` did not, and is fixed.

If you add a value on the gateway, add it here and in the app enum. A value the
app does not recognise does **not** throw — `JarvisJson` sets
`coerceInputValues`, so it silently becomes the default, and on a nullable field
the default is `null`.
