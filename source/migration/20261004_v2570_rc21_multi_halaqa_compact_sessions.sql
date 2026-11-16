-- Halaqati Native v2.57.0 RC21
-- Multi-halaqa memberships + program metadata. Additive and backwards compatible.

begin;

alter table public.halaqas
  add column if not exists halaqa_type text not null default 'regular',
  add column if not exists starts_on date null,
  add column if not exists ends_on date null,
  add column if not exists allow_secondary_members boolean not null default true;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname='halaqas_halaqa_type_check'
  ) then
    alter table public.halaqas add constraint halaqas_halaqa_type_check
      check (halaqa_type in ('regular','tajweed','tilawah','training','course','other'));
  end if;
  if not exists (
    select 1 from pg_constraint where conname='halaqas_program_dates_check'
  ) then
    alter table public.halaqas add constraint halaqas_program_dates_check
      check (ends_on is null or starts_on is null or ends_on >= starts_on);
  end if;
end $$;

create table if not exists public.student_halaqa_memberships (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.students(id) on delete cascade,
  halaqa_id uuid not null references public.halaqas(id) on delete cascade,
  membership_type text not null default 'participant',
  status text not null default 'active',
  joined_at date not null default current_date,
  ended_at date null,
  notes text null,
  added_by uuid null default auth.uid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(student_id,halaqa_id),
  constraint student_halaqa_memberships_type_check check (membership_type in ('participant','candidate','assistant')),
  constraint student_halaqa_memberships_status_check check (status in ('candidate','active','completed','withdrawn','suspended')),
  constraint student_halaqa_memberships_dates_check check (ended_at is null or ended_at >= joined_at)
);

create index if not exists student_halaqa_memberships_halaqa_status_idx
  on public.student_halaqa_memberships(halaqa_id,status,student_id);
create index if not exists student_halaqa_memberships_student_status_idx
  on public.student_halaqa_memberships(student_id,status,halaqa_id);
create index if not exists halaqas_type_active_idx
  on public.halaqas(halaqa_type,active) where archived_at is null;

alter table public.student_halaqa_memberships enable row level security;

create or replace function public.rc21_can_view_membership(p_student_id uuid,p_halaqa_id uuid)
returns boolean
language sql
stable
security definer
set search_path=public
as $$
select public.is_admin()
  or public.can_manage_halaqa(p_halaqa_id)
  or exists(
    select 1 from public.students s
    where s.id=p_student_id
      and (
        public.can_manage_halaqa(s.halaqa_id)
        or s.user_id=auth.uid()
        or exists(select 1 from public.student_guardians sg where sg.student_id=s.id and sg.guardian_id=auth.uid())
      )
  )
$$;

create or replace function public.rc21_can_manage_membership(p_student_id uuid,p_halaqa_id uuid)
returns boolean
language sql
stable
security definer
set search_path=public
as $$
select public.is_admin()
  or public.can_manage_halaqa(p_halaqa_id)
  or exists(
    select 1 from public.students s
    where s.id=p_student_id and public.can_manage_halaqa(s.halaqa_id)
  )
$$;

create or replace function public.rc21_secondary_student_access(p_student_id uuid)
returns boolean
language sql
stable
security definer
set search_path=public
as $$
select exists(
  select 1
  from public.student_halaqa_memberships m
  where m.student_id=p_student_id
    and m.status='active'
    and public.can_manage_halaqa(m.halaqa_id)
)
$$;

-- Students and guardians may read a program session when the student is an active secondary member.
create or replace function public.can_access_halaqa(hid uuid)
returns boolean
language sql
stable
security definer
set search_path=public
as $$
select public.is_admin()
  or public.is_halaqa_supervisor(hid)
  or public.is_halaqa_teacher(hid)
  or exists(select 1 from public.students s where s.halaqa_id=hid and s.user_id=auth.uid())
  or exists(select 1 from public.students s join public.student_guardians sg on sg.student_id=s.id where s.halaqa_id=hid and sg.guardian_id=auth.uid())
  or exists(
    select 1 from public.student_halaqa_memberships m
    join public.students s on s.id=m.student_id
    where m.halaqa_id=hid and m.status='active' and s.user_id=auth.uid()
  )
  or exists(
    select 1 from public.student_halaqa_memberships m
    join public.student_guardians sg on sg.student_id=m.student_id
    where m.halaqa_id=hid and m.status='active' and sg.guardian_id=auth.uid()
  )
$$;

-- Extend the canonical student-access helper used by recitation/report policies.
create or replace function public.can_access_student(sid uuid)
returns boolean
language sql
stable
security definer
set search_path=public
as $$
select exists(
  select 1 from public.students s where s.id=sid and (
    public.is_admin()
    or public.is_halaqa_supervisor(s.halaqa_id)
    or public.is_halaqa_teacher(s.halaqa_id)
    or public.rc21_secondary_student_access(s.id)
    or s.user_id=auth.uid()
    or exists(select 1 from public.student_guardians sg where sg.student_id=s.id and sg.guardian_id=auth.uid())
  )
)
$$;

-- A secondary-course teacher must be able to fetch the linked student row.
drop policy if exists students_secondary_membership_select on public.students;
create policy students_secondary_membership_select
  on public.students for select to authenticated
  using (public.rc21_secondary_student_access(id));

drop policy if exists student_halaqa_memberships_select on public.student_halaqa_memberships;
create policy student_halaqa_memberships_select
  on public.student_halaqa_memberships for select to authenticated
  using (public.rc21_can_view_membership(student_id,halaqa_id));

drop policy if exists student_halaqa_memberships_insert on public.student_halaqa_memberships;
create policy student_halaqa_memberships_insert
  on public.student_halaqa_memberships for insert to authenticated
  with check (public.rc21_can_manage_membership(student_id,halaqa_id));

drop policy if exists student_halaqa_memberships_update on public.student_halaqa_memberships;
create policy student_halaqa_memberships_update
  on public.student_halaqa_memberships for update to authenticated
  using (public.rc21_can_manage_membership(student_id,halaqa_id))
  with check (public.rc21_can_manage_membership(student_id,halaqa_id));

drop policy if exists student_halaqa_memberships_delete on public.student_halaqa_memberships;
create policy student_halaqa_memberships_delete
  on public.student_halaqa_memberships for delete to authenticated
  using (public.rc21_can_manage_membership(student_id,halaqa_id));

revoke all on function public.rc21_can_view_membership(uuid,uuid) from public,anon;
revoke all on function public.rc21_can_manage_membership(uuid,uuid) from public,anon;
revoke all on function public.rc21_secondary_student_access(uuid) from public,anon;
grant execute on function public.rc21_can_view_membership(uuid,uuid) to authenticated;
grant execute on function public.rc21_can_manage_membership(uuid,uuid) to authenticated;
grant execute on function public.rc21_secondary_student_access(uuid) to authenticated;

grant select,insert,update,delete on public.student_halaqa_memberships to authenticated;

commit;
