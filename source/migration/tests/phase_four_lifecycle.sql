\set ON_ERROR_STOP on
-- Empty, disposable PostgreSQL database only.
create role anon;
create role authenticated;
create table public.associations(id uuid primary key);
create table public.association_payments(id uuid primary key, association_id uuid not null
  constraint association_payments_association_id_fkey references public.associations(id) on delete cascade,
  amount numeric not null);
create table public.sessions(id uuid primary key, session_date date not null, status text not null,
  activity_data jsonb not null default '{}'::jsonb);
create table public.attendance_records(session_id uuid references public.sessions(id), status text);
create table public.recitation_entries(session_id uuid references public.sessions(id), pages numeric);
\ir ../proposals/phase_four_lifecycle.sql
insert into public.associations values ('00000000-0000-0000-0000-000000000001');
insert into public.association_payments values ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001',50);
insert into public.sessions(id,session_date,status,activity_data) values
 ('00000000-0000-0000-0000-000000000011','2026-11-27','open','{"keep":true}'),
 ('00000000-0000-0000-0000-000000000012','2026-11-28','open','{}'),
 ('00000000-0000-0000-0000-000000000013','2026-11-29','open','{}'),
 ('00000000-0000-0000-0000-000000000014','2026-11-27','planned','{}'),
 ('00000000-0000-0000-0000-000000000015','2026-11-27','open','{"archived":true}'),
 ('00000000-0000-0000-0000-000000000016','2026-11-27','closed','{}'),
 ('00000000-0000-0000-0000-000000000017','2026-11-27','cancelled','{}');
insert into public.attendance_records values ('00000000-0000-0000-0000-000000000011','present');
insert into public.recitation_entries values ('00000000-0000-0000-0000-000000000011',2);
do $test$
declare n integer; s uuid := '00000000-0000-0000-0000-000000000011';
begin
  begin
    delete from public.associations where id='00000000-0000-0000-0000-000000000001';
    raise exception 'association payment cascade was not blocked';
  exception when foreign_key_violation then null; end;
  if (select amount from public.association_payments) is distinct from 50 then raise exception 'payment lost'; end if;
  n := halaqati_lifecycle_private.close_past_sessions('2026-11-27 21:59:59+00',500);
  if n<>0 then raise exception 'closed before Gaza midnight'; end if;
  n := halaqati_lifecycle_private.close_past_sessions('2026-11-27 22:00:00+00',500);
  if n<>1 then raise exception 'incorrect midnight closure count: %',n; end if;
  if (select status from public.sessions where id=s) <> 'closed' then raise exception 'past session not closed'; end if;
  if (select count(*) from public.sessions)<>7 or (select count(*) from public.attendance_records)<>1
     or (select status from public.attendance_records) <> 'present' or (select pages from public.recitation_entries)<>2
     or (select activity_data from public.sessions where id=s) <> '{"keep":true}'::jsonb then
    raise exception 'closure changed historical data';
  end if;
  if (select status from public.sessions where id='00000000-0000-0000-0000-000000000014')<>'planned'
     or (select status from public.sessions where id='00000000-0000-0000-0000-000000000015')<>'open'
     or (select status from public.sessions where id='00000000-0000-0000-0000-000000000017')<>'cancelled' then
    raise exception 'noneligible session changed';
  end if;
  if halaqati_lifecycle_private.close_past_sessions('2026-11-28 10:00+00',500)<>0 then raise exception 'not idempotent'; end if;
  update public.sessions set status='open' where id=s;
  if halaqati_lifecycle_private.close_past_sessions('2026-11-28 10:00+00',500)<>0 then raise exception 'manual reopening overwritten'; end if;
  if (select status from public.sessions where id=s)<>'open' then raise exception 'manual reopening not preserved'; end if;
  if has_schema_privilege('authenticated','halaqati_lifecycle_private','USAGE')
     or has_function_privilege('authenticated','halaqati_lifecycle_private.close_past_sessions(timestamptz,integer)','EXECUTE')
     or has_function_privilege('anon','halaqati_lifecycle_private.close_past_sessions(timestamptz,integer)','EXECUTE') then
    raise exception 'client can run privileged scheduled closure';
  end if;
end
$test$;
