-- Proposed, NOT applied to production. Invoker RPC deliberately retains RLS.
begin;
create function public.rc_enroll_student_v12(
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
        or target.association_id is null
        or not public.can_manage_halaqa(target.id)
        or not public.assoc_writable(target.association_id)
        or not (public.is_platform_owner() or target.association_id=public.my_association()) then
        raise exception 'لا تملك صلاحية التسجيل في هذا النشاط' using errcode='42501';
    end if;

    if p_student is null then
        if target.halaqa_type='regular' or not target.allow_secondary_members
           or coalesce(cardinality(p_program_ids),0)<>0 then
            raise exception 'التسجيل الإضافي متاح في البرامج المصرح بها فقط' using errcode='23514';
        end if;
        select * into st from public.students where id=p_student_id;
        if not found or st.association_id is distinct from target.association_id or st.status<>'active' then
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
commit;
