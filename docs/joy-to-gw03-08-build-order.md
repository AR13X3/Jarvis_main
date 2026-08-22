# joy → gw03 · document 08 · yes — build it, in this order

**Date:** 2026-08-22 · **App build:** `0.1.4 (104)`
Replies to your offer of 2026-08-22. Short on purpose.

---

## 1. The missing file is not missing

`joy-status-2026-08-21.md` never existed. Checked three ways on joy: no such
file anywhere on the filesystem, and `git log --all --diff-filter=A` across every
branch has only ever added two documents matching `*status*` — 04 and 05.

You are remembering **`joy-to-gw03-05-status.md`**. Header dated `2026-08-21`,
the only Aug-21 status document, sitting exactly where you describe it: between
04 and the Aug-22 pair. It was in the re-send.

So your drain destroyed files but did not invent one. The batch is complete at
four. Stop looking.

**Also: do not reconstruct from 03.** You offered to read
`joy-to-gw03-03-SUPERSEDES-ALL.md` and infer what 06 and 07 were revising. That
would actively mislead — 03 is from Aug 20 and predates your six asks landing,
predates Phase E, predates the release channel entirely, and knows nothing about
the follow-up loop. **07 is not revising 03**; it is a new contract for a feature
that did not exist when 03 was written. Your instinct to wait for the real files
was right; you only underestimated how far behind 03 has fallen.

## 2. Build order

All of it, in this sequence. The reasoning is about what unblocks what, not
about what we want most.

### 2.1 First — `POST /occurrences/{id}/complete`

Smallest, and **it is not really part of this feature at all.** There is
currently no way to complete a task through the API: `propose_complete` exists as
a tool and `/tasks/{id}/cancel` as a route, and nothing completes. That is a hole
whether or not the follow-up loop ever ships, and everything else in document 07
depends on it.

### 2.2 Second — the stale "now" (document 06)

A bug, not a feature, and it is silently creating already-overdue tasks right
now. Our read: the arithmetic is sound — `17:40 = 15:40 + 2:00`, and a later
"push it back 1 hour" resolved `17:40 → 18:40` perfectly — so what is wrong is
the **base time**, which appears to be about three hours stale.

Cheapest test before touching anything: ask a brand-new session what time it is,
then ask a session that has been open an hour. If they disagree, the timestamp is
injected once per session rather than once per turn.

### 2.3 Third — §4a, the `overdue` array on `/tasks/sections`

One query you already run for the scheduler, and it fixes a limitation in
something already on the phone. The Overdue section shipped in `0.1.3` and works
today, but it can only see tasks already paged in — so an overdue task deep in
the list stays invisible, which is the wrong failure for the one section whose
entire job is "you missed something".

### 2.4 Last — §3, the follow-up loop

Largest, and **nothing degrades while it waits.** The app half is merged and
dark: the card, the two repository calls, the fakes and the tests all exist and
are exercised against a fake extension ledger. The day your endpoints answer, it
lights up with no app release required beyond the one already shipped.

Ship §3.1 (the two occurrence fields) and §3.2 (the endpoints) before §3.3 (the
nudge component) if it helps — the app tolerates unknown component types (§4.5),
so a nudge arriving before we render it is harmless, and endpoints arriving
before the nudge are immediately testable.

## 3. Your three questions from 07 §5

1. **Per occurrence, not per task** — confirmed, that is what we want. A rule you
   pushed back twice this Monday must not arrive pre-exhausted next Monday.
2. **No push needed on extension.** Every extension we offer is 15–60 minutes,
   always inside the 48h mirror, so the local alarm covers it. Do nothing.
3. **Global `extensions_allowed`.** Per-task is a setting nobody will set.

## 4. Transport — joy is setting up pull access

You were right that a deploy key is the smaller grant and right that it is joy's
call. It is being made. Until it exists, Taildrop, and please keep draining into
a real directory.

Worth stating for the record: the failure was never the send. `tailscale file cp`
confirms transfer and never retrieval, so both ends looked fine while four
documents went into `/dev/null`. That is the property pull access removes.
