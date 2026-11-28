\set ON_ERROR_STOP on
-- Extend the actual guardian v12 fixture; no production rows or credentials.
\ir guardian_activity_overview_v12.sql

alter table public.halaqas add column supervisor_id uuid;
create table public.profiles(id uuid primary key, full_name text, email text);
create table public.halaqa_teachers(halaqa_id uuid,teacher_id uuid);
create table public.halaqa_supervisors(halaqa_id uuid,supervisor_id uuid);
alter table public.sessions add column created_at timestamptz not null default now();
alter table public.recitation_entries add column id uuid default gen_random_uuid() primary key,
  add column selection_data jsonb, add column teacher_note text, add column scoring_version text,
  add column penalty_units numeric, add column penalty_per_page numeric;
create schema halaqati_scope_private;
create function public.can_manage_halaqa(hid uuid) returns boolean language sql stable
as $$ select public.current_role()::text='teacher' and auth.uid()='00000000-0000-0000-0000-000000000002'::uuid
  and hid in ('00000000-0000-0000-0000-000000000012'::uuid,'00000000-0000-0000-0000-000000000014'::uuid) $$;
create function halaqati_scope_private.may_access_record(sid uuid,stid uuid,p_write boolean) returns boolean language sql stable
as $$ select stid='00000000-0000-0000-0000-000000000021'::uuid and exists(select 1 from public.sessions s where s.id=sid and public.can_manage_halaqa(s.halaqa_id)) $$;
insert into public.halaqas(id,name,halaqa_type) values ('00000000-0000-0000-0000-000000000014','Empty program','tilawah');
insert into public.profiles values
 ('00000000-0000-0000-0000-000000000002','Teacher','private@example.invalid'),
 ('00000000-0000-0000-0000-000000000003','Supervisor','private@example.invalid'),
 ('00000000-0000-0000-0000-000000000004','Other','private@example.invalid');
insert into public.halaqa_teachers values
 ('00000000-0000-0000-0000-000000000014','00000000-0000-0000-0000-000000000002'),
 ('00000000-0000-0000-0000-000000000013','00000000-0000-0000-0000-000000000004');
insert into public.halaqa_supervisors values
 ('00000000-0000-0000-0000-000000000014','00000000-0000-0000-0000-000000000003');
update public.halaqas set supervisor_id='00000000-0000-0000-0000-000000000003' where id='00000000-0000-0000-0000-000000000014';
update public.sessions set created_at='2026-11-28 08:00Z' where id='00000000-0000-0000-0000-000000000032';
update public.sessions set created_at='2026-11-28 09:00Z' where id='00000000-0000-0000-0000-000000000033';
update public.recitation_entries set selection_data='{"tilawah_assessment":{"version":2,"lahn_khafi":1,"ward_reviewed":true,"marks":[{"word_id":"2:181:5","note":"private","correction_state":"corrected"}],"followed_refs":["ref"]}}',mistakes=2
  where session_id='00000000-0000-0000-0000-000000000032' and student_id='00000000-0000-0000-0000-000000000021';

\ir ../proposals/seven_points_scoped_projections.sql
-- Fixture models the actual preceding V7 scoring trigger.
create function public.fixture_v7_score() returns trigger language plpgsql as $$ begin new.safety_percentage:=12; new.scoring_version:='v7'; return new; end $$;
create trigger recitation_scoring_trigger before insert or update on public.recitation_entries for each row execute function public.fixture_v7_score();
\ir ../proposals/seven_points_tilawah_scoring.sql

set role authenticated;
select set_config('fixture.role','teacher',false);
select set_config('fixture.actor','00000000-0000-0000-0000-000000000002',false);
do $test$
declare j jsonb;
begin
  j:=public.rc_linked_staff_display_v2(array['00000000-0000-0000-0000-000000000014'::uuid,'00000000-0000-0000-0000-000000000013'::uuid]);
  if jsonb_array_length(j)<>1 or jsonb_array_length(j->0->'staff')<>2 or j::text like '%private@example%' then raise exception 'staff scope or projection failure'; end if;
  j:=public.rc_tilawah_previous_v2('00000000-0000-0000-0000-000000000033',array['00000000-0000-0000-0000-000000000021'::uuid,'00000000-0000-0000-0000-000000000022'::uuid]);
  if jsonb_array_length(j)<>1 or j#>>'{0,assessment,lahn_khafi}' is distinct from '1' then raise exception 'same-day previous history or membership failure'; end if;
end $test$;
select set_config('fixture.role','guardian',false);
select set_config('fixture.actor','00000000-0000-0000-0000-000000000001',false);
do $test$
declare j jsonb;
begin
  j:=public.rc_guardian_activity_overview_v13('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000012','2026-11-28');
  if jsonb_array_length(j->'tilawah_summary')<>2 or j::text like '%private%' or j::text like '%word_id%' then raise exception 'guardian projection leaks or misses rows'; end if;
  if not exists(select 1 from jsonb_array_elements(j->'tilawah_summary') a where a->>'major'='2' and a->>'corrected'='1' and a->>'followed'='1') then raise exception 'guardian summary mismatch'; end if;
  begin
    perform public.rc_guardian_activity_overview_v13('00000000-0000-0000-0000-000000000022','00000000-0000-0000-0000-000000000012','2026-11-28');
    raise exception 'unlinked guardian data visible';
  exception when insufficient_privilege then null; end;
end $test$;
reset role;
do $test$
declare r uuid:='00000000-0000-0000-0000-000000000099'; a jsonb; score numeric;
begin
  a:='{"version":2,"revision":"00000000-0000-0000-0000-000000000101","base_revision":null,"makharij":4,"tajweed":5,"waqf_ibtida":5,"correction_adherence":4,"ward_reviewed":true,"jali_words":1,"jali_letters":0,"jali_harakat":1,"overall_score":99}'::jsonb;
  insert into public.recitation_entries(id,student_id,session_id,activity_type,pages_count,selection_data) values
    (r,'00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000033','tilawah',2,jsonb_build_object('tilawah_assessment',a));
  select safety_percentage into score from public.recitation_entries where id=r;
  if score is distinct from 75.0 then raise exception 'V2 score must be 75, got %',score; end if;
  update public.recitation_entries set selection_data=jsonb_build_object('tilawah_assessment',a) where id=r;
  a:=a||jsonb_build_object('revision','00000000-0000-0000-0000-000000000102','base_revision','00000000-0000-0000-0000-000000000101','jali_words',0);
  update public.recitation_entries set selection_data=jsonb_build_object('tilawah_assessment',a) where id=r;
  begin
    update public.recitation_entries set selection_data=jsonb_build_object('tilawah_assessment',a||jsonb_build_object('revision','00000000-0000-0000-0000-000000000103')) where id=r;
    raise exception 'stale edit accepted';
  exception when sqlstate 'PT409' then null; end;
  begin
    update public.recitation_entries set selection_data='{"tilawah_assessment":{"version":1}}' where id=r;
    raise exception 'older client overwrote V2';
  exception when sqlstate 'PT409' then null; end;
  update public.sessions set title='Historical review preserved' where id='00000000-0000-0000-0000-000000000031';
  begin
    insert into public.sessions(id,halaqa_id,session_date,status,session_type) values('00000000-0000-0000-0000-000000000099','00000000-0000-0000-0000-000000000011','2026-11-29','open','review');
    raise exception 'new standalone review accepted';
  exception when check_violation then null; end;
  if has_function_privilege('anon','public.rc_linked_staff_display_v2(uuid[])','execute')
     or has_function_privilege('anon','public.rc_tilawah_previous_v2(uuid,uuid[])','execute')
     or has_function_privilege('anon','public.rc_guardian_activity_overview_v13(uuid,uuid,date)','execute') then raise exception 'anonymous projection access'; end if;
end $test$;
