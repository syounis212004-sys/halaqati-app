-- Halaqati Native v2.46.1 RC11 — Smart Plan Phases & Sard Cycle
-- Additive migration. Existing smart_plans continue to work unchanged when no phases exist.

begin;

create table if not exists public.smart_plan_phases (
  id uuid primary key default gen_random_uuid(),
  plan_id uuid not null references public.smart_plans(id) on delete cascade,
  phase_order smallint not null check (phase_order > 0),
  phase_type text not null check (phase_type in ('hifz','sard')),
  title text not null,
  start_date date not null,
  end_date date not null,
  schedule_weekdays smallint[] not null default array[0,1,2,3,4]::smallint[],
  goal_mode text not null default 'pages'
    check (goal_mode in ('pages','juz','quran_range','open')),
  goal_unit text not null default 'pages'
    check (goal_unit in ('pages','juz','range','open')),
  target_units numeric(10,2),
  target_pages numeric(10,2),
  distribution_mode text not null default 'flexible'
    check (distribution_mode in ('equal','flexible')),
  start_mode text not null default 'plan_start'
    check (start_mode in ('plan_start','last_sard','manual','range_start')),
  range_start_page smallint check (range_start_page between 1 and 604),
  range_end_page smallint check (range_end_page between 1 and 604),
  range_start_surah smallint check (range_start_surah between 1 and 114),
  range_start_ayah smallint check (range_start_ayah > 0),
  range_end_surah smallint check (range_end_surah between 1 and 114),
  range_end_ayah smallint check (range_end_ayah > 0),
  allow_surplus boolean not null default true,
  status text not null default 'active'
    check (status in ('draft','active','completed','archived')),
  completed_at timestamptz,
  settings jsonb not null default '{}'::jsonb,
  revision integer not null default 1 check (revision > 0),
  created_by uuid not null default auth.uid() references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (end_date >= start_date),
  check (cardinality(schedule_weekdays) > 0),
  check (schedule_weekdays <@ array[0,1,2,3,4,5,6]::smallint[]),
  check (
    goal_mode = 'open'
    or coalesce(target_pages, target_units, 0) > 0
  ),
  unique (plan_id, phase_order)
);

comment on table public.smart_plan_phases is
  'RC11 phase definitions under a monthly Smart Plan. Sard is an activity phase, never a student track.';

create table if not exists public.smart_plan_phase_states (
  id uuid primary key default gen_random_uuid(),
  phase_id uuid not null references public.smart_plan_phases(id) on delete cascade,
  student_id uuid not null references public.students(id) on delete cascade,
  status text not null default 'not_started'
    check (status in ('not_started','active','completed','skipped')),
  target_pages_override numeric(10,2),
  start_date_override date,
  end_date_override date,
  frozen_completed_pages numeric(10,2),
  frozen_target_pages numeric(10,2),
  completion_percent numeric(7,2),
  carry_shortfall_pages numeric(10,2) not null default 0 check (carry_shortfall_pages >= 0),
  completed_at timestamptz,
  settings jsonb not null default '{}'::jsonb,
  updated_by uuid default auth.uid() references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (start_date_override is null or end_date_override is null or end_date_override >= start_date_override),
  unique (phase_id, student_id)
);

comment on table public.smart_plan_phase_states is
  'Per-student phase state: supports collective transitions, exclusions, frozen results and optional shortfall carry.';

create table if not exists public.smart_plan_phase_templates (
  id uuid primary key default gen_random_uuid(),
  halaqa_id uuid not null references public.halaqas(id) on delete cascade,
  name text not null,
  template_mode text not null default 'weeks'
    check (template_mode in ('dates','weeks','manual')),
  definition jsonb not null default '{}'::jsonb,
  active boolean not null default true,
  created_by uuid not null default auth.uid() references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.smart_plan_phase_templates is
  'Reusable halaqa-level RC11 phase templates such as 3 weeks hifz + final week sard.';

create index if not exists smart_plan_phases_plan_order_idx
  on public.smart_plan_phases(plan_id, phase_order);
create index if not exists smart_plan_phases_active_dates_idx
  on public.smart_plan_phases(plan_id, start_date, end_date)
  where status = 'active';
create index if not exists smart_plan_phase_states_student_idx
  on public.smart_plan_phase_states(student_id, status, updated_at desc);
create index if not exists smart_plan_phase_states_phase_idx
  on public.smart_plan_phase_states(phase_id, status);
create index if not exists smart_plan_phase_templates_halaqa_idx
  on public.smart_plan_phase_templates(halaqa_id, active, updated_at desc);

-- Tag actual recitation facts/events with the phase when known. Quran range data remains
-- in recitation_entries/selection_data; it is not duplicated in the phase state tables.
alter table public.recitation_entries
  add column if not exists phase_id uuid references public.smart_plan_phases(id) on delete set null;
create index if not exists recitation_entries_phase_student_idx
  on public.recitation_entries(phase_id, student_id, created_at desc)
  where phase_id is not null;

alter table public.smart_plan_events
  add column if not exists phase_id uuid references public.smart_plan_phases(id) on delete set null;

alter table public.smart_plan_events
  drop constraint if exists smart_plan_events_event_type_check;
alter table public.smart_plan_events
  add constraint smart_plan_events_event_type_check check (event_type in (
    'milestone_approved','auto_extension','manual_credit','manual_debit',
    'pause','resume','notification_sent','retry_required','catch_up',
    'phase_started','phase_completed','phase_reopened','phase_shortfall_carried'
  ));

create index if not exists smart_plan_events_phase_date_idx
  on public.smart_plan_events(phase_id, event_date desc)
  where phase_id is not null;

alter table public.smart_plan_phases enable row level security;
alter table public.smart_plan_phase_states enable row level security;
alter table public.smart_plan_phase_templates enable row level security;

revoke all on public.smart_plan_phases from anon;
revoke all on public.smart_plan_phase_states from anon;
revoke all on public.smart_plan_phase_templates from anon;
revoke all on public.smart_plan_phases from authenticated;
revoke all on public.smart_plan_phase_states from authenticated;
revoke all on public.smart_plan_phase_templates from authenticated;
grant select, insert, update, delete on public.smart_plan_phases to authenticated;
grant select, insert, update, delete on public.smart_plan_phase_states to authenticated;
grant select, insert, update, delete on public.smart_plan_phase_templates to authenticated;

-- Phase definitions inherit access from their parent smart plan.
drop policy if exists smart_plan_phases_select_access on public.smart_plan_phases;
create policy smart_plan_phases_select_access on public.smart_plan_phases
for select to authenticated using (
  exists (
    select 1 from public.smart_plans sp
    where sp.id = smart_plan_phases.plan_id
      and (
        public.is_admin()
        or sp.created_by = (select auth.uid())
        or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=sp.halaqa_id and ht.teacher_id=(select auth.uid()))
        or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=sp.halaqa_id and hs.supervisor_id=(select auth.uid()))
        or exists (
          select 1 from public.students s
          join public.student_guardians sg on sg.student_id=s.id
          where sg.guardian_id=(select auth.uid())
            and s.halaqa_id=sp.halaqa_id
            and (sp.student_id is null or sp.student_id=s.id)
        )
      )
  )
);

drop policy if exists smart_plan_phases_manage_access on public.smart_plan_phases;
create policy smart_plan_phases_manage_access on public.smart_plan_phases
for all to authenticated
using (
  exists (
    select 1 from public.smart_plans sp
    where sp.id=smart_plan_phases.plan_id and (
      public.is_admin()
      or sp.created_by=(select auth.uid())
      or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=sp.halaqa_id and ht.teacher_id=(select auth.uid()))
      or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=sp.halaqa_id and hs.supervisor_id=(select auth.uid()))
    )
  )
)
with check (
  exists (
    select 1 from public.smart_plans sp
    where sp.id=smart_plan_phases.plan_id and (
      public.is_admin()
      or sp.created_by=(select auth.uid())
      or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=sp.halaqa_id and ht.teacher_id=(select auth.uid()))
      or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=sp.halaqa_id and hs.supervisor_id=(select auth.uid()))
    )
  )
);

-- A guardian may read only the state of their linked student; staff may read/manage states in their halaqas.
drop policy if exists smart_plan_phase_states_select_access on public.smart_plan_phase_states;
create policy smart_plan_phase_states_select_access on public.smart_plan_phase_states
for select to authenticated using (
  public.is_admin()
  or exists (
    select 1
    from public.smart_plan_phases p
    join public.smart_plans sp on sp.id=p.plan_id
    where p.id=smart_plan_phase_states.phase_id and (
      sp.created_by=(select auth.uid())
      or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=sp.halaqa_id and ht.teacher_id=(select auth.uid()))
      or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=sp.halaqa_id and hs.supervisor_id=(select auth.uid()))
    )
  )
  or exists (
    select 1 from public.student_guardians sg
    where sg.student_id=smart_plan_phase_states.student_id
      and sg.guardian_id=(select auth.uid())
  )
);

drop policy if exists smart_plan_phase_states_manage_access on public.smart_plan_phase_states;
create policy smart_plan_phase_states_manage_access on public.smart_plan_phase_states
for all to authenticated
using (
  public.is_admin()
  or exists (
    select 1 from public.smart_plan_phases p
    join public.smart_plans sp on sp.id=p.plan_id
    where p.id=smart_plan_phase_states.phase_id and (
      sp.created_by=(select auth.uid())
      or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=sp.halaqa_id and ht.teacher_id=(select auth.uid()))
      or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=sp.halaqa_id and hs.supervisor_id=(select auth.uid()))
    )
  )
)
with check (
  public.is_admin()
  or exists (
    select 1 from public.smart_plan_phases p
    join public.smart_plans sp on sp.id=p.plan_id
    where p.id=smart_plan_phase_states.phase_id and (
      sp.created_by=(select auth.uid())
      or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=sp.halaqa_id and ht.teacher_id=(select auth.uid()))
      or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=sp.halaqa_id and hs.supervisor_id=(select auth.uid()))
    )
  )
);

-- Templates are halaqa configuration; guardians do not need them.
drop policy if exists smart_plan_phase_templates_select_access on public.smart_plan_phase_templates;
create policy smart_plan_phase_templates_select_access on public.smart_plan_phase_templates
for select to authenticated using (
  public.is_admin()
  or created_by=(select auth.uid())
  or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=smart_plan_phase_templates.halaqa_id and ht.teacher_id=(select auth.uid()))
  or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=smart_plan_phase_templates.halaqa_id and hs.supervisor_id=(select auth.uid()))
);

drop policy if exists smart_plan_phase_templates_manage_access on public.smart_plan_phase_templates;
create policy smart_plan_phase_templates_manage_access on public.smart_plan_phase_templates
for all to authenticated
using (
  public.is_admin()
  or created_by=(select auth.uid())
  or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=smart_plan_phase_templates.halaqa_id and ht.teacher_id=(select auth.uid()))
  or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=smart_plan_phase_templates.halaqa_id and hs.supervisor_id=(select auth.uid()))
)
with check (
  public.is_admin()
  or created_by=(select auth.uid())
  or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=smart_plan_phase_templates.halaqa_id and ht.teacher_id=(select auth.uid()))
  or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=smart_plan_phase_templates.halaqa_id and hs.supervisor_id=(select auth.uid()))
);

commit;

-- Ask PostgREST/Supabase API to expose the new RC11 tables immediately.
NOTIFY pgrst, 'reload schema';
