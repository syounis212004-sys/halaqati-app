-- REVIEW-ONLY proposal. Do not apply to production until full-schema staging.
-- An independent group is not a shared tenant. Authority derives from explicit assignment,
-- the owner, or the exact non-null association, never from two NULL association IDs.
begin;
create or replace function public.rc86_admin_scope_halaqa(hid uuid)
returns boolean language sql stable security definer set search_path=''
as $scope$
select exists(select 1 from public.profiles p join public.halaqas h on h.id=hid
  where p.id=auth.uid() and p.active and not p.frozen and p.approval_status='approved'
  and p.role::text='admin'
  and (p.is_platform_owner or (h.association_id is not null and p.association_id=h.association_id)))
$scope$;

create or replace function public.can_manage_halaqa(hid uuid)
returns boolean language sql stable security definer set search_path=''
as $scope$
select public.rc86_admin_scope_halaqa(hid) or exists(
  select 1 from public.profiles p join public.halaqas h on h.id=hid
  where p.id=auth.uid() and p.active and not p.frozen and p.approval_status='approved'
    and (h.association_id is null or h.association_id=p.association_id)
    and ((p.role::text='teacher' and public.is_halaqa_teacher(h.id))
      or (p.role::text='supervisor' and public.is_halaqa_supervisor(h.id)))
)
$scope$;

create or replace function public.can_access_halaqa(hid uuid)
returns boolean language sql stable security definer set search_path=''
as $scope$
select public.can_manage_halaqa(hid) or exists(
  select 1 from public.profiles p
  join public.students st on (st.halaqa_id=hid or exists(
    select 1 from public.student_halaqa_memberships m
    where m.student_id=st.id and m.halaqa_id=hid and m.status='active'))
  where p.id=auth.uid() and p.active and not p.frozen and p.approval_status='approved'
    and ((p.role::text='student' and st.user_id=p.id) or
      (p.role::text='guardian' and exists(select 1 from public.student_guardians g
        where g.student_id=st.id and g.guardian_id=p.id)))
)
$scope$;

create or replace function public.rc18_can_manage_student(p_student_id uuid)
returns boolean language sql stable security definer set search_path=''
as $scope$
select exists(select 1 from public.students st where st.id=p_student_id and public.can_manage_halaqa(st.halaqa_id))
$scope$;

create schema if not exists halaqati_scope_private;
revoke all on schema halaqati_scope_private from public,anon,authenticated;
grant usage on schema halaqati_scope_private to authenticated;

-- Never use the creator's association as the owner of an independent session.
-- Preserve the existing restrictive subscription checks, but derive them from the actual group.
alter policy rc66_assoc_isolation on public.sessions
  using(public.assoc_ok(public.assoc_of_halaqa(halaqa_id)))
  with check(public.assoc_ok(public.assoc_of_halaqa(halaqa_id)));
alter policy rc66_assoc_write_ins on public.sessions
  with check(public.assoc_writable(public.assoc_of_halaqa(halaqa_id)));
alter policy rc66_assoc_write_upd on public.sessions
  using(public.assoc_writable(public.assoc_of_halaqa(halaqa_id)))
  with check(public.assoc_writable(public.assoc_of_halaqa(halaqa_id)));
alter policy rc66_assoc_write_del on public.sessions
  using(public.assoc_writable(public.assoc_of_halaqa(halaqa_id)));
create policy phase4_sessions_read_scope on public.sessions as restrictive for select to authenticated
  using(public.can_access_halaqa(halaqa_id));
create policy phase4_sessions_write_scope on public.sessions as restrictive for update to authenticated
  using(public.can_manage_halaqa(halaqa_id)) with check(public.can_manage_halaqa(halaqa_id));
create policy phase4_sessions_delete_scope on public.sessions as restrictive for delete to authenticated
  using(public.can_manage_halaqa(halaqa_id));

-- The legacy permissive SELECT policies include a generic is_admin() branch.
-- A restrictive student check ensures that an association admin cannot enumerate independent children.
create function halaqati_scope_private.may_read_student(p_student uuid)
returns boolean language sql stable security definer set search_path=''
as $scope$
select exists(select 1 from public.profiles p join public.students st on st.id=p_student
  where p.id=auth.uid() and p.active and not p.frozen and p.approval_status='approved'
    and (public.can_manage_halaqa(st.halaqa_id)
      or exists(select 1 from public.student_halaqa_memberships m where m.student_id=st.id
        and m.status='active' and public.can_manage_halaqa(m.halaqa_id))
      or (p.role::text='student' and st.user_id=p.id)
      or (p.role::text='guardian' and exists(select 1 from public.student_guardians g
        where g.student_id=st.id and g.guardian_id=p.id))))
$scope$;
revoke all on function halaqati_scope_private.may_read_student(uuid) from public,anon,authenticated;
grant execute on function halaqati_scope_private.may_read_student(uuid) to authenticated;
create policy phase4_students_read_scope on public.students as restrictive for select to authenticated
  using(halaqati_scope_private.may_read_student(id));
create policy phase4_students_delete_scope on public.students as restrictive for delete to authenticated
  using(public.can_manage_halaqa(halaqa_id));

-- A record belongs to a session, not merely to a visible student. Prevent a program teacher
-- from reading a child's unrelated memorization history, and prevent changing record IDs across scopes.
create function halaqati_scope_private.may_access_record(p_session uuid,p_student uuid,p_write boolean)
returns boolean language sql stable security definer set search_path=''
as $scope$
select exists(select 1 from public.sessions s join public.students st on st.id=p_student
  join public.profiles p on p.id=auth.uid()
  where s.id=p_session and p.active and not p.frozen and p.approval_status='approved'
    and (st.halaqa_id=s.halaqa_id or exists(
      select 1 from public.student_halaqa_memberships m where m.student_id=st.id and m.halaqa_id=s.halaqa_id
      and m.joined_at<=s.session_date and (m.ended_at is null or m.ended_at>=s.session_date)
      and m.status in ('active','completed','withdrawn','suspended')))
    and ((public.can_manage_halaqa(s.halaqa_id) and
      (not p_write or public.assoc_writable(public.assoc_of_halaqa(s.halaqa_id))))
      or (not p_write and ((p.role::text='student' and st.user_id=p.id)
        or (p.role::text='guardian' and exists(select 1 from public.student_guardians g
          where g.student_id=st.id and g.guardian_id=p.id)))))
)
$scope$;
revoke all on function halaqati_scope_private.may_access_record(uuid,uuid,boolean) from public,anon,authenticated;
grant execute on function halaqati_scope_private.may_access_record(uuid,uuid,boolean) to authenticated;

create policy phase4_record_read_scope on public.attendance_records as restrictive for select to authenticated
  using(halaqati_scope_private.may_access_record(session_id,student_id,false));
create policy phase4_record_insert_scope on public.attendance_records as restrictive for insert to authenticated
  with check(halaqati_scope_private.may_access_record(session_id,student_id,true));
create policy phase4_record_update_scope on public.attendance_records as restrictive for update to authenticated
  using(halaqati_scope_private.may_access_record(session_id,student_id,true))
  with check(halaqati_scope_private.may_access_record(session_id,student_id,true));
create policy phase4_record_delete_scope on public.attendance_records as restrictive for delete to authenticated
  using(halaqati_scope_private.may_access_record(session_id,student_id,true));

create policy phase4_record_read_scope on public.recitation_entries as restrictive for select to authenticated
  using(halaqati_scope_private.may_access_record(session_id,student_id,false));
create policy phase4_record_insert_scope on public.recitation_entries as restrictive for insert to authenticated
  with check(halaqati_scope_private.may_access_record(session_id,student_id,true));
create policy phase4_record_update_scope on public.recitation_entries as restrictive for update to authenticated
  using(halaqati_scope_private.may_access_record(session_id,student_id,true))
  with check(halaqati_scope_private.may_access_record(session_id,student_id,true));
create policy phase4_record_delete_scope on public.recitation_entries as restrictive for delete to authenticated
  using(halaqati_scope_private.may_access_record(session_id,student_id,true));

create policy phase4_record_read_scope on public.student_daily_notes as restrictive for select to authenticated
  using(halaqati_scope_private.may_access_record(session_id,student_id,false));
create policy phase4_record_insert_scope on public.student_daily_notes as restrictive for insert to authenticated
  with check(halaqati_scope_private.may_access_record(session_id,student_id,true));
create policy phase4_record_update_scope on public.student_daily_notes as restrictive for update to authenticated
  using(halaqati_scope_private.may_access_record(session_id,student_id,true))
  with check(halaqati_scope_private.may_access_record(session_id,student_id,true));
create policy phase4_record_delete_scope on public.student_daily_notes as restrictive for delete to authenticated
  using(halaqati_scope_private.may_access_record(session_id,student_id,true));

create or replace function public.rc_enroll_student_v12(
    p_student_id uuid, p_halaqa_id uuid, p_student jsonb default null,
    p_program_ids uuid[] default '{}'::uuid[]
) returns jsonb
language plpgsql security invoker set search_path = pg_catalog, public
as $f$
declare
    target public.halaqas%rowtype;
    program public.halaqas%rowtype;
    st public.students%rowtype;
    input public.students%rowtype;
    program_id uuid;
    wanted_programs uuid[];
begin
    if auth.uid() is null or coalesce(public."current_role"()::text, '') not in ('admin','supervisor','teacher')
       or not exists (select 1 from public.profiles p where p.id=auth.uid() and p.active
           and not coalesce(p.frozen,false) and p.approval_status='approved') then
        raise exception 'لا تملك صلاحية التسجيل' using errcode='42501';
    end if;
    if p_student_id is null or p_halaqa_id is null or coalesce(cardinality(p_program_ids),0)>50 then
        raise exception 'بيانات التسجيل غير صالحة' using errcode='22023';
    end if;
    select * into target from public.halaqas where id=p_halaqa_id;
    if not found or not target.active or target.archived_at is not null
        or not public.can_manage_halaqa(target.id)
        or not public.assoc_writable(target.association_id)
        then
        raise exception 'لا تملك صلاحية التسجيل في هذا النشاط' using errcode='42501';
    end if;

    if p_student is null then
        if target.halaqa_type='regular' or not target.allow_secondary_members
           or coalesce(cardinality(p_program_ids),0)<>0 then
            raise exception 'التسجيل الإضافي متاح في البرامج المصرح بها فقط' using errcode='23514';
        end if;
        select * into st from public.students where id=p_student_id;
        if not found or st.association_id is distinct from target.association_id or st.status<>'active'
           or (target.association_id is null and not public.can_manage_halaqa(st.halaqa_id)) then
            raise exception 'سجل الطالب غير متاح للتسجيل ضمن هذا النطاق' using errcode='42501';
        end if;
        if st.halaqa_id=target.id then
            return jsonb_build_object('ok',true,'already_member',true);
        end if;
        wanted_programs:=array[target.id];
    else
        if jsonb_typeof(p_student)<>'object' or exists(
            select 1 from jsonb_object_keys(p_student) as keys(key)
            where key not in ('id','halaqa_id','full_name','national_id','phone','school_grade','date_of_birth','gender',
                'guardian_phone','guardian_email','category_id','status','enrollment_date','program_name','address','notes',
                'track_code','photo_path','target_pages_weekly','target_review_pages_weekly')
        ) then raise exception 'حقول التسجيل غير صالحة' using errcode='22023'; end if;
        input:=jsonb_populate_record(null::public.students,p_student);
        if input.id is distinct from p_student_id or input.halaqa_id is distinct from target.id
           or length(trim(coalesce(input.full_name,'')))<2 then
            raise exception 'بيانات الطالب غير صالحة' using errcode='22023';
        end if;
        if target.halaqa_type='regular' and coalesce(input.track_code,'') not in ('general_hifz','tathbit') then
            raise exception 'اختر مسار التحفيظ' using errcode='23514';
        end if;
        -- The stable client UUID makes a retry safe after a lost response.
        select * into st from public.students where id=p_student_id;
        if found then
            if st.halaqa_id is distinct from target.id or st.association_id is distinct from target.association_id
               or st.full_name is distinct from trim(input.full_name) or st.national_id is distinct from input.national_id then
                raise exception 'تعارض معرف التسجيل؛ راجع سجل الطالب' using errcode='23505';
            end if;
        else
            insert into public.students(id,full_name,halaqa_id,national_id,phone,school_grade,date_of_birth,gender,
                guardian_phone,guardian_email,category_id,status,enrollment_date,program_name,address,notes,track_code,
                photo_path,target_pages_weekly,target_review_pages_weekly)
            values(p_student_id,trim(input.full_name),target.id,input.national_id,input.phone,input.school_grade,
                input.date_of_birth,input.gender,input.guardian_phone,input.guardian_email,input.category_id,
                coalesce(input.status,'active'),coalesce(input.enrollment_date,current_date),input.program_name,input.address,
                input.notes,case when target.halaqa_type='regular' then input.track_code else null end,input.photo_path,
                case when target.halaqa_type='regular' then coalesce(input.target_pages_weekly,7) else 0 end,
                case when target.halaqa_type='regular' then coalesce(input.target_review_pages_weekly,20) else 0 end)
            returning * into st;
        end if;
        wanted_programs:=coalesce(p_program_ids,'{}'::uuid[]);
    end if;

    -- Any failed membership rolls back the whole transaction, including a new student.
    for program_id in select distinct x from unnest(wanted_programs) as ids(x) order by x loop
        if program_id is null or program_id=st.halaqa_id then
            raise exception 'العضوية الإضافية غير صالحة' using errcode='23514';
        end if;
        select * into program from public.halaqas where id=program_id;
        if not found or not program.active or program.archived_at is not null or program.halaqa_type='regular'
            or not program.allow_secondary_members or program.association_id is distinct from st.association_id
            or (program.association_id is null and not public.can_manage_halaqa(st.halaqa_id))
            or not public.can_manage_halaqa(program.id) or not public.assoc_writable(program.association_id)
            or st.status<>'active' then
            raise exception 'لا تملك صلاحية إضافة العضوية المطلوبة' using errcode='42501';
        end if;
        insert into public.student_halaqa_memberships(student_id,halaqa_id,membership_type,status)
        values(st.id,program.id,'participant','active')
        on conflict(student_id,halaqa_id) do update
            set status='active',ended_at=null,updated_at=now()
            where student_halaqa_memberships.status<>'active';
    end loop;
    return case when p_student is null then jsonb_build_object('ok',true)
        else jsonb_build_object('ok',true,'student',to_jsonb(st)) end;
end $f$;
revoke all on function public.rc_enroll_student_v12(uuid,uuid,jsonb,uuid[]) from public, anon, authenticated;
grant execute on function public.rc_enroll_student_v12(uuid,uuid,jsonb,uuid[]) to authenticated;

create or replace function public.rc_transition_session_v12(p_session_id uuid,p_expected_status text,p_status text)
returns jsonb language plpgsql security invoker set search_path=pg_catalog,public as $f$
declare s public.sessions%rowtype; h public.halaqas%rowtype;
begin
    if auth.uid() is null or not exists(
        select 1 from public.profiles p where p.id=auth.uid() and p.active and not coalesce(p.frozen,false)
        and p.approval_status='approved' and p.role::text in ('admin','teacher','supervisor')
    ) then raise exception 'لا تملك صلاحية إدارة الجلسة' using errcode='42501'; end if;
    select * into s from public.sessions where id=p_session_id for update;
    if not found then raise exception 'الجلسة غير متاحة' using errcode='42501'; end if;
    select * into h from public.halaqas where id=s.halaqa_id;
    if not found or not h.active or h.archived_at is not null
       or not public.can_manage_halaqa(h.id)
       or not public.assoc_writable(h.association_id)
       then
        raise exception 'لا تملك صلاحية إدارة الجلسة' using errcode='42501';
    end if;
    if coalesce((s.activity_data->>'archived')::boolean,false) then
        raise exception 'استعد الجلسة المؤرشفة أولًا' using errcode='22023';
    end if;
    if not coalesce(((p_expected_status='open' and p_status='closed') or
       (p_expected_status in ('planned','closed') and p_status='open')),false) then
        raise exception 'انتقال حالة الجلسة غير صالح' using errcode='22023';
    end if;
    if s.status=p_status then return jsonb_build_object('ok',true,'session',to_jsonb(s)); end if;
    if s.status is distinct from p_expected_status then
        raise exception 'تغيرت حالة الجلسة؛ حدّث القائمة' using errcode='40001';
    end if;
    update public.sessions set status=p_status where id=s.id returning * into s;
    if not found then raise exception 'لم يؤكد الخادم تغيير الحالة' using errcode='42501'; end if;
    return jsonb_build_object('ok',true,'session',to_jsonb(s));
end $f$;
revoke all on function public.rc_transition_session_v12(uuid,text,text) from public,anon,authenticated;
grant execute on function public.rc_transition_session_v12(uuid,text,text) to authenticated;

CREATE OR REPLACE FUNCTION public.rc182_update_session_details(p_session_id uuid, p_halaqa_id uuid, p_session_date date, p_session_type text, p_title text DEFAULT NULL::text, p_notes text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog', 'public'
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
  if not found then raise exception 'session not found' using errcode='42501'; end if;
  select * into v_source from public.halaqas where id=v_old.halaqa_id;
  select * into v_target from public.halaqas where id=p_halaqa_id;
  if auth.uid() is null or not exists (
      select 1 from public.profiles p where p.id=auth.uid() and p.active and not coalesce(p.frozen,false)
      and p.approval_status='approved' and p.role::text in ('admin','supervisor','teacher')
    ) or v_source.id is null or v_target.id is null
      or v_target.association_id is distinct from v_source.association_id
      or not public.can_manage_halaqa(v_source.id) or not public.can_manage_halaqa(v_target.id)
      or not public.assoc_writable(v_source.association_id)
      then
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
