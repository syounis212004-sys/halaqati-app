\set ON_ERROR_STOP on
-- Run only in an empty disposable PostgreSQL 17 database as its owner.
do $$ begin
  if not exists (select 1 from pg_roles where rolname='anon') then create role anon; end if;
  if not exists (select 1 from pg_roles where rolname='authenticated') then create role authenticated; end if;
end $$;
create schema auth;
create function auth.uid() returns uuid language sql stable set search_path=''
as $$ select nullif(current_setting('fixture.actor',true),'')::uuid $$;
create table public.profiles(id uuid primary key, role text, active boolean not null default true,
  frozen boolean not null default false, approval_status text not null default 'approved',
  is_platform_owner boolean not null default false, association_id uuid);
create table public.halaqas(id uuid primary key, association_id uuid, active boolean not null default true, archived_at timestamptz);
create table public.students(id uuid primary key, halaqa_id uuid);
create table public.sessions(id uuid primary key, halaqa_id uuid references public.halaqas,
  session_type text, session_date date, status text, activity_data jsonb);
create table public.student_halaqa_memberships(student_id uuid, halaqa_id uuid, joined_at date, ended_at date, status text);
create table public.student_guardians(student_id uuid, guardian_id uuid);
create table public.attendance_records(session_id uuid, student_id uuid, status text);
create table public.fixture_teachers(halaqa_id uuid, teacher_id uuid);
create function public.is_halaqa_teacher(h uuid) returns boolean language sql stable set search_path=''
as $$ select exists(select 1 from public.fixture_teachers t where t.halaqa_id=h and t.teacher_id=auth.uid()) $$;
create function public.is_halaqa_supervisor(h uuid) returns boolean language sql stable as $$ select false $$;
create function public.assoc_writable(a uuid) returns boolean language sql stable as $$ select true $$;
\ir ../20261128120700_educational_assessments_v12.sql

insert into public.profiles(id,role) values
 ('00000000-0000-0000-0000-000000000001','teacher'),
 ('00000000-0000-0000-0000-000000000002','guardian'),
 ('00000000-0000-0000-0000-000000000003','teacher'),
 ('00000000-0000-0000-0000-000000000004','guardian');
insert into public.halaqas(id) values
 ('00000000-0000-0000-0000-000000000011'),('00000000-0000-0000-0000-000000000012');
insert into public.students values
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000011'),
 ('00000000-0000-0000-0000-000000000022','00000000-0000-0000-0000-000000000011');
insert into public.fixture_teachers values
 ('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000001');
insert into public.student_guardians values
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000002');
insert into public.sessions values
 ('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000011','lesson',(now() at time zone 'Asia/Gaza')::date,'open','{}'),
 ('00000000-0000-0000-0000-000000000032','00000000-0000-0000-0000-000000000011','lesson',(now() at time zone 'Asia/Gaza')::date,'open','{}');
insert into public.attendance_records values
 ('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000021','present');
set role authenticated;
select set_config('fixture.actor','00000000-0000-0000-0000-000000000001',false);
do $$
declare s uuid := '00000000-0000-0000-0000-000000000031'; st uuid := '00000000-0000-0000-0000-000000000021';
begin
  if (public.rc_save_education_score_v12(s,st,'general',4::smallint,false)->>'score')::int is distinct from 4 then raise exception 'initial save failed'; end if;
  if (public.rc_save_education_score_v12(s,st,'general',2::smallint,true)->>'score')::int is distinct from 4 then raise exception 'bulk overwrote existing score'; end if;
  begin
    perform public.rc_save_education_score_v12(s,'00000000-0000-0000-0000-000000000022','general',4::smallint,false);
    raise exception 'unrecorded attendance accepted';
  exception when check_violation then null; end;
  begin
    perform public.rc_save_education_score_v12(s,st,'general',6::smallint,false);
    raise exception 'invalid score accepted';
  exception when invalid_parameter_value then null; end;
end $$;
select set_config('fixture.actor','00000000-0000-0000-0000-000000000003',false);
do $$ begin
  if exists(select 1 from public.educational_assessments) then raise exception 'unassigned teacher can read scores'; end if;
  begin
    perform public.rc_save_education_score_v12('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000021','general',1::smallint,false);
    raise exception 'unassigned teacher can write scores';
  exception when insufficient_privilege then null; end;
end $$;
select set_config('fixture.actor','00000000-0000-0000-0000-000000000002',false);
do $$ begin
  if (select count(*) from public.educational_assessments) <> 1 then raise exception 'linked guardian cannot read own child'; end if;
  begin
    perform public.rc_save_education_score_v12('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000021','general',1::smallint,false);
    raise exception 'guardian can write scores';
  exception when insufficient_privilege then null; end;
end $$;
select set_config('fixture.actor','00000000-0000-0000-0000-000000000004',false);
do $$ begin
  if exists(select 1 from public.educational_assessments) then raise exception 'unlinked guardian can read scores'; end if;
end $$;
reset role;
do $$ begin
  begin
    update public.sessions set halaqa_id='00000000-0000-0000-0000-000000000012' where id='00000000-0000-0000-0000-000000000031';
    raise exception 'assessed session moved';
  exception when check_violation then null; end;
  begin
    update public.sessions set session_type='test' where id='00000000-0000-0000-0000-000000000031';
    raise exception 'assessed session type changed';
  exception when check_violation then null; end;
  update public.sessions set status='closed' where id='00000000-0000-0000-0000-000000000031';
  update public.sessions set halaqa_id='00000000-0000-0000-0000-000000000012' where id='00000000-0000-0000-0000-000000000032';
  if (select score from public.educational_assessments) is distinct from 4 then raise exception 'score changed during session edits'; end if;
end $$;
