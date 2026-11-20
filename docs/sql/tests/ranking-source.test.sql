\set ON_ERROR_STOP on
\pset format unaligned
\pset tuples_only on
create role anon nologin; create role authenticated nologin;
create table students(id uuid primary key, full_name text, category_id uuid, track_code text, halaqa_id uuid);
create table sessions(id uuid primary key, session_date date, status text, halaqa_id uuid);
create table recitation_entries(id uuid primary key default gen_random_uuid(), session_id uuid, student_id uuid, pages_count numeric, mistakes int, prompts int, points numeric, activity_type text, selection_data jsonb, start_surah int, start_ayah int, end_surah int, end_ayah int);
grant select on students, sessions, recitation_entries to authenticated;
alter table students enable row level security; alter table sessions enable row level security; alter table recitation_entries enable row level security;
create policy s on students for select to authenticated using (halaqa_id = 'aaaaaaaa-0000-0000-0000-000000000001' or current_setting('app.role') = 'admin');
create policy ss on sessions for select to authenticated using (true);
create policy r on recitation_entries for select to authenticated using (true);
\i /home/user/halaqati-app/docs/sql/2026-11-20-ranking-source.sql
insert into students values ('51000000-0000-0000-0000-000000000001','s1',null,'general_hifz','aaaaaaaa-0000-0000-0000-000000000001'),('52000000-0000-0000-0000-000000000002','s2',null,'general_hifz','bbbbbbbb-0000-0000-0000-000000000002');
insert into sessions values ('e1000000-0000-0000-0000-000000000001','2026-11-10','completed',null),('e2000000-0000-0000-0000-000000000002','2026-11-11','cancelled',null),('e3000000-0000-0000-0000-000000000003','2026-01-01','completed',null);
insert into recitation_entries(session_id,student_id,pages_count,selection_data) values
 ('e1000000-0000-0000-0000-000000000001','51000000-0000-0000-0000-000000000001',2,'{"qcf_line_keys":["a","b","b","", " "]}'),
 ('e1000000-0000-0000-0000-000000000001','52000000-0000-0000-0000-000000000002',1,'{"qcf_line_keys":["x"]}'),
 ('e2000000-0000-0000-0000-000000000002','51000000-0000-0000-0000-000000000001',1,'{}'),
 ('e3000000-0000-0000-0000-000000000003','51000000-0000-0000-0000-000000000001',1,null),
 ('e1000000-0000-0000-0000-000000000001','51000000-0000-0000-0000-000000000001',1,null);
set role authenticated; set app.role='teacher';
select 'T1 rows=' || jsonb_array_length(public.rc_ranking_source('2026-10-01')) || ' (RLS hides s2, cancelled and old rows: expect 2)';
select 'T2 counts=' || (select string_agg((e->'selection_data'->>'qcf_line_count'), ',' order by e->'selection_data'->>'qcf_line_count') from jsonb_array_elements(public.rc_ranking_source('2026-10-01')) e) || ' (expect 0,2: distinct non-blank keys; null/empty -> 0)';
select 'T3 no keys array leaked=' || (not (public.rc_ranking_source('2026-10-01')::text like '%qcf_line_keys%'));
select 'T4 shape=' || (public.rc_ranking_source('2026-10-01')->0 ?& array['student_id','pages_count','students','sessions','selection_data']);
set app.role='admin'; select 'T5 admin rows=' || jsonb_array_length(public.rc_ranking_source('2026-10-01')) || ' (expect 3)';
select 'T6 limit=' || jsonb_array_length(public.rc_ranking_source('2026-10-01', 1)) || ' (expect 1)';
reset role; set role anon;
do $$ begin perform public.rc_ranking_source('2026-10-01'); raise exception 'anon allowed'; exception when insufficient_privilege then raise notice 'T7 anon denied OK'; end $$;
