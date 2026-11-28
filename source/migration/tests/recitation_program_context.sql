\set ON_ERROR_STOP on
-- Run in an empty, disposable PostgreSQL 17 database.
create type public.activity_type as enum ('new_hifz', 'review', 'review_near', 'review_far', 'tathbit', 'tilawah', 'test');
create table public.halaqas (id uuid primary key, halaqa_type text);
create table public.sessions (id uuid primary key, halaqa_id uuid references public.halaqas, session_date date);
create table public.students (id uuid primary key, track_code text);
create table public.smart_plans (id uuid primary key, student_id uuid, plan_type text, status text, start_date date, end_date date);
create table public.recitation_entries (
  id uuid primary key default gen_random_uuid(), student_id uuid references public.students,
  session_id uuid references public.sessions, activity_type public.activity_type not null,
  plan_id uuid, phase_id uuid
);
create function public.rc15_guard_recitation_track() returns trigger language plpgsql as $$ begin return new; end $$;
create trigger rc15_guard_recitation_track
before insert or update of student_id, activity_type, plan_id, phase_id on public.recitation_entries
for each row execute function public.rc15_guard_recitation_track();

\ir ../20261128120100_recitation_program_context.sql

do $$
declare
  primary_group uuid := gen_random_uuid();
  program_group uuid := gen_random_uuid();
  primary_session uuid := gen_random_uuid();
  program_session uuid := gen_random_uuid();
  student uuid := gen_random_uuid();
  plan uuid := gen_random_uuid();
  entry public.recitation_entries%rowtype;
begin
  insert into public.halaqas values (primary_group, 'regular'), (program_group, 'tilawah');
  insert into public.sessions values (primary_session, primary_group, current_date), (program_session, program_group, current_date);
  insert into public.students values (student, 'tathbit');
  insert into public.smart_plans values (plan, student, 'tathbit', 'active', current_date - 1, current_date + 1);

  insert into public.recitation_entries(student_id, session_id, activity_type, plan_id, phase_id)
  values (student, program_session, 'tilawah', plan, gen_random_uuid()) returning * into entry;
  if entry.activity_type <> 'tilawah' or entry.plan_id is not null or entry.phase_id is not null then
    raise exception 'program recitation attached to memorization plan';
  end if;
  insert into public.recitation_entries(student_id, session_id, activity_type, plan_id)
  values (student, program_session, 'test', plan) returning * into entry;
  if entry.activity_type <> 'test' or entry.plan_id is not null then
    raise exception 'program test attached to memorization plan';
  end if;

  insert into public.recitation_entries(student_id, session_id, activity_type)
  values (student, primary_session, 'review_near') returning * into entry;
  if entry.activity_type <> 'tathbit' or entry.plan_id is distinct from plan then
    raise exception 'regular tathbit recitation changed';
  end if;
  begin
    update public.recitation_entries set session_id = program_session where id = entry.id;
    raise exception 'moving memorization activity to program accepted';
  exception when check_violation then null; end;
end
$$;
