-- Halaqati Native v2.46.2 RC11.2 — Core Phase Consistency
-- ADDITIVE migration. Run AFTER 20260926_v2461_rc11_smart_plan_phases.sql.
-- Safe to re-run: objects are created/replaced idempotently where possible.

begin;

-- One monthly Hifz phase + one monthly Sard phase is the supported RC11.2 contract.
create unique index if not exists smart_plan_phases_one_live_type_idx
  on public.smart_plan_phases(plan_id, phase_type)
  where status <> 'archived';

-- Phase edits use optimistic concurrency. Keep revision monotonic even for writes that
-- do not come from the Android client.
create or replace function public.rc112_bump_phase_revision()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if tg_op = 'UPDATE' then
    if new.revision is null or new.revision <= old.revision then
      new.revision := old.revision + 1;
    end if;
    new.updated_at := now();
  end if;
  return new;
end;
$$;

drop trigger if exists trg_rc112_bump_phase_revision on public.smart_plan_phases;
create trigger trg_rc112_bump_phase_revision
before update on public.smart_plan_phases
for each row execute function public.rc112_bump_phase_revision();

-- A used phase cannot silently change from Hifz to Sard (or the reverse), and live
-- phases in one plan cannot overlap. Gaps are permitted but surfaced in the UI.
create or replace function public.rc112_validate_phase_mutation()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if tg_op = 'UPDATE' and new.phase_type is distinct from old.phase_type then
    if exists(select 1 from public.smart_plan_phase_states s where s.phase_id = old.id)
       or exists(select 1 from public.recitation_entries r where r.phase_id = old.id) then
      raise exception 'RC11.2: phase_type cannot change after the phase has student history';
    end if;
  end if;

  if new.status <> 'archived' and exists (
    select 1 from public.smart_plan_phases p
    where p.plan_id = new.plan_id
      and p.id <> new.id
      and p.status <> 'archived'
      and daterange(p.start_date, p.end_date, '[]') && daterange(new.start_date, new.end_date, '[]')
  ) then
    raise exception 'RC11.2: smart plan phases may not overlap';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_rc112_validate_phase_mutation on public.smart_plan_phases;
create trigger trg_rc112_validate_phase_mutation
before insert or update on public.smart_plan_phases
for each row execute function public.rc112_validate_phase_mutation();

-- Defense in depth: a phase state may reference only a student who belongs to the
-- parent plan scope (student/category/halaqa) and the same halaqa.
create or replace function public.rc112_validate_phase_student_scope()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  sp public.smart_plans%rowtype;
  st public.students%rowtype;
begin
  select plan.* into sp
  from public.smart_plan_phases p
  join public.smart_plans plan on plan.id = p.plan_id
  where p.id = new.phase_id;
  if not found then raise exception 'RC11.2: phase does not exist'; end if;

  select * into st from public.students where id = new.student_id;
  if not found then raise exception 'RC11.2: student does not exist'; end if;

  if st.halaqa_id is distinct from sp.halaqa_id then
    raise exception 'RC11.2: student is outside the plan halaqa';
  end if;
  if sp.scope_type = 'student' and sp.student_id is distinct from st.id then
    raise exception 'RC11.2: student is outside student-scoped plan';
  elsif sp.scope_type = 'category' and sp.category_id is distinct from st.category_id then
    raise exception 'RC11.2: student is outside category-scoped plan';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_rc112_validate_phase_student_scope on public.smart_plan_phase_states;
create trigger trg_rc112_validate_phase_student_scope
before insert or update on public.smart_plan_phase_states
for each row execute function public.rc112_validate_phase_student_scope();

-- Capability probe used by the Android client to distinguish a fully migrated RC11.2
-- backend from one that only has the RC11 phase tables.
create or replace function public.rc112_capabilities()
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  select jsonb_build_object(
    'version','RC11.2',
    'phase_consistency',true,
    'atomic_transition',true,
    'optimistic_revision',true
  );
$$;

grant execute on function public.rc112_capabilities() to authenticated;

-- Optimistic concurrency for phase editing. PostgREST PATCH can return success even when
-- its revision filter matches zero rows, so edits go through this RPC instead.
create or replace function public.rc112_update_plan_phase(
  p_phase_id uuid,
  p_expected_revision integer,
  p_patch jsonb
)
returns jsonb
language plpgsql
security invoker
set search_path = public
as $$
declare
  next_revision integer;
  changed public.smart_plan_phases%rowtype;
begin
  if p_expected_revision is null or p_expected_revision < 1 then
    raise exception 'RC11.2_CONFLICT: invalid expected revision' using errcode = '40001';
  end if;

  update public.smart_plan_phases p set
    phase_order = coalesce((p_patch->>'phase_order')::integer, p.phase_order),
    phase_type = coalesce(p_patch->>'phase_type', p.phase_type),
    title = coalesce(p_patch->>'title', p.title),
    start_date = coalesce((p_patch->>'start_date')::date, p.start_date),
    end_date = coalesce((p_patch->>'end_date')::date, p.end_date),
    schedule_weekdays = case when p_patch ? 'schedule_weekdays' then
      array(select jsonb_array_elements_text(p_patch->'schedule_weekdays')::smallint) else p.schedule_weekdays end,
    goal_mode = coalesce(p_patch->>'goal_mode', p.goal_mode),
    goal_unit = coalesce(p_patch->>'goal_unit', p.goal_unit),
    target_units = case when p_patch ? 'target_units' then nullif(p_patch->>'target_units','null')::numeric else p.target_units end,
    target_pages = case when p_patch ? 'target_pages' then nullif(p_patch->>'target_pages','null')::numeric else p.target_pages end,
    distribution_mode = coalesce(p_patch->>'distribution_mode', p.distribution_mode),
    start_mode = coalesce(p_patch->>'start_mode', p.start_mode),
    range_start_page = case when p_patch ? 'range_start_page' then nullif(p_patch->>'range_start_page','null')::integer else p.range_start_page end,
    range_end_page = case when p_patch ? 'range_end_page' then nullif(p_patch->>'range_end_page','null')::integer else p.range_end_page end,
    range_start_surah = case when p_patch ? 'range_start_surah' then nullif(p_patch->>'range_start_surah','null')::integer else p.range_start_surah end,
    range_start_ayah = case when p_patch ? 'range_start_ayah' then nullif(p_patch->>'range_start_ayah','null')::integer else p.range_start_ayah end,
    range_end_surah = case when p_patch ? 'range_end_surah' then nullif(p_patch->>'range_end_surah','null')::integer else p.range_end_surah end,
    range_end_ayah = case when p_patch ? 'range_end_ayah' then nullif(p_patch->>'range_end_ayah','null')::integer else p.range_end_ayah end,
    allow_surplus = case when p_patch ? 'allow_surplus' then (p_patch->>'allow_surplus')::boolean else p.allow_surplus end,
    status = coalesce(p_patch->>'status', p.status),
    settings = case when p_patch ? 'settings' then coalesce(p_patch->'settings','{}'::jsonb) else p.settings end,
    revision = p_expected_revision + 1,
    updated_at = now()
  where p.id = p_phase_id and p.revision = p_expected_revision
  returning p.* into changed;

  if not found then
    select revision into next_revision from public.smart_plan_phases where id = p_phase_id;
    if next_revision is null then
      raise exception 'RC11.2: phase not found';
    end if;
    raise exception 'RC11.2_CONFLICT: phase was modified on another device (expected %, current %)',
      p_expected_revision, next_revision using errcode = '40001';
  end if;

  return to_jsonb(changed);
end;
$$;

grant execute on function public.rc112_update_plan_phase(uuid,integer,jsonb) to authenticated;

-- Safe historical backfill: attach only rows that have EXACTLY ONE eligible phase.
-- Generic `test` is deliberately excluded from Sard so a test cannot move the Sard anchor.
with candidates as (
  select
    re.id as recitation_id,
    p.id as phase_id,
    count(*) over (partition by re.id) as candidate_count
  from public.recitation_entries re
  join public.sessions sess on sess.id = re.session_id
  join public.students st on st.id = re.student_id
  join public.smart_plan_phases p
    on p.status <> 'archived'
   and (
     (p.phase_type = 'hifz' and re.activity_type in ('hifz','new_hifz'))
     or (p.phase_type = 'sard' and re.activity_type = 'sard')
   )
  join public.smart_plans sp on sp.id = p.plan_id
  where re.phase_id is null
    -- RC11.2 recovery path: if a phase date was edited before this migration, the
    -- parent-plan window can still prove ownership of a legacy untagged recitation.
    -- Ambiguous matches across multiple plans are skipped by candidate_count below.
    and (sess.session_date between p.start_date and p.end_date
         or sess.session_date between sp.start_date and sp.end_date)
    and st.halaqa_id = sp.halaqa_id
    and (
      sp.scope_type = 'halaqa'
      or (sp.scope_type = 'student' and sp.student_id = st.id)
      or (sp.scope_type = 'category' and sp.category_id = st.category_id)
    )
), unique_candidates as (
  select recitation_id, (array_agg(phase_id order by phase_id::text))[1] as phase_id
  from candidates
  group by recitation_id
  having max(candidate_count) = 1
)
update public.recitation_entries re
set phase_id = u.phase_id
from unique_candidates u
where re.id = u.recitation_id
  and re.phase_id is null;

-- Reconcile an ended phase even when the teacher never pressed "End". Historical
-- recitations tagged with phase_id stay counted even if the phase dates are edited later.
create or replace function public.rc112_reconcile_plan_phase_states(
  p_plan_id uuid,
  p_as_of date default current_date
)
returns jsonb
language plpgsql
security invoker
set search_path = public
as $$
declare
  p record;
  st record;
  done_pages numeric;
  target_pages numeric;
  pct numeric;
  touched integer := 0;
begin
  for p in
    select * from public.smart_plan_phases
    where plan_id = p_plan_id and status <> 'archived' and end_date < p_as_of
    order by phase_order
  loop
    for st in
      select s.id
      from public.students s
      join public.smart_plans sp on sp.id = p_plan_id
      where s.halaqa_id = sp.halaqa_id
        and s.status = 'active'
        and (sp.scope_type = 'halaqa'
          or (sp.scope_type = 'student' and sp.student_id = s.id)
          or (sp.scope_type = 'category' and sp.category_id = s.category_id))
    loop
      if exists (
        select 1 from public.smart_plan_phase_states ps
        where ps.phase_id=p.id and ps.student_id=st.id and ps.status in ('completed','skipped')
      ) then continue; end if;

      select coalesce(sum(re.pages_count),0) into done_pages
      from public.recitation_entries re
      join public.sessions sess on sess.id=re.session_id
      where re.student_id=st.id
        and (
          re.phase_id=p.id
          or (
            re.phase_id is null
            and sess.session_date between p.start_date and p.end_date
            and ((p.phase_type='hifz' and re.activity_type in ('hifz','new_hifz'))
                 or (p.phase_type='sard' and re.activity_type='sard'))
          )
        );

      select coalesce(ps.target_pages_override,
                      case when p.goal_mode='juz' then p.target_units*20 else p.target_pages end)
      into target_pages
      from (select 1) x
      left join public.smart_plan_phase_states ps on ps.phase_id=p.id and ps.student_id=st.id;

      pct := case when target_pages is not null and target_pages > 0
                  then round((done_pages/target_pages)*100,2) else null end;

      insert into public.smart_plan_phase_states(
        phase_id,student_id,status,frozen_completed_pages,frozen_target_pages,
        completion_percent,carry_shortfall_pages,completed_at,updated_by,updated_at
      ) values (
        p.id,st.id,'completed',done_pages,target_pages,pct,0,now(),auth.uid(),now()
      )
      on conflict (phase_id,student_id) do update set
        status='completed',
        frozen_completed_pages=coalesce(public.smart_plan_phase_states.frozen_completed_pages, excluded.frozen_completed_pages),
        frozen_target_pages=coalesce(public.smart_plan_phase_states.frozen_target_pages, excluded.frozen_target_pages),
        completion_percent=coalesce(public.smart_plan_phase_states.completion_percent, excluded.completion_percent),
        completed_at=coalesce(public.smart_plan_phase_states.completed_at, excluded.completed_at),
        updated_by=auth.uid(), updated_at=now();
      touched := touched + 1;
    end loop;
  end loop;
  return jsonb_build_object('reconciled', touched, 'plan_id', p_plan_id, 'as_of', p_as_of);
end;
$$;

grant execute on function public.rc112_reconcile_plan_phase_states(uuid,date) to authenticated;

-- Atomic/idempotent group transition. RLS remains active because this is SECURITY INVOKER.
create or replace function public.rc112_transition_plan_students(
  p_plan_id uuid,
  p_from_phase_id uuid,
  p_to_phase_id uuid,
  p_rows jsonb
)
returns jsonb
language plpgsql
security invoker
set search_path = public
as $$
declare
  item jsonb;
  sid uuid;
  completed numeric;
  target numeric;
  pct numeric;
  carry numeric;
  sard_override numeric;
  from_state_id uuid;
  to_state_id uuid;
  event_id uuid;
  moved integer := 0;
begin
  if not exists(select 1 from public.smart_plan_phases where id=p_from_phase_id and plan_id=p_plan_id and phase_type='hifz') then
    raise exception 'RC11.2: invalid source Hifz phase';
  end if;
  if not exists(select 1 from public.smart_plan_phases where id=p_to_phase_id and plan_id=p_plan_id and phase_type='sard') then
    raise exception 'RC11.2: invalid destination Sard phase';
  end if;
  if jsonb_typeof(p_rows) <> 'array' then raise exception 'RC11.2: p_rows must be a JSON array'; end if;

  for item in select value from jsonb_array_elements(p_rows)
  loop
    sid := (item->>'student_id')::uuid;
    completed := greatest(coalesce((item->>'completed_pages')::numeric,0),0);
    target := nullif(item->>'target_pages','')::numeric;
    pct := nullif(item->>'completion_percent','')::numeric;
    carry := greatest(coalesce((item->>'carry_shortfall_pages')::numeric,0),0);
    sard_override := nullif(item->>'sard_target_pages_override','')::numeric;

    from_state_id := md5('phase-state:'||p_from_phase_id::text||':'||sid::text)::uuid;
    to_state_id := md5('phase-state:'||p_to_phase_id::text||':'||sid::text)::uuid;

    insert into public.smart_plan_phase_states(
      id,phase_id,student_id,status,frozen_completed_pages,frozen_target_pages,
      completion_percent,carry_shortfall_pages,completed_at,updated_by,updated_at
    ) values (
      from_state_id,p_from_phase_id,sid,'completed',completed,target,pct,0,now(),auth.uid(),now()
    ) on conflict (phase_id,student_id) do update set
      status='completed', frozen_completed_pages=excluded.frozen_completed_pages,
      frozen_target_pages=excluded.frozen_target_pages, completion_percent=excluded.completion_percent,
      completed_at=coalesce(public.smart_plan_phase_states.completed_at,now()), updated_by=auth.uid(),updated_at=now();

    insert into public.smart_plan_phase_states(
      id,phase_id,student_id,status,target_pages_override,carry_shortfall_pages,updated_by,updated_at
    ) values (
      to_state_id,p_to_phase_id,sid,'active',sard_override,carry,auth.uid(),now()
    ) on conflict (phase_id,student_id) do update set
      status='active', target_pages_override=coalesce(excluded.target_pages_override, public.smart_plan_phase_states.target_pages_override),
      carry_shortfall_pages=excluded.carry_shortfall_pages, updated_by=auth.uid(),updated_at=now();

    event_id := md5('phase-event:'||p_plan_id::text||':'||sid::text||':phase_completed:'||p_from_phase_id::text)::uuid;
    insert into public.smart_plan_events(id,client_event_id,plan_id,student_id,event_type,event_date,payload,phase_id,created_by)
    values(event_id,event_id,p_plan_id,sid,'phase_completed',current_date,
      jsonb_build_object('phase_id',p_from_phase_id,'completed_pages',completed,'target_pages',target,'completion_percent',pct),p_from_phase_id,auth.uid())
    on conflict (client_event_id) do nothing;

    event_id := md5('phase-event:'||p_plan_id::text||':'||sid::text||':phase_started:'||p_to_phase_id::text)::uuid;
    insert into public.smart_plan_events(id,client_event_id,plan_id,student_id,event_type,event_date,payload,phase_id,created_by)
    values(event_id,event_id,p_plan_id,sid,'phase_started',current_date,
      jsonb_build_object('phase_id',p_to_phase_id,'carry_shortfall_pages',carry,'target_pages_override',sard_override),p_to_phase_id,auth.uid())
    on conflict (client_event_id) do nothing;

    moved := moved + 1;
  end loop;
  return jsonb_build_object('moved',moved,'plan_id',p_plan_id,'from_phase_id',p_from_phase_id,'to_phase_id',p_to_phase_id);
end;
$$;

grant execute on function public.rc112_transition_plan_students(uuid,uuid,uuid,jsonb) to authenticated;

commit;

NOTIFY pgrst, 'reload schema';
