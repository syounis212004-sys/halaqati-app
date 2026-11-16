# Supabase security review — Native v2.45.2

Read-only Supabase Security Advisor was checked on 2026-09-09 while hardening the Native migration.

Current actionable findings relevant to the app contract:
- `touch_notification_updated_at()` has a mutable `search_path`.
- Anonymous execution is exposed on three guardian/ranking range `SECURITY DEFINER` RPCs used by the Native app.
- Five additional `SECURITY DEFINER` functions are anonymously executable but their live definitions are not present in the audited v2.44 source snapshot, so they are not changed blindly.
- `halaqati_internal_secrets` has RLS enabled and no policies. This can be correct for a server-only table and should not be “fixed” by adding a client policy without intent.
- Leaked-password protection is disabled in Supabase Auth and requires an Auth setting change, not application code.

`20260909_v2452_security_hardening_REVIEW.sql` is review-only and is deliberately outside `supabase/migrations` so the Native build cannot apply production database changes accidentally.
