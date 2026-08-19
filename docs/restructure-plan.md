# Jarvis Restructure — LLM Offload + Task/Chat Agent

> Handoff document. Self-contained: assumes no prior conversation context.
> Scope is deliberately partial — Part 1 (remove the local LLMs) and Part 2
> (rebuild tasks + chat as an app-backed agent). Budget, Calorie, Gym, Prayer
> and the cross-app mirror are **out of scope here** and get added later.
>
> Companions: `ecosystem-plan.md` (the wider architecture),
> `prayer-tracker-plan.md` (one app's internals), `jarvis/BUILD_NOTES.md`
> (running build log for the existing Telegram bots).

---

# PART 1 — Offload the local LLMs

> **Status: done 2026-08-19**, except three items carried below. Both live bots
> run on OpenRouter; the Ollama container is stopped (volume kept) and memory on
> `gw03` went 9.8 → 7.0 GiB used, 174 MiB → 2.9 GiB free. Still open: the key
> rotation (step 1, now worse — see §1.6), `open-webui` (step 8), and deleting
> the volume (step 9). Detail in `jarvis/BUILD_NOTES.md`, 2026-08-19 entry.

## 1.1 Why

Ollama runs on `gw03` in Docker holding ~11GB of models plus their RAM and CPU.
That machine is wanted for other work. Every consumer now has a validated
replacement on OpenRouter, so the container can go.

## 1.2 What runs today

| consumer | model | replacement | status |
|---|---|---|---|
| bot chat + tool calling | `qwen2.5` | `nvidia/nemotron-3-super-120b-a12b:free` | **validated** |
| receipt vision | `qwen2.5vl:7b` | `dots-studio/dots-3-note-preview:free` | **validated** |
| embeddings | `nomic-embed-text` | *none — stop writing them* | decided |
| `open-webui` | `qwen2.5` etc. | OpenRouter via OpenAI-compatible base URL | compose edit |

### Evidence behind the model choices

Both were measured, not assumed. Harness lives in `jarvis/llm-eval/`.

**Chat/tools — `nemotron-3-super-120b-a12b:free`.** 40 scenarios across all three
bots, transcribed from real failures in BUILD_NOTES rather than invented.
Criticals: 3/3 expenses, 15/15 tasks, 5/5 gym. It cleared **both** ceilings
BUILD_NOTES had written off as model-capability limits — `didi_after_afterpay`
(BUILD_NOTES:291) and the `AxB` → `num_sets` drop (BUILD_NOTES:316). Median
latency ~4s, roughly 3x faster than any alternative tested.

**Vision — `dots-3-note-preview:free`.** The receipt pipeline is unforgiving:
`_extract_json_object()` is a strict whole-text `json.loads` after fence
stripping (bot.py:229-242), so one sentence of prose loses the receipt. Tested
against synthetic receipts degraded toward phone photos (rotation, blur, sensor
noise, uneven lighting, JPEG q55): **12/12 parsed, 12/12 correct**, 5-9s.
Fallback `google/gemma-4-26b-a4b-it:free` (accurate, 25-36s). Avoid
`gemma-4-31b` (repeatedly 429-blocked on Google's shared free pool) and
`nemotron-nano-12b-v2-vl` (three upstream 504s across runs).

> Caveat kept deliberately: those receipts are **rendered and synthetically
> degraded**. That is a floor, not a promise. Acceptance test at cutover is one
> real receipt photo through the live pipeline — **still outstanding.**

**Measured 2026-08-19 — the local vision model never fit the GPU.** `ollama ps`
reports `qwen2.5vl:7b` at 6.6GB on a 4GB RTX 2050, running **91% CPU / 9% GPU**.
Same receipt image, against the receipt path's 180s ceiling:

| path | latency | headroom |
|---|---|---|
| OpenRouter — `dots-3-note-preview` | 6.3s | — |
| Ollama — warm model | 60.4s | 119.6s |
| Ollama — cold load | **167.9s** | **12.1s** |

Twelve seconds of margin on the *simple* receipt, and almost certainly none on
the harder one. This is the evidence behind dropping the local models outright
rather than keeping them as a fallback: the fallback would not have held.

## 1.3 The one non-obvious change

All three bots build tool results as `{"role": "tool", "content": ...}` with **no
`tool_call_id`**, and echo the assistant turn with `arguments` as a dict.
Ollama tolerated both; OpenAI-compatible APIs reject them with a 400.

Fix already written — `assistant_message()` and `tool_result_message()`, now
living in `jarvis_common/llm.py`. It touches **3 call sites per bot**
(e.g. `bots/expenses/bot.py:2039`, `:2057`, `:2074`).

> **Corrected 2026-08-19 — this section claimed the change was backwards
> compatible because "Ollama ignores the extra fields". Only half true, and the
> other half would have broken the rollback path.** Measured against the live
> Ollama before writing any of it:
>
> | history shape | Ollama | OpenRouter |
> |---|---|---|
> | `arguments` dict, no ids (what the bots sent) | 200 | 400 |
> | `arguments` dict + `id` / `tool_call_id` | 200 | 400 |
> | `arguments` as JSON **string** + ids | **400** | 200 |
>
> The extra *fields* are indeed mutually tolerated. But the `arguments` encoding
> is a type change, not a field addition — Ollama parses that key into a Go map
> and rejects a string outright (`"Value looks like object, but can't find
> closing '}' symbol"`). So the encoder emits ids unconditionally, and switches
> only the `arguments` encoding on the active backend. Had it shipped as
> written, flipping back to `ollama` after a bad cutover would have 400'd on the
> first tool call — the rollback the whole design was built around.
>
> Second thing the fix needed: `_extract_leaked_tool_call()` builds calls out of
> raw text, so they have **no `id`** and every one would have been rejected by
> OpenRouter. `assistant_message()` fills a synthetic id in place, so the tool
> result built from the same dict quotes a matching one.

## 1.4 Steps

1. **Rotate the OpenRouter API key.** ❗ **Still open, and now wider.** It went
   through a chat transcript and was confined to `jarvis/llm-eval/.env`; the
   cutover copied it into all three bot `.env` files, so it is now in **four
   files** and is the live credential for running services. See §1.6.
2. ✅ Promote `or_client.py` into `jarvis_common` as the OpenRouter backend.
3. ✅ Add `LLM_BACKEND` env selector (`ollama` | `openrouter`), defaulting to
   `ollama`. Rollback becomes an env edit plus a restart, not a code revert.
   (All three now sit at `openrouter`; an unknown value raises at boot rather
   than silently falling back.)
4. ✅ Fix the `tool_call_id` / `arguments` shape in all three bots (§1.3).
5. ✅ Cut over **gym → tasks → expenses**, one at a time, watching
   `journalctl --user -u jarvis-<bot> -f`. Tasks and expenses restarted and
   confirmed from their startup line; **gym is staged only** — its `.env` is set
   but it still has no systemd unit and no token, so nothing was restarted.
6. ✅ Repoint receipt vision at `dots-3-note-preview:free` with a `models`
   fallback array. Verified on the rendered/degraded test images (6.3s, parsed);
   **one real receipt photo is still the outstanding acceptance test.**
7. ✅ Stop calling `embed_and_store()`. Leave the existing 650 rows alone.
8. ❗ **Still open — and now orphaned.** `open-webui` is running against a
   backend that no longer exists. Evidence says drop it rather than repoint it:
   zero HTTP requests in 30 days, 7 chats all from June, the last one
   prototyping the receipt prompt that now lives in the bot. Repointing means
   `OPENAI_API_BASE_URL=https://openrouter.ai/api/v1` + key, clear
   `OLLAMA_BASE_URL`.
9. ◐ **Half done.** Container **stopped**, volume **kept** on purpose, so
   `docker start ollama` is the entire rollback and `restart: unless-stopped`
   means the stop survives a reboot. Delete the volume, and the ~11GB, once
   OpenRouter has a few days behind it.

## 1.5 Verification

- ✅ **Tool loop, both backends.** Full round trip — call → `assistant_message`
  → `tool_result_message` → follow-up — against the live OpenRouter API and
  against Ollama, including a deliberately id-stripped call to exercise the
  salvage path. Driven from a harness, not from Telegram.
- ✅ **Static + service health.** pyflakes clean; all three bots import against
  their real `.env`; `jarvis_common` resolves to the repo in every venv (so a
  restart picks up new code, no reinstall); both units active with no errors
  since cutover.
- ◐ **A real receipt photo produces a parseable draft.** Both *rendered* test
  images parsed through the bot's strict whole-text `json.loads`. A genuine
  phone photo has still not gone through the live pipeline.
- ⊘ **Each bot answers a live Telegram message.** Not done — and deliberately
  not chased, since the Telegram bots are being retired (Part 2, phase 7). What
  carries forward is `jarvis_common.llm`, and that is exercised against the real
  API.
- ⊘ **`open-webui` reaches an OpenRouter model.** Not done; see step 8.
- ◐ **`docker ps` shows no `ollama`.** True — stopped. Disk **not** reclaimed;
  the volume is kept on purpose.
- ⊘ **Rollback rehearsed once.** Skipped knowingly. It is worth less than it
  looks: chat would roll back fine, but the receipt path measured **167.9s cold
  against a 180s timeout** (§1.2), so the half most likely to need rescuing is
  the half least likely to survive.

## 1.6 Carried forward — the API key

The key exposed on 2026-08-17 is **still unrotated**, and Part 1 made its
footprint larger rather than smaller. It was deliberately kept out of the bots
(their `OPENROUTER_API_KEY=` lines were left blank) precisely so a rotation would
be a one-file change. Cutting over the same day, ahead of a rotation, required
filling those lines in — so it now sits in **four** files:

```
jarvis/llm-eval/.env          bots/tasks/.env
bots/expenses/.env            bots/gym/.env
```

It is free-tier, so the blast radius is quota rather than money. The tradeoff was
stated and accepted at the time; the consequence is that rotating now means
updating four files, and it is the live credential for running services rather
than an idle harness. **This is the oldest unactioned item in the whole plan.**

Related, for the repo move: `.gitignore` covers `.env` / `*.env` today, but a new
repo starts with none — it has to be in place *before* the first `git add`, or
the key lands in the initial commit. `bots/expenses/` and `bots/gym/` are also
untracked entirely, so a move driven by `git ls-files` would silently drop two of
the three bots.

---

# PART 2 — Task + Chat agent

## 2.1 Shape

A phone app with a two-tab bottom nav — **Tasks** and **Chat** — talking to an
**agent gateway** on `gw03`, which owns the OpenRouter key, the tool loop, and
the database.

```
   Android app                gw03 (Tailscale-only)
  ┌───────────┐        ┌──────────────────────────────┐
  │  Tasks    │◄──────►│  gateway                     │
  │  Chat     │  HTTPS │   · session + tool loop      │──► OpenRouter
  └───────────┘        │   · scheduler                │
                       │   · Postgres: agent.* tasks.*│
                       └──────────────────────────────┘
```

The existing Telegram bots keep running untouched on their own `jarvis_*`
schemas until the app replaces them. **Nothing is migrated.** Old data stays
where it is; the new system starts empty.

## 2.2 The central design idea

**One session is bound to at most one task.**

This is not a UI convenience, it is what removes the hardest problem in the
Telegram design. There, `awaiting_queue` existed solely so that "yes" had a
single possible referent — only one draft could ever be live per chat. With a
session per task, that ambiguity cannot arise: there is exactly one candidate
by construction.

Two consequences fall straight out:

- **No queue, no auto-confirm.** `AWAITING_AUTO_RESOLVE_MINUTES` auto-committed
  a silent draft after 3 minutes because a draft could scroll out of view in a
  chat. With a persistent task list on screen, silently committing because the
  user didn't look at their phone is simply wrong. Both mechanisms are deleted.
- **The pending-draft bug disappears.** Measured on every model tested: on "yes"
  the model re-drafts instead of confirming, because the prompt never states a
  draft is live. With one session per task and explicit state injected into the
  prompt, there is nothing to infer.

## 2.3 Tools are scoped by session state — enforced in code

The rule "you cannot create a second task inside a session" **must not** be a
prompt instruction. Measured violation rates for explicit prompt rules ran 5-25%
even with the strongest wording available.

Instead the gateway decides which tools exist for a given turn:

| session state | tools offered |
|---|---|
| new, unbound | `propose_create` |
| bound, `active` / `awaiting` / `incomplete` | `propose_update`, `propose_cancel`, `propose_complete`, `get_task` |
| bound, terminal (`completed` / `cancelled`) | `get_task` only |
| general chat | `find_tasks`, `get_task` |

The model cannot do the wrong thing because the wrong thing is not in its tool
list. The prompt only has to explain the situation, never police it.

**`incomplete` is not terminal — decided.** A lapsed task keeps its full mutation
set, because it is the one you most want to reschedule. `propose_update` carrying
a new `due_at` returns it to `active`. Only `completed` and `cancelled` lock a
task. This is the only status that is both a resolution and re-openable, so the
scheduler must treat `incomplete → active` as a legal transition rather than
assuming resolution is one-way.

## 2.4 Confirmation is a first-class object, not a database row

Nothing writes to `tasks.tasks` until confirmed. A `propose_*` tool creates a
row in `agent.proposals`; the app renders it as a confirmation card; confirming
executes it. So `tasks.tasks` only ever contains real tasks — no half-formed
drafts, no status archaeology to tell a draft from a task.

This also makes multi-turn refinement natural: the model asks questions across
as many turns as it needs, and only proposes when it has enough. The eval shows
the model does this well already — 5/5 refusing to guess gym details, 4/5
refusing to guess an expense type, asking a clarifying question instead.

## 2.5 Schema

New schemas in the existing local Postgres, fully isolated from `jarvis_*`.
Naming: domain schemas unprefixed (`tasks`, later `budget`, `calorie`);
data mirrored from external multi-user apps will use `mirror_*`.

```sql
create schema if not exists agent;
create schema if not exists tasks;

create type tasks.task_status as enum (
  'active',      -- scheduled, not yet due
  'awaiting',    -- fired, waiting on the user
  'completed',   -- done
  'cancelled',   -- deliberately called off
  'incomplete'   -- due passed, never actioned; editable, NOT terminal
);
```

`cancelled` and `incomplete` are deliberately distinct: one is a decision, the
other is a lapse. That difference is the whole value of looking back at history.

```sql
create table tasks.tasks (
  id            bigint generated always as identity primary key,
  title         text        not null check (length(trim(title)) between 1 and 200),
  description   text        not null default '',   -- long-form, new
  due_at        timestamptz not null,              -- exact instant
  due_date      date        not null,              -- user's LOCAL calendar day; see note
  is_priority   boolean     not null default false,
  recurrence    text,                              -- RFC 5545 RRULE; null => one-shot
  next_fire_at  timestamptz,                       -- next occurrence; null when terminal
  status        tasks.task_status not null default 'active',
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  completed_at  timestamptz,
  cancelled_at  timestamptz
);
create index tasks_due_idx      on tasks.tasks (due_at desc);
create index tasks_status_idx   on tasks.tasks (status, due_at desc);
create index tasks_priority_idx on tasks.tasks (is_priority, due_at) where is_priority;
create index tasks_recur_idx    on tasks.tasks (next_fire_at) where recurrence is not null;
```

> **`due_date` is a bare `date`, computed for the user's timezone — not derived
> server-side from `due_at`.** The server runs UTC. A 9 PM Sydney reminder is the
> next UTC day, so filtering "due today" off a `timestamptz` silently puts tasks
> on the wrong day. Same failure the prayer app plan calls its highest-risk bug
> (§7.1 there). Compute once, store explicitly.

### Occurrences — required by recurrence

A recurring task is not "completed"; its **occurrences** are. The parent row
stays `active` and each firing gets its own resolution, otherwise marking
Monday's gym session done would close the whole weekly reminder.

```sql
create table tasks.occurrences (
  id            bigint generated always as identity primary key,
  task_id       bigint not null references tasks.tasks(id) on delete cascade,
  scheduled_for timestamptz not null,
  scheduled_date date       not null,             -- local day, same rule as due_date
  status        tasks.task_status not null default 'active',
  resolved_at   timestamptz,
  unique (task_id, scheduled_for)
);
create index occurrences_date_idx on tasks.occurrences (scheduled_date, status);
```

One-shot tasks get exactly one occurrence, so the scheduler, the "due today"
indicator, and history all read from one place rather than branching on whether
a task repeats.

### Recurrence machinery — reuse, don't rewrite

`bots/tasks/bot.py` already contains a working RRULE layer worth carrying over
verbatim: `build_rrule()` (assembles and **validates** an RFC 5545 rule from
structured args, rejecting nonsense like `BYMONTHDAY` on a `WEEKLY` rule or
`BYSETPOS` without `BYDAY`), `parse_rrule_components()`, `first_fire_at()` — which
exists because "every Tuesday starting today" on a Saturday must resolve to the
next real Tuesday rather than firing immediately — and `describe_recurrence()`.

Two rules carried forward from that build:

1. **Never accept a raw RRULE string from the model.** Take structured arguments
   and assemble the rule in code. This is the only reason a misparsed recurrence
   is a wrong day rather than a malformed rule in the database.
2. **Render `describe_recurrence()` into the confirmation card.** A wrong day set
   or interval is then visible *before* you confirm, instead of being discovered
   when it starts misfiring at 7am on the wrong weekday.

Rule 2 matters more than it used to. The eval found `nemotron` emits malformed
tool arguments roughly **1 in 5** on the Nth-weekday pattern ("last Friday of
every month"), leaking parameter markup into the `title` field. The proposal
step catches exactly this: nothing executes, and a garbled summary is obvious on
screen. Validate on the way in and reject rather than store.

**Rows are never deleted.** Cancelling sets status and `cancelled_at`. The row
cancel button and the chat path both go through the same transition.

```sql
create table agent.sessions (
  id          uuid primary key default gen_random_uuid(),
  kind        text not null check (kind in ('task','general')),
  task_id     bigint unique references tasks.tasks(id),  -- unique => one session per task
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create table agent.messages (
  id          bigserial primary key,
  session_id  uuid not null references agent.sessions(id) on delete cascade,
  role        text not null check (role in ('user','assistant','tool')),
  content     text not null default '',
  tool_calls  jsonb,
  tool_call_id text,
  created_at  timestamptz not null default now()
);
create index messages_session_idx on agent.messages (session_id, created_at);
```

### Attachments — schema now, feature later (decided)

The Chat tab will take **picture uploads** — receipts and meal photos. The
feature is not in phases 3–5, but the table belongs in the phase-2 migration
anyway: phase 2 is explicitly "migrations from zero", and adding attachments
afterwards is a migration against a live message history for no benefit.

```sql
create table agent.attachments (
  id            uuid primary key default gen_random_uuid(),
  session_id    uuid not null references agent.sessions(id) on delete cascade,
  -- Nullable on purpose: the picture is uploaded BEFORE the message that
  -- carries it exists, so the upload lands first and the message claims it.
  -- A row still pointing at null after its session ends is an abandoned upload.
  message_id    bigint references agent.messages(id) on delete cascade,
  storage_path  text not null,
  mime_type     text not null,
  bytes         integer not null,
  created_at    timestamptz not null default now()
);
create index attachments_session_idx on agent.attachments (session_id, created_at);
create index attachments_message_idx on agent.attachments (message_id);
```

Three things this reuses rather than invents:

- **Bytes never go in the database.** The expenses bot already uploads to the
  Supabase storage bucket (`jarvis-receipts`) and stores a path; the gateway does
  the same. `storage_path` is the whole integration.
- **The vision call already exists.** `jarvis_common.llm.vision_content()` was
  built and validated in Part 1 and handles the OpenAI content-parts encoding, so
  the gateway inherits a working image path rather than writing one.
- **A picture is a user turn, not a separate concept.** It attaches to a
  `role='user'` message, so session context, history and the one-session-per-task
  rule all apply unchanged — no parallel timeline to reconcile.

The open question this leaves is **what a picture is allowed to do**. Reading one
in general chat is harmless. Letting it *drive a proposal* — a photo of a receipt
producing a draft expense — only makes sense once the Budget domain exists, which
is out of scope here. Until then the chat tab can accept and display images
without any tool being able to act on them.

```sql
create table agent.proposals (
  id          uuid primary key default gen_random_uuid(),
  session_id  uuid not null references agent.sessions(id) on delete cascade,
  action      text not null check (action in ('create','update','cancel','complete')),
  payload     jsonb not null,
  status      text not null default 'pending'
              check (status in ('pending','confirmed','rejected','superseded')),
  created_at  timestamptz not null default now(),
  resolved_at timestamptz
);
```

`task_id unique` is what enforces **one persistent session per task** at the
database level rather than in application logic.

### Context, replacing the old short-term memory

The Telegram bots retrieved a **time window** of recent turns. A conversation
that took ten minutes could lose its own beginning mid-flow. Here, context is
`agent.messages` for the session — bounded by the session, not by a clock. The
session boundary does the work that retrieval used to do badly.

`jarvis_core.raw_items` and `embeddings` are not carried forward.

## 2.6 Gateway API

Responses are **structured**, never bare text — the app needs to render
confirmation cards and option buttons.

```
POST /sessions                      {kind, task_id?}      -> session
GET  /sessions/{id}/messages        ?before=&limit=
POST /sessions/{id}/messages        {text}                -> AgentResponse
POST /sessions/{id}/attachments     multipart            -> {attachment_id, ...}
POST /proposals/{id}/confirm                              -> {task, ...}
POST /proposals/{id}/reject
GET  /tasks                         ?status=&from=&to=&page=   (20/page)
POST /tasks/{id}/cancel             {confirm:true}
PATCH /tasks/{id}                   {is_priority}          -- UI toggle, no proposal
GET  /tasks/sections                                       -- priority + recurring + page 1
```

```jsonc
// AgentResponse
{
  "text": "Just to confirm — 'Call the dentist', Thursday 21 Aug, 9:00 AM.",
  "components": [
    {"type": "confirm", "proposal_id": "…", "summary": {…}},
    {"type": "task_options",                    // disambiguation
     "options": [{"task_id": 12, "title": "…", "due_at": "…"}],
     "more_cursor": "…"}
  ]
}
```

## 2.7 App

### Tasks tab

Three sections, top to bottom. **These are views over one table, not task
types** — `is_priority` is a flag and `recurrence` is a rule, so a task can be
both and the sections are just queries:

| section | query |
|---|---|
| **1. Priority** | `is_priority and status in ('active','awaiting')`, by `due_at` |
| **2. Recurring** | `recurrence is not null and status='active'`, by `next_fire_at` |
| **3. All tasks** | everything, newest first, **20 per page** |

Modelling these as views rather than a `type` column matters: a recurring task
you also mark priority needs no special case, and a task can be promoted or
demoted without changing what kind of thing it is.

- A recurring task with an occurrence due today carries a **due-today icon**,
  driven by `tasks.occurrences.scheduled_date = <local today>`.
- Each row: status indicator + cancel button. **Cancel asks for confirmation** —
  a mis-tap is as destructive as a wrong model call.
- Each row has a **3-dot menu** → *Set as priority* / *Remove priority*.
- Filters by status and by date.
- `+` top-left creates a new unbound session.
- Tapping a row opens **that task's** persistent session, with its full history.

### Priority

Set two ways, and they behave differently on purpose:

- **In conversation** — "make it priority" during creation, or later in the
  task's session. Goes through a proposal like any other AI-initiated mutation.
- **From the 3-dot menu** — applies immediately, no confirmation.

The rule: **AI-initiated mutations confirm; direct UI manipulation of a
reversible flag does not.** The model can misparse; a menu tap cannot. Cancel
stays confirmed from either path because it is not cheaply reversible.

### Task session
- Claude-style chat, scoped to one task, context never leaves the session.
- Multi-turn refinement before anything executes.
- Confirmation card before every mutation.
- Terminal tasks (`completed`, `cancelled`) are **read-only** — questions
  answerable, mutations impossible because the tools aren't offered (§2.3).

### Chat tab
General purpose. For now it carries task functions, with the lookup flow:

1. Ask about a task in natural language.
2. If ambiguous, the agent asks for a date.
3. `find_tasks` returns candidates as **buttons, 3 at a time**, "show more"
   pages through the rest.
4. Tapping a button navigates into that task's session.

This is why exact timestamps are stored — the date is the disambiguation key.

**Picture uploads** are part of the Chat tab's eventual shape (schema in §2.5):
upload first, then send the message that references it. Not built in phases 3–5,
but the upload endpoint and the attachments table exist so it is an additive
feature rather than a schema migration.

## 2.8 Reminders

Both server-side and on-device, as specified.

- **Server owns schedule and state.** The gateway scheduler holds truth, does
  the `active → awaiting → completed | incomplete` transitions, and pushes via
  FCM (outbound from `gw03`, so no inbound exposure).
- **Device mirrors a window.** The app syncs the next ~48h and sets exact
  `AlarmManager` alarms, so firing is precise and works offline. Reschedule
  daily via `WorkManager` and on `BOOT_COMPLETED` — alarms do not survive reboot.
- **Dedup by `(task_id, fire_at)`** so a push and a local alarm for the same
  occurrence show once.

**FCM is not yet committed.** It is the only external dependency in Part 2 —
Firebase project, `google-services.json`, and a service-account key on `gw03` —
and with one user, one device, and the Telegram bot retired in phase 7, there is
no second writer whose changes need pushing. Local alarms plus the daily
`WorkManager` refresh may well cover it.

Decide by measurement, not principle. The target device is a **Samsung S23
Ultra**, and Samsung's battery management defers exactly the kind of periodic
background work the refresh depends on. So: ship local alarms only, then leave
the phone untouched for 3-4 days with a reminder scheduled *beyond* the 48h
mirrored window. Fires on time → FCM is dead weight, skip it. Doesn't fire →
that is precisely the failure FCM exists to fix, and it gets added knowing why.

Android specifics that bite (same list as the prayer app):
`POST_NOTIFICATIONS` runtime permission on 13+; `SCHEDULE_EXACT_ALARM` /
`canScheduleExactAlarms()` on 12+; `setExactAndAllowWhileIdle()` for Doze.

## 2.9 Verification

1. A session bound to a task **cannot** create a second task — verified by
   inspecting the tool list sent for that turn, not by asking the model nicely.
2. A terminal task's session offers no mutation tool.
3. Multi-turn: a vague request ("remind me about the thing") reaches a correct
   task through several turns, with nothing written until confirmation.
4. Reject a proposal — confirm `tasks.tasks` is untouched.
5. Cancel from the row button — confirmation appears; the row persists with
   `status='cancelled'`, not deleted.
6. `cancelled` and `incomplete` are reachable and visibly distinct in filters.
7. Disambiguation: three same-titled tasks on different dates resolve correctly
   through the date → buttons flow, including "show more".
8. Kill the app mid-refinement; reopen; the session resumes with history intact.
9. Reminder fires once, on time, with the phone offline.
10. `agent.sessions.task_id` uniqueness actually rejects a second session.
11. An `incomplete` task's session still offers `propose_update`; rescheduling it
    returns it to `active` and re-arms the scheduler.

## 2.10 Build phases

1. **Part 1** — LLM offload. Independent of everything below.
2. **Schema** — `agent` + `tasks` + occurrences, migrations from zero.
3. **Gateway core** — sessions, message persistence, tool loop on the shared
   runtime, state-scoped tool registry, proposals, structured responses.
4. **Tasks tab + session UI** — the core loop. Get this good.
5. **Chat tab** — general chat plus `find_tasks` and the disambiguation flow.
6. **Reminders** — scheduler, device alarms, dedup. Ship local alarms *first*
   and decide FCM by the test in §2.8, not up front.
7. **Retire the Telegram tasks bot**, then decide about its old data.
8. **Release channel** — signed APK, public releases repo, Obtainium (§2.11).

## 2.11 Distribution — Obtainium + GitHub Releases

Updates go through **Obtainium** on the phone, tracking GitHub Releases. Chosen
over serving the APK from the gateway because Obtainium checks and installs
**off-tailnet**, which is when you are actually likely to notice an update.

**The source repo stays private.** `AR13X3/Jarvis` is private, and private
release assets need an authenticated download — meaning a GitHub token living on
the phone. Obtainium's token setting is global across all its sources, so that
token would be a standing credential for every private repo on the account, in
order to solve a problem that has a free fix:

> Publish to a **separate public repo** — `AR13X3/jarvis-releases` — holding no
> code, only tagged releases with APK assets. Obtainium needs no token, the
> unauthenticated rate limit is irrelevant at one device, and the source stays
> private.

Mechanics that matter:

- **Stable signing key.** Obtainium installs updates in place, so every release
  must carry the same signature or the install fails outright. Back the keystore
  up. The blast radius is still mild — the gateway owns all state, so a forced
  uninstall costs a cache, not data — but it breaks the update channel.
- **Monotonic `versionCode`**, with the git tag matching `versionName`
  (`v1.4.0`) so Obtainium's version detection has something unambiguous to read.
- **One APK asset per release**, consistently named (`jarvis-v1.4.0.apk`).
  Multiple assets force an Obtainium regex filter for no benefit here.
- Build on `joy` in Android Studio, publish with `gh release create` against the
  releases repo. No CI needed at this size; adding Actions later would mean
  putting the keystore in secrets, which is a real decision, not a chore.
- Obtainium itself needs "install unknown apps" granted once.

*Not automatically right for the prayer app.* There, updates go to friends, and
"install this other app first, then add a repo URL" is a real cost for
non-technical users that a direct APK link does not have.

---

## 3. Open questions

- **Priority + recurring overlap.** A task that is both appears in section 1 and
  section 2 as written. Show it twice, or does priority win and remove it from
  the recurring list? Duplicating is arguably correct (both statements are true)
  but looks like a bug.
- **Is priority a flag or a level?** Modelled as boolean. If you want
  high/medium/low later, that is an enum change and a sort change.
- **Completing a recurring occurrence from its session.** One session per task
  spans every occurrence, so its history accumulates across weeks. When you say
  "done" in that session, it resolves the *current* occurrence — confirm that is
  what you expect, versus resolving from the notification only.
- **Gateway auth.** Tailscale-only means a shared token is probably enough. If
  the app must work off-tailnet, that is a public ingress and a different
  security conversation.
- ~~**Attachments.**~~ **Resolved 2026-08-19 — pictures get uploaded through the
  chat app.** The schema lands in the phase-2 migration (§2.5) so the feature is
  additive later; what stays open is narrower: whether an image may *drive a
  proposal*, which is really a question about the Budget domain and not about
  tasks.
- **Calorie app.** Listed among the five domains earlier, unmentioned since.
  Dropped, deferred, or single-user like Budget?
