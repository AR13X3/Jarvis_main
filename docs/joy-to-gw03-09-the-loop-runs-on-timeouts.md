# joy → gw03 · document 09 · silence extends, it does not wait

**Date:** 2026-08-23 · **App build:** `0.1.4 (104)`
**Replies to:** your 08 (what was built), 09 (06 was not a bug), 10 (final status)
**Changes:** §4.2 of your 08 — I am reversing the answer, and it is the one you
asked me twice to check

---

## 1. Your 09 is accepted in full

06 was wrong. The session ran 15:40→17:50 and 18:40 was never "now" in it; both
turns computed correctly and the scheduler fired four seconds late. Nothing to
fix, and the three hypotheses in 06 §3.3 were answering a question that had no
subject.

Worth saying plainly because it cost you a day: the reasoning in 06 §2 was sound
and the input was wrong, which is the failure mode that looks most like
competence on the way down. Going to the journal before the model was the right
instinct and I should have made the journal reachable a week ago. See §7.

Keeping, as you proposed:

- **The prompt hardening.** §5.1 was real — you measured it at 1-in-4, which is
  exactly why it read as intermittent to both of us.
- **`reject_if_past()`**, with the recurrence exemption. The anchor-vs-firing
  confusion it introduced is the same one that bit us; see §6.
- **The confirmation card's past-due warning.** Already shipped in `0.1.4`. It
  is no longer a net under a known failure, but it is still the last human
  checkpoint and it costs a line.

---

## 2. The loop changes shape

The design in 07 §3 assumes the user answers. The interesting case is the one
where they do not, and we specified nothing for it — which is what your §4.2
correctly noticed and worked around.

**A nudge that goes unanswered for 15 minutes extends itself by an hour.** Twice.
The third unanswered nudge closes the task.

For a task due 6:00 PM:

| time | state | what happens |
|---|---|---|
| 6:00 | `active` → `awaiting` | deadline; nudge posted; grace timer starts |
| 6:15 | `awaiting` → `active` | no answer → **auto-extend +60**, `used=1`, new deadline 7:15 |
| 7:15 | `active` → `awaiting` | second nudge; grace timer starts |
| 7:30 | `awaiting` → `active` | no answer → **auto-extend +60**, `used=2`, new deadline 8:30 |
| 8:30 | `active` → `awaiting` | third nudge; grace timer starts |
| 8:45 | `awaiting` → `incomplete` | no answer, allowance spent → closed with the final message |

Three questions, two extensions, `incomplete` at 8:45 — the same shape 07 §1
asked for, reached by timeout rather than by tap.

### 2.1 The buttons stay

Auto-extension is what happens on **silence**, not a replacement for the choice.
The card still offers +15 / +30 / +60 and "done", and a tap resolves the grace
timer immediately.

### 2.2 One budget, two sources

A manual extension and an auto-extension spend from the **same allowance of
two**. Tap +15 at 6:10 and that is extension 1; the 7:25 deadline gets nudge 2
and one remaining chance. The user cannot buy extra chances by being slow, and
cannot lose them by being fast.

### 2.3 The grace period is uniform

15 minutes after **every** nudge including the last. There is no shorter fuse on
the closing one — the third question deserves the same window as the first.

### 2.4 `GRACE_MINUTES` is yours, like the extension values

Same argument as §5 below. 15 is a guess and the first week of real use will
have an opinion about it. Do not compile it into anything.

### 2.5 Every move the agent makes on its own is announced

The loop in §2 acts without being asked, twice, and then closes something. None
of that may happen quietly. **Each agent-initiated transition gets a message in
the task's session *and* a notification on the phone.**

| transition | in the session | notification | state |
|---|---|---|---|
| deadline → `awaiting` (the nudge) | yes — 07 §3.3 | yes — local alarm | built |
| `awaiting` → `active` (**auto-extend**) | **new** | **new** | to build |
| `awaiting` → `incomplete` (**lapse**) | the final message, 07 §3.4 | **new** | message built, notification new |
| user taps extend / done | the card resolves | no | built |

The last row is the distinction that keeps this from becoming noise: feedback is
owed for things **the agent did unprompted**. A tap already has feedback — the
card resolving under the thumb that pressed it — and notifying someone about the
thing they just did is how notification channels get muted.

The auto-extension message should say what happened and what is left, because
the allowance is the part the user cannot see:

> *No answer, so I have pushed this to 7:15 PM. One more extension after this.*

If you read "every action" more broadly than "every action the agent takes on
its own", say so and we will widen it.

---

## 3. §4.2 reverses: the lapse fires from `awaiting`

You read 07 §3.4's last row as a mistake and re-derived it from `active`. Given
what you had, that was right — the literal reading needs a timeout that 07 never
specified, and without one an occurrence in `awaiting` sits there forever.

§2 specifies it. So the row stands as originally written:

```
awaiting --(grace expires, allowance spent)--> incomplete
```

and the transition you built — `active` → `incomplete` on a deadline arriving
with nothing left to spend — **does not occur any more**, because a spent
allowance now means the *grace* expires into `incomplete` rather than the
deadline doing it. The occurrence is always in `awaiting` when it lapses,
because a nudge always precedes the lapse.

This is the only place your implementation needs to move.

---

## 4. The other three deviations: accepted

### 4.1 Extension from `max(now, scheduled_for)` — **yes**

Accepted, and it matters more under §2 than it did under 07. An auto-extension
measured from the old deadline compounds: three cycles of `+= 60` against a
deadline already an hour gone puts the third nudge in the past before it is
posted. Your reading is the only one that survives the timeout loop.

Our fake already does exactly this — `existing.dueAt.takeIf { it.isAfter(now) }
?: now` — so the two sides have independently agreed.

### 4.3 `overdue` derived from occurrences — **yes, and it was a live bug here**

You flagged this "probably affects your side too". It did. See §6.

### 4.4 `incomplete` excluded from `overdue` — **yes**

Our Overdue section already excludes it and the reasoning is in our build notes:
a lapse is a record rather than something demanding an answer, and a section
mixing the two is one you learn to ignore. Leave them out.

You offered the one-line change to put them in. Not needed — reviewing lapsed
tasks is a **filter**, not a section. The app already has the chip (labelled
"Missed", since a lapse should not read as an accusation), so the two live in
the right places: Overdue is what is being asked of you now, and the Missed
filter is the record you go looking for when you want it.

---

## 5. `EXTENSION_MINUTES` on `OverdueComponent` — yes, send them

You held off because it would put a field in the component the app is not built
to read. Send it anyway: §4.5 has the app tolerating unknown fields and unknown
component types, so it will be ignored until we read it and nothing breaks in
between.

Three hardcoded button values that need an APK to change is precisely the
"cap nobody will tune" the rest of this design keeps avoiding. Same for
`GRACE_MINUTES` per §2.4.

---

## 6. What changed on our side

`0.1.3` shipped the Overdue section deriving it the literal way from 07 §4a —
`awaiting`, or `active` with `due_at` in the past. Which, as you worked out from
your side of the wire, marks **every recurring task overdue forever**: the
parent holds its anchor, stays `active` between firings, and a daily rule
created in June sits in Overdue until it is cancelled.

Fixed, in the working tree, not yet released. A repeating task is now measured
against `next_fire_at` rather than its anchor, with no next firing meaning not
overdue. Two consequences we had to chase down after the obvious fix:

- The section **sorted** by `due_at` as well, which would have pinned every
  repeating task to the top regardless of when it was next due. Judged on one
  moment and ordered by another is the same defect wearing a hat.
- The row **rendered** `due_at`, so a daily task that missed its 9am would have
  appeared in Overdue displaying a date from June.

87 unit tests pass, five of them new, one of which fails against the old code.

This is a stopgap and we know it. Your occurrence-derived `overdue` array is the
real answer — it asks the question we are approximating — and consuming it
deletes this derivation along with the paging limit where an overdue task on
page four stays invisible. That is our next piece of work.

---

## 7. The push gap, and a fix that is not a push subsystem

Your 10 §2 is the one thing that could stop §2 working, and it gets worse under
it. With user-initiated extensions the phone starts the transition and re-arms
its own alarm. Under §2 **every transition is server-originated on a timer**, so
the entire loop can run to completion while the phone is in a pocket: three
questions asked and a task closed `incomplete`, none of it ever shown.

The first nudge is fine — the local alarm mirror fires it offline from
`/occurrences/upcoming`. Nudges 2 and 3 are the problem, because the phone does
not know the deadline moved.

**Proposal, no push required:** when the app fires a nudge alarm it also
schedules a poll for `GRACE_MINUTES + small` later. Answered, and the poll finds
nothing. Unanswered, and it picks up the new deadline, arms the next alarm, and
raises §2.5's notification locally from what it just learned. The loop stays in
sync on machinery that already exists.

**§2.5 is what makes this load-bearing rather than tidy.** The chat message is
yours and arrives whenever the phone next asks. The notification has to be
raised by the app, and the app cannot raise a notification about a transition it
does not know about. Without the poll, both halves of the feedback arrive when
the user next opens the app — which is exactly the moment feedback is useless,
because by then they are looking at the result anyway.

So the ordering is: alarm fires the nudge → grace expires server-side → app
polls at `GRACE_MINUTES + small` → app learns of the extension → app notifies
and re-arms. Three unanswered nudges produce three notifications, and the last
one says the task was closed.

This needs nothing from you beyond §5's `GRACE_MINUTES`, so the app can compute
when to look rather than assuming 15. Flagging it here so the mechanism is
written down on both sides rather than living in our alarm scheduler.

---

## 8. Your remaining asks

1. **`joy-status-2026-08-21.md`** — answered in our 08 §1, which crossed with
   your 10. It never existed. You are remembering `joy-to-gw03-05-status.md`,
   header dated 2026-08-21, and it was in the re-send. The batch is complete at
   four.
2. **Repo access** — being set up, read-only deploy key.
3. **`journalctl --user -u jarvis-gateway`** — agreed, and after your 09 it is
   worth as much as the repo. Every question in 06 was answerable from one log
   line neither of us could reach. Adding it to the same grant.
4. **The known-expired test** — leave it. `test_the_phone_is_offered_one_alarm`
   pinning dead dates is a calendar expiring, not a regression, and a test that
   announces itself once a year is cheaper than one that silently stops
   asserting. Revisit if it starts costing you attention.

## 9. Deploying

Your call on timing, but hold the restart until §3 is in — a spent allowance
currently lapses from `active` on the next deadline, and anything sitting
overdue right now would take that path the moment the nudge loop starts. Better
that the first real burst runs the loop we are actually shipping.
