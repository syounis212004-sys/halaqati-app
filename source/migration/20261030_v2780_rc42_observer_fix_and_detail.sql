-- RC42 (2.78.0) — fixes rc40_observer_breakdown (activity_type is an enum: new_hifz / review_near / review_far / sard / tilawah / test / tathbit)
-- and adds a read-only per-student detail for the observer. Aggregates and recitation facts only; no contact details or notes.
create or replace function public.rc40_observer_breakdown(p_from date default (current_date - 29), p_to date default current_date)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare result jsonb;
begin
  if not (public.rc32_is_observer() or public.is_admin()) then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  with base as (
    select re.student_id, st.halaqa_id,
           case re.activity_type::text when 'new_hifz' then 'hifz' when 'review_near' then 'review' when 'review_far' then 'review' else re.activity_type::text end as kind,
           coalesce(re.pages_count,0) as pages, re.start_page, re.end_page
    from public.recitation_entries re
    join public.sessions s on s.id = re.session_id
    join public.students st on st.id = re.student_id
    where s.session_date between p_from and p_to and st.status = 'active'
      and re.activity_type::text in ('new_hifz','sard','review_near','review_far','tathbit')
  ), per_student as (
    select student_id, halaqa_id,
      coalesce(sum(pages) filter (where kind='hifz'),0) as hifz,
      coalesce(sum(pages) filter (where kind='review'),0) as review,
      coalesce(sum(pages) filter (where kind='tathbit'),0) as tathbit,
      case when count(*) filter (where kind='sard' and start_page is not null and end_page is not null) > 0
           then greatest(max(end_page) filter (where kind='sard') - min(start_page) filter (where kind='sard') + 1, 0)
           else coalesce(sum(pages) filter (where kind='sard'),0) end as sard
    from base group by student_id, halaqa_id
  )
  select jsonb_build_object(
    'students', coalesce((select jsonb_agg(jsonb_build_object('student_id',student_id,'halaqa_id',halaqa_id,'hifz',round(hifz::numeric,2),'review',round(review::numeric,2),'sard',round(sard::numeric,2),'tathbit',round(tathbit::numeric,2))) from per_student),'[]'::jsonb),
    'halaqas', coalesce((select jsonb_agg(jsonb_build_object('halaqa_id',h.halaqa_id,'hifz',round(h.hifz::numeric,2),'review',round(h.review::numeric,2),'sard',round(h.sard::numeric,2),'tathbit',round(h.tathbit::numeric,2)))
      from (select halaqa_id, sum(hifz) hifz, sum(review) review, sum(sard) sard, sum(tathbit) tathbit from per_student group by halaqa_id) h),'[]'::jsonb)
  ) into result;
  return result;
end $$;

create or replace function public.rc42_observer_student_detail(p_student uuid, p_from date default (current_date - 29), p_to date default current_date)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare result jsonb;
begin
  if not (public.rc32_is_observer() or public.is_admin()) then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  select jsonb_build_object(
    'attendance', coalesce((
      select jsonb_object_agg(status, n) from (
        select ar.status::text as status, count(*) n from public.attendance_records ar
        join public.sessions s on s.id = ar.session_id
        where ar.student_id = p_student and s.session_date between p_from and p_to group by ar.status) a), '{}'::jsonb),
    'recitations', coalesce((
      select jsonb_agg(x order by x->>'date' desc) from (
        select jsonb_build_object(
          'date', s.session_date, 'kind', re.activity_type::text,
          'start_surah', re.start_surah, 'start_ayah', re.start_ayah, 'end_surah', re.end_surah, 'end_ayah', re.end_ayah,
          'start_page', re.start_page, 'end_page', re.end_page, 'pages', re.pages_count,
          'evaluation', re.evaluation::text, 'safety', re.safety_percentage) as x
        from public.recitation_entries re join public.sessions s on s.id = re.session_id
        where re.student_id = p_student and s.session_date between p_from and p_to
        order by s.session_date desc limit 80) r), '[]'::jsonb)
  ) into result;
  return result;
end $$;

revoke all on function public.rc40_observer_breakdown(date, date) from public, anon;
revoke all on function public.rc42_observer_student_detail(uuid, date, date) from public, anon;
grant execute on function public.rc40_observer_breakdown(date, date) to authenticated;
grant execute on function public.rc42_observer_student_detail(uuid, date, date) to authenticated;
