-- Halaqati v2.50.1 · RC15.1 — Tathbit phase isolation (run after the RC15 migration)
-- Root cause: a student whose plan already had hifz+sard phases was moved to tathbit;
-- rc15_normalize_smart_plan flipped plan_type to 'tathbit' but kept the hifz/sard phases.
begin;

-- 1) Guard: allow archiving a mismatched phase (so repair is possible), block everything else.
create or replace function public.rc15_guard_phase_type()
returns trigger language plpgsql as $$
declare v_type text;
begin
  if new.status = 'archived' then return new; end if;
  select p.plan_type into v_type from public.smart_plans p where p.id = new.plan_id;
  if v_type = 'tathbit' and new.phase_type <> 'tathbit' then
    raise exception 'خطة التثبيت تقبل مرحلة تثبيت فقط' using errcode = '23514';
  elsif v_type is not null and v_type <> 'tathbit' and new.phase_type = 'tathbit' then
    raise exception 'مرحلة التثبيت لخطط مسار التثبيت فقط' using errcode = '23514';
  end if;
  return new;
end $$;

-- 2) When a plan becomes tathbit, archive its hifz/sard phases automatically.
create or replace function public.rc151_archive_foreign_phases()
returns trigger language plpgsql as $$
begin
  if new.plan_type = 'tathbit' and (tg_op = 'INSERT' or old.plan_type is distinct from 'tathbit') then
    update public.smart_plan_phases set status = 'archived'
     where plan_id = new.id and phase_type <> 'tathbit' and status <> 'archived';
  end if;
  return new;
end $$;
drop trigger if exists rc151_archive_foreign_phases on public.smart_plans;
create trigger rc151_archive_foreign_phases after insert or update of plan_type on public.smart_plans
  for each row execute function public.rc151_archive_foreign_phases();

-- 3) When a student's track changes to tathbit, re-normalize their student-scope plans
--    (fires rc15_normalize_smart_plan → plan_type tathbit → trigger 2 archives phases).
create or replace function public.rc151_student_track_changed()
returns trigger language plpgsql as $$
begin
  if new.track_code is distinct from old.track_code and new.track_code = 'tathbit' then
    update public.smart_plans set plan_type = 'tathbit', review_tier = 'none'
     where scope_type = 'student' and student_id = new.id and plan_type <> 'tathbit' and status <> 'archived';
  end if;
  return new;
end $$;
drop trigger if exists rc151_student_track_changed on public.students;
create trigger rc151_student_track_changed after update of track_code on public.students
  for each row execute function public.rc151_student_track_changed();

-- 4) One-time repair of existing rows (reversible: status only).
update public.smart_plan_phases ph set status = 'archived'
  from public.smart_plans p
 where p.id = ph.plan_id and p.plan_type = 'tathbit' and ph.phase_type <> 'tathbit' and ph.status <> 'archived';

commit;

-- Check: must return 0 rows
-- select p.id, ph.phase_type from smart_plans p join smart_plan_phases ph on ph.plan_id=p.id
--  where p.plan_type='tathbit' and ph.phase_type<>'tathbit' and ph.status<>'archived';
