-- Halaqati Native v2.50.0 RC15 — Tathbit first-class activity + smart plan integrity
-- Apply BEFORE installing the RC15 APK. Idempotent. No data is deleted; RLS is NOT relaxed.

-- 1) Recitation activity: explicit 'tathbit' (was stored as review_far before RC15).
--    ADD VALUE must run outside the transaction below.
alter type public.activity_type add value if not exists 'tathbit';

begin;

-- 2) Phases may be tathbit.
alter table public.smart_plan_phases drop constraint if exists smart_plan_phases_phase_type_check;
alter table public.smart_plan_phases
  add constraint smart_plan_phases_phase_type_check check (phase_type in ('hifz','sard','tathbit'));

-- 3) Scope normalization (same as the live hotfix; kept here so the repo matches production).
create or replace function public.rc15_normalize_smart_plan()
returns trigger language plpgsql as $$
begin
  if new.scope_type = 'student' then
    new.category_id := null;
    if exists (select 1 from public.students s where s.id = new.student_id and s.track_code = 'tathbit') then
      new.plan_type := 'tathbit';
      new.review_tier := 'none';
    end if;
  elsif new.scope_type = 'category' then
    new.student_id := null;
  elsif new.scope_type = 'halaqa' then
    new.student_id := null;
    new.category_id := null;
  end if;
  return new;
end $$;
drop trigger if exists rc15_normalize_smart_plan on public.smart_plans;
create trigger rc15_normalize_smart_plan before insert or update on public.smart_plans
for each row execute function public.rc15_normalize_smart_plan();

-- 4) Engine guard: tathbit plans own tathbit phases only; other plans never get tathbit.
create or replace function public.rc15_guard_phase_type()
returns trigger language plpgsql as $$
declare v_type text;
begin
  select p.plan_type into v_type from public.smart_plans p where p.id = new.plan_id;
  if v_type = 'tathbit' and new.phase_type <> 'tathbit' then
    raise exception 'خطة التثبيت تقبل مرحلة تثبيت فقط' using errcode = '23514';
  elsif v_type is not null and v_type <> 'tathbit' and new.phase_type = 'tathbit' then
    raise exception 'مرحلة التثبيت لخطط مسار التثبيت فقط' using errcode = '23514';
  end if;
  return new;
end $$;
drop trigger if exists rc15_guard_phase_type on public.smart_plan_phases;
create trigger rc15_guard_phase_type before insert or update of phase_type, plan_id on public.smart_plan_phases
for each row execute function public.rc15_guard_phase_type();

-- 5) New recitations of a tathbit student are tathbit (or test). Historical rows untouched.
create or replace function public.rc15_guard_recitation_track()
returns trigger language plpgsql as $$
begin
  if exists (select 1 from public.students s where s.id = new.student_id and s.track_code = 'tathbit')
     and new.activity_type::text not in ('tathbit','test') then
    if new.activity_type::text in ('review','review_near','review_far') then
      new.activity_type := 'tathbit'::public.activity_type;
    else
      raise exception 'طالب مسار التثبيت يُسجَّل له تثبيت فقط' using errcode = '23514';
    end if;
  end if;
  return new;
end $$;
drop trigger if exists rc15_guard_recitation_track on public.recitation_entries;
create trigger rc15_guard_recitation_track before insert on public.recitation_entries
for each row execute function public.rc15_guard_recitation_track();

-- 6) Atomic bundle: plan + phases + phase states in one transaction. SECURITY INVOKER,
--    so the caller's RLS still applies to every row. Any failure rolls back everything.
create or replace function public.rc15_create_smart_plan_bundle(p_plan jsonb, p_phases jsonb default '[]', p_states jsonb default '[]')
returns uuid language plpgsql security invoker as $$
declare v_plan_id uuid;
begin
  insert into public.smart_plans select * from jsonb_populate_record(null::public.smart_plans, p_plan)
  on conflict (id) do nothing
  returning id into v_plan_id;
  v_plan_id := coalesce(v_plan_id, (p_plan->>'id')::uuid);
  if v_plan_id is null then raise exception 'plan id missing' using errcode = '23502'; end if;
  insert into public.smart_plan_phases
    select * from jsonb_populate_recordset(null::public.smart_plan_phases,
      (select coalesce(jsonb_agg(e || jsonb_build_object('plan_id', v_plan_id)), '[]') from jsonb_array_elements(p_phases) e))
  on conflict (id) do nothing;
  insert into public.smart_plan_phase_states
    select * from jsonb_populate_recordset(null::public.smart_plan_phase_states, p_states)
  on conflict (phase_id, student_id) do nothing;
  return v_plan_id;
end $$;
grant execute on function public.rc15_create_smart_plan_bundle(jsonb, jsonb, jsonb) to authenticated;

-- 7) Consistency report (read-only). Surfaces problems instead of hiding them.
create or replace view public.rc15_tathbit_consistency_issues with (security_invoker = true) as
  select 'tathbit_student_non_tathbit_plan'::text as issue, p.id as record_id, p.student_id, p.plan_type as value
  from public.smart_plans p join public.students s on s.id = p.student_id
  where p.scope_type = 'student' and s.track_code = 'tathbit' and p.plan_type <> 'tathbit' and p.status <> 'archived'
  union all
  select 'tathbit_plan_non_tathbit_phase', ph.id, p.student_id, ph.phase_type
  from public.smart_plan_phases ph join public.smart_plans p on p.id = ph.plan_id
  where p.plan_type = 'tathbit' and ph.phase_type <> 'tathbit' and ph.status <> 'archived'
  union all
  select 'general_student_tathbit_plan', p.id, p.student_id, p.plan_type
  from public.smart_plans p join public.students s on s.id = p.student_id
  where p.scope_type = 'student' and s.track_code <> 'tathbit' and p.plan_type = 'tathbit' and p.status <> 'archived';
grant select on public.rc15_tathbit_consistency_issues to authenticated;

commit;
