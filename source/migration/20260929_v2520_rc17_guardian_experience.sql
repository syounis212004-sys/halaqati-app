-- ============================================================================
-- Halaqati v2.52.0 RC17 — Guardian experience backend (additive, no table changes)
--  1) guardian_competition_board_range: track-aware (general_hifz vs tathbit), accepts 'tathbit'.
--  2) guardian_child_overview: ONE call returns everything the guardian home needs.
-- Both are SECURITY DEFINER and verify guardian ↔ student link (student_guardians) first.
-- ============================================================================
begin;

create or replace function public.guardian_competition_board_range(
  p_student_id uuid, p_track text default 'hifz', p_scope text default 'center',
  p_from date default current_date, p_to date default current_date)
returns table(rank_no integer, total_students integer, student_id uuid, full_name text, category_name text,
              halaqa_name text, pages numeric, points numeric, is_my_child boolean, avg_pages numeric)
language plpgsql stable security definer set search_path to 'public' as $function$
declare
  v_role public.app_role; v_category uuid; v_halaqa uuid; v_program text;
begin
  v_role := public.current_role();
  if v_role is distinct from 'guardian'::public.app_role then raise exception 'guardian access only'; end if;
  if p_from is null or p_to is null or p_from > p_to then raise exception 'invalid date range'; end if;
  if not exists (select 1 from public.student_guardians sg join public.students s0 on s0.id = sg.student_id
                  where sg.student_id = p_student_id and sg.guardian_id = auth.uid() and s0.status = 'active') then
    raise exception 'student is not linked to this guardian';
  end if;
  if p_scope not in ('center','category','halaqa') then raise exception 'invalid scope'; end if;

  select s0.category_id, s0.halaqa_id, case when s0.track_code = 'tathbit' then 'tathbit' else 'general_hifz' end
    into v_category, v_halaqa, v_program from public.students s0 where s0.id = p_student_id;

  -- The program track is the student's own; tathbit students only compete in tathbit.
  if v_program = 'tathbit' then p_track := 'tathbit';
  elsif p_track not in ('hifz','review','sard') then raise exception 'invalid track';
  end if;
  if p_scope = 'category' and v_category is null then return; end if;

  return query
  with eligible as (
    select s0.id, s0.full_name, c.name as cat_name, h.name as hq_name
      from public.students s0
      left join public.student_categories c on c.id = s0.category_id
      join public.halaqas h on h.id = s0.halaqa_id
     where s0.status = 'active'
       and (case when s0.track_code = 'tathbit' then 'tathbit' else 'general_hifz' end) = v_program
       and (p_scope = 'center' or (p_scope = 'category' and s0.category_id = v_category)
            or (p_scope = 'halaqa' and s0.halaqa_id = v_halaqa))
  ), scored as (
    select e.id as sid, e.full_name as sname, e.cat_name, e.hq_name,
           coalesce(sum(case when se.id is not null and (
                 (p_track = 'hifz' and r.activity_type = 'new_hifz'::public.activity_type)
              or (p_track = 'review' and r.activity_type in ('review_near'::public.activity_type,'review_far'::public.activity_type,'tilawah'::public.activity_type))
              or (p_track = 'sard' and r.activity_type = 'sard'::public.activity_type)
              or (p_track = 'tathbit' and r.activity_type::text <> 'test'))
             then r.pages_count else 0 end),0)::numeric as pg,
           coalesce(sum(case when se.id is not null and (
                 (p_track = 'hifz' and r.activity_type = 'new_hifz'::public.activity_type)
              or (p_track = 'review' and r.activity_type in ('review_near'::public.activity_type,'review_far'::public.activity_type,'tilawah'::public.activity_type))
              or (p_track = 'sard' and r.activity_type = 'sard'::public.activity_type)
              or (p_track = 'tathbit' and r.activity_type::text <> 'test'))
             then r.points else 0 end),0)::numeric as pts
      from eligible e
      left join public.recitation_entries r on r.student_id = e.id
      left join public.sessions se on se.id = r.session_id and se.status <> 'cancelled'
           and se.session_date between p_from and p_to
     group by e.id, e.full_name, e.cat_name, e.hq_name
  ), ranked as (
    select sc.*, row_number() over(order by sc.pts desc, sc.pg desc, sc.sname asc)::int as rn,
           count(*) over()::int as cnt, avg(sc.pg) over()::numeric as avpg
      from scored sc
  ), mine as (select rr.rn as my_rank from ranked rr where rr.sid = p_student_id)
  select rr.rn, rr.cnt, rr.sid, rr.sname, rr.cat_name, rr.hq_name, rr.pg, rr.pts, (rr.sid = p_student_id), round(rr.avpg,2)
    from ranked rr cross join mine mm
   where rr.rn <= 5 or abs(rr.rn - mm.my_rank) <= 2
   order by rr.rn;
end;
$function$;

create or replace function public.guardian_child_overview(p_student_id uuid, p_today date default current_date)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $function$
declare
  v_student record; v_program text; v_month_start date := date_trunc('month', p_today)::date;
  v_plan record; v_result jsonb;
begin
  if public.current_role() is distinct from 'guardian'::public.app_role then raise exception 'guardian access only'; end if;
  if not exists (select 1 from public.student_guardians sg where sg.student_id = p_student_id and sg.guardian_id = auth.uid()) then
    raise exception 'student is not linked to this guardian';
  end if;

  select s.id, s.full_name, s.track_code, s.status, c.name as category_name, h.name as halaqa_name
    into v_student
    from public.students s left join public.student_categories c on c.id = s.category_id
    left join public.halaqas h on h.id = s.halaqa_id where s.id = p_student_id;
  v_program := case when v_student.track_code = 'tathbit' then 'tathbit' else 'general_hifz' end;

  select p.id, p.plan_type, p.title, p.start_date, p.end_date, p.target_pages
    into v_plan
    from public.smart_plans p
   where p.status = 'active' and p.legacy is not true
     and (p.student_id = p_student_id or (p.scope_type = 'halaqa' and p.halaqa_id = (select halaqa_id from students where id = p_student_id)))
     and p_today between p.start_date and p.end_date
     and ((v_program = 'tathbit') = (p.plan_type = 'tathbit'))
   order by (p.student_id = p_student_id) desc nulls last, p.start_date desc limit 1;

  with rec as (
    select r.*, se.session_date
      from public.recitation_entries r join public.sessions se on se.id = r.session_id and se.status <> 'cancelled'
     where r.student_id = p_student_id and se.session_date between least(v_month_start, coalesce(v_plan.start_date, v_month_start)) and p_today
  ), att as (
    select ar.status::text as status, se.session_date
      from public.attendance_records ar join public.sessions se on se.id = ar.session_id and se.status <> 'cancelled'
     where ar.student_id = p_student_id and se.session_date between v_month_start and p_today
  )
  select jsonb_build_object(
    'student', jsonb_build_object('id', v_student.id, 'full_name', v_student.full_name, 'track', v_program,
                                  'category_name', v_student.category_name, 'halaqa_name', v_student.halaqa_name),
    'today', jsonb_build_object(
       'attendance', (select a.status from att a where a.session_date = p_today limit 1),
       'recited', exists (select 1 from rec where session_date = p_today and activity_type::text <> 'test'),
       'pages', coalesce((select sum(pages_count) from rec where session_date = p_today), 0),
       'ranges', coalesce((select jsonb_agg(jsonb_build_object('activity', activity_type, 'start_surah', start_surah, 'start_ayah', start_ayah,
                     'end_surah', end_surah, 'end_ayah', end_ayah, 'pages', pages_count) order by created_at)
                   from rec where session_date = p_today), '[]'::jsonb)),
    'month', jsonb_build_object(
       'hifz', coalesce((select sum(pages_count) from rec where session_date >= v_month_start and activity_type::text = 'new_hifz'), 0),
       'review', coalesce((select sum(pages_count) from rec where session_date >= v_month_start and activity_type::text in ('review_near','review_far','tilawah')), 0),
       'sard', coalesce((select sum(pages_count) from rec where session_date >= v_month_start and activity_type::text = 'sard'), 0),
       -- tathbit students: every non-test recitation is tathbit (legacy rows included), matching the reports
       'tathbit', coalesce((select sum(pages_count) from rec where session_date >= v_month_start and v_program = 'tathbit' and activity_type::text <> 'test'), 0),
       'points', coalesce((select sum(points) from rec where session_date >= v_month_start), 0),
       'quality', (select round(avg(safety_percentage), 1) from rec where session_date >= v_month_start and safety_percentage is not null),
       'mistakes', coalesce((select sum(mistakes) from rec where session_date >= v_month_start), 0),
       'present', (select count(*) from att where status in ('present','late')),
       'absent', (select count(*) from att where status = 'absent'),
       'excused', (select count(*) from att where status = 'excused'),
       'late', (select count(*) from att where status = 'late'),
       'sessions', (select count(*) from att)),
    'plan', case when v_plan.id is null then null else jsonb_build_object(
       'id', v_plan.id, 'type', v_plan.plan_type, 'title', v_plan.title, 'start_date', v_plan.start_date,
       'end_date', v_plan.end_date, 'target_pages', v_plan.target_pages,
       'done_pages', coalesce((select sum(pages_count) from rec where session_date between v_plan.start_date and v_plan.end_date
            and case when v_program = 'tathbit' then activity_type::text <> 'test' else activity_type::text in ('new_hifz','sard') end), 0)) end,
    'latest', coalesce((select jsonb_agg(x order by x->>'date' desc) from (
         select jsonb_build_object('date', session_date, 'activity', activity_type, 'start_surah', start_surah, 'start_ayah', start_ayah,
                'end_surah', end_surah, 'end_ayah', end_ayah, 'pages', pages_count, 'evaluation', evaluation,
                'mistakes', mistakes, 'prompts', prompts, 'quality', safety_percentage) as x
           from rec order by session_date desc, created_at desc limit 5) t), '[]'::jsonb),
    'updated_at', now())
  into v_result;
  return v_result;
end;
$function$;
revoke all on function public.guardian_child_overview(uuid, date) from public;
grant execute on function public.guardian_child_overview(uuid, date) to authenticated;

commit;
