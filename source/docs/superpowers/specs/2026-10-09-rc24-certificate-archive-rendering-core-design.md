# RC24 — Certificate Archive & Rendering Core

**Target:** Halaqati v2.60.0 RC24  
**Date:** 2026-10-09  
**Status:** Design approved in chat; written specification pending user review before implementation.

## 1. Goal

Rebuild the certificate subsystem so that:

1. the A4 landscape preview and exported PDF are the same rendering artifact;
2. required certificate body content can never silently disappear;
3. official certificate numbers can be grouped by configurable series codes;
4. one official certificate number can have multiple revisions/print versions without consuming a new number;
5. the official archive can be managed from the app while keeping Supabase lightweight (metadata + text/config snapshots only; no PDF/assets upload);
6. existing official certificate numbers remain valid and unchanged.

## 2. Confirmed current-state problems

### 2.1 Missing certificate body in exported PDF
The current HTML template contains the kicker, main title, intro, student name, achievement and dua, but the PDF supplied for `HAL-CERT-2026-00009` contains the header, kicker, certificate number, dates, signatures and footer while the central body is absent.

The current export validator only requires:
- non-blank student name;
- non-blank certificate number;
- valid date.

It does **not** validate title, intro, achievement, dua, or the post-layout visibility of any required DOM node. Therefore a visually incomplete document can still be issued and archived.

The rendering stack also has preview, Chromium/vector printing, and fallback behavior, so RC24 must make the preview/export contract explicit rather than rely on a best-effort path.

### 2.2 Current archive is too light for exact regeneration
`certificates.snapshot` currently stores a lightweight snapshot such as student name/category/kind, but not a complete immutable render snapshot. This is insufficient to reconstruct a historical certificate exactly after text/style changes.

### 2.3 Current numbering has one yearly counter
The current format is `HAL-CERT-YYYY-00000` and `certificate_number_counters` is keyed only by year. It cannot maintain separate sequences for Sard, Khatm, general awards, etc.

### 2.4 Issued certificate content is immutable with no revision model
The current trigger intentionally prevents content edits after issue. This is correct for auditability, but there is no supported mechanism to create a corrected visual/text revision while preserving the same official number.

## 3. Non-goals

- Do not upload certificate PDFs, logos, signatures, stamps or other binary assets to Supabase.
- Do not renumber legacy certificates `HAL-CERT-2026-00001` through existing legacy numbers.
- Do not delete or rewrite historical official numbers merely to make them fit the new series format.
- Do not allow changing the student identity or semantic certificate category under an already-issued official number.
- Do not merge the certificate subsystem with reports or unrelated document export flows in RC24.

## 4. Visual certificate contract

### 4.1 Layout order
The A4 landscape document will use fixed semantic zones in this order:

1. logos;
2. center name + halaqa name;
3. ornamental line and center diamond;
4. **`شهادة تقدير وإنجاز` directly below the diamond**;
5. certificate title;
6. introduction;
7. student name;
8. achievement body;
9. dua;
10. Gregorian/Hijri dates;
11. teacher/supervisor signatures + official stamp;
12. footer verse and optional certificate-number position.

The large unused whitespace shown in the current broken output is not an accepted layout state.

### 4.2 Kicker placement
`شهادة تقدير وإنجاز` must be anchored to the header ornamental diamond, not positioned by negative margins relative to a variable header height. Its position must remain stable when logo size changes.

### 4.3 Single render source
`CertificateHtmlRenderer` is the canonical render source for:
- in-app A4 preview;
- PDF export;
- re-rendering archived revisions.

No independent native-canvas certificate layout may be used as a visually different fallback for an official certificate. A fallback may save the *same already-laid-out HTML capture*, but must not construct a second certificate layout.

### 4.4 Required body validation
Before issue or PDF export, all required fields must be non-blank:
- center name;
- halaqa name;
- kicker;
- certificate title;
- student name;
- achievement body;
- issue date;
- official number when issuing/exporting an issued certificate.

Intro and dua may be configurable as optional, but if configured as visible they must also be non-blank.

### 4.5 DOM/render gate
After fonts and images are ready, the renderer must expose a structured result, not only `__HALAQATI_READY__`:

- `ready`
- `layout_ok`
- `required_nodes_present`
- `required_nodes_visible`
- `required_nodes_inside_page`
- `no_protected_overlap`
- `render_revision`

Required nodes include:
- `.cert-kicker`
- `.cert-main-title`
- `.cert-name-box strong`
- `.cert-achievement`

The export action must fail closed when any required node is missing, blank, `display:none`, `visibility:hidden`, zero-size, outside the A4 bounds, or overlapped by protected signature/date/footer zones.

The user sees a specific Arabic error naming the missing/invalid section instead of receiving a blank PDF.

### 4.6 Preview parity
The preview must display the exact same HTML string and style snapshot later passed to PDF export. Once an official revision is selected, no recomputation from current global style settings is allowed; archived revision snapshots render exactly as stored.

## 5. Certificate identity and numbering

### 5.1 Stable official identity
`certificates` remains the table with one row per official certificate identity/number.

An official certificate number belongs permanently to:
- one student;
- one halaqa at issue time;
- one series/category;
- one issue year.

These identity fields cannot be changed after first issue.

### 5.2 Series codes
Add a separate numbering-series concept rather than overloading existing `certificate_type`.

Default series:

| Series key | Default code | Arabic label | Typical current type |
|---|---:|---|---|
| `general` | `G` | عام / تقدير | achievement |
| `sard` | `S` | السرد | sard |
| `khatm` | `K` | ختم القرآن | hifz |
| `monthly` | `M` | التميز الشهري | achievement |
| `ideal` | `I` | الطالب المثالي | achievement |
| `rank` | `R` | المراكز / التفوق | achievement |
| `custom` | `C` | مخصص | achievement |

Default new format:

`HAL-CERT-YYYY-CODE-00001`

Example:

`HAL-CERT-2026-S-00001`

### 5.3 Configurable codes
Add `certificate_series` configuration in Supabase. Authorized staff can change the display/series code for **future allocations**.

Rules:
- code is normalized uppercase ASCII/alphanumeric, 1–6 characters;
- code must be unique within the active numbering namespace;
- changing a code does not rewrite previously issued numbers;
- disabled series cannot allocate new numbers but historical certificates remain readable.

### 5.4 Independent counters
Replace yearly-only allocation with counters keyed by `(issue_year, series_key)`.

Each series increments independently:
- `S-00015`
- `K-00003`
- `G-00027`

Allocation remains atomic on Supabase.

### 5.5 Legacy numbers
Existing numbers without a series segment remain valid and are marked `legacy_number=true` or inferred as legacy.

They are never rewritten by migration.

## 6. Revisions: multiple versions under one official number

### 6.1 Data model
Add `certificate_revisions`:

- `id uuid pk`
- `certificate_id uuid not null fk certificates`
- `revision_no integer not null`
- `render_snapshot jsonb not null`
- `content_hash text not null`
- `created_by uuid`
- `created_at timestamptz`
- optional `note text`
- unique `(certificate_id, revision_no)`
- unique `(certificate_id, content_hash)` where appropriate to avoid accidental duplicate revisions

Add to `certificates`:
- `series_key text`
- `series_code_at_issue text`
- `current_revision_id uuid`
- `revision_count integer` (derived or maintained)
- `legacy_partial boolean default false`

### 6.2 Revision behavior
First official issue creates revision `1` and allocates the official number once.

Editing an issued certificate does **not** update revision 1. It creates revision 2, 3, etc. The certificate number remains unchanged.

The archive displays, for example:

`HAL-CERT-2026-S-00017 · 3 إصدارات`

with child history:
- v1 — original
- v2 — corrected text/layout
- v3 — latest/current

### 6.3 What may change in a revision
Allowed while keeping the same official number:
- title wording;
- intro;
- achievement body;
- dua;
- typography and palette;
- layout/template settings;
- signatures/stamp visibility/config references;
- display names for teacher/supervisor;
- non-identity descriptive fields.

Not allowed under the same official number:
- student ID;
- certificate series/category;
- semantic source identity that would make it a different award;
- issue year if that changes the official identity.

Attempting those changes creates a new certificate identity/number instead.

## 7. Render snapshot schema

Each revision stores enough text/config to reproduce the certificate without depending on mutable current settings.

Minimum fields:

```json
{
  "schema": "halaqati_certificate_render_v2",
  "student_name": "...",
  "center_name": "...",
  "halaqa_name": "...",
  "certificate_type": "...",
  "series_key": "...",
  "title": "...",
  "intro": "...",
  "achievement": "...",
  "dua": "...",
  "issue_date": "YYYY-MM-DD",
  "teacher_name": "...",
  "supervisor_name": "...",
  "style": { },
  "asset_refs": {
    "policy": "local_only",
    "center_logo_hash": "...",
    "halaqa_logo_hash": "...",
    "teacher_signature_hash": "...",
    "supervisor_signature_hash": "...",
    "stamp_hash": "..."
  },
  "source": { },
  "renderer_version": "rc24"
}
```

Binary asset bytes remain local. Hashes/labels make it clear when a historical revision references an asset no longer present locally.

If an old asset is unavailable, the archive must show `الأصل البصري غير متوفر على هذا الجهاز` instead of silently substituting a new logo/signature and pretending it is exact.

## 8. Archive management UX

### 8.1 Official archive card
Each certificate card shows:
- student name;
- certificate series/type;
- official number;
- issue date;
- state (`draft`, `issued`, `voided`, legacy partial);
- revision count;
- current revision badge.

### 8.2 Actions
For an issued certificate:
- preview current revision;
- preview an older revision;
- create revised version;
- regenerate local PDF from selected revision;
- export JSON metadata/render snapshot;
- void official certificate;
- duplicate as **new certificate** (new number) when identity/type changes.

For a draft:
- edit;
- issue;
- delete permanently.

### 8.3 Delete policy
Official issued certificates are not silently hard-deleted from normal UI.

Normal management action is **void**:
- number remains reserved;
- audit history remains;
- archive displays `ملغاة`.

Hard delete, if retained at all, is restricted to a dedicated admin-only destructive path and only when there are no legal/audit dependencies. It is not the default archive delete button.

### 8.4 Add archive record
Add `إضافة سجل أرشيف قديم` for historical certificates not originally created by the app.

Rules:
- requires student, date, type/series and historical number;
- imported/manual historical number is checked for uniqueness;
- does not increment a new-number counter;
- can be marked `legacy_partial` if exact original render snapshot is unavailable;
- cannot masquerade as a newly server-issued RC24 certificate.

## 9. Local archive behavior

The local archive remains useful for offline speed and local file references, but Supabase becomes authoritative for official identity + revisions.

Local record should store:
- `certificate_id`
- `revision_id`
- official number
- local PDF URI/path if present
- local asset availability
- local last-render timestamp

Local JSON import/export must merge by `certificate_id + revision_no`, not by number alone.

## 10. Supabase changes

### 10.1 New/changed tables
- add `certificate_series`
- add `certificate_revisions`
- alter `certificates` with series/current-revision/legacy fields
- replace or extend `certificate_number_counters` to key by `(issue_year, series_key)`

### 10.2 RPCs
Add/replace server RPCs:
- `rc24_issue_certificate(...)`
- `rc24_next_certificate_number(series_key, issue_date)`
- `rc24_create_certificate_revision(certificate_id, render_snapshot, note)`
- `rc24_void_certificate(certificate_id, reason)`
- `rc24_import_legacy_certificate(...)` for authorized historical archive entry

All RPCs must enforce server-side authorization and identity immutability.

### 10.3 RLS/security
- staff access remains halaqa-scoped;
- guardians can only read issued/current certificates for linked students;
- revision rows inherit access from parent certificate;
- counters are not directly writable by clients;
- certificate-series configuration is writable only by authorized management roles;
- SECURITY DEFINER RPCs must set an explicit safe `search_path` and revoke anon execute.

## 11. Migration of current production archive

### 11.1 Preserve existing records
No existing `certificate_number` changes.

### 11.2 Create revision 1 where possible
For each existing certificate:
- build revision 1 from existing `snapshot` and row metadata;
- if required render content is not present, set `legacy_partial=true`;
- do not invent missing historical title/body/style data.

### 11.3 Current certificate 00009
The existing `HAL-CERT-2026-00009` remains unchanged as an official legacy number.

Its current lightweight snapshot does not contain enough data to claim an exact historical re-render. It should therefore be migrated as legacy/partial unless a complete local archive snapshot can be matched by certificate identity.

## 12. App/API model changes

### 12.1 Models
Extend `CertificateRecord` and add:
- `CertificateSeriesRow`
- `CertificateRevisionRow`
- `CertificateRenderSnapshot`

### 12.2 Repository
Add repository methods:
- list series;
- list revisions by certificate;
- create revision;
- issue using series;
- void certificate;
- import legacy archive record;
- refresh authoritative archive.

Do not mutate issued certificate content with generic table writes.

### 12.3 Certificate Studio
Split screen into clear sections:
- Create / Edit draft
- Live A4 preview
- Issue / export
- Official Archive
- Series settings (management roles)

Revision editing starts from the selected archived snapshot, not from current global Studio defaults.

## 13. Implementation files expected to change

Primary code:
- `app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt`
- `app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt`
- `app/src/main/assets/documents/certificate.css`
- `app/src/main/java/com/mohammedsamir/halaqati/documents/HtmlDocumentPreview.kt`
- `app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt`
- `app/src/main/java/com/mohammedsamir/halaqati/model/CertificateCore.kt`
- `app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt`

Database:
- new RC24 migration file under `migration/`
- `migration/source-contract.json` if required by gates

Verification:
- new RC24 certificate-render/archive check script
- renderer fixtures for normal, long-name, long-achievement, no-optional-intro/dua, each number position, and legacy partial archive
- repository/core tests for numbering/revisions/immutability

## 14. Test strategy

### 14.1 TDD behavior tests
Write failing tests before production changes for:

1. new series numbering allocates independent sequences;
2. legacy numbers are preserved;
3. second revision reuses the same certificate number;
4. changing student/series cannot create a revision under the same number;
5. voided certificate cannot issue a new revision as current without explicit restore/new-certificate workflow;
6. official archive can list all revisions;
7. draft deletion is allowed; issued default hard delete is not;
8. manual legacy archive insert does not consume the live counter.

### 14.2 Render regression fixtures
For every fixture, assert:
- required DOM nodes exist and are non-empty;
- all required nodes have non-zero client rects;
- nodes are inside A4 bounds;
- body does not overlap dates/signatures/footer;
- screenshot/print output contains title, student name and achievement text;
- `شهادة تقدير وإنجاز` sits immediately beneath the ornamental diamond/header line zone.

### 14.3 PDF verification
Generated PDF text extraction is a secondary check only (Arabic extraction order may differ). Visual screenshot/render bounds are authoritative for layout.

At least one integration fixture must confirm the exact failure class of the supplied certificate: header/footer must not pass if main body is absent.

## 15. Release and rollback

### 15.1 Release order
1. create migration and tests on source;
2. verify migration against schema assumptions;
3. apply RC24 DB migration;
4. build APK;
5. verify archive/revision reads against production-compatible schema;
6. release v2.60.0 RC24.

### 15.2 Backward compatibility
RC23 clients may continue reading base `certificates` rows. RC24 migration must not remove columns used by RC23 during the transition.

New RC24-only revision/series behavior is additive first.

### 15.3 Rollback
If APK rollback is needed, DB additions remain harmless/additive. Do not drop RC24 tables during app rollback.

## 16. Acceptance criteria

RC24 is accepted only when all are true:

1. the supplied blank-body failure cannot be reproduced;
2. a certificate with blank required body is blocked before issue/export;
3. preview and PDF use the same render snapshot;
4. kicker is visibly directly below the ornament diamond/header separator;
5. new series numbers follow configurable `HAL-CERT-YYYY-CODE-NNNNN` format;
6. counters are independent per series;
7. legacy numbers remain unchanged;
8. an issued certificate can create v2/v3 while preserving exactly the same official number;
9. archive exposes revision history and current revision;
10. draft can be deleted; issued certificate is voided by default, preserving number/history;
11. manual legacy archive add works without consuming a new number;
12. no PDF/image/signature/stamp bytes are uploaded to Supabase;
13. production archive migration does not invent missing historical content;
14. existing certificates and RC23 clients remain readable during transition;
15. security/RLS/advisor review adds no new avoidable high-risk exposure.

## 17. Explicit decisions approved from chat

- Use the full RC24 architecture, not a quick visual patch.
- Keep PDFs/assets local; Supabase stores lightweight structured text/config only.
- Move `شهادة تقدير وإنجاز` upward to directly below the header ornament diamond.
- Add configurable series letters/codes and independent counters.
- Preserve existing legacy certificate numbers.
- Support multiple revisions under one official number.
- Make official archive editable through revisions and voiding, not silent history rewriting.
- Allow adding historical archive records.
- Prevent visually incomplete official PDFs with a render gate.
