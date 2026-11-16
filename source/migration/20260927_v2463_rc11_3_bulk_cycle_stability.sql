-- Halaqati v2.46.3-RC11.3
-- Unified Hifz -> Sard cycle control + phase/sync/session stability hardening.
-- Requires RC11 base + RC11.2 migrations first.

begin;

-- Product invariant: students have two tracks only. Sard is an activity/phase, never a track.
update public.students set track_code='general_hifz' where track_code='sard';
alter table public.students drop constraint if exists students_track_code_check;
alter table public.students add constraint students_track_code_check
  check (track_code is null or track_code in ('general_hifz','tathbit'));

-- Lightweight backend capability probe used by the Native app before phase mutations.
create or replace function public.rc113_capabilities()
returns jsonb
language sql
stable
security invoker
set search_path=public
as $$
  select jsonb_build_object(
    'rc113', true,
    'bulk_cycle', to_regprocedure('public.rc113_bulk_configure_plan_cycles(jsonb)') is not null,
    'phase_tables', to_regclass('public.smart_plan_phases') is not null
  );
$$;
grant execute on function public.rc113_capabilities() to authenticated;

-- RC11.2 optimistic update hotfix: an identical replay after a client timeout is success,
-- not a false revision conflict. This also stops retry storms after a committed mutation.
create or replace function public.rc112_update_plan_phase(
  p_phase_id uuid,
  p_expected_revision integer,
  p_patch jsonb
)
returns jsonb
language plpgsql
set search_path to 'public'
as $function$
declare
  next_revision integer;
  changed public.smart_plan_phases%rowtype;
  current_row public.smart_plan_phases%rowtype;
  comparable_patch jsonb;
begin
  if p_expected_revision is null or p_expected_revision < 1 then
    raise exception 'RC11.2_CONFLICT: invalid expected revision' using errcode = '40001';
  end if;

  update public.smart_plan_phases p
  set
    phase_order = coalesce((p_patch->>'phase_order')::integer, p.phase_order),
    phase_type = coalesce(p_patch->>'phase_type', p.phase_type),
    title = coalesce(p_patch->>'title', p.title),
    start_date = coalesce((p_patch->>'start_date')::date, p.start_date),
    end_date = coalesce((p_patch->>'end_date')::date, p.end_date),
    schedule_weekdays = case when p_patch ? 'schedule_weekdays'
      then array(select jsonb_array_elements_text(p_patch->'schedule_weekdays')::smallint)
      else p.schedule_weekdays end,
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
  where p.id=p_phase_id and p.revision=p_expected_revision
  returning p.* into changed;

  if found then return to_jsonb(changed); end if;

  select * into current_row from public.smart_plan_phases where id=p_phase_id;
  if not found then raise exception 'RC11.2: phase not found'; end if;

  next_revision := current_row.revision;
  comparable_patch := coalesce(p_patch,'{}'::jsonb) - 'revision' - 'updated_at';
  if to_jsonb(current_row) @> comparable_patch then return to_jsonb(current_row); end if;

  raise exception 'RC11.2_CONFLICT: phase was modified on another device (expected %, current %)',
    p_expected_revision,next_revision using errcode='40001';
end;
$function$;

grant execute on function public.rc112_update_plan_phase(uuid,integer,jsonb) to authenticated;

-- Lifecycle reconciliation now starts scheduled phases when their date arrives and freezes ended
-- phases. phase_started is never emitted before the effective start date.
create or replace function public.rc112_reconcile_plan_phase_states(
  p_plan_id uuid,
  p_as_of date default current_date
)
returns jsonb
language plpgsql
security invoker
set search_path=public
set statement_timeout='15s'
as $$
declare
  p record;
  st record;
  existing_state public.smart_plan_phase_states%rowtype;
  effective_start date;
  effective_end date;
  done_pages numeric;
  target_pages numeric;
  pct numeric;
  touched integer := 0;
  started integer := 0;
  completed integer := 0;
  event_id uuid;
begin
  for p in
    select * from public.smart_plan_phases
    where plan_id=p_plan_id and status<>'archived'
    order by phase_order
  loop
    for st in
      select s.id
      from public.students s
      join public.smart_plans sp on sp.id=p_plan_id
      where s.halaqa_id=sp.halaqa_id and s.status='active'
        and (sp.scope_type='halaqa'
          or (sp.scope_type='student' and sp.student_id=s.id)
          or (sp.scope_type='category' and sp.category_id=s.category_id))
    loop
      select * into existing_state
      from public.smart_plan_phase_states ps
      where ps.phase_id=p.id and ps.student_id=st.id;

      effective_start := coalesce(existing_state.start_date_override,p.start_date);
      effective_end := coalesce(existing_state.end_date_override,p.end_date);

      if existing_state.status='skipped' then continue; end if;

      if p_as_of between effective_start and effective_end
         and coalesce(existing_state.status,'not_started') not in ('active','completed') then
        insert into public.smart_plan_phase_states(phase_id,student_id,status,carry_shortfall_pages,updated_by,updated_at)
        values(p.id,st.id,'active',coalesce(existing_state.carry_shortfall_pages,0),auth.uid(),now())
        on conflict(phase_id,student_id) do update set
          status=case when public.smart_plan_phase_states.status='completed' then 'completed' else 'active' end,
          updated_by=auth.uid(),updated_at=now();

        event_id := md5('phase-event:'||p_plan_id::text||':'||st.id::text||':phase_started:'||p.id::text)::uuid;
        insert into public.smart_plan_events(id,client_event_id,plan_id,student_id,event_type,event_date,payload,phase_id,created_by)
        values(event_id,event_id,p_plan_id,st.id,'phase_started',effective_start,
          jsonb_build_object('phase_id',p.id,'effective_start_date',effective_start),p.id,auth.uid())
        on conflict(client_event_id) do nothing;
        started := started + 1;
        touched := touched + 1;
      end if;

      if effective_end < p_as_of
         and coalesce(existing_state.status,'not_started') not in ('completed','skipped') then
        select coalesce(sum(re.pages_count),0) into done_pages
        from public.recitation_entries re
        join public.sessions sess on sess.id=re.session_id
        where re.student_id=st.id
          and (
            re.phase_id=p.id
            or (re.phase_id is null and sess.session_date between effective_start and effective_end
                and ((p.phase_type='hifz' and re.activity_type::text in ('hifz','new_hifz'))
                     or (p.phase_type='sard' and re.activity_type::text='sard')))
          );

        target_pages := coalesce(existing_state.target_pages_override,
          case when p.goal_mode='juz' then p.target_units*20 else p.target_pages end);
        pct := case when target_pages is not null and target_pages>0
          then round((done_pages/target_pages)*100,2) else null end;

        insert into public.smart_plan_phase_states(
          phase_id,student_id,status,frozen_completed_pages,frozen_target_pages,
          completion_percent,carry_shortfall_pages,completed_at,updated_by,updated_at
        ) values(p.id,st.id,'completed',done_pages,target_pages,pct,0,now(),auth.uid(),now())
        on conflict(phase_id,student_id) do update set
          status='completed',
          frozen_completed_pages=coalesce(public.smart_plan_phase_states.frozen_completed_pages,excluded.frozen_completed_pages),
          frozen_target_pages=coalesce(public.smart_plan_phase_states.frozen_target_pages,excluded.frozen_target_pages),
          completion_percent=coalesce(public.smart_plan_phase_states.completion_percent,excluded.completion_percent),
          completed_at=coalesce(public.smart_plan_phase_states.completed_at,excluded.completed_at),
          updated_by=auth.uid(),updated_at=now();

        event_id := md5('phase-event:'||p_plan_id::text||':'||st.id::text||':phase_completed:'||p.id::text)::uuid;
        insert into public.smart_plan_events(id,client_event_id,plan_id,student_id,event_type,event_date,payload,phase_id,created_by)
        values(event_id,event_id,p_plan_id,st.id,'phase_completed',effective_end,
          jsonb_build_object('phase_id',p.id,'completed_pages',done_pages,'target_pages',target_pages,'completion_percent',pct),p.id,auth.uid())
        on conflict(client_event_id) do nothing;
        completed := completed + 1;
        touched := touched + 1;
      end if;
    end loop;
  end loop;
  return jsonb_build_object('reconciled',touched,'started',started,'completed',completed,'plan_id',p_plan_id,'as_of',p_as_of);
end;
$$;
grant execute on function public.rc112_reconcile_plan_phase_states(uuid,date) to authenticated;

-- Keep the original per-plan transition API compatible, but never mark a future Sard phase active early.
create or replace function public.rc112_transition_plan_students(
  p_plan_id uuid,
  p_from_phase_id uuid,
  p_to_phase_id uuid,
  p_rows jsonb
)
returns jsonb
language plpgsql
security invoker
set search_path=public
set statement_timeout='15s'
as $$
declare
  item jsonb;
  sid uuid;
  completed numeric;
  target numeric;
  pct numeric;
  carry numeric;
  sard_override numeric;
  to_start date;
  to_status text;
  from_state_id uuid;
  to_state_id uuid;
  event_id uuid;
  moved integer := 0;
  scheduled integer := 0;
begin
  if not exists(select 1 from public.smart_plan_phases where id=p_from_phase_id and plan_id=p_plan_id and phase_type='hifz') then
    raise exception 'RC11.3: invalid source Hifz phase';
  end if;
  select start_date into to_start from public.smart_plan_phases
  where id=p_to_phase_id and plan_id=p_plan_id and phase_type='sard';
  if not found then raise exception 'RC11.3: invalid destination Sard phase'; end if;
  if jsonb_typeof(p_rows) <> 'array' then raise exception 'RC11.3: p_rows must be a JSON array'; end if;

  to_status := case when to_start<=current_date then 'active' else 'not_started' end;
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
    ) values(from_state_id,p_from_phase_id,sid,'completed',completed,target,pct,0,now(),auth.uid(),now())
    on conflict(phase_id,student_id) do update set
      status='completed', frozen_completed_pages=excluded.frozen_completed_pages,
      frozen_target_pages=excluded.frozen_target_pages, completion_percent=excluded.completion_percent,
      completed_at=coalesce(public.smart_plan_phase_states.completed_at,excluded.completed_at), updated_by=auth.uid(),updated_at=now();

    insert into public.smart_plan_phase_states(
      id,phase_id,student_id,status,target_pages_override,carry_shortfall_pages,updated_by,updated_at
    ) values(to_state_id,p_to_phase_id,sid,to_status,sard_override,carry,auth.uid(),now())
    on conflict(phase_id,student_id) do update set
      status=case when public.smart_plan_phase_states.status='completed' then 'completed' else excluded.status end,
      target_pages_override=coalesce(excluded.target_pages_override,public.smart_plan_phase_states.target_pages_override),
      carry_shortfall_pages=excluded.carry_shortfall_pages,updated_by=auth.uid(),updated_at=now();

    event_id := md5('phase-event:'||p_plan_id::text||':'||sid::text||':phase_completed:'||p_from_phase_id::text)::uuid;
    insert into public.smart_plan_events(id,client_event_id,plan_id,student_id,event_type,event_date,payload,phase_id,created_by)
    values(event_id,event_id,p_plan_id,sid,'phase_completed',current_date,
      jsonb_build_object('phase_id',p_from_phase_id,'completed_pages',completed,'target_pages',target,'completion_percent',pct),p_from_phase_id,auth.uid())
    on conflict(client_event_id) do nothing;

    if to_status='active' then
      event_id := md5('phase-event:'||p_plan_id::text||':'||sid::text||':phase_started:'||p_to_phase_id::text)::uuid;
      insert into public.smart_plan_events(id,client_event_id,plan_id,student_id,event_type,event_date,payload,phase_id,created_by)
      values(event_id,event_id,p_plan_id,sid,'phase_started',to_start,
        jsonb_build_object('phase_id',p_to_phase_id,'carry_shortfall_pages',carry,'target_pages_override',sard_override),p_to_phase_id,auth.uid())
      on conflict(client_event_id) do nothing;
      moved := moved+1;
    else
      scheduled := scheduled+1;
    end if;
  end loop;
  return jsonb_build_object('processed',jsonb_array_length(p_rows),'started',moved,'scheduled',scheduled,
    'plan_id',p_plan_id,'from_phase_id',p_from_phase_id,'to_phase_id',p_to_phase_id);
end;
$$;
grant execute on function public.rc112_transition_plan_students(uuid,uuid,uuid,jsonb) to authenticated;

-- Existing RC11/11.2 Sard phases may already contain the correct route in phase.settings while
-- older app builds read the parent-plan Sard route. Align both sources once during migration.
update public.smart_plans sp
set settings=jsonb_set(coalesce(sp.settings,'{}'::jsonb),'{sard_route_direction}',to_jsonb(p.settings->>'route_direction'),true),
    updated_at=now()
from public.smart_plan_phases p
where p.plan_id=sp.id and p.phase_type='sard' and p.status<>'archived'
  and nullif(p.settings->>'route_direction','') is not null
  and coalesce(sp.settings->>'sard_route_direction','') is distinct from p.settings->>'route_direction';

-- Safe one-time legacy backfill: assign phase_id only when exactly one phase can own the recitation.
with candidates as (
  select re.id as recitation_id, min(p.id::text)::uuid as phase_id, count(distinct p.id) as matches
  from public.recitation_entries re
  join public.sessions sess on sess.id=re.session_id
  join public.smart_plans sp on sp.student_id=re.student_id and sp.status<>'archived'
  join public.smart_plan_phases p on p.plan_id=sp.id and p.status<>'archived'
    and sess.session_date between p.start_date and p.end_date
    and ((p.phase_type='hifz' and re.activity_type::text in ('hifz','new_hifz'))
      or (p.phase_type='sard' and re.activity_type::text='sard'))
  where re.phase_id is null
  group by re.id
)
update public.recitation_entries re set phase_id=c.phase_id
from candidates c where c.recitation_id=re.id and c.matches=1;

-- Halaqa-wide controller. Current production uses student-scoped plans, so each row owns its
-- phase definitions while Hifz targets remain individual. One RPC transaction prevents partial
-- transitions and Outbox storms.
create or replace function public.rc113_bulk_configure_plan_cycles(p_rows jsonb)
returns jsonb
language plpgsql
security invoker
set search_path=public
set statement_timeout='30s'
as $$
declare
  item jsonb;
  sp public.smart_plans%rowtype;
  sid uuid;
  plan_id uuid;
  hifz_id uuid;
  sard_id uuid;
  hifz_start date;
  hifz_end date;
  sard_start date;
  sard_end date;
  completed_pages numeric;
  hifz_target numeric;
  pct numeric;
  sard_target numeric;
  carry numeric;
  route text;
  weekdays smallint[];
  sard_state text;
  state_id uuid;
  event_id uuid;
  moved integer := 0;
  prepared integer := 0;
  scheduled integer := 0;
begin
  if jsonb_typeof(p_rows)<>'array' then raise exception 'RC11.3: p_rows must be an array'; end if;

  for item in select value from jsonb_array_elements(p_rows)
  loop
    plan_id := (item->>'plan_id')::uuid;
    sid := (item->>'student_id')::uuid;
    hifz_start := (item->>'hifz_start_date')::date;
    hifz_end := (item->>'hifz_end_date')::date;
    sard_start := (item->>'sard_start_date')::date;
    sard_end := (item->>'sard_end_date')::date;
    completed_pages := greatest(coalesce((item->>'hifz_completed_pages')::numeric,0),0);
    hifz_target := nullif(item->>'hifz_target_pages','')::numeric;
    pct := nullif(item->>'hifz_completion_percent','')::numeric;
    sard_target := greatest(coalesce((item->>'sard_target_pages')::numeric,25),0.01);
    carry := greatest(coalesce((item->>'carry_shortfall_pages')::numeric,0),0);
    route := coalesce(nullif(item->>'sard_route_direction',''),'reverse_surahs_forward_ayahs');
    weekdays := coalesce(array(select jsonb_array_elements_text(coalesce(item->'sard_weekdays','[0,1,2,3,4,5]'::jsonb))::smallint),array[0,1,2,3,4,5]::smallint[]);

    if hifz_end<hifz_start then raise exception 'RC11.3: invalid Hifz date range for plan %',plan_id; end if;
    if sard_start<=hifz_end then raise exception 'RC11.3: Sard must start after Hifz for plan %',plan_id; end if;
    if sard_end<sard_start then raise exception 'RC11.3: invalid Sard date range for plan %',plan_id; end if;
    if route not in ('reverse_surahs_forward_ayahs','forward_surahs_forward_ayahs') then raise exception 'RC11.3: invalid Sard route'; end if;

    select * into sp from public.smart_plans
    where id=plan_id and status='active' and scope_type='student' and student_id=sid;
    if not found then raise exception 'RC11.3: plan % is not an active student-scoped plan for student %',plan_id,sid; end if;

    select id into hifz_id from public.smart_plan_phases
    where plan_id=sp.id and phase_type='hifz' and status<>'archived' order by phase_order limit 1;
    if hifz_id is null then
      hifz_id := md5('rc113-phase:hifz:'||sp.id::text)::uuid;
      insert into public.smart_plan_phases(
        id,plan_id,phase_order,phase_type,title,start_date,end_date,schedule_weekdays,
        goal_mode,goal_unit,target_pages,distribution_mode,start_mode,allow_surplus,status,settings,created_by
      ) values(
        hifz_id,sp.id,1,'hifz','مرحلة الحفظ',hifz_start,hifz_end,sp.schedule_weekdays,
        'pages','pages',coalesce(hifz_target,sp.target_pages),'flexible','plan_start',true,'active',
        jsonb_build_object('route_direction','reverse_surahs_forward_ayahs'),auth.uid()
      );
      prepared := prepared+1;
    else
      update public.smart_plan_phases set start_date=hifz_start,end_date=hifz_end,updated_at=now(),revision=revision+1
      where id=hifz_id;
    end if;

    select id into sard_id from public.smart_plan_phases
    where plan_id=sp.id and phase_type='sard' and status<>'archived' order by phase_order limit 1;
    if sard_id is null then
      sard_id := md5('rc113-phase:sard:'||sp.id::text)::uuid;
      insert into public.smart_plan_phases(
        id,plan_id,phase_order,phase_type,title,start_date,end_date,schedule_weekdays,
        goal_mode,goal_unit,target_pages,distribution_mode,start_mode,allow_surplus,status,settings,created_by
      ) values(
        sard_id,sp.id,2,'sard','مرحلة السرد',sard_start,sard_end,weekdays,
        'pages','pages',sard_target,'flexible','last_sard',true,'active',
        jsonb_build_object('route_direction',route),auth.uid()
      );
      prepared := prepared+1;
    else
      update public.smart_plan_phases set
        start_date=sard_start,end_date=sard_end,schedule_weekdays=weekdays,
        goal_mode='pages',goal_unit='pages',target_pages=sard_target,
        distribution_mode='flexible',start_mode=coalesce(nullif(start_mode,''),'last_sard'),
        settings=jsonb_set(coalesce(settings,'{}'::jsonb),'{route_direction}',to_jsonb(route),true),
        updated_at=now(),revision=revision+1
      where id=sard_id;
    end if;

    -- Parent plan is now a container spanning both phases. Preserve the student's Hifz target.
    update public.smart_plans set
      start_date=hifz_start,
      end_date=sard_end,
      settings=jsonb_set(coalesce(settings,'{}'::jsonb),'{sard_route_direction}',to_jsonb(route),true),
      updated_at=now()
    where id=sp.id;

    state_id := md5('phase-state:'||hifz_id::text||':'||sid::text)::uuid;
    if hifz_end <= current_date then
      insert into public.smart_plan_phase_states(
        id,phase_id,student_id,status,frozen_completed_pages,frozen_target_pages,
        completion_percent,carry_shortfall_pages,completed_at,updated_by,updated_at
      ) values(state_id,hifz_id,sid,'completed',completed_pages,coalesce(hifz_target,sp.target_pages),pct,0,now(),auth.uid(),now())
      on conflict(phase_id,student_id) do update set
        status='completed',frozen_completed_pages=excluded.frozen_completed_pages,
        frozen_target_pages=excluded.frozen_target_pages,completion_percent=excluded.completion_percent,
        completed_at=coalesce(public.smart_plan_phase_states.completed_at,excluded.completed_at),updated_by=auth.uid(),updated_at=now();
    else
      insert into public.smart_plan_phase_states(
        id,phase_id,student_id,status,carry_shortfall_pages,updated_by,updated_at
      ) values(state_id,hifz_id,sid,case when hifz_start<=current_date then 'active' else 'not_started' end,0,auth.uid(),now())
      on conflict(phase_id,student_id) do update set
        status=case when public.smart_plan_phase_states.status='completed' then 'completed' else excluded.status end,
        updated_by=auth.uid(),updated_at=now();
    end if;

    sard_state := case when sard_start<=current_date then 'active' else 'not_started' end;
    state_id := md5('phase-state:'||sard_id::text||':'||sid::text)::uuid;
    insert into public.smart_plan_phase_states(
      id,phase_id,student_id,status,target_pages_override,carry_shortfall_pages,updated_by,updated_at
    ) values(state_id,sard_id,sid,sard_state,null,carry,auth.uid(),now())
    on conflict(phase_id,student_id) do update set
      status=case when public.smart_plan_phase_states.status='completed' then 'completed' else excluded.status end,
      carry_shortfall_pages=excluded.carry_shortfall_pages,updated_by=auth.uid(),updated_at=now();

    -- Make legacy records date-edit safe whenever ownership is unambiguous for this student plan.
    update public.recitation_entries re set phase_id=hifz_id
    from public.sessions sess
    where re.session_id=sess.id and re.student_id=sid and re.phase_id is null
      and re.activity_type::text in ('hifz','new_hifz') and sess.session_date between hifz_start and hifz_end;
    update public.recitation_entries re set phase_id=sard_id
    from public.sessions sess
    where re.session_id=sess.id and re.student_id=sid and re.phase_id is null
      and re.activity_type::text='sard' and sess.session_date between sard_start and sard_end;

    if hifz_end <= current_date then
      event_id := md5('phase-event:'||sp.id::text||':'||sid::text||':phase_completed:'||hifz_id::text)::uuid;
      insert into public.smart_plan_events(id,client_event_id,plan_id,student_id,event_type,event_date,payload,phase_id,created_by)
      values(event_id,event_id,sp.id,sid,'phase_completed',hifz_end,
        jsonb_build_object('phase_id',hifz_id,'completed_pages',completed_pages,'target_pages',coalesce(hifz_target,sp.target_pages),'completion_percent',pct),hifz_id,auth.uid())
      on conflict(client_event_id) do nothing;
    end if;

    if sard_state='active' then
      event_id := md5('phase-event:'||sp.id::text||':'||sid::text||':phase_started:'||sard_id::text)::uuid;
      insert into public.smart_plan_events(id,client_event_id,plan_id,student_id,event_type,event_date,payload,phase_id,created_by)
      values(event_id,event_id,sp.id,sid,'phase_started',sard_start,
        jsonb_build_object('phase_id',sard_id,'target_pages',sard_target,'route_direction',route),sard_id,auth.uid())
      on conflict(client_event_id) do nothing;
      moved := moved+1;
    else
      scheduled := scheduled+1;
    end if;
  end loop;

  return jsonb_build_object('rows',jsonb_array_length(p_rows),'started',moved,'scheduled',scheduled,'phases_created',prepared);
end;
$$;
grant execute on function public.rc113_bulk_configure_plan_cycles(jsonb) to authenticated;

-- Refresh API metadata after new RPCs/constraint changes.
notify pgrst,'reload schema';
notify pgrst,'reload config';

commit;
