-- Review-only additive RPCs. Existing RPCs and restrictive RLS remain intact.
begin;
create or replace function public.rc_linked_staff_display_v2(p_halaqa_ids uuid[])
returns jsonb language plpgsql stable security definer set search_path='' as $f$
declare result jsonb;
begin
  if auth.uid() is null or coalesce(cardinality(p_halaqa_ids),0) not between 1 and 200 then
    raise exception 'invalid scope request' using errcode='42501';
  end if;
  select coalesce(jsonb_agg(jsonb_build_object('halaqa_id',h.id,'staff',coalesce((
    select jsonb_agg(jsonb_build_object('id',a.id,'role',a.role,'full_name',p.full_name) order by a.role,p.full_name,a.id)
    from (
      select ht.teacher_id as id,'teacher'::text as role from public.halaqa_teachers ht where ht.halaqa_id=h.id
      union
      select hs.supervisor_id,'supervisor'::text from public.halaqa_supervisors hs where hs.halaqa_id=h.id
      union
      select h.supervisor_id,'supervisor'::text where h.supervisor_id is not null
    ) a left join public.profiles p on p.id=a.id
  ),'[]'::jsonb)) order by h.id),'[]'::jsonb) into result
  from public.halaqas h where h.id=any(p_halaqa_ids) and public.can_manage_halaqa(h.id);
  return result;
end $f$;
revoke all on function public.rc_linked_staff_display_v2(uuid[]) from public,anon,authenticated;
grant execute on function public.rc_linked_staff_display_v2(uuid[]) to authenticated;

create or replace function public.rc_tilawah_previous_v2(p_session_id uuid,p_student_ids uuid[])
returns jsonb language plpgsql stable security definer set search_path='' as $f$
declare current_session record;
begin
  if auth.uid() is null or coalesce(cardinality(p_student_ids),0) not between 1 and 100 then
    raise exception 'invalid roster request' using errcode='42501';
  end if;
  select s.id,s.halaqa_id,s.session_date,s.created_at,s.session_type::text as kind,s.status::text as status into current_session
    from public.sessions s where s.id=p_session_id;
  if current_session.id is null or current_session.status='cancelled'
     or current_session.kind not in ('tilawah','tajweed')
     or not public.can_manage_halaqa(current_session.halaqa_id) then
    raise exception 'session not accessible' using errcode='42501';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object('student_id',i.id,'record_id',r.id,'assessment',r.assessment) order by i.id)
    from (select distinct unnest(p_student_ids) as id) i
    left join lateral (
      select x.id,x.selection_data->'tilawah_assessment' as assessment
      from public.recitation_entries x join public.sessions s on s.id=x.session_id
      where x.student_id=i.id and x.activity_type::text='tilawah'
        and s.halaqa_id=current_session.halaqa_id and s.status::text<>'cancelled'
        and (s.session_date<current_session.session_date or
          (s.session_date=current_session.session_date and s.created_at<current_session.created_at))
        and x.session_id<>p_session_id
        and halaqati_scope_private.may_access_record(s.id,i.id,false)
      order by s.session_date desc,s.created_at desc,x.created_at desc,x.id desc limit 1
    ) r on true
    where halaqati_scope_private.may_access_record(p_session_id,i.id,true)
  ),'[]'::jsonb);
end $f$;
revoke all on function public.rc_tilawah_previous_v2(uuid,uuid[]) from public,anon,authenticated;
grant execute on function public.rc_tilawah_previous_v2(uuid,uuid[]) to authenticated;

-- Existing v12 validates guardian link, role and active membership. Return counts only.
create or replace function public.rc_guardian_activity_overview_v13(p_student_id uuid,p_halaqa_id uuid,
  p_today date default ((now() at time zone 'Asia/Gaza')::date))
returns jsonb language plpgsql stable security definer set search_path='' as $f$
declare base jsonb; summary jsonb;
begin
  if auth.uid() is null or public.current_role() is distinct from 'guardian'::public.app_role then
    raise exception 'guardian only' using errcode='42501';
  end if;
  base:=public.rc_guardian_activity_overview_v12(p_student_id,p_halaqa_id,p_today);
  select coalesce(jsonb_agg(jsonb_build_object(
    'date',x.session_date,'quality',x.safety_percentage,'major',greatest(coalesce(x.mistakes,0),0),
    'minor',case when jsonb_typeof(x.a->'lahn_khafi')='number' then least(greatest((x.a->>'lahn_khafi')::numeric,0),100000) else null end,
    'ward_reviewed',case when jsonb_typeof(x.a->'ward_reviewed')='boolean' then x.a->'ward_reviewed' else null end,
    'corrected',coalesce((select count(*) from jsonb_array_elements(case when jsonb_typeof(x.a->'marks')='array' then x.a->'marks' else '[]'::jsonb end) m where m->>'correction_state'='corrected'),0),
    'followed',case when jsonb_typeof(x.a->'followed_refs')='array' then jsonb_array_length(x.a->'followed_refs') else 0 end
  ) order by x.session_date desc,x.created_at desc),'[]'::jsonb) into summary
  from (
    select s.session_date,r.created_at,r.safety_percentage,r.mistakes,r.selection_data->'tilawah_assessment' a
    from public.recitation_entries r join public.sessions s on s.id=r.session_id
    where r.student_id=p_student_id and s.halaqa_id=p_halaqa_id and s.status::text<>'cancelled'
      and r.activity_type::text='tilawah' and s.session_date<=p_today
    order by s.session_date desc,r.created_at desc limit 5
  ) x;
  return base || jsonb_build_object('tilawah_summary',summary);
end $f$;
revoke all on function public.rc_guardian_activity_overview_v13(uuid,uuid,date) from public,anon,authenticated;
grant execute on function public.rc_guardian_activity_overview_v13(uuid,uuid,date) to authenticated;
commit;
