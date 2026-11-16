-- Halaqati RC27 (2.63.0) — security hardening + notification cleanup.
-- Safe to run more than once. Does not change any data except what rc27_cleanup_notifications is later asked to clean.
-- pg_cron jobs run as postgres and are not affected by these REVOKEs.

-- 1) No sign-in => no access. Anonymous callers must not execute any of these SECURITY DEFINER functions.
revoke execute on function public.guardian_child_overview(uuid, date) from anon, public;
revoke execute on function public.guardian_competition_board_range(uuid, text, text, date, date) from anon, public;
revoke execute on function public.guardian_student_dashboard_range(uuid, date, date) from anon, public;
revoke execute on function public.student_rank_snapshot_range(uuid, date, date) from anon, public;
revoke execute on function public.rc152_roll_tathbit_months() from anon, public;
revoke execute on function public.rc152_tathbit_consistency() from anon, public;
revoke execute on function public.rc15_create_smart_plan_bundle(jsonb, jsonb, jsonb) from anon, public;

-- 2) Trigger functions are never meant to be called through the API.
revoke execute on function public.halaqati_delete_cancelled_session() from anon, authenticated, public;
revoke execute on function public.link_guardian_profile() from anon, authenticated, public;
revoke execute on function public.link_student_guardian_and_teacher() from anon, authenticated, public;
revoke execute on function public.protect_last_admin() from anon, authenticated, public;
revoke execute on function public.protect_profile_security_fields() from anon, authenticated, public;
revoke execute on function public.rc18_audit_management_mutation() from anon, authenticated, public;
revoke execute on function public.rc24_4_snapshot_session_before_delete() from anon, authenticated, public;
revoke execute on function public.rc15_guard_phase_type() from anon, authenticated, public;
revoke execute on function public.rc15_guard_recitation_track() from anon, authenticated, public;
revoke execute on function public.rc15_normalize_smart_plan() from anon, authenticated, public;
revoke execute on function public.rc151_archive_foreign_phases() from anon, authenticated, public;
revoke execute on function public.rc151_student_track_changed() from anon, authenticated, public;
revoke execute on function public.touch_notification_updated_at() from anon, authenticated, public;

-- 3) Fixed search_path (Supabase advisor: function_search_path_mutable).
alter function public.touch_notification_updated_at() set search_path = public;
alter function public.rc15_guard_phase_type() set search_path = public;
alter function public.rc15_guard_recitation_track() set search_path = public;
alter function public.rc15_normalize_smart_plan() set search_path = public;
alter function public.rc15_create_smart_plan_bundle(jsonb, jsonb, jsonb) set search_path = public;
alter function public.rc151_archive_foreign_phases() set search_path = public;
alter function public.rc151_student_track_changed() set search_path = public;

-- 4) Notification housekeeping. Old unread notifications are marked read, old read ones are deleted.
--    Everyone cleans their own; an admin may clean everybody's with p_all = true.
create or replace function public.rc27_cleanup_notifications(p_days integer default 30, p_all boolean default false)
returns jsonb
language sql
security definer
set search_path = public
as $fn$
  with scope as (select auth.uid() as u, coalesce(public.is_admin(), false) as adm),
  marked as (
    update public.notifications n set read_at = now()
      from scope s
     where n.read_at is null
       and s.u is not null
       and n.created_at < now() - make_interval(days => greatest(p_days, 7) * 2)
       and (n.recipient_id = s.u or (p_all and s.adm))
    returning n.id
  ),
  removed as (
    delete from public.notifications n
      using scope s
     where n.read_at is not null
       and s.u is not null
       and n.created_at < now() - make_interval(days => greatest(p_days, 7))
       and (n.recipient_id = s.u or (p_all and s.adm))
    returning n.id
  )
  select jsonb_build_object('marked_read', (select count(*) from marked), 'deleted', (select count(*) from removed));
$fn$;

revoke execute on function public.rc27_cleanup_notifications(integer, boolean) from anon, public;
grant execute on function public.rc27_cleanup_notifications(integer, boolean) to authenticated;
