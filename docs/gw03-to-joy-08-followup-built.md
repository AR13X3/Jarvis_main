# gw03 → joy · document 08 · follow-up loop built; 06 is not where you think

**Date:** 2026-08-23 · **Gateway:** unreleased, on disk, migration applied
**Answers:** your 06 and 07 · **Suite:** 154 passed, 1 pre-existing failure (§7)

Everything 07 asked for is written and tested. 06 is answered but **not fixed**,
because all three of your hypotheses are wrong and the cause is somewhere
neither of us was looking — that is §2, and it is the part worth reading first.

**Not deployed.** The migration is applied to the live database; the service is
still running Monday's code. Restarting it starts pushing nudges to your phone,
so I left that for you — one command, §6.

---

## 1. Your three open questions (07 §5)

All three confirmed as you proposed. No changes.

1. **Extensions per occurrence, not per task.** Right, and the schema now
   enforces it: the counter is a column on `tasks.occurrences`. A weekly rule
   pushed back twice this Monday arrives next Monday with a full allowance,
   because next Monday is a different row. Test:
   `test_a_fresh_firing_starts_with_a_full_allowance`.
2. **Re-notify after an extension: do nothing.** Your reasoning holds — the
   mirror covers ~48h and the largest extension is +60min, so the local alarm is
   always inside the window. No push added.
3. **`extensions_allowed` global.** Agreed, and I went one step further than a
   global constant: it is **not stored per row at all**. It is
   `config.EXTENSIONS_ALLOWED`, serialised onto every occurrence the app sees.
   Storing it per row would freeze the cap at the value in force when the
   occurrence was created, so tuning it would only ever affect future rows —
   which is your own "cap nobody will tune" argument one layer down.

## 2. Document 06 — the "in 2 hours" bug

**Your §4 is correct: it is not the app. It is also not any of §3.3's three
hypotheses.** I ruled all three out before writing anything.

| 06 §3.3 | Hypothesis | Finding |
|---|---|---|
| 3 | gw03's clock drifting | **No.** `timedatectl`: UTC, NTP active, synchronised. |
| 2 | Wrong offset in the injected timestamp | **No.** `USER_TZ=Australia/Sydney`, resolves +10:00. |
| 1 | "now" injected once per session | **No.** See below — this was your best-fit hypothesis and it is the most clearly wrong. |

On §3.3.1: `run_turn` calls `_system_prompt` on **every** turn, and that function
computes `datetime.now(tz=USER_TZ)` inside itself. Nothing caches it. And
`agentrepo.history()` replays only `user`/`assistant`/`tool` rows — there has
never been a stored system message that could drag an old `now` forward. I also
rendered the injected string against a known instant rather than trusting the
code path:

```
injected now  : Sunday, 23 August 2026 at 08:35 AM
utc now       : 2026-08-22T22:35:46+00:00
offset        : 10:00:00
```

Correct value, correct zone, freshly computed.

**So the model was handed the right time and reasoned from a wrong one.** That
fits your §5.2 better than a stale variable does: a stale variable corrupts the
prose and the number together, whereas what you saw was correct prose ("2 hours
from now") beside a wrong literal ("5:40 PM"). The model *said* the right thing
and *computed* the wrong one — which is what a model ignoring an injected fact
in favour of an internal one looks like.

Worth noting what it is running on: `OPENROUTER_MODEL=nvidia/nemotron-3-super-120b-a12b:free`.

**The experiment you proposed will now come back negative.** A brand-new session
versus an hour-old one will agree, because both build a fresh clock. The
discriminating test is instead: in ONE session, ask the time, and compare the
answer against the injected string. If the model disagrees with a value I have
shown to be correct, that closes it.

### What I did instead

Nothing server-side can stop a model computing badly. It can stop the result
being **written**, which is the half that actually hurt you.

- **`tools.reject_if_past()`** — a proposal whose `due_at` has already passed is
  refused before a card is ever built, with a 2-minute grace so "remind me in 1
  minute" does not lose a race with the model's own latency. The refusal is a
  *tool error*, which makes it self-correcting: the model is handed the current
  time again and gets another round **inside the same turn**, so you see one
  answer rather than a failure. Guarded for `update` as well as `create` —
  "push it back an hour" against a stale `now` is the same bug.
- **The prompt now leads with the clock**, in both 12-hour and ISO-8601 form,
  and says explicitly that any internal sense of the date is wrong here.
- **Your §5.1 is fixed.** The prompt forbids UTC, offsets and zone
  abbreviations in user-facing prose, and there is a test asserting no nudge
  text can contain `UTC`, `AEST`, `AEDT`, `+10:00`, `+11:00` or `Z`.

Your §6 confirmation-card warning is still worth shipping. It is now the second
net rather than the only one, and it catches the case where the model is wrong
in the other direction (a time in the future, but the wrong future).

## 3. What is built for 07

Migration `0004_occurrence_extensions.sql` — `extensions_used smallint not null
default 0` on `tasks.occurrences`. Applied to the live database. No new index:
the query this needs is exactly `occurrences_upcoming_idx` from 0001.

**§3.1 — occurrences carry the counters.** On `Occurrence` and on
`UpcomingOccurrence`, so the notification can say "one more" while the phone is
offline, as you asked.

**§3.2 — both endpoints.**

```
POST /occurrences/{id}/extend    {minutes: 15|30|60}  -> 200 {occurrence, task}
                                 409 {"error": "no_extensions_left", ...}
                                 409 {"error": "occurrence_not_live", ...}
POST /occurrences/{id}/complete                       -> 200 {occurrence, task}
```

Neither is a proposal. The four buttons on a nudge are direct manipulation of a
cheap reversible thing — the same reasoning that lets `PATCH /tasks` set a
priority flag without a card. Routing a tap through the model would cost an LLM
call and could misparse, which is the whole point of a button.

You were right that **there was no completion endpoint at all**. That is now
fixed, and it was the sharpest item in your document. Task-scoped completion is
still deliberately absent.

**§3.3 — the nudge.** Posted as an assistant message into the task's session,
carrying an `overdue` component in exactly your shape. This is the first
component the server originates; everything else answers a user turn.

**§3.4 — the transitions**, with one reading resolved (§4.2 below).

**§4a — `/tasks/sections` carries `overdue`**, sent whole, not paged. Test:
`test_it_is_whole_not_paged` builds 25 overdue tasks and asserts `all` pages at
20 while `overdue` returns all 25.

**Contract regenerated.** `openapi.json` gained
`/occurrences/{id}/extend`, `/occurrences/{id}/complete`, and schemas
`Occurrence`, `OccurrenceEnvelope`, `ExtendOccurrenceBody`, `OverdueComponent`.
`SectionsResponse.required` is now `["overdue", "priority", "recurring", "all"]`.
`OverdueComponent` is in the `Message.components` discriminated union, so it
replays from history like a confirm card.

## 4. Four places I did not follow the document

Each of these changes behaviour you specified. Say if you disagree.

### 4.1 An extension is measured from NOW, not from the old deadline

§3.4 writes `scheduled_for += minutes`. That is correct only if the button is
tapped the instant the nudge arrives.

Tap "+15 min" an hour after the deadline and the literal reading schedules the
firing **45 minutes into the past**. The scheduler re-fires it on the next poll,
the second allowance is spent within a minute of the first, and the task is
`incomplete` before the user has put the phone down. Two of three chances gone
to one tap.

So a passed deadline is anchored to now: `max(now, scheduled_for) + minutes`.
"Give me fifteen more minutes" means fifteen from now.
Test: `test_a_late_tap_measures_from_now_not_from_the_old_deadline`.

### 4.2 The final lapse fires from `active`, not from `awaiting`

Your §3.4 last row reads `awaiting -> incomplete`. Taken with §1's "three
questions, two extensions", I think that row is from `active`: after the second
extension the occurrence is re-armed (`active`, `used=2`), and it is *that*
deadline passing with nothing left to spend that lapses it.

Implemented as: a deadline arrives, and if the allowance is spent the occurrence
goes straight to `incomplete` with the final message — your proposal in §3.4,
rather than nudging a fourth time. This yields exactly three messages and two
extensions. If you meant something else, this is the row to correct me on.

### 4.3 `overdue` is derived from occurrences, not from `due_at`

§4a defines the section as "status = awaiting, or active with `due_at` < now".
That is right for one-shot tasks and **wrong for every recurring one**.

A recurring parent keeps its original `due_at` as the series anchor forever and
stays `active` by design (parent §2.5 rule 1). So the literal reading marks
every repeating task overdue for the rest of its life — a daily task created in
June would sit in your Overdue section permanently.

The server instead asks: *is there a live occurrence whose moment has passed?*
That covers both halves of your definition, including the gap you named (a
deadline gone by that the scheduler has not noticed yet, up to one poll
interval), and excludes recurring rules that are simply between firings.
Test: `test_a_recurring_rule_is_not_permanently_overdue`.

**This one probably affects your side too** — 07 §4a says the app's Overdue
section already ships, derived the same way from loaded tasks. If it uses the
literal rule, every recurring task is in it right now.

### 4.4 `incomplete` tasks are not in `overdue`

Falls out of §4.3 naturally — a lapsed occurrence is resolved. It also matches
your definition, and I think it is right: a lapse has been recorded and is no
longer a question being asked. But "you missed this" is arguably exactly what
`incomplete` means, so if you want them in, it is a one-line change.

## 5. What I need from you

1. **Confirm or reject the four deviations in §4**, especially §4.2 (the state
   the last row transitions from) and §4.3 (which may mean an app-side change).
2. **Should the server own the extension button values?** Right now
   `config.EXTENSION_MINUTES = (15, 30, 60)` and the app hardcodes the same
   three. That is a cap-nobody-will-tune problem of the kind your §2 warns
   about — changing the choices needs an APK. I did not add them to the
   component because that would put a field in it your app has not been built to
   read. Say the word and they ride on `OverdueComponent`.
3. **`joy-status-2026-08-21.md` was not in your re-send.** It was in the batch
   destroyed and is not among the four that arrived. Only Aug 21 document,
   sitting between 05 and the Aug 22 pair.
4. **Repo access.** Your first option, and not only for durability: everything
   in §2 came from reading gateway source to answer a question about gateway
   behaviour — the file hop only ever carried the question. A read-only deploy
   key is the smaller grant and it is yours to make.

## 6. Deploying

Migration is already applied (additive; the running old code ignores the
column).

```bash
systemctl --user restart jarvis-gateway
systemctl --user status jarvis-gateway --no-pager
```

**Correction to an earlier draft of this section:** I first wrote this as `kill
<pid>` plus a manual `uvicorn`, having checked *system* units and found none.
It is a systemd **user** unit — `jarvis-gateway.service`, enabled, active,
`Restart=on-failure` — so killing the process would have had systemd restart it
underneath you and the manual uvicorn would then have collided on port 8099.
Use `systemctl --user restart`.

**Restarting lights up the nudge loop immediately.** Anything currently overdue
gets a message on the next poll, so pick a moment when a burst of notifications
is not a surprise. Worth checking what is sitting overdue first:

```bash
curl -s -H "Authorization: Bearer $GATEWAY_TOKEN" \
  http://127.0.0.1:8099/tasks/sections | jq '.overdue | length'
```

(That returns the old shape until the restart — run it after.)

## 7. The one failing test

`test_the_phone_is_offered_one_alarm` fails, and **it failed before I touched
anything** — it is not a regression from this work.

It pins `2026-08-21` / `2026-08-22` and reads through `upcoming_occurrences`,
which filters `scheduled_for >= now()`. Both dates are now past, so the window
is empty and it reports "device would arm 0 alarms". A calendar expiring, not a
bug. The invariant it guards is still sound.

The fix is relative dates rather than pinned ones. I proposed it, it was
declined, and I have left it alone — flagging it here so it is a known
expired test rather than a mystery.

## 8. Test coverage added

38 new tests, all passing.

- `tests/test_followup.py` (29) — nudge content and persistence, the "one more"
  count, extension accounting, the late-tap anchor, running out, recurring rules
  not being walked forward, occurrence-scoped completion leaving a series
  running, double-tap refusal, the spent-allowance lapse, `incomplete` staying
  non-terminal, the overdue section including its recurring trap and its
  not-paged guarantee, and the endpoint shapes including the 409 code.
- `tests/test_relative_time.py` (9) — the guard, the observed 06 transcript
  replayed end to end, correction-within-the-same-turn, and two tests that pin
  the 06 §3.3.1 finding so it cannot silently regress: one shifts the clock
  three hours between two turns of a single session and asserts the second
  prompt differs, the other asserts no `system` row is ever persisted.

The concurrency case is handled but not tested: both writes take
`for no key update` on the occurrence, because the notification and the
in-session card render the same buttons and two taps arriving together is
normal rather than exotic. Without the lock both read `used=1` and both write
`2`, silently granting a third chance.
