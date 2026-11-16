-- ============================================================================
-- Halaqati v2.50.0 RC15.2 — Tathbit Core Track (monthly plans, no phases)
-- Run AFTER RC15 and RC15.1 migrations. Idempotent. Never deletes history.
-- ============================================================================
begin;

-- 1) Tathbit recitations link directly to the monthly plan (phase_id stays null).
alter table public.recitation_entries
  add column if not exists plan_id uuid references public.smart_plans(id) on delete set null;
create index if not exists recitation_entries_plan_id_idx
  on public.recitation_entries(plan_id) where plan_id is not null;

-- 2) Monthly period + frozen result + legacy marker on plans.
alter table public.smart_plans
  add column if not exists period_month date,
  add column if not exists frozen_at timestamptz,
  add column if not exists frozen_summary jsonb,
  add column if not exists legacy boolean not null default false;
create index if not exists smart_plans_tathbit_student_period_idx
  on public.smart_plans(student_id, start_date, end_date) where plan_type = 'tathbit';

-- 3) Plan normalization: tathbit students get tathbit plans; legacy rows are left untouched.
create or replace function public.rc15_normalize_smart_plan()
returns trigger language plpgsql as $$
begin
  if new.scope_type = 'student' then
    new.category_id := null;
    if coalesce(new.legacy, false) = false
       and exists (select 1 from public.students s where s.id = new.student_id and s.track_code = 'tathbit') then
      new.plan_type := 'tathbit';
      new.review_tier := 'none';
    end if;
  elsif new.scope_type = 'category' then
    new.student_id := null;
  elsif new.scope_type = 'halaqa' then
    new.student_id := null;
    new.category_id := null;
  end if;
  if new.plan_type = 'tathbit' and new.period_month is null then
    new.period_month := date_trunc('month', new.start_date)::date;
  end if;
  return new;
end $$;

-- 4) Tathbit plans have NO phases at all. General plans never get a tathbit phase.
create or replace function public.rc15_guard_phase_type()
returns trigger language plpgsql as $$
declare v_type text;
begin
  if new.status = 'archived' then return new; end if;
  select p.plan_type into v_type from public.smart_plans p where p.id = new.plan_id;
  if v_type = 'tathbit' then
    raise exception 'خطة التثبيت شهرية بلا مراحل؛ لا يمكن إضافة مرحلة لها' using errcode = '23514';
  elsif new.phase_type = 'tathbit' then
    raise exception 'مرحلة التثبيت غير مستخدمة؛ التثبيت خطة شهرية مستقلة' using errcode = '23514';
  end if;
  return new;
end $$;
drop trigger if exists rc15_guard_phase_type on public.smart_plan_phases;
create trigger rc15_guard_phase_type
  before insert or update of phase_type, plan_id, status on public.smart_plan_phases
  for each row execute function public.rc15_guard_phase_type();

-- When a plan becomes tathbit, every live phase under it is archived (history kept).
create or replace function public.rc151_archive_foreign_phases()
returns trigger language plpgsql as $$
begin
  if new.plan_type = 'tathbit' and (tg_op = 'INSERT' or old.plan_type is distinct from 'tathbit') then
    update public.smart_plan_phases set status = 'archived'
     where plan_id = new.id and status <> 'archived';
  end if;
  return new;
end $$;

-- 5) Recitation guard: tathbit student → tathbit/test only, phase_id null, plan_id = monthly plan.
create or replace function public.rc15_guard_recitation_track()
returns trigger language plpgsql as $$
declare v_tathbit boolean; v_plan_type text; v_date date;
begin
  select (s.track_code = 'tathbit') into v_tathbit from public.students s where s.id = new.student_id;
  if coalesce(v_tathbit, false) then
    if new.activity_type::text not in ('tathbit','test') then
      if new.activity_type::text in ('review','review_near','review_far') then
        new.activity_type := 'tathbit'::public.activity_type;
      else
        raise exception 'طالب مسار التثبيت يُسجَّل له تثبيت فقط' using errcode = '23514';
      end if;
    end if;
    new.phase_id := null;
    if new.plan_id is not null then
      select p.plan_type into v_plan_type from public.smart_plans p where p.id = new.plan_id;
      if v_plan_type is distinct from 'tathbit' then new.plan_id := null; end if;
    end if;
    if new.plan_id is null then
      select s.session_date into v_date from public.sessions s where s.id = new.session_id;
      select p.id into new.plan_id from public.smart_plans p
       where p.student_id = new.student_id and p.plan_type = 'tathbit' and p.status <> 'archived'
         and coalesce(v_date, current_date) between p.start_date and p.end_date
       order by p.start_date desc limit 1;
    end if;
  else
    if new.activity_type::text = 'tathbit' then
      raise exception 'التثبيت نشاط خاص بطلاب مسار التثبيت' using errcode = '23514';
    end if;
    if new.plan_id is not null
       and exists (select 1 from public.smart_plans p where p.id = new.plan_id and p.plan_type = 'tathbit') then
      new.plan_id := null;
    end if;
  end if;
  return new;
end $$;
drop trigger if exists rc15_guard_recitation_track on public.recitation_entries;
create trigger rc15_guard_recitation_track
  before insert or update of student_id, activity_type, plan_id, phase_id on public.recitation_entries
  for each row execute function public.rc15_guard_recitation_track();

-- 6) Monthly roll: freeze finished tathbit months, open the new month from the last plan.
create or replace function public.rc152_roll_tathbit_months()
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_frozen int := 0; v_created int := 0;
  v_month_start date := date_trunc('month', current_date)::date;
  v_month_end date := (date_trunc('month', current_date) + interval '1 month - 1 day')::date;
  r record;
begin
  -- Freeze: numbers become fixed for reports.
  with done as (
    select p.id, p.target_pages,
           coalesce(sum(e.pages_count) filter (where e.activity_type::text = 'tathbit'), 0) as pages,
           count(e.id) filter (where e.activity_type::text = 'tathbit') as sessions
      from public.smart_plans p
      left join public.recitation_entries e on e.plan_id = p.id
     where p.plan_type = 'tathbit' and p.frozen_at is null and p.end_date < current_date and p.status <> 'archived'
     group by p.id, p.target_pages
  )
  update public.smart_plans p
     set status = 'completed', frozen_at = now(),
         frozen_summary = jsonb_build_object(
           'completed_pages', d.pages, 'target_pages', d.target_pages, 'recitations', d.sessions,
           'percent', case when d.target_pages > 0 then round(least(100, d.pages * 100.0 / d.target_pages), 1) else 0 end,
           'frozen_on', current_date)
    from done d where d.id = p.id;
  get diagnostics v_frozen = row_count;

  -- Open: one tathbit plan per active tathbit student per month (copy of the latest plan).
  for r in
    select distinct on (s.id) s.id as sid, s.halaqa_id as shalaqa, p.*
      from public.students s
      join public.smart_plans p on p.student_id = s.id and p.plan_type = 'tathbit' and p.legacy = false
     where s.track_code = 'tathbit' and coalesce(s.status::text, 'active') = 'active' and s.withdrawn_at is null
       and not exists (select 1 from public.smart_plans q
                        where q.student_id = s.id and q.plan_type = 'tathbit' and q.status <> 'archived'
                          and current_date between q.start_date and q.end_date)
     order by s.id, p.end_date desc
  loop
    insert into public.smart_plans (scope_type, halaqa_id, student_id, plan_type, review_tier, title, goal_unit,
      target_units, target_pages, start_date, end_date, schedule_weekdays, catch_up_weekday, max_daily_multiplier,
      auto_extend, quality_gate_enabled, minimum_evaluation, range_start_page, range_end_page, range_start_surah,
      range_start_ayah, range_end_surah, range_end_ayah, selected_surahs, selected_juz, selected_rubs,
      near_revision_pages, far_revision_pages, milestone_mode, milestone_requires_approval, delay_alert_days,
      home_prep_push, status, note, settings, created_by, period_month)
    values ('student', r.shalaqa, r.sid, 'tathbit', 'none',
      'خطة تثبيت ' || to_char(v_month_start, 'YYYY-MM'), r.goal_unit, r.target_units, r.target_pages,
      v_month_start, v_month_end, r.schedule_weekdays, r.catch_up_weekday, r.max_daily_multiplier,
      false, r.quality_gate_enabled, r.minimum_evaluation, r.range_start_page, r.range_end_page, r.range_start_surah,
      r.range_start_ayah, r.range_end_surah, r.range_end_ayah, r.selected_surahs, r.selected_juz, r.selected_rubs,
      r.near_revision_pages, r.far_revision_pages, r.milestone_mode, r.milestone_requires_approval, r.delay_alert_days,
      r.home_prep_push, 'active', r.note, r.settings || jsonb_build_object('rolled_from', r.id), r.created_by, v_month_start);
    v_created := v_created + 1;
  end loop;
  return jsonb_build_object('frozen', v_frozen, 'created', v_created);
end $$;
revoke all on function public.rc152_roll_tathbit_months() from public;
grant execute on function public.rc152_roll_tathbit_months() to authenticated, service_role;

-- 7) Consistency report (used by the app health card). Recitations recorded before this
--    migration are historical (legacy) and are reported separately, never as failures.
do $mig$
begin
  execute format($f$
create or replace function public.rc152_tathbit_consistency()
returns jsonb language sql stable security definer set search_path = public as $b$
  select jsonb_build_object(
    'students', (select count(*) from students where track_code = 'tathbit' and withdrawn_at is null),
    'bad_plans', (select count(*) from smart_plans p join students s on s.id = p.student_id
                   where s.track_code = 'tathbit' and p.plan_type <> 'tathbit' and p.legacy = false and p.status <> 'archived'),
    'live_phases_on_tathbit', (select count(*) from smart_plan_phases ph join smart_plans p on p.id = ph.plan_id
                   where p.plan_type = 'tathbit' and ph.status <> 'archived'),
    'foreign_recitations', (select count(*) from recitation_entries e join students s on s.id = e.student_id
                   where s.track_code = 'tathbit' and e.activity_type::text not in ('tathbit','test')
                     and e.created_at > %L::timestamptz),
    'legacy_recitations', (select count(*) from recitation_entries e join students s on s.id = e.student_id
                   where s.track_code = 'tathbit' and e.activity_type::text not in ('tathbit','test')
                     and e.created_at <= %L::timestamptz));
$b$;$f$, now(), now());
end $mig$;
grant execute on function public.rc152_tathbit_consistency() to authenticated, service_role;

-- 8) One-time repair (history preserved).
update public.smart_plan_phases ph set status = 'archived'
  from public.smart_plans p where p.id = ph.plan_id and p.plan_type = 'tathbit' and ph.status <> 'archived';
update public.smart_plans p set legacy = true, status = 'archived'
  from public.students s where s.id = p.student_id and s.track_code = 'tathbit'
   and p.plan_type <> 'tathbit' and p.status <> 'archived';
update public.smart_plans set period_month = date_trunc('month', start_date)::date
  where plan_type = 'tathbit' and period_month is null;
update public.recitation_entries e set plan_id = p.id, phase_id = null
  from public.sessions ss, public.smart_plans p, public.students s
 where ss.id = e.session_id and s.id = e.student_id and s.track_code = 'tathbit'
   and p.student_id = e.student_id and p.plan_type = 'tathbit' and p.status <> 'archived'
   and ss.session_date between p.start_date and p.end_date and e.plan_id is null
   and e.activity_type::text in ('tathbit','review_far');

commit;

-- 9) Daily idempotent schedule (00:15 UTC): freezes last month, opens the new one.
do $$
begin
  perform cron.unschedule(jobid) from cron.job where jobname = 'rc152-tathbit-monthly-roll';
  perform cron.schedule('rc152-tathbit-monthly-roll', '15 0 * * *', 'select public.rc152_roll_tathbit_months()');
exception when others then
  raise notice 'pg_cron unavailable; the app calls rc152_roll_tathbit_months() on plan load instead';
end $$;

-- Verification (expect bad_plans = live_phases_on_tathbit = foreign_recitations = 0):
-- select public.rc152_tathbit_consistency();
