\set ON_ERROR_STOP on
-- Run in an empty, disposable PostgreSQL 17 database.
do $roles$ begin
  if not exists(select 1 from pg_roles where rolname='anon') then create role anon; end if;
  if not exists(select 1 from pg_roles where rolname='authenticated') then create role authenticated; end if;
end $roles$;
create schema auth;
create function auth.uid() returns uuid language sql stable
as $$ select nullif(current_setting('fixture.actor', true), '')::uuid $$;
create type public.app_role as enum ('guardian', 'teacher');
create function public.current_role() returns public.app_role language sql stable
as $role$ select coalesce(nullif(current_setting('fixture.role',true),''),'guardian')::public.app_role $role$;
create table public.halaqas (id uuid primary key, name text, halaqa_type text);
create table public.student_categories (id uuid primary key, name text);
create table public.students (id uuid primary key, full_name text, track_code text, status text, halaqa_id uuid, category_id uuid);
create table public.student_guardians (student_id uuid, guardian_id uuid);
create table public.sessions (id uuid primary key, halaqa_id uuid, session_date date, status text);
create table public.recitation_entries (
  student_id uuid, session_id uuid, activity_type text, plan_id uuid, phase_id uuid, evaluation text,
  pages_count numeric, points numeric, mistakes int, prompts int, safety_percentage numeric,
  start_surah int, start_ayah int, end_surah int, end_ayah int, created_at timestamptz default now()
);
create table public.attendance_records (student_id uuid, session_id uuid, status text);
create table public.smart_plans (
  id uuid, student_id uuid, category_id uuid, halaqa_id uuid, scope_type text,
  plan_type text, status text, legacy boolean, title text, start_date date, end_date date,
  target_pages numeric, settings jsonb
);
create table public.smart_plan_phases (
  id uuid, plan_id uuid, phase_type text, goal_mode text, target_units numeric, target_pages numeric, status text
);
create table public.smart_plan_phase_states (
  phase_id uuid, student_id uuid, target_pages_override numeric, frozen_target_pages numeric, status text
);

create function public.rc18_guardian_owns_student(p_student_id uuid) returns boolean language sql stable
as $owns$ select exists(select 1 from public.student_guardians sg where sg.student_id=p_student_id and sg.guardian_id=auth.uid()) $owns$;
alter table public.sessions add column title text;
create table public.student_halaqa_memberships(student_id uuid,halaqa_id uuid,status text);
create table public.educational_assessments(session_id uuid,student_id uuid,metric text,score smallint);
\ir ../20261128120800_guardian_activity_overview_v12.sql

insert into public.halaqas values
 ('00000000-0000-0000-0000-000000000011','Regular','regular'),
 ('00000000-0000-0000-0000-000000000012','Program','tilawah'),
 ('00000000-0000-0000-0000-000000000013','Other','tilawah');
insert into public.students values
 ('00000000-0000-0000-0000-000000000021','Child','tathbit','active','00000000-0000-0000-0000-000000000011',null),
 ('00000000-0000-0000-0000-000000000022','Other child','general_hifz','active','00000000-0000-0000-0000-000000000012',null);
insert into public.student_guardians values ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000001');
insert into public.student_halaqa_memberships values
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000012','active'),
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000013','withdrawn');
insert into public.sessions(id,halaqa_id,session_date,status,title) values
 ('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000011','2026-11-28','closed','Regular lesson'),
 ('00000000-0000-0000-0000-000000000032','00000000-0000-0000-0000-000000000012','2026-11-28','open','Program lesson');
insert into public.recitation_entries(student_id,session_id,activity_type,pages_count) values
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000031','tathbit',8),
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000032','tilawah',3),
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000032','tajweed',1),
 ('00000000-0000-0000-0000-000000000022','00000000-0000-0000-0000-000000000032','tilawah',99);
insert into public.attendance_records values
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000031','present'),
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000032','excused');
insert into public.educational_assessments values
 ('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000021','general',4),
 ('00000000-0000-0000-0000-000000000032','00000000-0000-0000-0000-000000000021','general',2),
 ('00000000-0000-0000-0000-000000000032','00000000-0000-0000-0000-000000000022','general',5);
set role authenticated;
select set_config('fixture.actor','00000000-0000-0000-0000-000000000001',false);
do $test$
declare
  child uuid := '00000000-0000-0000-0000-000000000021';
  h uuid := '00000000-0000-0000-0000-000000000011';
  p uuid := '00000000-0000-0000-0000-000000000012';
  result jsonb;
begin
  result:=public.rc_guardian_activities_v12(child);
  if jsonb_array_length(result)<>2 then raise exception 'incorrect active activities'; end if;
  result:=public.rc_guardian_activity_overview_v12(child,h,'2026-11-28');
  if (result#>>'{today,pages}')::numeric is distinct from 8
     or (result#>>'{today,attendance}') is distinct from 'present'
     or jsonb_array_length(result->'educational_assessments')<>1
     or (result#>>'{educational_assessments,0,score}')::integer is distinct from 4 then
    raise exception 'regular activity mixed with program';
  end if;
  result:=public.rc_guardian_activity_overview_v12(child,p,'2026-11-28');
  if (result#>>'{today,pages}')::numeric is distinct from 4
     or (result#>>'{today,attendance}') is distinct from 'excused'
     or (result->'plan') is distinct from 'null'::jsonb
     or (result#>'{student,track}') is distinct from 'null'::jsonb
     or jsonb_array_length(result->'educational_assessments')<>1
     or (result#>>'{educational_assessments,0,score}')::integer is distinct from 2 then
    raise exception 'program activity mixed with regular or other child';
  end if;
  begin
    perform public.rc_guardian_activity_overview_v12(child,'00000000-0000-0000-0000-000000000013','2026-11-28');
    raise exception 'withdrawn membership accessible';
  exception when insufficient_privilege then null; end;
  begin
    perform public.rc_guardian_activity_overview_v12('00000000-0000-0000-0000-000000000022',p,'2026-11-28');
    raise exception 'unlinked child accessible';
  exception when insufficient_privilege then null; end;
  perform set_config('fixture.role','teacher',true);
  begin
    perform public.rc_guardian_activities_v12(child);
    raise exception 'non-guardian accessible';
  exception when insufficient_privilege then null; end;
end $test$;
reset role;
do $acl$ begin
  if has_function_privilege('anon','public.rc_guardian_activities_v12(uuid)','execute')
     or has_function_privilege('anon','public.rc_guardian_activity_overview_v12(uuid,uuid,date)','execute') then
    raise exception 'anonymous guardian RPC exposed';
  end if;
end $acl$;
