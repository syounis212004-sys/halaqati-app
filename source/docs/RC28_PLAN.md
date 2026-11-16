# RC28 implementation plan

Goal: deliver the approved Arabic native interface redesign as version 2.64.0 RC28.
Architecture: keep existing repositories and domain engines; redesign composables around the current callback contracts. Shared tokens provide cross-screen consistency.
Spec: docs/RC28_DESIGN.md.

## Tasks
1. Shared foundation and navigation (root): Theme.kt, CairoTypography.kt, HalaqatiPalette.kt, Components.kt, GoldenShell.kt, HalaqatiMotionKit.kt. Reduce layered borders, establish hierarchy, compact toolbar, group drawer actions and shorten motion. Validate contrast and existing navigation rules.
2. Core daily workflow (implementer): CoreScreens.kt plus optional new local composable files. Compact student/session cards, reorganize roster chrome and assignment cards, quick notes/attendance sheets, plan phase readability. Preserve callbacks and full recitation editor. Review diff for behavior regressions.
3. Home/profile/guardian/report polish (root): DashboardScreen.kt, StudentProfileScreen.kt, UnifiedCards.kt, ReportsAndTools.kt and shared component consumers. Preserve role-specific sections and current values.
4. Sync presentation (root): introduce pure SyncPresentationRules.kt and regression tests for known RPC labels, safe unknown fallback, query stripping. Refine SyncCenterScreen.kt and preserve diagnostics behind expansion.
5. Release integration (root): version 2.64.0/code 26400, release notes, retain prior server hotfix SQL for reproducibility, create Halaqati_RC28.zip and Halaqati_RC28.yml from existing workflow, bind SHA-256, preserve signing and checks.
6. Verification/review: domain tests, available compiler/build checks, final independent review, fix important findings, validate archive and upload or save deliverables.

## Ledger
- Baseline: GitHub main 2c4c495e1162b17212b814c804cea72c7ed3e350 / Halaqati_RC27_50.zip, extracted into isolated feature branch.
- Ruling: use one implementation agent for the independent large CoreScreens task, while root owns other files and release integration. No two implementation agents run simultaneously.
- Ruling: existing long forms stay full-screen to preserve safe keyboard/scroll behavior. Quick, bounded actions use bottom sheets.

- Completed shared theme/chrome, daily workflow, home/profile/guardian/reports and sync presentation.
- Preserved existing role policy, data callbacks, domain engines, signing identity and source layout.
- Source gates updated for approved 16dp gutters/cards, 20dp hero radius, Cairo 600/700/800 weights and native NavigationBar semantics. Legacy version allowlists gained 2.64.0/26400; domain checks remain intact.
- New sync-label regression test was observed failing before implementation and passing all six cases afterward; full pure Kotlin suite passes.
- Independent CoreScreens review and whole-branch review completed. Fixed latest-recitation aggregation, scrollable quick actions, unique navigation selection and reduced-motion presentation for all new sheets.
- Release uses the original signing/secrets/publish workflow with a new archive checksum; prior server fix is documentation-only and is not reapplied.
- GitHub write attempt rejected HTTP 403 Resource not accessible by integration; deliverables supplied for user upload.
- Final Android validation: Kotlin application compile, 124 JVM unit tests, instrumented-test compilation and debug lint all passed. Browser fixture checks were blocked by the environment; on-device visual checks remain outstanding.
