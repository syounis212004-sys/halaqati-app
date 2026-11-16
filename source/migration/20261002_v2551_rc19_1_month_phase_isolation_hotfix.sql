-- Halaqati Native v2.55.1 RC19.1 — Monthly Phase Isolation Hotfix
-- Fixes previous-month recitations leaking into the current month when an existing phase row was reused.
-- Additive/idempotent. No recitation/session history is deleted or reassigned.

begin;

-- Preserve the deployed RC19 implementation once, then wrap it with a phase-isolation preflight.
do $$
begin
  if to_regprocedure('public.rc19_start_month_pre_rc191(date)') is null
     and to_regprocedure('public.rc19_start_month(date)') is not null then
    execute 'alter function public.rc19_start_month(date) rename to rc19_start_month_pre_rc191';
  end if;
end;
$$;

create or replace function public.rc191_isolate_cross_month_phases(
  p_month_start date default date_trunc('month',current_date)::date
)
returns jsonb
language plpgsql
security definer
set search_path=public
as $$
declare
  v_month_start date := date_trunc('month',p_month_start)::date;
  v_month_end date := (date_trunc('month',p_month_start)+interval '1 month - 1 day')::date;
  r record;
  v_count integer := 0;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;

  for r in
    select distinct sp.id as plan_id
    from public.smart_plans sp
    where coalesce(sp.legacy,false)=false
      and sp.scope_type='student'
      and sp.plan_type<>'tathbit'
      and sp.status<>'archived'
      and sp.start_date<=v_month_end and sp.end_date>=v_month_start
      and public.rc182_can_manage_halaqa(sp.halaqa_id)
      and exists (
        select 1
        from public.smart_plan_phases ph
        join public.recitation_entries re on re.phase_id=ph.id
        join public.sessions se on se.id=re.session_id
        where ph.plan_id=sp.id
          and ph.status<>'archived'
          and ph.phase_type in ('hifz','sard')
          and se.status<>'cancelled'
          and se.session_date < v_month_start
      )
  loop
    update public.smart_plan_phases
       set status='archived',
           phase_order=phase_order+100,
           settings=coalesce(settings,'{}'::jsonb)||jsonb_build_object(
             'rc191_cross_month_archived',true,
             'rc191_archived_for_month',v_month_start
           ),
           updated_at=now(),revision=revision+1
     where plan_id=r.plan_id
       and phase_type in ('hifz','sard')
       and status<>'archived';
    v_count:=v_count+1;
  end loop;

  return jsonb_build_object('month',v_month_start,'isolated_plans',v_count);
end;
$$;
revoke all on function public.rc191_isolate_cross_month_phases(date) from public,anon;
grant execute on function public.rc191_isolate_cross_month_phases(date) to authenticated;

create or replace function public.rc19_start_month(
  p_month_start date default date_trunc('month',current_date)::date
)
returns jsonb
language plpgsql
security definer
set search_path=public
as $$
declare
  v_iso jsonb;
  v_result jsonb;
begin
  v_iso := public.rc191_isolate_cross_month_phases(p_month_start);
  v_result := public.rc19_start_month_pre_rc191(p_month_start);
  return coalesce(v_result,'{}'::jsonb) || jsonb_build_object('rc191_phase_isolation',v_iso);
end;
$$;
revoke all on function public.rc19_start_month(date) from public,anon;
grant execute on function public.rc19_start_month(date) to authenticated;

-- Keep older clients on the corrected path.
create or replace function public.rc18_start_month(
  p_month_start date default date_trunc('month',current_date)::date
)
returns jsonb
language sql
security definer
set search_path=public
as $$ select public.rc19_start_month(p_month_start); $$;
revoke all on function public.rc18_start_month(date) from public,anon;
grant execute on function public.rc18_start_month(date) to authenticated;

-- One-time repair for the currently open month. We only replace phase identities;
-- historical recitations and sessions remain attached to their original archived phases.
do $$
declare
  v_month_start date := date_trunc('month',current_date)::date;
  v_month_end date := (date_trunc('month',current_date)+interval '1 month - 1 day')::date;
  v_hifz_end date := least((date_trunc('month',current_date)+interval '1 month - 1 day')::date,date_trunc('month',current_date)::date+21);
  v_sard_start date := least((date_trunc('month',current_date)+interval '1 month - 1 day')::date,date_trunc('month',current_date)::date+22);
  p record;
  v_hifz_target numeric;
  v_hifz_phase uuid;
  v_sard_phase uuid;
begin
  for p in
    select distinct sp.*
    from public.smart_plans sp
    where coalesce(sp.legacy,false)=false
      and sp.scope_type='student'
      and sp.plan_type<>'tathbit'
      and sp.status<>'archived'
      and sp.start_date<=v_month_end and sp.end_date>=v_month_start
      and exists (
        select 1
        from public.smart_plan_phases ph
        join public.recitation_entries re on re.phase_id=ph.id
        join public.sessions se on se.id=re.session_id
        where ph.plan_id=sp.id
          and ph.status<>'archived'
          and ph.phase_type in ('hifz','sard')
          and se.status<>'cancelled'
          and se.session_date < v_month_start
      )
  loop
    select coalesce(ps.target_pages_override,ph.target_pages,p.target_pages,25)
      into v_hifz_target
      from public.smart_plan_phases ph
      left join public.smart_plan_phase_states ps
        on ps.phase_id=ph.id and ps.student_id=p.student_id
     where ph.plan_id=p.id and ph.phase_type='hifz' and ph.status<>'archived'
     order by ph.phase_order,ph.updated_at desc limit 1;
    v_hifz_target:=greatest(coalesce(v_hifz_target,p.target_pages,25),0.01);

    update public.smart_plan_phases
       set status='archived',
           phase_order=phase_order+100,
           settings=coalesce(settings,'{}'::jsonb)||jsonb_build_object(
             'rc191_cross_month_archived',true,
             'rc191_archived_for_month',v_month_start
           ),
           updated_at=now(),revision=revision+1
     where plan_id=p.id and phase_type in ('hifz','sard') and status<>'archived';

    insert into public.smart_plan_phases(
      plan_id,phase_order,phase_type,title,start_date,end_date,schedule_weekdays,
      goal_mode,goal_unit,target_units,target_pages,distribution_mode,start_mode,
      allow_surplus,status,settings,created_by
    ) values (
      p.id,1,'hifz','مرحلة الحفظ',v_month_start,v_hifz_end,
      coalesce(p.schedule_weekdays,array[0,1,2,3,4]::smallint[]),
      'pages','pages',v_hifz_target,v_hifz_target,'flexible','last_activity',true,'active',
      jsonb_build_object('rc191_fresh_phase',true,'continuation_mode','last_activity'),p.created_by
    ) returning id into v_hifz_phase;

    insert into public.smart_plan_phases(
      plan_id,phase_order,phase_type,title,start_date,end_date,schedule_weekdays,
      goal_mode,goal_unit,target_units,target_pages,distribution_mode,start_mode,
      allow_surplus,status,settings,created_by
    ) values (
      p.id,2,'sard','مرحلة السرد',v_sard_start,v_month_end,array[5]::smallint[],
      'pages','pages',25,25,'flexible','last_activity',true,'active',
      jsonb_build_object('rc191_fresh_phase',true,'fixed_sard_target',25,'continuation_mode','last_activity'),p.created_by
    ) returning id into v_sard_phase;

    insert into public.smart_plan_phase_states(
      phase_id,student_id,status,target_pages_override,carry_shortfall_pages,settings,updated_by
    ) values (
      v_hifz_phase,p.student_id,'not_started',null,0,jsonb_build_object('rc191_fresh_phase',true),p.created_by
    ) on conflict(phase_id,student_id) do nothing;

    insert into public.smart_plan_phase_states(
      phase_id,student_id,status,target_pages_override,carry_shortfall_pages,settings,updated_by
    ) values (
      v_sard_phase,p.student_id,'not_started',null,0,jsonb_build_object('rc191_fresh_phase',true,'fixed_sard_target',25),p.created_by
    ) on conflict(phase_id,student_id) do nothing;

    update public.smart_plans
       set start_date=v_month_start,end_date=v_month_end,period_month=v_month_start,
           target_pages=v_hifz_target,target_units=v_hifz_target,
           status=case when status='paused' then 'paused' else 'active' end,
           settings=coalesce(settings,'{}'::jsonb)||jsonb_build_object(
             'rc191_phase_isolation',true,
             'rc19_hifz_start_day',1,'rc19_hifz_end_day',22,
             'rc19_sard_start_day',23,'rc19_sard_target_pages',25,
             'sard_target_pages',25,'monthly_hifz_target_pages',v_hifz_target
           ),updated_at=now(),revision=revision+1
     where id=p.id;
  end loop;
end;
$$;

commit;
