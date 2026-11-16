-- Halaqati Native v2.45.2 — SECURITY REVIEW ONLY
-- Generated from Supabase Security Advisor findings on 2026-09-09.
-- IMPORTANT: This file is intentionally outside supabase/migrations and is NOT auto-applied.
-- Review against production function definitions before moving any statement into a real migration.

begin;

-- Fix mutable search_path on the notification trigger helper.
alter function public.touch_notification_updated_at()
  set search_path = public, pg_temp;

-- These three range RPCs are intentionally callable by authenticated users only.
-- Explicitly remove anonymous/public EXECUTE while preserving the app contract.
revoke all on function public.guardian_student_dashboard_range(uuid,date,date) from anon;
revoke all on function public.guardian_student_dashboard_range(uuid,date,date) from public;
grant execute on function public.guardian_student_dashboard_range(uuid,date,date) to authenticated;

revoke all on function public.guardian_competition_board_range(uuid,text,text,date,date) from anon;
revoke all on function public.guardian_competition_board_range(uuid,text,text,date,date) from public;
grant execute on function public.guardian_competition_board_range(uuid,text,text,date,date) to authenticated;

revoke all on function public.student_rank_snapshot_range(uuid,date,date) from anon;
revoke all on function public.student_rank_snapshot_range(uuid,date,date) from public;
grant execute on function public.student_rank_snapshot_range(uuid,date,date) to authenticated;

commit;

-- Advisor also reported anonymous EXECUTE on the following SECURITY DEFINER functions:
--   public.halaqati_delete_cancelled_session()
--   public.link_guardian_profile()
--   public.link_student_guardian_and_teacher()
--   public.protect_last_admin()
--   public.protect_profile_security_fields()
-- Their current production definitions are not present in the audited v2.44 source tree.
-- Do NOT revoke/alter them blindly. Fetch the live definitions first and classify each as:
-- trigger-only / authenticated RPC / service-only. Then grant only the minimum role required.
--
-- Advisor also reports:
--   * public.halaqati_internal_secrets has RLS enabled with no policies.
--     If this table is intentionally server-only, no client policy should be added.
--   * leaked-password protection is disabled in Supabase Auth.
--     This is an Auth setting, not a SQL migration.
