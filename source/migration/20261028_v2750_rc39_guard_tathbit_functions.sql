-- RC39 (2.75.0) — ALREADY APPLIED on 2026-10-28 (kept here as the record).
-- rc152_roll_tathbit_months() was a SECURITY DEFINER function callable by any signed-in user (guardian, student...)
-- and it freezes expired tathbit plans and creates next-month plans for every tathbit student.
-- It is now a staff-only wrapper (admin / supervisor / teacher). pg_cron runs it without a signed-in user and stays allowed.
-- rc152_tathbit_consistency() (counts only) is now admin / supervisor only.

alter function public.rc152_roll_tathbit_months() rename to rc152_roll_tathbit_months_core;
revoke all on function public.rc152_roll_tathbit_months_core() from public, anon, authenticated;
create function public.rc152_roll_tathbit_months() returns jsonb language plpgsql security definer set search_path to 'public' as $$
begin
  if auth.uid() is not null and coalesce(public.current_role()::text, '') not in ('admin','supervisor','teacher') then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  return public.rc152_roll_tathbit_months_core();
end $$;
revoke all on function public.rc152_roll_tathbit_months() from public, anon;
grant execute on function public.rc152_roll_tathbit_months() to authenticated, service_role;

alter function public.rc152_tathbit_consistency() rename to rc152_tathbit_consistency_core;
revoke all on function public.rc152_tathbit_consistency_core() from public, anon, authenticated;
create function public.rc152_tathbit_consistency() returns jsonb language plpgsql security definer set search_path to 'public' as $$
begin
  if auth.uid() is not null and coalesce(public.current_role()::text, '') not in ('admin','supervisor') then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  return public.rc152_tathbit_consistency_core();
end $$;
revoke all on function public.rc152_tathbit_consistency() from public, anon;
grant execute on function public.rc152_tathbit_consistency() to authenticated, service_role;
