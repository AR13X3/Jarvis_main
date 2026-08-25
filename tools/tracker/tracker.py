#!/usr/bin/env python3
"""
Jarvis tracker — a shared checklist for joy, gw03 and Joy.

    !!! THIS FILE IS NOT WHAT RUNS. gw03 patched it before deploying, and the
    !!! deployed copy is canonical. Do not redeploy this one. Four fixes, all
    !!! verified live from joy against https://gw03.tail9662e3.ts.net/tracker:
    !!!
    !!!   1. The page fetched a RELATIVE 'api/items'. At /tracker with no
    !!!      trailing slash that resolves to /api/items, which on this tailnet
    !!!      is the GATEWAY — a 404, leaving the page on "loading..." forever,
    !!!      at exactly the URL document 11 told people to open. Now derives a
    !!!      base from location.pathname.
    !!!   2. Any section string was accepted with a 201 and then rendered
    !!!      nowhere. Now 400.
    !!!   3. A non-string field reached sqlite and raised mid-write, dropping
    !!!      the connection with NO response — a writer could not tell a
    !!!      rejection from a crash. Now 400.
    !!!   4. X-Actor was self-declared. The tailnet identity is now recorded
    !!!      beside it in events.identity, so attribution is checkable.
    !!!
    !!! Fix 1 is the one worth learning from. It was tested here at
    !!! http://127.0.0.1:8799/ — root path — where a relative fetch resolves
    !!! correctly and the bug cannot appear. The code was tested; the
    !!! deployment path was not, and "tested end to end" was claimed for both.

Runs on gw03, served over Tailscale. Three writers, one truth, and every change
attributed and timestamped.

WHY THIS EXISTS
    Documents 03-10 went by Taildrop, which confirms transfer and never
    retrieval. Four of them were destroyed without either side noticing, and the
    receipt of document 09 is still unknown. Every blocked item on both sides
    funnels through that one question.

    Here, a writer can read back what it just wrote. Delivery stops being a
    question anyone has to ask.

DESIGN CONSTRAINTS, and they are the reason for the odd choices below
    * STDLIB ONLY. No FastAPI, no pip, no venv. Whoever deploys this cannot
      easily debug it, so it must not be able to fail on a missing import.
    * SEPARATE FROM THE GATEWAY. A tracker that needs the thing it tracks to be
      healthy is not a tracker. Its own port, its own process, its own restart.
    * ITEM-LEVEL WRITES. Two agents editing different lines must never conflict.
      This is the whole advantage over a file in git, where they would.
    * APPEND-ONLY HISTORY. Every mutation is logged with who and when. The thing
      that actually went missing today was not state, it was provenance.
    * NO AUTH. Tailscale is the perimeter, exactly as it is for the gateway
      (app plan section 4.1). This holds a checklist, not the task database, and
      a second credential to guard a to-do list is a worse trade than the risk.
      It binds to 127.0.0.1 and is reached only through `tailscale serve`.

DEPLOY
    See README.md beside this file. Two commands.
"""

from __future__ import annotations

import json
import sqlite3
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

DB_PATH = Path(__file__).with_name("tracker.db")
PORT = 8787

# Ordered as they are rendered. Grouped by WHO IS BLOCKED rather than by
# feature, because "who is this waiting on" is the question that keeps having to
# be reconstructed by reading nine documents.
SECTIONS = ["joy", "gw03", "Joy", "buildable now", "done"]


def db() -> sqlite3.Connection:
    """A connection per request. Cheap, and removes every threading question."""
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("pragma journal_mode=wal")
    return conn


def init() -> None:
    with db() as conn:
        conn.execute(
            """
            create table if not exists items (
                id        integer primary key autoincrement,
                section   text    not null,
                text      text    not null,
                owner     text    not null default '',
                note      text    not null default '',
                done      integer not null default 0,
                position  integer not null default 0,
                updated   real    not null
            )
            """
        )
        conn.execute(
            """
            create table if not exists events (
                id      integer primary key autoincrement,
                at      real not null,
                actor   text not null,
                action  text not null,
                item_id integer,
                detail  text not null default ''
            )
            """
        )


def log(conn: sqlite3.Connection, actor: str, action: str, item_id, detail: str = "") -> None:
    conn.execute(
        "insert into events (at, actor, action, item_id, detail) values (?,?,?,?,?)",
        (time.time(), actor or "unknown", action, item_id, detail),
    )


class Handler(BaseHTTPRequestHandler):
    server_version = "jarvis-tracker/1"

    # --- plumbing ------------------------------------------------------------

    def _send(self, code: int, body: bytes, content_type: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code: int, payload) -> None:
        self._send(code, json.dumps(payload, indent=2).encode(), "application/json; charset=utf-8")

    def _body(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return {}
        try:
            return json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            return {}

    def _actor(self) -> str:
        """
        Who is writing.

        `X-Actor` if the caller says so; otherwise the Tailscale identity that
        `tailscale serve` attaches, which a browser tick will carry and a curl
        from another node will too. Attribution is the point of the whole
        exercise, so falling back to "unknown" is deliberate rather than
        guessing.
        """
        return (
            self.headers.get("X-Actor")
            or self.headers.get("Tailscale-User-Login")
            or "unknown"
        )

    def log_message(self, fmt, *args) -> None:  # noqa: A003
        pass  # journald gets the systemd unit's own output; access logs are noise

    # --- routes --------------------------------------------------------------

    def do_GET(self) -> None:  # noqa: N802
        path = urlparse(self.path).path.rstrip("/") or "/"
        if path == "/":
            self._send(200, PAGE.encode(), "text/html; charset=utf-8")
        elif path == "/api/items":
            with db() as conn:
                rows = conn.execute(
                    "select * from items order by position, id"
                ).fetchall()
            self._json(200, {"sections": SECTIONS, "items": [dict(r) for r in rows]})
        elif path == "/api/events":
            with db() as conn:
                rows = conn.execute(
                    "select * from events order by id desc limit 100"
                ).fetchall()
            self._json(200, {"events": [dict(r) for r in rows]})
        elif path == "/api/health":
            self._json(200, {"ok": True, "at": time.time()})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        if urlparse(self.path).path.rstrip("/") != "/api/items":
            self._json(404, {"error": "not found"})
            return
        body = self._body()
        text = (body.get("text") or "").strip()
        if not text:
            self._json(400, {"error": "text is required"})
            return
        section = body.get("section") or "buildable now"
        with db() as conn:
            cur = conn.execute(
                "insert into items (section, text, owner, note, done, position, updated)"
                " values (?,?,?,?,0,?,?)",
                (
                    section,
                    text,
                    body.get("owner") or "",
                    body.get("note") or "",
                    int(body.get("position") or 0),
                    time.time(),
                ),
            )
            log(conn, self._actor(), "add", cur.lastrowid, text)
            row = conn.execute("select * from items where id=?", (cur.lastrowid,)).fetchone()
        self._json(201, dict(row))

    def do_PATCH(self) -> None:  # noqa: N802
        item_id = self._item_id()
        if item_id is None:
            return
        body = self._body()
        fields, values, changes = [], [], []
        for key in ("section", "text", "owner", "note", "position"):
            if key in body:
                fields.append(f"{key}=?")
                values.append(body[key])
                changes.append(f"{key}={body[key]!r}")
        if "done" in body:
            fields.append("done=?")
            values.append(1 if body["done"] else 0)
            changes.append("done" if body["done"] else "undone")
        if not fields:
            self._json(400, {"error": "nothing to change"})
            return
        fields.append("updated=?")
        values.append(time.time())
        values.append(item_id)
        with db() as conn:
            cur = conn.execute(f"update items set {', '.join(fields)} where id=?", values)
            if cur.rowcount == 0:
                self._json(404, {"error": "no such item"})
                return
            log(conn, self._actor(), "edit", item_id, "; ".join(changes))
            row = conn.execute("select * from items where id=?", (item_id,)).fetchone()
        self._json(200, dict(row))

    def do_DELETE(self) -> None:  # noqa: N802
        item_id = self._item_id()
        if item_id is None:
            return
        with db() as conn:
            row = conn.execute("select * from items where id=?", (item_id,)).fetchone()
            if row is None:
                self._json(404, {"error": "no such item"})
                return
            conn.execute("delete from items where id=?", (item_id,))
            log(conn, self._actor(), "delete", item_id, row["text"])
        self._json(200, {"ok": True})

    def _item_id(self):
        parts = urlparse(self.path).path.rstrip("/").split("/")
        if len(parts) != 4 or parts[1:3] != ["api", "items"]:
            self._json(404, {"error": "not found"})
            return None
        try:
            return int(parts[3])
        except ValueError:
            self._json(400, {"error": "id must be a number"})
            return None


PAGE = """<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Jarvis tracker</title>
<style>
  :root {
    --bg:#faf7f8; --surface:#fff; --ink:#15100f; --muted:#6b6164;
    --line:#e7e0e2; --brand:#d8203c; --done:#2e7d5b;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg:#141011; --surface:#1e1819; --ink:#f4eff0; --muted:#a2989b;
      --line:#2e2729; --brand:#f04156; --done:#5fd1a3;
    }
  }
  * { box-sizing:border-box }
  body { margin:0; background:var(--bg); color:var(--ink);
         font:16px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }
  header { padding:20px 20px 8px; }
  h1 { margin:0; font-size:26px; letter-spacing:-.5px }
  .sub { color:var(--muted); font-size:13px; margin-top:2px }
  main { padding:0 16px 48px; max-width:760px; margin:0 auto }
  h2 { font-size:11px; text-transform:uppercase; letter-spacing:1px;
       color:var(--muted); margin:26px 0 8px 4px }
  .item { display:flex; gap:12px; align-items:flex-start; background:var(--surface);
          border:1px solid var(--line); border-radius:14px; padding:12px 14px; margin-bottom:8px }
  .item.done .text { text-decoration:line-through; color:var(--muted) }
  input[type=checkbox] { width:20px; height:20px; margin:2px 0 0; accent-color:var(--brand); flex:none }
  .text { flex:1 }
  .meta { color:var(--muted); font-size:12px; margin-top:3px }
  .owner { display:inline-block; border:1px solid var(--line); border-radius:999px;
           padding:0 8px; font-size:11px; color:var(--muted); margin-right:6px }
  .empty { color:var(--muted); font-size:14px; padding:4px }
  footer { color:var(--muted); font-size:12px; text-align:center; padding:8px }
</style>
<header>
  <h1>Jarvis tracker</h1>
  <div class="sub" id="sub">loading…</div>
</header>
<main id="main"></main>
<footer>Ticking a box here is recorded as <b>Joy</b>. Refreshes every 5s.</footer>
<script>
const esc = s => s.replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));

// The poll must not rebuild the DOM when nothing changed. A blind re-render
// every 5s destroys the element under a thumb mid-tap, and briefly reverts a box
// you just ticked. Only redraw when the data actually differs.
let signature = null;

async function load(force) {
  const r = await fetch('api/items', {cache:'no-store'});
  const payload = await r.json();
  const next = JSON.stringify(payload);
  if (!force && next === signature) return;
  signature = next;
  const {sections, items} = payload;
  const main = document.getElementById('main');
  main.innerHTML = '';
  for (const section of sections) {
    const mine = items.filter(i => i.section === section);
    if (!mine.length) continue;
    const h = document.createElement('h2');
    const open = mine.filter(i => !i.done).length;
    h.textContent = open ? section + ' · ' + open : section;
    main.appendChild(h);
    for (const item of mine) {
      const d = document.createElement('div');
      d.className = 'item' + (item.done ? ' done' : '');
      d.innerHTML =
        '<input type=checkbox ' + (item.done ? 'checked' : '') + ' data-id=' + item.id + '>' +
        '<div class=text>' + esc(item.text) +
        (item.owner || item.note
          ? '<div class=meta>' +
            (item.owner ? '<span class=owner>' + esc(item.owner) + '</span>' : '') +
            esc(item.note) + '</div>'
          : '') +
        '</div>';
      main.appendChild(d);
    }
  }
  if (!main.children.length) main.innerHTML = '<div class=empty>Nothing tracked yet.</div>';
  const done = items.filter(i => i.done).length;
  document.getElementById('sub').textContent =
    done + ' of ' + items.length + ' done · updated ' + new Date().toLocaleTimeString();
}

document.addEventListener('change', async e => {
  if (e.target.type !== 'checkbox') return;
  await fetch('api/items/' + e.target.dataset.id, {
    method: 'PATCH',
    headers: {'Content-Type':'application/json', 'X-Actor':'Joy'},
    body: JSON.stringify({done: e.target.checked}),
  });
  load(true);
});

load(true);
setInterval(load, 5000);
</script>
"""


def main() -> None:
    init()
    port = int(sys.argv[1]) if len(sys.argv) > 1 else PORT
    # 127.0.0.1 only. `tailscale serve` is what exposes it, so the tailnet is
    # the boundary and nothing is listening on a public interface even by
    # accident.
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"jarvis-tracker on 127.0.0.1:{port}, db at {DB_PATH}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
