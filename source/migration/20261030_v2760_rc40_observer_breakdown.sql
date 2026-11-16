-- RC40 (2.76.0) — observer breakdown: hifz / review / sard / tathbit pages separately (read-only, aggregates only).
-- Sard is a span: highest end page minus lowest start page + 1 across the whole period (a repeated sard is not counted twice).
-- Safe to run more than once.
create or replace function public.rc40_observer_breakdown(p_from date default (current_date - 29), p_to date default current_date)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare result jsonb;
begin
  if not (public.rc32_is_observer() or public.is_admin()) then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  with base as (
    select re.student_id, st.halaqa_id,
           case when re.activity_type in ('review','review_near','review_far') then 'review' else re.activity_type end as kind,
           coalesce(re.pages_count,0) as pages, re.start_page, re.end_page
    from public.recitation_entries re
    join public.sessions s on s.id = re.session_id
    join public.students st on st.id = re.student_id
    where s.session_date between p_from and p_to and st.status = 'active'
      and re.activity_type in ('hifz','sard','review','review_near','review_far','tathbit')
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
revoke all on function public.rc40_observer_breakdown(date, date) from public, anon;
grant execute on function public.rc40_observer_breakdown(date, date) to authenticated;
