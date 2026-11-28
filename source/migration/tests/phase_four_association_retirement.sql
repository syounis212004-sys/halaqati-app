\set ON_ERROR_STOP on
-- Disposable PostgreSQL only: test the transaction, not the production RLS matrix.
do $roles$ begin
  if not exists(select 1 from pg_roles where rolname='anon') then create role anon; end if;
  if not exists(select 1 from pg_roles where rolname='authenticated') then create role authenticated; end if;
end $roles$;
create schema auth;
create function auth.uid() returns uuid language sql stable as $f$ select nullif(current_setting('fixture.actor',true),'')::uuid $f$;
create table public.associations(id uuid primary key,status text not null default 'active');
create table public.profiles(id uuid primary key,association_id uuid references public.associations,
 role text not null,is_platform_owner boolean not null default false,active boolean not null default true,
 frozen boolean not null default false,approval_status text not null default 'approved');
create table public.centers(id uuid primary key,association_id uuid references public.associations,manager_id uuid references public.profiles);
create table public.halaqas(id uuid primary key,association_id uuid references public.associations,center_id uuid references public.centers,supervisor_id uuid references public.profiles);
create table public.students(id uuid primary key,association_id uuid references public.associations,halaqa_id uuid references public.halaqas,primary_teacher_id uuid references public.profiles);
create table public.halaqa_teachers(halaqa_id uuid references public.halaqas,teacher_id uuid references public.profiles);
create table public.halaqa_supervisors(halaqa_id uuid references public.halaqas,supervisor_id uuid references public.profiles);
create table public.student_halaqa_memberships(student_id uuid references public.students,halaqa_id uuid references public.halaqas);
create table public.student_guardians(student_id uuid references public.students,guardian_id uuid references public.profiles);
create table public.association_payments(id uuid primary key,association_id uuid not null constraint association_payments_association_id_fkey references public.associations on delete restrict,amount numeric);
create table public.sessions(id uuid primary key,halaqa_id uuid references public.halaqas);
create table public.recitation_entries(session_id uuid references public.sessions,student_id uuid references public.students,pages numeric);
\ir ../proposals/phase_four_association_retirement.sql

do $test$
declare
  a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); owner uuid:=gen_random_uuid();
  teacher uuid:=gen_random_uuid(); other_teacher uuid:=gen_random_uuid(); supervisor uuid:=gen_random_uuid();
  guardian uuid:=gen_random_uuid(); center uuid:=gen_random_uuid(); h uuid:=gen_random_uuid(); program uuid:=gen_random_uuid();
  child uuid:=gen_random_uuid(); sess uuid:=gen_random_uuid(); payment uuid:=gen_random_uuid(); result jsonb;
begin
  insert into public.associations(id) values(a),(b);
  insert into public.profiles(id,association_id,role,is_platform_owner) values
    (owner,null,'admin',true),(teacher,a,'teacher',false),(other_teacher,b,'teacher',false),
    (supervisor,a,'supervisor',false),(guardian,a,'guardian',false);
  insert into public.centers values(center,a,supervisor);
  insert into public.halaqas values(h,a,center,supervisor),(program,a,center,supervisor);
  insert into public.students values(child,a,h,teacher);
  insert into public.halaqa_teachers values(h,teacher),(program,teacher);
  insert into public.halaqa_supervisors values(h,supervisor),(program,supervisor);
  insert into public.student_halaqa_memberships values(child,program);
  insert into public.student_guardians values(child,guardian);
  insert into public.association_payments values(payment,a,100);
  insert into public.sessions values(sess,h);
  insert into public.recitation_entries values(sess,child,3);
  perform set_config('fixture.actor',teacher::text,true);
  begin
    perform halaqati_lifecycle_private.retire_association(a);
    raise exception 'non-owner retirement permitted';
  exception when insufficient_privilege then null; end;
  perform set_config('fixture.actor',owner::text,true);
  insert into public.halaqa_teachers values(h,other_teacher);
  begin
    perform halaqati_lifecycle_private.retire_association(a);
    raise exception 'cross-scope assignment retirement permitted';
  exception when check_violation then null; end;
  if (select association_id from public.centers where id=center) is distinct from a then raise exception 'partial retirement'; end if;
  delete from public.halaqa_teachers where teacher_id=other_teacher;
  result:=halaqati_lifecycle_private.retire_association(a);
  if (result->>'payments_preserved')::int is distinct from 1
     or not exists(select 1 from public.associations where id=a and retired_at is not null and status='suspended')
     or not exists(select 1 from public.associations where id=b and retired_at is null)
     or (select count(*) from public.halaqas where center_id=center and association_id is null)<>2
     or not exists(select 1 from public.students where id=child and halaqa_id=h and association_id is null and primary_teacher_id=teacher)
     or (select count(*) from public.profiles where id in (teacher,supervisor,guardian) and association_id is null and active)<>3
     or (select count(*) from public.halaqa_teachers)<>2
     or (select count(*) from public.halaqa_supervisors)<>2
     or not exists(select 1 from public.student_halaqa_memberships where student_id=child and halaqa_id=program)
     or not exists(select 1 from public.student_guardians where student_id=child and guardian_id=guardian)
     or not exists(select 1 from public.recitation_entries where session_id=sess and student_id=child and pages=3)
     or not exists(select 1 from public.association_payments where id=payment and association_id=a and amount=100)
  then raise exception 'retirement failed to preserve hierarchy or records'; end if;
  if (halaqati_lifecycle_private.retire_association(a)->>'already_retired')::boolean is distinct from true then
    raise exception 'retirement is not idempotent';
  end if;
  if has_schema_privilege('authenticated','halaqati_lifecycle_private','usage')
     or has_function_privilege('authenticated','halaqati_lifecycle_private.retire_association(uuid)','execute')
     or has_function_privilege('anon','halaqati_lifecycle_private.retire_association(uuid)','execute') then
    raise exception 'retirement exposed before authorization review';
  end if;
end $test$;
