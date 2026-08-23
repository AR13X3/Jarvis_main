# gw03 → joy · document 09 · the "in 2 hours" bug did not happen

**Date:** 2026-08-23 · **Supersedes:** my §2 in document 08, and 06 §1–§3
**Evidence:** gateway journal, `tasks.tasks` row 24, `agent.messages` for its session

I found the original session. **The model computed correctly, the scheduler
fired on time, and there is nothing to fix.** 06 §5.1 is the one real defect in
the document and it is now fixed.

The premise that fails is 06 §1's "Phone local time throughout: **18:40 AEST**".
The session did not happen at 18:40. It started at **15:40** and ran to 17:50.

---

## 1. The transcript, with the times it actually happened at

Straight from `agent.messages`, rendered in Sydney local:

```
15:40 local | user      | Remind me to apply for jobs in 2 hours
15:40 local | assistant | ...reminder set for 2 hours from now (5:40 PM today)
15:41 local | user      | add in description to Apply for the warehouse job again...
15:44 local | user      | add to the description, apply at David Jones Warehouse
17:50 local | user      | push it back 1 hour
17:50 local | assistant | ...new due time 08:40 UTC (which is 6:40 PM AEST)
```

Four turns, as you said. But they span **two hours and ten minutes**, and 18:40
is never "now" in this session — it is the due time the last turn produced.

At 15:40, "in 2 hours" is **17:40**. That is exactly what it proposed.

At 17:50, pushing 17:40 back an hour is **18:40**. That is exactly what it
proposed.

Both turns are right.

## 2. The corrected version of your §2 table

Your arithmetic was sound. One input was wrong.

| | your §2 | actual |
|---|---|---|
| now, at the moment of asking | 18:40 | **15:40** |
| correct answer for "in 2 hours" | 20:40 | **17:40** |
| what was produced | 17:40 | 17:40 |
| verdict | 3 hours stale | **correct** |

You wrote: *"the current time the model is reasoning from was 15:40 AEST — three
hours stale."* The inference was right and the conclusion inverted: it **was**
reasoning from 15:40, because 15:40 was the time. Working back to the model's
base was good debugging; the base just turned out to be correct.

## 3. The gateway log, which timestamps the tool call itself

```
Aug 22 05:40:23 UTC  gateway.agent: tool propose_create
                     args={'title': 'Apply for jobs',
                           'due_at': '2026-08-22T17:40:00', ...}
```

`05:40 UTC` = `15:40 AEST`. The request and the proposal are in the same log
line, two hours apart by design.

## 4. It did not fire immediately either

06 §1 says the scheduler fired on sight because the task was already past due,
and the header showed **Waiting on you** within seconds. The database and the
journal both disagree:

```
task 24  'Apply for jobs'
  created  2026-08-22 15:40 local
  due_at   2026-08-22 18:40 local        (after the 17:50 reschedule)
  occ 16   scheduled 17:40 local  status=cancelled   <- superseded by the reschedule
  occ 17   scheduled 18:40 local  status=incomplete

Aug 22 07:40:04 UTC  gateway.scheduler: fired 1 occurrence(s)
```

`07:40:04 UTC` = `17:40:04 AEST` — four seconds after its due time, two hours
after it was created. Correct to the second.

`occ 16` being `cancelled` is also correct: that is the reschedule superseding
the old firing, which is the bug fixed on 2026-08-20 working as intended.

My guess at how "Waiting on you within seconds" was seen: the task genuinely
was awaiting by the time the app was looked at again — it fired legitimately at
17:40, ten minutes before the "push it back" turn at 17:50. The state was real;
what was wrong was reading it as immediate.

## 5. What IS a real bug in 06, and is fixed

**§5.1 — UTC leaking into user-facing prose.** Visible in the last line above:
"new due time 08:40 UTC (which is 6:40 PM AEST)". You were right that the person
holding the phone should never see that. The prompt now forbids UTC, offsets and
zone abbreviations in prose, and there is a test asserting no user-facing nudge
text can contain `UTC`, `AEST`, `AEDT`, `+10:00`, `+11:00` or `Z`.

**§5.2** dissolves with the rest: "2 hours from now (5:40 PM today)" was not a
sentence contradicting its own number. Both halves were true.

**§6** — your confirmation-card past-due warning. Still worth shipping, but it
is no longer urgent, and it is no longer a net under a known failure. Your call
whether it stays in this release.

## 6. What I did before finding this, and what I am keeping

I reported in document 08 that all three of your §3.3 hypotheses were wrong.
That still holds — clock, offset and per-turn injection are all fine — but I
had the wrong reason. They were not wrong because the fault was in the model.
They were wrong because **there was no fault**.

Before finding the log I ran 20 trials against the live model trying to
reproduce it: old prompt vs hardened prompt, and a variant with a decoy time
planted in conversation history to test whether it anchors on the transcript
instead of the clock. **Zero reproductions, all 20 correct.** At the time I read
that as "intermittent". It was actually the first real evidence that the bug
did not exist, and I should have gone to the journal before writing document 08
rather than after.

Keeping, because they are cheap and correct on their own terms:

- **The prompt hardening** (clock in 12-hour and ISO form, no-UTC rule). The
  no-UTC half fixes a real defect; the rest is harmless.
- **`tools.reject_if_past()`**. It guards a real class of error even though this
  particular instance was not one.

### The guard had a bug of its own

Worth stating plainly, because it is the only defect this whole exercise
actually introduced. The first version ran on **every** proposal carrying a
`due_at`, including recurring ones. On a repeating rule `due_at` is the
**anchor**, not the next firing, and `first_fire_at()` deliberately advances a
past anchor forward. So "remind me every Monday at 9am", said on a Tuesday,
anchors to a Monday that has gone — and my guard refused it.

That would have broken most natural phrasings of a repeating task. Now
exempted when a recurrence rule is present, with a test both ways. Found by
running it, not by reasoning about it.

## 7. Nothing here changes document 07

The follow-up loop, the two endpoints, `overdue` on `/tasks/sections` and the
four deviations in document 08 §4 all stand. Those answer a design ask, not a
bug report. Document 08 §5 still lists what I need from you; the only item that
drops off is any expectation that I am fixing a relative-time fault.

## 8. Suggestion

The thing that made this expensive was not the analysis — your §2 reasoning was
correct and would have found a real bug. It was that the report and the
evidence lived on different machines, so the first available check was a
hypothesis rather than a log line.

`journalctl --user -u jarvis-gateway` on gw03 carries every tool call with its
arguments and a timestamp. For anything time-related that is the first place to
look, and it answers in one line what four hypotheses could not. Another reason
for the repo access in 08 §5.4 — read access to gw03's journal would be worth
as much.
