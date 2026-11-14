---
name: halaqati-engineering-journal
description: "Halaqati project memory for engineering lessons: confirmed bugs and root causes, failed approaches, verified fixes, and measured experiments. Load BEFORE proposing a fix for a bug that may have been seen before, and when the user asks to record, look up, or review a bug, failed attempt, fix, experiment or lesson. Read-mostly: it never writes an entry unless the user explicitly asks in the current conversation."
---

# Halaqati engineering journal

A small, append-only knowledge base so the project stops repeating failed fixes. It stores **verified engineering facts**
about Halaqati (Android Kotlin/Compose, Supabase PostgreSQL/Auth/RLS, legacy Vite web, offline-first sync). It is not a
task tracker and not a place for application data.

## Safety boundaries (non-negotiable)

- **Write only on request.** Create or append an entry only when the user says so in this conversation
  ("record this", "add to the journal"). Installing or loading this skill writes nothing. Never auto-journal.
- **Never fabricate.** Do not invent bugs, root causes, results, timings or test outcomes. If a test or command was not
  actually run in this session (or its output is not provided by the user), the entry must say **"not run"**.
- **No sensitive content.** Never store credentials, API keys, tokens, signing data, `.env` values, personal
  information, student/guardian/donor records, phone numbers, photos, or production rows. Use synthetic stand-ins
  (`student_A`, `guardian_B`, `halaqa_1`) and describe data shape, not data.
- **No silent rewrites.** Existing entries are history. Never edit, reorder, merge or delete an old entry's content.
  A correction is a **new entry** that says `Amends: EJ-NNNN`. The only permitted change to an old entry is appending a
  dated line to its `Status history`, visibly, with the reason.
- **Code and database are out of scope.** This skill never modifies application code, migrations, RLS or configuration.

## Where things live

| Item | Path |
|---|---|
| Entries | `docs/engineering-journal/EJ-NNNN-<slug>.md` (create the folder only when the first entry is approved) |
| Index (one line per entry) | `docs/engineering-journal/INDEX.md` |
| Entry template | `.claude/skills/halaqati-engineering-journal/templates/entry-template.md` |

## Looking up (before proposing a fix)

1. If `docs/engineering-journal/INDEX.md` exists, read **only the index**, then open at most **3** entries whose tags, files
   or symptoms match. Do not load the whole journal (context budget).
2. If an entry shows an approach already failed, do **not** propose it again unless new evidence changes the premise;
   say which entry you are relying on (`EJ-NNNN`).
3. If nothing matches, say "no matching journal entry" and continue normally. Absence of an entry is not proof that a
   fix is safe.

## Recording (only when asked)

1. Pick the next free number from the index (never reuse numbers).
2. Fill the template. Keep the entry **under ~60 lines**; link to files/commits instead of pasting code or logs.
3. Label every statement: `[FACT]` (observed and cited), `[HYPOTHESIS]` (plausible, untested), `[UNKNOWN]`.
   A root cause is only `[FACT]` if confirmed by a reproduction, a failing-then-passing test, a trace, or a code
   reading that the user agrees settles it. Otherwise record it as `[HYPOTHESIS]`.
4. Fixes: record **what was changed** (file path, commit hash when it exists), **how it was verified** (exact command or
   manual steps, and the real result), and **what could still go wrong**.
5. Experiments: record question, method, environment (device/OS/build, data size), the **measured** numbers before and
   after, and the decision taken. No number without a method.
6. Add one line to `INDEX.md`: `EJ-NNNN | date | type | status | title | tags`.
7. Show the user the exact text to be written and wait for their approval before saving.

## Entry types

`bug` · `failed-approach` · `solution` · `experiment` · `lesson`

Status values: `open` · `confirmed` · `fixed-verified` · `fixed-unverified` · `abandoned` · `superseded`.

## Style

Short, factual, in the user's language (Arabic or English; keep identifiers, paths and commands in English).
No blame, no speculation presented as fact.
