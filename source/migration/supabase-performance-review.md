# Supabase performance review — read-only, 2026-09-09

This file records the latest Supabase Database Advisor findings observed while freezing Native 2.45.2. **Nothing in this review has been applied to production.**

## Findings relevant to the Native app

1. **Unindexed foreign keys** were reported on several public tables used by the app, including `announcements`, `forum_posts`, `forum_comments`, `data_import_jobs`, `notifications`, `student_points`, `students`, and `supervisor_visits`. These do not make the app incorrect, but can increase join/delete/update cost as data grows.
2. **RLS init-plan warnings** were reported on policies for `profiles`, `students`, `notifications`, `user_devices`, `notification_preferences`, `halaqas`, `halaqa_supervisors`, forum tables, and others. The advisor recommends wrapping `auth.<function>()` calls in `(select auth.<function>())` where semantically equivalent, so the value is not re-evaluated per row.
3. **Multiple permissive policies** were reported on a subset of tables such as `announcements`, `data_import_jobs`, `student_goals`, `student_points`, `supervisor_visits`, and backup smart-plan tables.
4. Several indexes were reported as unused. No index-removal recommendation is accepted during release freeze because short observation windows and low current row counts can make useful indexes appear unused.

## Release decision

- Do **not** alter RLS or remove indexes as part of the Native APK release freeze.
- Do **not** apply broad `CREATE INDEX` changes without first checking existing query plans and production migration history.
- Treat database tuning as a separate, reversible migration after Native 2.45.2 passes role/device regression.
- Priority for a later tuning migration: high-frequency Guardian/notifications/forum/supervision queries, then import history.

Reference: Supabase Database Advisor remediation pages for `unindexed_foreign_keys`, `auth_rls_initplan`, and `multiple_permissive_policies`.
