# joy → gw03 · document 06 · "in 2 hours" resolves to a time in the past

**Date:** 2026-08-22 · **App build:** `0.1.1 (101)`, release
**Severity:** high — it silently creates already-overdue tasks
**Side:** yours. The app sends no clock and no timezone; see §4.

---

## 1. What happened

One session, four turns, on device. Phone local time throughout: **18:40 AEST**
(= 08:40 UTC).

> **user:** Remind me to apply for jobs in 2 hours
>
> **agent:** I've proposed creating a task titled **Apply for jobs** with a
> reminder set for **2 hours from now** (5:40 PM today). Please confirm to
> create it.

5:40 PM is **an hour before the message was sent**, not two hours after it.

The task was created on confirmation and the scheduler fired it immediately,
because it was already past due. The session header showed **Waiting on you**
within seconds of the task existing.

## 2. The arithmetic points at one thing

| | local (AEST) | UTC |
|---|---|---|
| actual now | 18:40 | 08:40 |
| correct answer for "in 2 hours" | 20:40 | 10:40 |
| what was produced | **17:40** | **07:40** |

Not off by two hours in either direction. Off by **three hours, backwards** —
and exactly `15:40 + 2:00`.

**The addition is fine. The base is wrong.** A later turn in the same session
confirms it:

> **user:** push it back 1 hour
>
> **agent:** I've proposed pushing the reminder back by 1 hour — new due time
> 08:40 UTC (which is 6:40 PM AEST). Please confirm.

17:40 → 18:40 is correct, and the UTC↔AEST conversion in that sentence is
correct too. So relative arithmetic against an existing `due_at` works. The only
failing step is resolving **now**.

Which means: **the current time the model is reasoning from was 15:40 AEST /
05:40 UTC — three hours stale.**

## 3. Where we would look first

Ordered by how cheap they are to rule out.

1. **Is "now" injected once per session rather than once per turn?** This is the
   hypothesis that fits best. A timestamp captured at session creation and
   carried in the system prompt would be correct on turn one and drift on every
   turn after — and a session opened around 15:40 would produce exactly this.
   Cheap to test: ask a *brand-new* session "what time is it now?", then ask the
   same question in a session that has been open for an hour.
2. **Is the injected timestamp built with a wrong offset?** Sydney is UTC+10 on
   AEST and UTC+11 on AEDT. Neither is three hours, so a plain offset bug does
   not explain it alone — but a stale value *plus* an offset assumption might.
3. **Is `gw03`'s own clock drifting?** Worth one `timedatectl` before assuming
   anything about the prompt. If the host is three hours behind, this reproduces
   without any prompt bug at all, and it would also quietly skew the scheduler.

## 4. Why this cannot be the app

Stated plainly so it can be ruled out in one read rather than investigated:

- **The app sends no clock and no timezone.** Not a header, not a body field —
  `grep` for `ZoneId`, `TimeZone`, `offset` across the network layer returns
  nothing. The only headers we add are `Authorization` and `X-App-Version`.
- **The app never computes a due time.** Plan §3.2, and it holds: `due_at` and
  `due_date` are rendered exactly as received.
- The card rendered `5:40 pm` because that is what `due_at` said. Displaying it
  correctly is the app doing its job; the value was already wrong on arrival.
- Nothing auto-confirms. Every card in the transcript was tapped by hand.

## 5. Two smaller things from the same transcript

**5.1 UTC is leaking into user-facing prose.** "new due time 08:40 UTC (which is
6:40 PM AEST)" — the person holding the phone should never see a UTC timestamp
or a timezone abbreviation. Everything they see is already local by the time it
reaches them. Worth a line in the prompt.

**5.2 The prose contradicted its own number.** "a reminder set for **2 hours
from now** (5:40 PM today)" states the correct intent and the wrong literal in
one sentence. Whatever fixes §2 probably fixes this, but it is worth noting that
the model *said* the right thing and *computed* the wrong one — which is why
this got confirmed. The sentence read as correct.

## 6. What we are adding on our side

Not a fix — the value still has to be right — but a net under it.

The confirmation card is the last human checkpoint before anything is written
(plan §4.5), so it will now say plainly when a proposed reminder is **already in
the past**. A card reading "Today, 5:40 pm · this time has already passed" is
one nobody confirms by accident.

To be explicit, since it sits next to a rule we do not break: this compares two
instants, which is timezone-independent. It does **not** derive a calendar day
from a timestamp, which is what §3.2 forbids and what we still leave to you.
