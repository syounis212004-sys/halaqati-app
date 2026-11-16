# RC24 Certificate Archive & Rendering Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Halaqati v2.60.0 RC24 with a fail-closed certificate renderer, configurable per-series official numbering, authoritative certificate revisions under one official number, and an editable/voidable official archive while preserving all legacy certificate numbers and keeping PDFs/assets local.

**Architecture:** Keep `certificates` as the immutable official identity/number row, add `certificate_series` plus `certificate_revisions` as the authoritative render-history layer, and make Supabase RPCs the only path for issue/revision/void/import operations. `CertificateHtmlRenderer` remains the single visual source for preview and PDF; a structured DOM render gate is evaluated in the same WebView/HTML before export. Local storage becomes a revision-aware cache of file/asset availability, not the source of official identity.

**Tech Stack:** Kotlin/JVM + Jetpack Compose, Android WebView/Chromium print adapter, HTML/CSS, PostgreSQL/Supabase RPC + RLS, Python source/visual gates, existing GitHub Actions release pipeline.

**Spec:** `docs/superpowers/specs/2026-10-09-rc24-certificate-archive-rendering-core-design.md`

## Global Constraints

- Target version is `2.60.0`, `versionCode = 26000`, release label `RC24`.
- Existing official numbers such as `HAL-CERT-2026-00009` must never be rewritten.
- New official numbers use `HAL-CERT-YYYY-CODE-NNNNN`, with an independent atomic counter per `(issue_year, series_key)`.
- Default series keys/codes: `general/G`, `sard/S`, `khatm/K`, `monthly/M`, `ideal/I`, `rank/R`, `custom/C`.
- Series code normalization is uppercase ASCII alphanumeric, length `1..6`; future code changes never rewrite historical numbers.
- PDFs, logos, signatures, stamps, and other binary assets remain local and are never uploaded to Supabase.
- An issued certificate keeps one official number; corrections create `v2`, `v3`, ... revisions under that same number.
- Student identity, issue year, and series/category are immutable after first issue; changing them requires a new certificate identity/number.
- Official issued certificates are voided by default, not silently hard-deleted.
- Preview and PDF must consume the exact same HTML string/render snapshot.
- Required render nodes are `.cert-kicker`, `.cert-main-title`, `.cert-name-box strong`, `.cert-achievement`; export fails closed when any required node is blank, invisible, zero-size, out of A4 bounds, or overlaps protected footer/date/signature zones.
- `شهادة تقدير وإنجاز` is positioned directly below the header ornament diamond without negative-margin coupling to logo height.
- RC24 DB changes are additive first so RC23 clients can continue reading base `certificates` rows during transition.

## Review Focus

1. **Long Arabic student/title/achievement text:** must stay inside A4 and must not overlap dates/signatures/footer. Covered by Task 5 visual fixtures.
2. **Historical certificate with incomplete lightweight snapshot:** must migrate as `legacy_partial=true` and never invent missing original text/style. Covered by Task 2 migration contract tests and Task 7 archive UI tests.
3. **Series code changed after prior certificates exist:** old numbers stay unchanged while future allocation uses the new code. Covered by Task 2 SQL gate and Task 3 domain/repository tests.
4. **Local asset missing for an archived revision:** UI must report `الأصل البصري غير متوفر على هذا الجهاز` rather than silently substituting current assets as an exact historical render. Covered by Task 6 local archive tests and Task 7 archive UI tests.
5. **Concurrent issue/revision actions from multiple devices:** numbering and revision numbers remain atomic and duplicate-free. Covered by Task 2 RPC definitions plus Task 8 production-compatible verification queries.

---

### Task 1: Certificate domain models, render snapshot schema, and validation rules

**Files:**
- Modify: `app/src/main/java/com/mohammedsamir/halaqati/model/CertificateCore.kt`
- Create: `app/src/main/java/com/mohammedsamir/halaqati/model/CertificateRules.kt`
- Create: `app/src/test/java/com/mohammedsamir/halaqati/certificates/CertificateRulesTest.kt`
- Create: `scripts/kotlin-selftest/CertificateRulesSelfTest.kt`
- Modify: `scripts/run-kotlin-selftests.sh`

**Interfaces:**
- Consumes: existing `CertificateRecord`, `CertificateCoreDraft`, JSON parsing conventions.
- Produces:
  - `data class CertificateSeriesRow(...)`
  - `data class CertificateRevisionRow(...)`
  - `data class CertificateRenderSnapshot(...)`
  - `data class CertificateRequiredFieldError(val key: String, val arabicLabel: String)`
  - `object CertificateRules`
  - `fun normalizeSeriesCode(raw: String): String`
  - `fun validateRequiredSnapshot(snapshot: CertificateRenderSnapshot, requireOfficialNumber: Boolean): List<CertificateRequiredFieldError>`
  - `fun canReviseIdentity(original: CertificateRecord, candidateStudentId: String, candidateSeriesKey: String, candidateIssueDate: String): Boolean`
  - `fun isLegacyNumber(number: String?): Boolean`

- [ ] **Step 1: Write failing unit tests for series normalization and required body validation**

Add tests named:
- `normalizeSeriesCode_uppercases_ascii_and_limits_six_chars`
- `validateRequiredSnapshot_rejects_blank_title_student_and_achievement`
- `validateRequiredSnapshot_requires_number_only_for_official_export`
- `legacy_number_detection_preserves_pre_rc24_format`
- `revision_identity_rejects_student_series_or_issue_year_change`

Assertions must pin `S`, `K`, `G`, `HAL-CERT-2026-00009` legacy detection, and issue-year identity behavior.

- [ ] **Step 2: Run the new unit test class and verify RED**

Run: `python3 scripts/ci-build.py testDebugUnitTest 540 --tests com.mohammedsamir.halaqati.certificates.CertificateRulesTest`

Expected: FAIL because the RC24 models/rules do not exist.

- [ ] **Step 3: Implement the RC24 domain types and pure rules**

Keep the JSON schema value exact: `halaqati_certificate_render_v2`. `CertificateRenderSnapshot` must include the text/style/source/asset-ref fields from the spec and provide `toJson()` / `from(JSONObject)`.

- [ ] **Step 4: Add a matching pure Kotlin self-test and run GREEN**

Run: `bash scripts/run-kotlin-selftests.sh`

Expected: PASS including `CertificateRulesSelfTest`.

- [ ] **Step 5: Re-run the targeted JUnit test and commit**

Run: `python3 scripts/ci-build.py testDebugUnitTest 540 --tests com.mohammedsamir.halaqati.certificates.CertificateRulesTest`

Expected: PASS.

Commit:
```bash
git add app/src/main/java/com/mohammedsamir/halaqati/model/CertificateCore.kt app/src/main/java/com/mohammedsamir/halaqati/model/CertificateRules.kt app/src/test/java/com/mohammedsamir/halaqati/certificates/CertificateRulesTest.kt scripts/kotlin-selftest/CertificateRulesSelfTest.kt scripts/run-kotlin-selftests.sh
git commit -m "feat: add rc24 certificate domain rules"
```

### Task 2: Supabase RC24 schema, atomic numbering, revision RPCs, legacy migration, and security

**Files:**
- Create: `migration/20261009_v2600_rc24_certificate_archive_rendering_core.sql`
- Modify: `migration/source-contract.json`
- Create: `scripts/rc24-certificate-db-contract-check.py`

**Interfaces:**
- Consumes: `public.certificates`, `public.certificate_number_counters`, RC12.1 certificate RLS/scope rules, staff authorization helpers.
- Produces tables/columns:
  - `public.certificate_series`
  - `public.certificate_revisions`
  - `certificates.series_key`
  - `certificates.series_code_at_issue`
  - `certificates.current_revision_id`
  - `certificates.legacy_number`
  - `certificates.legacy_partial`
  - `(issue_year, series_key)` primary key/counter semantics in `certificate_number_counters`
- Produces RPCs:
  - `rc24_next_certificate_number(p_series_key text, p_issue_date date) returns text`
  - `rc24_issue_certificate(p_certificate_id uuid, p_series_key text, p_issue_date date, p_render_snapshot jsonb) returns jsonb`
  - `rc24_create_certificate_revision(p_certificate_id uuid, p_render_snapshot jsonb, p_note text) returns jsonb`
  - `rc24_void_certificate(p_certificate_id uuid, p_reason text) returns jsonb`
  - `rc24_import_legacy_certificate(...) returns jsonb`

- [ ] **Step 1: Write the static DB contract checker first**

The checker must fail unless the migration contains:
- all seven default series seeds and codes;
- independent `(issue_year, series_key)` counter key;
- both unique revision constraints;
- explicit `security definer` + safe `set search_path=pg_catalog, public` on RC24 RPCs;
- `revoke execute ... from anon` for each RC24 RPC;
- guardian revision read policy inherited through parent certificate;
- migration code that leaves existing `certificate_number` unchanged;
- backfill that marks incomplete old snapshots `legacy_partial=true` rather than inventing missing content.

- [ ] **Step 2: Run the checker and verify RED**

Run: `python3 scripts/rc24-certificate-db-contract-check.py`

Expected: FAIL because the RC24 migration does not exist.

- [ ] **Step 3: Implement the additive migration and update source contract**

Rules pinned by the migration:
- `certificate_series.code` matches `^[A-Z0-9]{1,6}$`.
- new allocations format exactly `HAL-CERT-<year>-<code>-<5 digits>`.
- issuance and revision allocation are atomic under row locks/unique constraints.
- revision 1 is created on first RC24 issue.
- revision creation rejects student/series/issue-year identity drift.
- legacy import does not advance live counters.
- existing `HAL-CERT-YYYY-NNNNN` rows retain their exact number.
- no PDF or asset byte columns are introduced.

Add the new tables/RPCs to `migration/source-contract.json` without removing RC23 entries.

- [ ] **Step 4: Run the DB contract checker and source contract checks**

Run:
```bash
python3 scripts/rc24-certificate-db-contract-check.py
python3 scripts/release-contract-check.py
```

Expected: RC24 DB contract PASS; release contract may still fail on version until Task 9, but must not report missing RC24 schema/RPC declarations.

- [ ] **Step 5: Commit**

```bash
git add migration/20261009_v2600_rc24_certificate_archive_rendering_core.sql migration/source-contract.json scripts/rc24-certificate-db-contract-check.py
git commit -m "feat: add rc24 certificate archive schema"
```

### Task 3: Repository API for series, authoritative revisions, issue, revise, void, and legacy import

**Files:**
- Modify: `app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt`
- Create: `app/src/test/java/com/mohammedsamir/halaqati/data/CertificateRepositoryContractTest.kt`
- Modify: `scripts/compiler-regression-check.py`

**Interfaces:**
- Consumes: Task 1 models and Task 2 RPC/table names.
- Produces:
  - `fun certificateSeries(forceRefresh: Boolean = false): List<CertificateSeriesRow>`
  - `fun certificateRevisions(certificateId: String, forceRefresh: Boolean = false): List<CertificateRevisionRow>`
  - `fun issueCertificateRc24(certificateId: String, seriesKey: String, issueDate: String, snapshot: CertificateRenderSnapshot): CertificateRecord`
  - `fun createCertificateRevision(certificateId: String, snapshot: CertificateRenderSnapshot, note: String? = null): CertificateRevisionRow`
  - `fun voidCertificate(certificateId: String, reason: String): CertificateRecord`
  - `fun importLegacyCertificate(...): CertificateRecord`

- [ ] **Step 1: Write failing repository contract tests**

Tests must inspect/execute pure repository mapping paths to prove:
- issued RC24 writes go through `rc24_issue_certificate`, not generic certificate update;
- revision creation calls `rc24_create_certificate_revision`;
- revision list parses `render_snapshot` and `revision_no`;
- series list parses enabled/disabled code settings;
- RC23 `certificates()` remains readable when RC24 fields are absent/null.

- [ ] **Step 2: Run targeted tests and verify RED**

Run: `python3 scripts/ci-build.py testDebugUnitTest 540 --tests com.mohammedsamir.halaqati.data.CertificateRepositoryContractTest`

Expected: FAIL because RC24 repository interfaces are missing.

- [ ] **Step 3: Implement repository methods and caches**

Add separate cache keys for series and revisions. Do not cache revision rows by official number; key by `certificate_id` and revision id/no. Keep the old RC12 methods temporarily for RC23 compatibility but stop calling them from RC24 UI.

- [ ] **Step 4: Run targeted tests and compiler regression**

Run:
```bash
python3 scripts/ci-build.py testDebugUnitTest 540 --tests com.mohammedsamir.halaqati.data.CertificateRepositoryContractTest
python3 scripts/compiler-regression-check.py
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt app/src/test/java/com/mohammedsamir/halaqati/data/CertificateRepositoryContractTest.kt scripts/compiler-regression-check.py
git commit -m "feat: add rc24 certificate repository api"
```

### Task 4: Canonical HTML layout and structured render gate

**Files:**
- Modify: `app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt`
- Modify: `app/src/main/assets/documents/certificate.css`
- Modify: `app/src/main/java/com/mohammedsamir/halaqati/documents/HtmlDocumentPreview.kt`
- Create: `app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateRenderGate.kt`
- Create: `app/src/test/java/com/mohammedsamir/halaqati/certificates/CertificateHtmlContractTest.kt`

**Interfaces:**
- Consumes: `CertificateRenderSnapshot`, existing `NativeCertificateAssets` until Task 6 completes the screen migration.
- Produces:
  - `data class CertificateRenderGateResult(val ready: Boolean, val layoutOk: Boolean, val requiredNodesPresent: Boolean, val requiredNodesVisible: Boolean, val requiredNodesInsidePage: Boolean, val noProtectedOverlap: Boolean, val invalidSections: List<String>, val renderRevision: String)`
  - `suspend fun LiveDocumentPreview.evaluateCertificateGate(view: WebView): CertificateRenderGateResult`
  - renderer script function `window.__HALAQATI_CERTIFICATE_GATE__()` returning the structured gate payload.

- [ ] **Step 1: Write failing HTML contract tests**

Tests must assert generated HTML contains:
- the four required selectors;
- a gate script named `__HALAQATI_CERTIFICATE_GATE__`;
- no negative-margin/translate coupling on `.cert-kicker`;
- kicker positioned after `.cert-orn-line` and before main title;
- the exact Arabic kicker text `شهادة تقدير وإنجاز`.

- [ ] **Step 2: Run test and verify RED**

Run: `python3 scripts/ci-build.py testDebugUnitTest 540 --tests com.mohammedsamir.halaqati.certificates.CertificateHtmlContractTest`

Expected: FAIL on missing structured render gate and old kicker positioning.

- [ ] **Step 3: Rework header/body CSS into fixed semantic zones and implement render gate JS**

The gate must reject blank text, hidden nodes, zero rects, out-of-page nodes, and overlap with `.cert-dates`, `.cert-signatures`, or `.cert-footer`. Optional intro/dua only participate when present/visible in the snapshot.

- [ ] **Step 4: Parse gate JSON in `CertificateRenderGate.kt` and wire preview readiness to it**

`PreviewRenderState.ready` for a certificate is not enough to authorize export; gate evaluation is a separate explicit result.

- [ ] **Step 5: Run tests and commit**

Run:
```bash
python3 scripts/ci-build.py testDebugUnitTest 540 --tests com.mohammedsamir.halaqati.certificates.CertificateHtmlContractTest
python3 scripts/compiler-regression-check.py
```

Expected: PASS.

Commit:
```bash
git add app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt app/src/main/assets/documents/certificate.css app/src/main/java/com/mohammedsamir/halaqati/documents/HtmlDocumentPreview.kt app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateRenderGate.kt app/src/test/java/com/mohammedsamir/halaqati/certificates/CertificateHtmlContractTest.kt
git commit -m "fix: make certificate rendering fail closed"
```

### Task 5: PDF export parity and visual regression fixtures

**Files:**
- Modify: `app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt`
- Create: `scripts/rc24-certificate-render-fixtures.py`
- Create: `scripts/fixtures/rc24-certificates/` fixture JSON files
- Modify: `scripts/rc13-render-fixtures.py` only if shared fixture helpers are reusable without changing report behavior

**Interfaces:**
- Consumes: Task 4 `CertificateRenderGateResult` and the exact HTML string already loaded in `LiveDocumentPreview`.
- Produces: export behavior that refuses official certificate PDF creation unless the structured gate passes; raster compatibility path captures the same already-laid-out WebView and never constructs a second certificate layout.

- [ ] **Step 1: Add failing visual fixtures**

Fixtures:
- normal certificate;
- very long Arabic student name;
- long two/three-line achievement;
- optional intro/dua hidden;
- number position under logo;
- number position top;
- footer-start/footer-end number;
- deliberately blank achievement reproducing the 00009 failure class;
- legacy partial archive preview.

The blank-body fixture must fail the gate even if header/footer are valid.

- [ ] **Step 2: Run fixtures and verify RED**

Run: `python3 scripts/rc24-certificate-render-fixtures.py --verify --out /tmp/rc24-certificate-fixtures`

Expected: FAIL because current export/render stack does not enforce the structured gate.

- [ ] **Step 3: Gate `VectorDocumentRenderer` certificate exports before print/write**

For official certificates, evaluate the gate on the same acquired live preview WebView. If invalid, throw an Arabic `IllegalStateException` listing the invalid sections. Isolated Chromium path must evaluate the same JS gate before creating the print adapter.

- [ ] **Step 4: Verify all visual fixtures**

Run: `python3 scripts/rc24-certificate-render-fixtures.py --verify --out /tmp/rc24-certificate-fixtures`

Expected: PASS; every valid fixture reports required nodes inside A4 with no protected overlap, and the intentional blank-body fixture is rejected.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt scripts/rc24-certificate-render-fixtures.py scripts/fixtures/rc24-certificates
git commit -m "fix: enforce certificate preview pdf parity"
```

### Task 6: Revision-aware local archive/cache and asset availability semantics

**Files:**
- Modify: `app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt` (`NativeCertificate` object only in this task)
- Create: `app/src/test/java/com/mohammedsamir/halaqati/certificates/CertificateLocalArchiveTest.kt`

**Interfaces:**
- Consumes: Task 1 snapshot/revision identifiers.
- Produces:
  - local archive schema `archive_v4` keyed by `(certificate_id, revision_no)`;
  - local fields `certificate_id`, `revision_id`, `revision_no`, `number`, `local_pdf_uri`, `asset_availability`, `last_rendered_at`;
  - merge/import that never overwrites distinct revisions sharing one official number.

- [ ] **Step 1: Write failing local archive tests**

Tests:
- `two_revisions_same_number_are_both_preserved`
- `import_merges_by_certificate_id_and_revision_no_not_number`
- `missing_asset_is_reported_not_substituted_as_exact`
- `legacy_v3_local_archive_migrates_without_losing_number_or_student`

- [ ] **Step 2: Run targeted tests and verify RED**

Run: `python3 scripts/ci-build.py testDebugUnitTest 540 --tests com.mohammedsamir.halaqati.certificates.CertificateLocalArchiveTest`

Expected: FAIL because `archive_v3` dedupes by number.

- [ ] **Step 3: Implement archive v4 and one-time v3 migration**

Keep local binary asset policy unchanged. Store only hashes/availability metadata with revision cache rows. Exact historical render must surface the Arabic missing-asset message when required historical assets are not locally available.

- [ ] **Step 4: Run tests and commit**

Run: `python3 scripts/ci-build.py testDebugUnitTest 540 --tests com.mohammedsamir.halaqati.certificates.CertificateLocalArchiveTest`

Expected: PASS.

Commit:
```bash
git add app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt app/src/test/java/com/mohammedsamir/halaqati/certificates/CertificateLocalArchiveTest.kt
git commit -m "feat: make local certificate archive revision aware"
```

### Task 7: Certificate Studio and official archive UX

**Files:**
- Modify: `app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt`
- Create: `app/src/androidTest/java/com/mohammedsamir/halaqati/ui/screens/CertificateStudioRc24Test.kt`
- Modify: `scripts/uiux-pro-max-check.py`

**Interfaces:**
- Consumes: Tasks 1–6 models, repository methods, renderer gate, local archive.
- Produces UI flows:
  - Create/Edit Draft
  - exact Live A4 Preview
  - Issue/Export
  - Official Archive with revision history
  - Series Settings for authorized management roles
  - Add Historical Archive Record

- [ ] **Step 1: Write failing UI/instrumentation tests**

Tests must prove:
- issue button is disabled/blocked for invalid required render content;
- issued archive card displays official number + revision count/current badge;
- `إنشاء نسخة مصححة` starts from the selected revision snapshot, not current global defaults;
- revision v2 preserves the same official number;
- changing student or series routes to `نسخ كشهادة جديدة` rather than revision;
- void action preserves archive entry and shows `ملغاة`;
- draft delete is available, issued hard-delete is absent from normal UI;
- legacy partial card shows its incomplete-original warning;
- series settings can edit future code but historical number shown on existing certificate does not change;
- historical archive add uses manual number and does not show live allocation wording.

- [ ] **Step 2: Compile/run instrumentation test and verify RED**

Run: `python3 scripts/ci-build.py compileDebugAndroidTestKotlin 420`

Expected: compile/test contract fails until RC24 UI semantics/components are present.

- [ ] **Step 3: Refactor `CertificatesScreen` into focused internal composables without changing navigation route**

Create internal composables/functions in the same file unless size forces a focused new file:
- `CertificateCreatePanel`
- `CertificatePreviewPanel`
- `CertificateArchivePanel`
- `CertificateRevisionSheet`
- `CertificateSeriesSettingsSheet`
- `LegacyCertificateImportSheet`

Do not create a second renderer. All preview/export actions use a `CertificateRenderSnapshot`.

- [ ] **Step 4: Replace local number preview allocation with series-aware server semantics**

Draft UI may show a non-reserved preview label such as `سيُخصص عند الإصدار`. The real official number is only consumed by `rc24_issue_certificate`.

- [ ] **Step 5: Wire revision/void/import/archive actions to repository APIs**

After any mutation, refresh authoritative server archive and revision list, then update the local revision cache. Do not mutate issued certificate rows through generic `write("certificates", ...)`.

- [ ] **Step 6: Run UI compile, UIUX gate, and commit**

Run:
```bash
python3 scripts/ci-build.py compileDebugAndroidTestKotlin 420
python3 scripts/uiux-pro-max-check.py
```

Expected: PASS.

Commit:
```bash
git add app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt app/src/androidTest/java/com/mohammedsamir/halaqati/ui/screens/CertificateStudioRc24Test.kt scripts/uiux-pro-max-check.py
git commit -m "feat: rebuild rc24 certificate studio archive ux"
```

### Task 8: RC24 integration gate, migration safety checks, and production-compatible verification

**Files:**
- Create: `scripts/rc24-certificate-archive-rendering-check.py`
- Modify: `scripts/full-release-parity-check.py`
- Modify: `scripts/parity-matrix-check.py` if certificate parity declarations require new checks
- Create: `RELEASE_2_60_0_RC24_CERTIFICATE_ARCHIVE_RENDERING_CORE_AR.md`

**Interfaces:**
- Consumes: all previous tasks.
- Produces one deterministic RC24 source gate used by CI.

- [ ] **Step 1: Write the RC24 source gate and run it RED before all markers are present**

The gate must check:
- target migration exists and is referenced by workflow/release contract;
- `halaqati_certificate_render_v2` exists;
- required render selectors + structured gate exist;
- no number dedupe by number alone remains in local archive;
- RC24 repository RPC names are present;
- normal issued UI has void/revision actions and no default hard-delete;
- legacy numbers are explicitly preserved;
- release note states PDFs/assets remain local.

Run: `python3 scripts/rc24-certificate-archive-rendering-check.py`

Expected: FAIL until every RC24 requirement is wired.

- [ ] **Step 2: Add production-compatible SQL verification queries to the release notes/checklist**

Queries must verify after migration:
- all existing certificate numbers unchanged;
- certificate `HAL-CERT-2026-00009` remains the same number and is `legacy_partial` unless a complete snapshot exists;
- seven default series rows exist;
- counters are keyed by year + series;
- no duplicate `(certificate_id, revision_no)`;
- no anon execute grant on RC24 SECURITY DEFINER RPCs.

Do not mutate production in this task; this is verification definition only.

- [ ] **Step 3: Run integration/source parity gates**

Run:
```bash
python3 scripts/rc24-certificate-db-contract-check.py
python3 scripts/rc24-certificate-archive-rendering-check.py
python3 scripts/rc24-certificate-render-fixtures.py --verify --out /tmp/rc24-certificate-fixtures
python3 scripts/full-release-parity-check.py
bash scripts/run-kotlin-selftests.sh
```

Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add scripts/rc24-certificate-archive-rendering-check.py scripts/full-release-parity-check.py scripts/parity-matrix-check.py RELEASE_2_60_0_RC24_CERTIFICATE_ARCHIVE_RENDERING_CORE_AR.md
git commit -m "test: add rc24 certificate release gates"
```

### Task 9: Version 2.60.0, workflow, release artifacts, and final verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `migration/source-contract.json`
- Modify: `scripts/release-contract-check.py`
- Modify: `scripts/make-update-publish.py`
- Create: `Halaqati_RC24.yml` at packaging time / workflow destination
- Package: `Halaqati_RC24.zip`
- Package: `Halaqati_RC24.sql` (copy of the exact RC24 migration)

**Interfaces:**
- Consumes: all RC24 code/migration/gates.
- Produces release `2.60.0`, `26000`, short artifact names, GitHub Actions build/update workflow, exact ZIP SHA-256 pin.

- [ ] **Step 1: Write/update release-contract expectations first**

`release-contract-check.py` must expect `versionCode = 26000`, `versionName = "2.60.0"`, `native_version = 2.60.0`, RC24 migration, and RC24 release note.

- [ ] **Step 2: Run release contract and verify RED**

Run: `python3 scripts/release-contract-check.py`

Expected: FAIL while Gradle/update metadata still says 2.59.0.

- [ ] **Step 3: Update Gradle, updater metadata, source contract version, and workflow**

The workflow must:
- verify exact `Halaqati_RC24.zip` SHA-256;
- require `migration/20261009_v2600_rc24_certificate_archive_rendering_core.sql`;
- run RC23 regression gates plus all RC24 gates;
- run targeted unit tests, Kotlin compile, Android test compile, full tests, lint, assemble, APK identity/signing verification;
- package `RC24_REQUIRED_SUPABASE_MIGRATION.sql` but state clearly when production migration is already applied;
- publish `Halaqati-v2.60.0-release.apk` and direct-update package.

- [ ] **Step 4: Run the complete local source verification available in this environment**

Run:
```bash
python3 scripts/rc24-certificate-db-contract-check.py
python3 scripts/rc24-certificate-archive-rendering-check.py
python3 scripts/rc24-certificate-render-fixtures.py --verify --out /tmp/rc24-certificate-fixtures
python3 scripts/release-contract-check.py
python3 scripts/full-release-parity-check.py
python3 scripts/compiler-regression-check.py
bash scripts/run-kotlin-selftests.sh
```

Expected: all PASS.

- [ ] **Step 5: Run Gradle verification when Android SDK is available**

Run:
```bash
python3 scripts/ci-build.py testReleaseUnitTest 540
python3 scripts/ci-build.py compileReleaseKotlin 540
python3 scripts/ci-build.py compileDebugAndroidTestKotlin 420
python3 scripts/ci-build.py lintRelease 540
python3 scripts/ci-build.py assembleRelease 540
```

Expected: all PASS. If local Android SDK is unavailable, do not claim these passed; GitHub Actions must provide the evidence.

- [ ] **Step 6: Package and verify short-name artifacts**

Create:
- `Halaqati_RC24.zip`
- `Halaqati_RC24.yml`
- `Halaqati_RC24.sql`

Then verify ZIP integrity, migration byte identity, YAML parse, and exact SHA-256 pin.

- [ ] **Step 7: Final whole-branch verification and commit**

Run `git diff --check`, the complete verification commands above, and inspect the final diff against the spec acceptance criteria.

Commit:
```bash
git add app/build.gradle.kts migration/source-contract.json scripts/release-contract-check.py scripts/make-update-publish.py Halaqati_RC24.yml RELEASE_2_60_0_RC24_CERTIFICATE_ARCHIVE_RENDERING_CORE_AR.md
git commit -m "release: prepare halaqati v2.60.0 rc24"
```

## Plan Self-Review

- **Spec coverage:** all 17 spec sections map to Tasks 1–9: domain/snapshot, DB numbering/revisions/security, repository, single renderer/render gate, PDF parity, local archive, Studio/archive UX, migration/production checks, and release/rollback compatibility.
- **Step scan:** every implementation task begins with a failing test/gate, then minimal implementation, then explicit verification and commit.
- **Type consistency:** `CertificateRenderSnapshot`, `CertificateSeriesRow`, `CertificateRevisionRow`, and `CertificateRenderGateResult` are defined once and consumed by later tasks under the same names.
- **Review Focus:** all five high-risk input classes have owning tests/tasks listed above.
- **Proportion:** the plan specifies interfaces, test names, exact invariants, and commands without transcribing production function bodies.
