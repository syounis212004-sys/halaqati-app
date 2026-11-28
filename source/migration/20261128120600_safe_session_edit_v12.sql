-- Proposed forward migration; preserves signature/grants and legacy date recalculation for regular groups.
-- Must pass production-schema staging tests before applying. Does not modify existing rows.
begin;
CREATE OR REPLACE FUNCTION public.rc182_update_session_details(p_session_id uuid, p_halaqa_id uuid, p_session_date date, p_session_type text, p_title text DEFAULT NULL::text, p_notes text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_old public.sessions%rowtype;
  v_source public.halaqas%rowtype;
  v_target public.halaqas%rowtype;
  v_type_name text;
  v_result jsonb;
  v_dep_count integer;
  v_student uuid;
  v_old_month date;
  v_new_month date;
begin
  if p_session_date is null then raise exception 'session date required'; end if;
  if p_session_type is null or p_session_type not in ('tasmee','sard','review','test','tajweed','tilawah','lesson','other') then
    raise exception 'invalid session type';
  end if;

  select * into v_old from public.sessions where id=p_session_id for update;
  if not found then raise exception 'session not found'; end if;
  select * into v_source from public.halaqas where id=v_old.halaqa_id;
  select * into v_target from public.halaqas where id=p_halaqa_id;
  if auth.uid() is null or not exists (
      select 1 from public.profiles p where p.id=auth.uid() and p.active and not coalesce(p.frozen,false)
      and p.approval_status='approved' and p.role::text in ('admin','supervisor','teacher')
    ) or v_source.id is null or v_target.id is null
      or v_source.association_id is null or v_target.association_id is distinct from v_source.association_id
      or not public.can_manage_halaqa(v_source.id) or not public.can_manage_halaqa(v_target.id)
      or not public.assoc_writable(v_source.association_id)
      or not (public.is_platform_owner() or v_source.association_id=public.my_association()) then
    raise exception 'لا تملك صلاحية تعديل الجلسة ضمن هذا النطاق' using errcode='42501';
  end if;
  if p_halaqa_id is distinct from v_old.halaqa_id or p_session_type is distinct from v_old.session_type::text then
    if not v_target.active or v_target.archived_at is not null then
      raise exception 'النشاط المستهدف غير مفعل' using errcode='23514';
    end if;
    if v_target.halaqa_type <> 'regular' and p_session_type not in ('tilawah','tajweed','test','lesson','other') then
      raise exception 'نوع الجلسة غير مناسب للبرنامج' using errcode='23514';
    end if;
    select
      (select count(*) from public.recitation_entries r where r.session_id=p_session_id)
      +(select count(*) from public.attendance_records a where a.session_id=p_session_id)
      +(select count(*) from public.student_daily_notes n where n.session_id=p_session_id)
      into v_dep_count;
    if v_dep_count>0 then
      raise exception 'لا يمكن تغيير نوع أو نشاط جلسة تحتوي على بيانات. أرشفها أو أنشئ جلسة جديدة.' using errcode='23514';
    end if;
  end if;

  v_type_name := pg_typeof(v_old.session_type)::text;
  execute format(
    'update public.sessions set halaqa_id=$1, session_date=$2, session_type=$3::%s, title=$4, notes=$5 where id=$6',
    v_type_name
  ) using p_halaqa_id,p_session_date,p_session_type,nullif(trim(coalesce(p_title,'')),''),nullif(trim(coalesce(p_notes,'')),''),p_session_id;

  -- A program date change must not rebind activities into memorization plans.
  -- The scope/type guard above prevents moving existing data into another context.
  if v_target.halaqa_type <> 'regular' then
    select to_jsonb(s) into v_result from public.sessions s where s.id=p_session_id;
    return v_result;
  end if;

  for v_student in
    select distinct x.student_id from (
      select r.student_id from public.recitation_entries r where r.session_id=p_session_id
      union all select a.student_id from public.attendance_records a where a.session_id=p_session_id
      union all select n.student_id from public.student_daily_notes n where n.session_id=p_session_id
    ) x where x.student_id is not null
  loop
    -- Rebind every recitation in the edited session to the correct date-specific plan/phase.
    perform public.rc182_rebind_recitation(r.id)
      from public.recitation_entries r where r.session_id=p_session_id and r.student_id=v_student
      and r.activity_type::text not in ('tilawah','tajweed');

    v_old_month := date_trunc('month',v_old.session_date)::date;
    v_new_month := date_trunc('month',p_session_date)::date;
    if v_old_month < date_trunc('month',current_date)::date then
      perform public.rc18_archive_finished_plans((v_old_month + interval '1 month')::date);
    end if;
    if v_new_month <> v_old_month and v_new_month < date_trunc('month',current_date)::date then
      perform public.rc18_archive_finished_plans((v_new_month + interval '1 month')::date);
    end if;

    if public.rc18_can_manage_student(v_student) then
      perform public.rc182_clear_computed_badges(v_student,v_old_month);
      perform public.rc18_refresh_student_badges(v_student,v_old_month);
      if v_new_month<>v_old_month then
        perform public.rc182_clear_computed_badges(v_student,v_new_month);
        perform public.rc18_refresh_student_badges(v_student,v_new_month);
      end if;
    end if;
  end loop;

  select to_jsonb(s) into v_result from public.sessions s where s.id=p_session_id;
  return v_result;
end;
$function$;
commit;
