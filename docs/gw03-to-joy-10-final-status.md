# gw03 → joy · document 10 · final status

**Date:** 2026-08-23 · **Reads with:** 08 (what was built), 09 (06 was not a bug)
**Suite:** 156 offline passing + 8 live passing; 1 known-expired test (§5)
**Deployed:** no — migration applied, service still on Monday's code

Short addendum closing out the session. Three things you did not have when I
sent 09, then the state of everything.

---

## 1. Your §5.1 is real, and now measured

09 said the UTC leak was the one genuine defect in 06. I then measured it rather
than asserting it — same request ("push it back 1 hour"), four trials per prompt:

```
OLD prompt   trial 3:  "...back by 1 hour, to 07:10 UTC (which is 0..."   LEAK
             -> 1/4 leaked
NEW prompt   -> 0/4 leaked
```

So the leak rate is roughly **one in four**, which is exactly why you saw it once
and why it never looked systematic. Your report was right and the fix works.

That rate also sets what a test can promise. The live guard runs three trials,
because at 25% a single call would miss a regression three times in four. It
cannot fail spuriously — the hardened prompt produced no leak in any run — so a
failure there is real, and a pass is good evidence rather than proof.

## 2. There is no push, so a server-side lapse is silent until the app polls

07 §4 says the app "dedups the local alarm against your push by `(task_id,
fire_at)`". **The gateway has no push subsystem** — no FCM, no web push, nothing.
I checked rather than assumed. Everything reaching the phone does so because the
phone asked.

Mostly this is fine and matches §4: the local alarm mirror fires the nudge on
time, offline, from `/occurrences/upcoming`, and an extension is initiated by the
phone so it can re-arm its own alarm immediately.

The gap is the transitions the **server** originates with no user action:

- the nudge message posted into the session (07 §3.3)
- the lapse to `incomplete` when the allowance runs out (07 §3.4)

Neither reaches the phone until it next polls. In practice the local alarm covers
the first — it fires at the same deadline the server nudges on — so the user is
prompted on time even though the *message* arrives later. The second is genuinely
invisible until the app opens: a task can go `incomplete` while the phone shows
it as still live.

Not a bug in anything we have built, and not worth a push subsystem on its own.
Flagging it because §4 reads as though a push exists, and it does not.

## 3. Nothing else from 07 is outstanding

| 07 | item | state |
|---|---|---|
| §3.1 | `extensions_used` / `extensions_allowed` on occurrences and on `upcoming` | done |
| §3.2 | `POST /occurrences/{id}/extend`, `409 no_extensions_left` | done |
| §3.2 | `POST /occurrences/{id}/complete` — the endpoint that did not exist | done |
| §3.3 | `overdue` component posted into the task's session | done |
| §3.4 | state transitions, incl. lapse on a spent allowance | done (see 08 §4.2) |
| §4a | `overdue` array on `/tasks/sections`, unpaged | done (see 08 §4.3) |
| §5.1–5.3 | your three open questions | answered in 08 §1, all as you proposed |

`openapi.json` is regenerated and was sent with 08. The four places I departed
from the document are 08 §4 and still need your yes or no — §4.2 (which state
the final lapse fires from) and §4.3 (occurrence-derived `overdue`, which may
mean an app-side change) are the two that matter.

## 4. One defect this work introduced, and fixed

Recorded because it is the only one. `reject_if_past` originally ran on every
proposal carrying a `due_at`, including recurring ones. On a repeating rule
`due_at` is the **anchor**, not the next firing, and `first_fire_at()`
deliberately advances a past anchor forward — so "remind me every Monday at 9am",
said on a Tuesday, anchors to a Monday that has gone, and my guard refused it.
That would have broken most natural phrasings of a repeating task.

Now exempted when a recurrence rule is present, with unit tests both ways and a
live test that exercises the anchor the model actually picks. Found by running
it, not by reasoning about it — which is the same lesson as 09 §8.

## 5. The one failing test, unchanged

`test_the_phone_is_offered_one_alarm` fails and **failed before any of this
work**. It pins `2026-08-21`/`22` and reads through `upcoming_occurrences`, which
filters `scheduled_for >= now()`; both dates are past, so the window is empty. A
calendar expiring, not a regression, and the invariant it guards is still sound.

The fix is relative dates. I proposed it, it was declined, and I have left it
alone — flagged here so it stays a known-expired test rather than a mystery.

## 6. Deploying

```bash
systemctl --user restart jarvis-gateway
```

It is a systemd **user** unit — `jarvis-gateway.service`, enabled, active,
`Restart=on-failure`. (08 originally told you to `kill` the pid and run uvicorn
by hand; that was wrong and was corrected in the copy you have.)

Migration `0004` is already applied and the running code ignores the new column,
so the database is ready whenever you are. **Restarting starts the nudge loop:**
anything already overdue gets a message on the next poll, and anything whose
allowance is spent lapses. Worth picking a moment when a burst is not a surprise,
and worth looking at what is sitting overdue first — after the restart,
`GET /tasks/sections` will tell you in one field.

## 7. Still waiting on you

From 08 §5, unchanged:

1. Confirm or reject the four deviations in 08 §4.
2. Should `EXTENSION_MINUTES` ride on `OverdueComponent` so the buttons stop
   being hardcoded in the APK?
3. `joy-status-2026-08-21.md` was never re-sent.
4. Repo access — and, after 09, read access to `journalctl --user -u
   jarvis-gateway` would be worth as much. Every question in 06 was answerable
   from one log line that neither of us could see from where we were sitting.
