\set ON_ERROR_STOP on
-- Run in an empty, disposable PostgreSQL 17 database.
create schema auth;
create function auth.uid() returns uuid language sql stable
as $$ select nullif(current_setting('fixture.actor', true), '')::uuid $$;
create type public.app_role as enum ('guardian', 'teacher');
create function public.current_role() returns public.app_role language sql stable
as $$ select 'guardian'::public.app_role $$;
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
\ir ../20261128120200_guardian_overview_primary_scope.sql

do $$
declare
  guardian uuid := gen_random_uuid();
  child uuid := gen_random_uuid();
  primary_group uuid := gen_random_uuid();
  program_group uuid := gen_random_uuid();
  primary_session uuid := gen_random_uuid();
  program_session uuid := gen_random_uuid();
  expected_day date := date '2026-11-28';
  result jsonb;
begin
  insert into public.halaqas values (primary_group, 'Primary', 'regular'), (program_group, 'Program', 'tilawah');
  insert into public.students values (child, 'Fixture', 'general_hifz', 'active', primary_group, null);
  insert into public.student_guardians values (child, guardian);
  insert into public.sessions values (primary_session, primary_group, expected_day, 'open'), (program_session, program_group, expected_day, 'open');
  insert into public.attendance_records values (child, primary_session, 'present'), (child, program_session, 'absent');
  insert into public.recitation_entries(student_id, session_id, activity_type, pages_count)
  values (child, primary_session, 'review_near', 2), (child, primary_session, 'tilawah', 1), (child, program_session, 'tilawah', 10);

  perform set_config('fixture.actor', gen_random_uuid()::text, true);
  begin
    perform public.guardian_child_overview(child, expected_day);
    raise exception 'unexpected access to an unlinked child' using errcode = 'XX000';
  exception when raise_exception then null; end;

  perform set_config('fixture.actor', guardian::text, true);
  result := public.guardian_child_overview(child, expected_day);
  if (result #>> '{month,review}')::numeric is distinct from 2
     or (result #>> '{month,absent}')::int is distinct from 0
     or (result #>> '{today,attendance}') is distinct from 'present' then
    raise exception 'regular overview mixed activity or attendance scopes';
  end if;
  update public.students set halaqa_id = program_group where id = child;
  result := public.guardian_child_overview(child, expected_day);
  if (result #> '{student,track}') is distinct from 'null'::jsonb
     or (result #> '{plan}') is distinct from 'null'::jsonb
     or (result #>> '{today,pages}')::numeric is distinct from 10
     or (result #>> '{today,attendance}') is distinct from 'absent' then
    raise exception 'program overview included the memorization track or its activity';
  end if;
end
$$;
