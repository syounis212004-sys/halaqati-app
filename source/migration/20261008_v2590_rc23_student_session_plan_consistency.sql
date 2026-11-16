-- Halaqati v2.59.0 RC23
-- Student status consistency + smart-plan milestone decoupling + one session/type/day/halaqa.

-- Manual tilawah already exists in the native UI. Keep the database enum in parity.
alter type public.session_type add value if not exists 'tilawah';

-- Smart-plan milestone exams are no longer part of plan progression.
update public.smart_plans
set milestone_mode = 'none',
    milestone_requires_approval = false,
    updated_at = now()
where milestone_mode is distinct from 'none'
   or milestone_requires_approval is distinct from false;

-- A non-active student must not keep an active student-scoped plan running silently.
update public.smart_plans p
set status = 'paused',
    updated_at = now()
from public.students s
where p.scope_type = 'student'
  and p.student_id = s.id
  and p.status = 'active'
  and s.status <> 'active';

create or replace function public.rc23_guard_smart_plan_consistency()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
  new.milestone_mode := 'none';
  new.milestone_requires_approval := false;

  if new.scope_type = 'student' and new.student_id is not null and new.status = 'active' then
    if exists (
      select 1 from public.students s
      where s.id = new.student_id and s.status <> 'active'
    ) then
      new.status := 'paused';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists rc23_guard_smart_plan_consistency on public.smart_plans;
create trigger rc23_guard_smart_plan_consistency
before insert or update on public.smart_plans
for each row execute function public.rc23_guard_smart_plan_consistency();

create or replace function public.rc23_pause_plans_for_nonactive_student()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
  if old.status = 'active' and new.status <> 'active' then
    update public.smart_plans
       set status = 'paused', updated_at = now()
     where scope_type = 'student'
       and student_id = new.id
       and status = 'active';
  end if;
  return new;
end;
$$;

drop trigger if exists rc23_pause_plans_for_nonactive_student on public.students;
create trigger rc23_pause_plans_for_nonactive_student
after update of status on public.students
for each row execute function public.rc23_pause_plans_for_nonactive_student();

-- Existing duplicate sessions can only be collapsed automatically when the duplicate row is empty.
-- RC22 production had exactly one duplicate pair and both rows were empty.
do $$
begin
  if exists (
    with ranked as (
      select s.id,
             row_number() over (
               partition by s.halaqa_id, s.session_date, s.session_type
               order by s.created_at, s.id
             ) as rn
      from public.sessions s
      where s.status <> 'cancelled'
    )
    select 1
    from ranked r
    where r.rn > 1
      and (
        exists (select 1 from public.attendance_records a where a.session_id = r.id)
        or exists (select 1 from public.recitation_entries x where x.session_id = r.id)
        or exists (select 1 from public.student_daily_notes n where n.session_id = r.id)
        or exists (select 1 from public.notifications z where z.session_id = r.id)
      )
  ) then
    raise exception 'RC23 found duplicate daily sessions that contain data; merge them manually before applying the unique session rule.';
  end if;
end;
$$;

with ranked as (
  select s.id,
         row_number() over (
           partition by s.halaqa_id, s.session_date, s.session_type
           order by s.created_at, s.id
         ) as rn
  from public.sessions s
  where s.status <> 'cancelled'
)
delete from public.sessions s
using ranked r
where s.id = r.id and r.rn > 1;

-- The old title-based key allowed duplicates whenever title was NULL or different.
alter table public.sessions drop constraint if exists sessions_halaqa_id_session_date_session_type_title_key;
create unique index if not exists sessions_one_type_per_halaqa_day
on public.sessions (halaqa_id, session_date, session_type)
where status <> 'cancelled';

-- Atomic online get-or-create. All entry points return the same canonical session.
create or replace function public.rc23_get_or_create_session(
  p_halaqa_id uuid,
  p_session_date date,
  p_session_type public.session_type,
  p_title text default null,
  p_proposed_id uuid default gen_random_uuid()
)
returns setof public.sessions
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  v_row public.sessions%rowtype;
begin
  if auth.uid() is null then
    raise exception using errcode = '42501', message = 'Authentication required';
  end if;

  if not (public.is_admin() or public.can_manage_halaqa(p_halaqa_id)) then
    raise exception using errcode = '42501', message = 'لا تملك صلاحية إدارة هذه الحلقة';
  end if;

  select s.* into v_row
  from public.sessions s
  where s.halaqa_id = p_halaqa_id
    and s.session_date = p_session_date
    and s.session_type = p_session_type
    and s.status <> 'cancelled'
  order by s.created_at, s.id
  limit 1;

  if found then
    return next v_row;
    return;
  end if;

  begin
    insert into public.sessions (
      id, halaqa_id, session_date, session_type, title, status, created_by
    ) values (
      coalesce(p_proposed_id, gen_random_uuid()),
      p_halaqa_id,
      p_session_date,
      p_session_type,
      nullif(trim(coalesce(p_title, '')), ''),
      'open',
      auth.uid()
    )
    returning * into v_row;
  exception when unique_violation then
    select s.* into v_row
    from public.sessions s
    where s.halaqa_id = p_halaqa_id
      and s.session_date = p_session_date
      and s.session_type = p_session_type
      and s.status <> 'cancelled'
    order by s.created_at, s.id
    limit 1;
  end;

  if v_row.id is null then
    raise exception 'تعذر فتح أو إنشاء جلسة اليوم';
  end if;

  return next v_row;
end;
$$;

revoke all on function public.rc23_get_or_create_session(uuid,date,public.session_type,text,uuid) from public;
revoke all on function public.rc23_get_or_create_session(uuid,date,public.session_type,text,uuid) from anon;
grant execute on function public.rc23_get_or_create_session(uuid,date,public.session_type,text,uuid) to authenticated;
