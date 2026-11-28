-- PC-06. Keep the existing trigger and permissions. No historical rows are rewritten.
begin;

CREATE OR REPLACE FUNCTION public.rc15_guard_recitation_track()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
declare
  v_tathbit boolean;
  v_plan_type text;
  v_date date;
  v_program boolean;
begin
  select coalesce(nullif(h.halaqa_type, ''), 'regular') <> 'regular'
    into v_program
    from public.sessions s join public.halaqas h on h.id = s.halaqa_id
    where s.id = new.session_id;

  -- Programs assess recitation independently of the student's memorization track.
  -- Preserve RLS and any membership checks on the existing write path.
  if coalesce(v_program, false) and new.activity_type::text not in ('tilawah', 'test') then
    raise exception 'جلسات البرامج تقبل التلاوة والاختبار فقط' using errcode = '23514';
  end if;
  if coalesce(v_program, false) or new.activity_type::text = 'tilawah' then
    new.plan_id := null;
    new.phase_id := null;
    return new;
  end if;

  select (s.track_code = 'tathbit') into v_tathbit from public.students s where s.id = new.student_id;
  if coalesce(v_tathbit, false) then
    if new.activity_type::text not in ('tathbit','test') then
      if new.activity_type::text in ('review','review_near','review_far') then
        new.activity_type := 'tathbit'::public.activity_type;
      else
        raise exception 'طالب مسار التثبيت يُسجَّل له تثبيت فقط' using errcode = '23514';
      end if;
    end if;
    new.phase_id := null;
    if new.plan_id is not null then
      select p.plan_type into v_plan_type from public.smart_plans p where p.id = new.plan_id;
      if v_plan_type is distinct from 'tathbit' then new.plan_id := null; end if;
    end if;
    if new.plan_id is null then
      select s.session_date into v_date from public.sessions s where s.id = new.session_id;
      select p.id into new.plan_id from public.smart_plans p
       where p.student_id = new.student_id and p.plan_type = 'tathbit' and p.status <> 'archived'
         and coalesce(v_date, current_date) between p.start_date and p.end_date
       order by p.start_date desc limit 1;
    end if;
  else
    if new.activity_type::text = 'tathbit' then
      raise exception 'التثبيت نشاط خاص بطلاب مسار التثبيت' using errcode = '23514';
    end if;
    if new.plan_id is not null
       and exists (select 1 from public.smart_plans p where p.id = new.plan_id and p.plan_type = 'tathbit') then
      new.plan_id := null;
    end if;
  end if;
  return new;
end $function$
;

drop trigger rc15_guard_recitation_track on public.recitation_entries;
create trigger rc15_guard_recitation_track
before insert or update of student_id, session_id, activity_type, plan_id, phase_id
on public.recitation_entries for each row execute function public.rc15_guard_recitation_track();

commit;
