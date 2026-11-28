-- Proposed phase-four migration, NOT deployed. Test in isolated database before applying.
-- One independent score per criterion; no recitation, plan or existing daily note is rewritten.
begin;
create schema if not exists halaqati_education_private;
revoke all on schema halaqati_education_private from public, anon, authenticated;
grant usage on schema halaqati_education_private to authenticated;

create table public.educational_assessments (
    session_id uuid not null references public.sessions(id) on delete restrict,
    student_id uuid not null references public.students(id) on delete restrict,
    metric text not null check(metric in ('general','commitment','listening','participation','behavior','comprehension')),
    score smallint not null check(score between 1 and 5),
    recorded_by uuid references public.profiles(id) on delete set null,
    updated_at timestamptz not null default now(),
    primary key(session_id,student_id,metric)
);
create index educational_assessments_student_idx on public.educational_assessments(student_id,session_id);
alter table public.educational_assessments enable row level security;
revoke all on public.educational_assessments from public,anon,authenticated;
grant select on public.educational_assessments to authenticated;

-- Explicit scope and membership at time of session. No global admin or observer visibility.
create function halaqati_education_private.may_manage(p_session uuid,p_student uuid)
returns boolean language sql stable security definer set search_path=pg_catalog,public
as $$
select exists(
    select 1 from public.profiles p
    join public.sessions s on s.id=p_session
    join public.halaqas h on h.id=s.halaqa_id
    join public.students st on st.id=p_student
    where p.id=auth.uid() and p.active and not p.frozen and p.approval_status='approved'
      and s.session_type::text='lesson'
      and (
          (p.role::text='admin' and (p.is_platform_owner or (p.association_id is not null and p.association_id=h.association_id)))
          or (p.role::text='teacher' and public.is_halaqa_teacher(h.id))
          or (p.role::text='supervisor' and public.is_halaqa_supervisor(h.id))
      )
      and (st.halaqa_id=h.id or exists(
          select 1 from public.student_halaqa_memberships m where m.student_id=st.id and m.halaqa_id=h.id
          and m.joined_at<=s.session_date and (m.ended_at is null or m.ended_at>=s.session_date)
          and m.status in ('active','completed','withdrawn','suspended')
      ))
)
$$;
revoke all on function halaqati_education_private.may_manage(uuid,uuid) from public,anon,authenticated;
grant execute on function halaqati_education_private.may_manage(uuid,uuid) to authenticated;

create function halaqati_education_private.may_read(p_session uuid,p_student uuid)
returns boolean language sql stable security definer set search_path=pg_catalog,public
as $$
select halaqati_education_private.may_manage(p_session,p_student) or exists(
    select 1 from public.profiles p join public.student_guardians sg on sg.guardian_id=p.id
    where p.id=auth.uid() and p.active and not p.frozen and p.approval_status='approved'
      and p.role::text='guardian' and sg.student_id=p_student
)
$$;
revoke all on function halaqati_education_private.may_read(uuid,uuid) from public,anon,authenticated;
grant execute on function halaqati_education_private.may_read(uuid,uuid) to authenticated;
create policy educational_assessments_read on public.educational_assessments for select to authenticated
using (halaqati_education_private.may_read(session_id,student_id));

create function public.rc_save_education_score_v12(p_session_id uuid,p_student_id uuid,p_metric text,p_score smallint,p_only_unrated boolean default false)
returns jsonb language plpgsql security definer set search_path=pg_catalog,public
as $$
declare s public.sessions%rowtype; h public.halaqas%rowtype; actual smallint;
begin
    if not halaqati_education_private.may_manage(p_session_id,p_student_id) then
        raise exception 'لا تملك صلاحية تقييم هذا الطالب في الجلسة' using errcode='42501';
    end if;
    if p_metric is null or p_metric not in ('general','commitment','listening','participation','behavior','comprehension')
       or p_score is null or p_score not between 1 and 5 then
        raise exception 'درجة أو معيار غير صالح' using errcode='22023';
    end if;
    select * into s from public.sessions where id=p_session_id for update;
    select * into h from public.halaqas where id=s.halaqa_id;
    if s.status not in ('open','closed') or s.session_date>(now() at time zone 'Asia/Gaza')::date
       or coalesce(s.activity_data->>'archived','false')='true' or not h.active or h.archived_at is not null
       or not public.assoc_writable(h.association_id) then
        raise exception 'الجلسة غير قابلة للتقييم' using errcode='42501';
    end if;
    if not exists(select 1 from public.attendance_records a where a.session_id=s.id and a.student_id=p_student_id and a.status::text in ('present','late')) then
        raise exception 'سجل حضور الطالب أولًا' using errcode='23514';
    end if;
    insert into public.educational_assessments(session_id,student_id,metric,score,recorded_by)
    values(s.id,p_student_id,p_metric,p_score,auth.uid())
    on conflict(session_id,student_id,metric) do update
        set score=excluded.score,recorded_by=excluded.recorded_by,updated_at=now()
        where not coalesce(p_only_unrated,false);
    select score into actual from public.educational_assessments
        where session_id=s.id and student_id=p_student_id and metric=p_metric;
    return jsonb_build_object('ok',true,'score',actual);
end
$$;
revoke all on function public.rc_save_education_score_v12(uuid,uuid,text,smallint,boolean) from public,anon,authenticated;
grant execute on function public.rc_save_education_score_v12(uuid,uuid,text,smallint,boolean) to authenticated;
commit;
