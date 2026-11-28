\set ON_ERROR_STOP on
-- Run in an empty, disposable PostgreSQL 17 database as its owner.
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated; end if;
end $$;
create schema auth;
create function auth.uid() returns uuid language sql stable
as $$ select nullif(current_setting('fixture.actor', true), '')::uuid $$;
create function auth.role() returns text language sql stable
as $$ select 'authenticated'::text $$;

create type public.app_role as enum ('guardian', 'teacher', 'supervisor', 'admin', 'observer');
create table public.profiles (
  id uuid primary key, role public.app_role not null, requested_role public.app_role,
  association_id uuid, approval_status text not null default 'pending',
  active boolean not null default false, frozen boolean not null default false,
  onboarded boolean not null default true, is_platform_owner boolean not null default false,
  full_name text, email text, phone text, created_at timestamptz default now(),
  approved_at timestamptz, approved_by uuid, rejection_reason text
);
create table public.halaqas (id uuid primary key, name text);
create table public.students (
  id uuid primary key, association_id uuid, full_name text, status text not null,
  halaqa_id uuid, primary_teacher_id uuid
);
create table public.guardian_link_requests (
  id uuid primary key, guardian_id uuid references public.profiles,
  student_id uuid references public.students, status text not null default 'pending',
  teacher_id uuid, teacher_at timestamptz, admin_id uuid, admin_at timestamptz,
  reason text, created_at timestamptz default now()
);
create table public.student_guardians (
  student_id uuid references public.students, guardian_id uuid references public.profiles,
  relation text, is_primary boolean, primary key(student_id, guardian_id)
);
create table public.fixture_events (target uuid, action text);
create function public.current_role() returns public.app_role language sql stable as $$
  select role from public.profiles where id = auth.uid()
$$;
create function public.my_association() returns uuid language sql stable as $$
  select association_id from public.profiles where id = auth.uid()
$$;
create function public.is_platform_owner() returns boolean language sql stable as $$
  select coalesce((select is_platform_owner and role = 'admin' from public.profiles where id = auth.uid()), false)
$$;
create function public.rc_is_student_teacher(actor uuid, student uuid) returns boolean language sql stable as $$
  select exists (select 1 from public.students where id = student and primary_teacher_id = actor)
$$;
create function public.rc_approval_notify(target uuid, title text, body text, key text) returns void language sql as $$
  insert into public.fixture_events values (target, key)
$$;
create function public.rc_audit(entity text, id text, action text, details jsonb) returns void language sql as $$
  insert into public.fixture_events values (id::uuid, action)
$$;

\ir ../20261128120000_guardian_link_review_scope.sql

do $$
declare
  assoc_a uuid := gen_random_uuid();
  assoc_b uuid := gen_random_uuid();
  admin_a uuid := gen_random_uuid();
  admin_b uuid := gen_random_uuid();
  teacher uuid := gen_random_uuid();
  guardian uuid := gen_random_uuid();
  staff uuid := gen_random_uuid();
  unscoped uuid := gen_random_uuid();
  child_a uuid := gen_random_uuid();
  child_b uuid := gen_random_uuid();
  inactive_child uuid := gen_random_uuid();
  link_a uuid := gen_random_uuid();
  link_b uuid := gen_random_uuid();
  inactive_link uuid := gen_random_uuid();
  event_count int;
begin
  insert into public.profiles(id, role, requested_role, association_id, approval_status, active)
  values (admin_a, 'admin', null, assoc_a, 'approved', true),
         (admin_b, 'admin', null, assoc_b, 'approved', true),
         (teacher, 'teacher', null, assoc_a, 'approved', true),
         (guardian, 'guardian', 'guardian', null, 'pending', false),
         (staff, 'guardian', 'teacher', assoc_a, 'pending', false),
         (unscoped, 'guardian', 'observer', null, 'pending', false);
  insert into public.students(id, association_id, full_name, status, primary_teacher_id)
  values (child_a, assoc_a, 'Fixture A', 'active', teacher),
         (child_b, assoc_b, 'Fixture B', 'active', null),
         (inactive_child, assoc_a, 'Fixture inactive', 'inactive', teacher);
  insert into public.guardian_link_requests(id, guardian_id, student_id)
  values (link_a, guardian, child_a), (link_b, guardian, child_b),
         (inactive_link, guardian, inactive_child);

  perform set_config('fixture.actor', admin_a::text, true);
  if public.rc_pending_requests() @> jsonb_build_array(jsonb_build_object('id', link_b)) then
    raise exception 'cross-association request visible';
  end if;
  begin
    update public.profiles set approval_status = 'approved', active = true where id = guardian;
    raise exception 'direct profile transition allowed';
  exception when insufficient_privilege then null; end;
  update public.profiles set association_id = assoc_a where id = guardian;
  if (public.rc_review_request('account', guardian, 'approve')->>'ok')::boolean is not false then
    raise exception 'guardian account treated as a standalone request';
  end if;
  update public.profiles set association_id = null where id = guardian;
  begin
    perform public.rc_review_request('link', link_b, 'approve');
    raise exception 'cross-scope approval allowed';
  exception when insufficient_privilege then null; end;
  begin
    perform public.rc_review_request('account', unscoped, 'approve');
    raise exception 'unscoped profile approved by association admin';
  exception when insufficient_privilege then null; end;
  if (public.rc_review_request('link', inactive_link, 'approve')->>'ok')::boolean is not false then
    raise exception 'inactive student link accepted';
  end if;
  if (public.rc_review_request('account', staff, 'approve')->>'ok')::boolean is not true then
    raise exception 'staff approval regressed';
  end if;

  perform set_config('fixture.actor', teacher::text, true);
  if (public.rc_review_request('link', link_a, 'approve')->>'ok')::boolean is not true then
    raise exception 'teacher review failed';
  end if;
  if exists (select 1 from public.student_guardians where guardian_id = guardian) then
    raise exception 'teacher review must stage the request without granting a relationship';
  end if;
  perform set_config('fixture.actor', admin_a::text, true);
  if (public.rc_review_request('link', link_a, 'approve')->>'ok')::boolean is not true then
    raise exception 'admin review failed';
  end if;
  if not exists (select 1 from public.student_guardians where student_id = child_a and guardian_id = guardian)
     or not exists (select 1 from public.profiles where id = guardian and approval_status = 'approved' and active) then
    raise exception 'account and link were not approved together';
  end if;
  select count(*) into event_count from public.fixture_events;
  if (public.rc_review_request('link', link_a, 'approve')->>'ok')::boolean is not false then
    raise exception 'retry reported another successful transition';
  end if;
  if (select count(*) from public.fixture_events) <> event_count then
    raise exception 'retry repeated approval effects';
  end if;

  perform set_config('fixture.actor', admin_b::text, true);
  if (public.rc_review_request('link', link_b, 'approve')->>'ok')::boolean is not false then
    raise exception 'second association acquired a guardian relationship';
  end if;
  perform public.rc_review_request('link', link_b, 'reject');
  if not exists (select 1 from public.profiles where id = guardian and active)
     or (select count(*) from public.student_guardians where guardian_id = guardian) <> 1 then
    raise exception 'link rejection changed the valid account or relationship';
  end if;
end
$$;
