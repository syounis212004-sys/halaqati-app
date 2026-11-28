\set ON_ERROR_STOP on
-- Disposable PG17 fixture, intentionally models the observed broad legacy predicates.
do $roles$ begin
 if not exists(select 1 from pg_roles where rolname='anon') then create role anon; end if;
 if not exists(select 1 from pg_roles where rolname='authenticated') then create role authenticated; end if;
end $roles$;
create schema auth;
create function auth.uid() returns uuid language sql stable as $f$ select nullif(current_setting('fixture.actor',true),'')::uuid $f$;
grant usage on schema auth to authenticated;
create table public.profiles(id uuid primary key,role text,association_id uuid,active boolean default true,frozen boolean default false,approval_status text default 'approved',is_platform_owner boolean default false);
create table public.halaqas(id uuid primary key,association_id uuid,active boolean default true,archived_at timestamptz,halaqa_type text default 'regular',allow_secondary_members boolean default true);
create table public.students(id uuid primary key,halaqa_id uuid references public.halaqas,association_id uuid,user_id uuid,status text default 'active');
create table public.student_guardians(student_id uuid,guardian_id uuid);
create table public.student_halaqa_memberships(student_id uuid,halaqa_id uuid,joined_at date,ended_at date,status text);
create table public.halaqa_teachers(halaqa_id uuid,teacher_id uuid);
create table public.halaqa_supervisors(halaqa_id uuid,supervisor_id uuid);
create table public.sessions(id uuid primary key,halaqa_id uuid references public.halaqas,session_date date,session_type text,status text,activity_data jsonb default '{}',created_by uuid,title text,notes text);
create table public.attendance_records(session_id uuid,student_id uuid,status text);
create table public.recitation_entries(id uuid primary key,session_id uuid,student_id uuid,activity_type text,pages_count numeric);
create table public.student_daily_notes(session_id uuid,student_id uuid,parent_visible boolean default false);
create function public.is_admin() returns boolean language sql stable security definer as $f$ select exists(select 1 from public.profiles where id=auth.uid() and role='admin') $f$;
create function public.is_platform_owner() returns boolean language sql stable security definer as $f$ select exists(select 1 from public.profiles where id=auth.uid() and is_platform_owner) $f$;
create function public.my_association() returns uuid language sql stable security definer as $f$ select association_id from public.profiles where id=auth.uid() $f$;
create function public."current_role"() returns text language sql stable security definer as $f$ select role from public.profiles where id=auth.uid() $f$;
create function public.assoc_of_halaqa(hid uuid) returns uuid language sql stable security definer as $f$ select association_id from public.halaqas where id=hid $f$;
create function public.assoc_ok(a uuid) returns boolean language sql stable as $f$ select a is null or a=public.my_association() or public.is_platform_owner() $f$;
create function public.assoc_writable(a uuid) returns boolean language sql stable as $f$ select true $f$;
create function public.is_halaqa_teacher(hid uuid) returns boolean language sql stable security definer as $f$ select exists(select 1 from public.halaqa_teachers where halaqa_id=hid and teacher_id=auth.uid()) $f$;
create function public.is_halaqa_supervisor(hid uuid) returns boolean language sql stable security definer as $f$ select exists(select 1 from public.halaqa_supervisors where halaqa_id=hid and supervisor_id=auth.uid()) $f$;
create function public.rc86_admin_scope_halaqa(hid uuid) returns boolean language sql stable as $f$ select public.is_platform_owner() or (public.is_admin() and public.assoc_of_halaqa(hid) is not null) $f$;
create function public.can_manage_halaqa(hid uuid) returns boolean language sql stable as $f$ select public.is_admin() or public.is_halaqa_teacher(hid) or public.is_halaqa_supervisor(hid) $f$;
create function public.can_access_halaqa(hid uuid) returns boolean language sql stable as $f$ select public.can_manage_halaqa(hid) $f$;
create function public.rc18_can_manage_student(p_student_id uuid) returns boolean language sql stable as $f$ select public.is_admin() $f$;
grant select on public.profiles,public.halaqas to authenticated;
grant select,insert,update,delete on public.sessions,public.students,public.attendance_records,public.recitation_entries,public.student_daily_notes to authenticated;
alter table public.sessions enable row level security;
create policy fixture_session_legacy on public.sessions for all to authenticated using(true) with check(true);
create policy rc66_assoc_isolation on public.sessions as restrictive for all to authenticated using(true) with check(true);
create policy rc66_assoc_write_ins on public.sessions as restrictive for insert to authenticated with check(true);
create policy rc66_assoc_write_upd on public.sessions as restrictive for update to authenticated using(true) with check(true);
create policy rc66_assoc_write_del on public.sessions as restrictive for delete to authenticated using(true);
alter table public.students enable row level security;
create policy fixture_student_legacy on public.students for all to authenticated using(true) with check(true);
alter table public.attendance_records enable row level security;
create policy fixture_attendance_legacy on public.attendance_records for all to authenticated using(true) with check(true);
alter table public.recitation_entries enable row level security;
create policy fixture_recitation_legacy on public.recitation_entries for all to authenticated using(true) with check(true);
alter table public.student_daily_notes enable row level security;
create policy fixture_notes_legacy on public.student_daily_notes for all to authenticated using(true) with check(true);
insert into public.profiles(id,role,association_id,is_platform_owner,frozen) values
 ('00000000-0000-0000-0000-000000000001','admin',null,true,false),
 ('00000000-0000-0000-0000-000000000002','admin','00000000-0000-0000-0000-000000000091',false,false),
 ('00000000-0000-0000-0000-000000000003','teacher',null,false,false),
 ('00000000-0000-0000-0000-000000000004','teacher',null,false,false),
 ('00000000-0000-0000-0000-000000000005','guardian',null,false,false),
 ('00000000-0000-0000-0000-000000000006','teacher',null,false,true),
 ('00000000-0000-0000-0000-000000000007','supervisor',null,false,false),
 ('00000000-0000-0000-0000-000000000008','teacher','00000000-0000-0000-0000-000000000092',false,false);
insert into public.halaqas(id,association_id) values
 ('00000000-0000-0000-0000-000000000011',null),
 ('00000000-0000-0000-0000-000000000012',null),
 ('00000000-0000-0000-0000-000000000013','00000000-0000-0000-0000-000000000091'),
 ('00000000-0000-0000-0000-000000000014','00000000-0000-0000-0000-000000000092');
insert into public.halaqa_teachers values
 ('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000003'),
 ('00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000004'),
 ('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000006'),
 ('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000008');
insert into public.halaqa_supervisors values ('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000007');
insert into public.students(id,halaqa_id) values
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000011'),
 ('00000000-0000-0000-0000-000000000022','00000000-0000-0000-0000-000000000012');
insert into public.student_guardians values ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000005');
insert into public.student_halaqa_memberships values ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000012','2026-11-01',null,'active');
insert into public.sessions(id,halaqa_id,session_date,session_type,status,created_by) values
 ('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000011','2026-11-28','lesson','open','00000000-0000-0000-0000-000000000008'),
 ('00000000-0000-0000-0000-000000000032','00000000-0000-0000-0000-000000000012','2026-11-28','lesson','open','00000000-0000-0000-0000-000000000004');
insert into public.attendance_records values
 ('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000021','present'),
 ('00000000-0000-0000-0000-000000000032','00000000-0000-0000-0000-000000000021','present'),
 ('00000000-0000-0000-0000-000000000032','00000000-0000-0000-0000-000000000022','absent');
-- Establish the red baseline: old admin predicate actually permits independent groups.
set role authenticated;
select set_config('fixture.actor','00000000-0000-0000-0000-000000000002',false);
do $red$ begin
 if not public.can_manage_halaqa('00000000-0000-0000-0000-000000000011') then raise exception 'fixture no longer reproduces legacy flaw'; end if;
end $red$;
reset role;
\ir ../proposals/phase_four_independent_scope.sql
set role authenticated;
do $test$
declare h uuid:='00000000-0000-0000-0000-000000000011'; p uuid:='00000000-0000-0000-0000-000000000012';
 s uuid:='00000000-0000-0000-0000-000000000031'; c uuid:='00000000-0000-0000-0000-000000000021'; result jsonb;
begin
 if public.can_manage_halaqa(h) or exists(select 1 from public.students) or exists(select 1 from public.sessions) then raise exception 'unassigned admin can access independent records'; end if;
 begin
  insert into public.sessions(id,halaqa_id,status) values(gen_random_uuid(),h,'open');
  raise exception 'unassigned admin inserted independent session';
 exception when insufficient_privilege then null; end;
 begin
  insert into public.students(id,halaqa_id) values(gen_random_uuid(),h);
  raise exception 'unassigned admin inserted independent student';
 exception when insufficient_privilege then null; end;
 if not public.can_manage_halaqa('00000000-0000-0000-0000-000000000013') or public.can_manage_halaqa('00000000-0000-0000-0000-000000000014') then raise exception 'association admin scope wrong'; end if;
 perform set_config('fixture.actor','00000000-0000-0000-0000-000000000003',true);
 if not public.can_manage_halaqa(h) or public.can_manage_halaqa(p) or (select count(*) from public.sessions)<>1 or (select count(*) from public.attendance_records)<>1 then raise exception 'assigned teacher scope wrong'; end if;
 result:=public.rc_transition_session_v12(s,'open','closed');
 if result#>>'{session,status}' is distinct from 'closed' then raise exception 'independent transition failed'; end if;
 result:=public.rc_transition_session_v12(s,'open','closed');
 if result#>>'{session,status}' is distinct from 'closed' then raise exception 'retry not idempotent'; end if;
 perform set_config('fixture.actor','00000000-0000-0000-0000-000000000004',true);
 if (select count(*) from public.students)<>2 or (select count(*) from public.attendance_records)<>2 then raise exception 'program teacher cannot see members or can see primary history'; end if;
 update public.students set status='withdrawn' where id=c;
 if found then raise exception 'secondary program teacher altered primary student identity'; end if;
 begin
  insert into public.attendance_records values(s,c,'absent');
  raise exception 'program teacher inserted into other activity';
 exception when insufficient_privilege then null; end;
 begin
  perform public.rc_transition_session_v12(s,'closed','open');
  raise exception 'unassigned teacher changed independent session';
 exception when insufficient_privilege then null; end;
 perform set_config('fixture.actor','00000000-0000-0000-0000-000000000005',true);
 if (select count(*) from public.students)<>1 or (select count(*) from public.attendance_records)<>2 then raise exception 'guardian isolation wrong'; end if;
 begin
  insert into public.attendance_records values(s,c,'absent');
  raise exception 'guardian write succeeded';
 exception when insufficient_privilege then null; end;
 perform set_config('fixture.actor','00000000-0000-0000-0000-000000000006',true);
 if public.can_manage_halaqa(h) or exists(select 1 from public.sessions) then raise exception 'frozen teacher access'; end if;
 perform set_config('fixture.actor','00000000-0000-0000-0000-000000000007',true);
 if not public.can_manage_halaqa(h) or public.can_manage_halaqa(p) then raise exception 'supervisor scope wrong'; end if;
 perform set_config('fixture.actor','00000000-0000-0000-0000-000000000008',true);
 if not public.can_manage_halaqa(h) or (select count(*) from public.sessions)<>1 then raise exception 'independent assignment lost because staff belongs to association'; end if;
 perform set_config('fixture.actor','00000000-0000-0000-0000-000000000001',true);
 if not public.can_manage_halaqa(h) or not public.can_manage_halaqa(p) or (select count(*) from public.sessions)<>2 then raise exception 'platform owner lost access'; end if;
end $test$;
reset role;
do $preserve$ begin
 if (select count(*) from public.sessions)<>2 or (select count(*) from public.attendance_records)<>3
  or (select count(*) from public.student_halaqa_memberships)<>1 or (select count(*) from public.student_guardians)<>1
  or (select count(*) from public.halaqa_teachers)<>4 then raise exception 'records lost'; end if;
end $preserve$;
