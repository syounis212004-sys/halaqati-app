-- v2.92.0 — Donor contests (مسابقات المانحين).
-- The donor (role 'observer') proposes a contest for a halaqa he is linked to (table donor_halaqas, RC48). The system admin approves it
-- (optionally assigning / correcting the halaqa) or rejects it with a reason. Once approved, everyone connected to the halaqa
-- (teachers, supervisors, guardians of its students, the students) reads it, read-only.
-- Idempotent. NOT applied to any project by the author: review, back up, then run in the Supabase SQL editor.
-- Design: docs/discovery/donor-contests/DESIGN.md. Definer functions pin `search_path = public, pg_temp` (lesson of the digest/search_path hotfix).

-- 1) Table ---------------------------------------------------------------------------------------------------------------------
create table if not exists public.donor_contests (
  id uuid primary key default gen_random_uuid(),
  donor_id uuid not null references public.profiles(id) on delete cascade,
  halaqa_id uuid references public.halaqas(id) on delete set null,
  name text not null check (char_length(btrim(name)) between 3 and 80),
  title text not null check (char_length(btrim(title)) between 3 and 120),
  conditions text not null check (char_length(btrim(conditions)) between 10 and 2000),
  reward text not null check (char_length(btrim(reward)) between 3 and 500),
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  reject_reason text check (reject_reason is null or char_length(btrim(reject_reason)) between 3 and 500),
  decided_by uuid references public.profiles(id) on delete set null,
  decided_at timestamptz,
  starts_on date,
  ends_on date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint donor_contests_dates_ok check (starts_on is null or ends_on is null or ends_on >= starts_on),
  constraint donor_contests_reject_reason_ok check (status <> 'rejected' or reject_reason is not null)
);
create index if not exists donor_contests_donor_idx on public.donor_contests (donor_id, created_at desc);
create index if not exists donor_contests_status_idx on public.donor_contests (status, created_at desc);
create index if not exists donor_contests_halaqa_idx on public.donor_contests (halaqa_id) where status = 'approved';
create index if not exists donor_contests_decided_by_idx on public.donor_contests (decided_by);

-- 2) Helpers (SECURITY DEFINER: membership lives in tables whose own RLS would hide rows inside a policy) --------------------------
create or replace function public.contest_is_donor() returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists(select 1 from public.profiles p
    where p.id = auth.uid() and p.active = true and p.approval_status = 'approved' and p.role::text = 'observer')
$$;

create or replace function public.contest_donor_linked(p_halaqa uuid) returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists(select 1 from public.donor_halaqas d
    where d.donor_id = auth.uid() and d.halaqa_id = p_halaqa and d.active = true)
$$;

-- Halaqat the caller is connected to as teacher, supervisor, guardian of a student, or student.
create or replace function public.contest_user_halaqas() returns setof uuid
language sql stable security definer set search_path = public, pg_temp as $$
  select ht.halaqa_id from public.halaqa_teachers ht where ht.teacher_id = auth.uid()
  union select hs.halaqa_id from public.halaqa_supervisors hs where hs.supervisor_id = auth.uid()
  union select h.id from public.halaqas h where h.supervisor_id = auth.uid()
  union select s.halaqa_id from public.students s where s.user_id = auth.uid() and s.halaqa_id is not null
  union select s.halaqa_id from public.student_guardians sg join public.students s on s.id = sg.student_id
        where sg.guardian_id = auth.uid() and s.halaqa_id is not null
  union select m.halaqa_id from public.student_halaqa_memberships m join public.students s on s.id = m.student_id
        where s.user_id = auth.uid() or exists(select 1 from public.student_guardians sg where sg.student_id = s.id and sg.guardian_id = auth.uid())
$$;

-- 3) RLS -----------------------------------------------------------------------------------------------------------------------
alter table public.donor_contests enable row level security;

drop policy if exists donor_contests_donor_select on public.donor_contests;
create policy donor_contests_donor_select on public.donor_contests for select to authenticated
  using (donor_id = (select auth.uid()));

drop policy if exists donor_contests_admin_select on public.donor_contests;
create policy donor_contests_admin_select on public.donor_contests for select to authenticated
  using (public.is_admin());

drop policy if exists donor_contests_related_select on public.donor_contests;
create policy donor_contests_related_select on public.donor_contests for select to authenticated
  using (status = 'approved' and halaqa_id is not null and halaqa_id in (select public.contest_user_halaqas()));

drop policy if exists donor_contests_donor_insert on public.donor_contests;
create policy donor_contests_donor_insert on public.donor_contests for insert to authenticated
  with check (
    donor_id = (select auth.uid()) and public.contest_is_donor()
    and status = 'pending' and decided_by is null and decided_at is null and reject_reason is null
    and (halaqa_id is null or public.contest_donor_linked(halaqa_id))
  );

drop policy if exists donor_contests_donor_update on public.donor_contests;
create policy donor_contests_donor_update on public.donor_contests for update to authenticated
  using (donor_id = (select auth.uid()) and status = 'pending')
  with check (
    donor_id = (select auth.uid()) and status = 'pending' and decided_by is null and decided_at is null and reject_reason is null
    and (halaqa_id is null or public.contest_donor_linked(halaqa_id))
  );

drop policy if exists donor_contests_admin_update on public.donor_contests;
create policy donor_contests_admin_update on public.donor_contests for update to authenticated
  using (public.is_admin()) with check (public.is_admin());

revoke all on public.donor_contests from anon, authenticated;
grant select, insert, update on public.donor_contests to authenticated;

-- 4) RPCs ----------------------------------------------------------------------------------------------------------------------
-- Halaqat a contest can target: the donor's linked active halaqat; the admin sees every active halaqa (to assign one).
create or replace function public.contest_donor_halaqas() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if public.is_admin() then
    return coalesce((select jsonb_agg(jsonb_build_object('id', h.id, 'name', h.name) order by h.name)
                     from public.halaqas h where h.active = true), '[]'::jsonb);
  end if;
  if not public.contest_is_donor() then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('id', h.id, 'name', h.name) order by h.name)
                   from public.donor_halaqas d join public.halaqas h on h.id = d.halaqa_id
                   where d.donor_id = auth.uid() and d.active = true and h.active = true), '[]'::jsonb);
end $$;

create or replace function public.contest_submit(
  p_name text, p_title text, p_conditions text, p_reward text,
  p_halaqa uuid default null, p_starts_on date default null, p_ends_on date default null
) returns uuid
language plpgsql security definer set search_path = public, pg_temp as $$
declare cid uuid; dname text; hname text; admin_row record; has_link boolean;
begin
  if not public.contest_is_donor() then raise exception 'not allowed' using errcode = '42501'; end if;
  select exists(select 1 from public.donor_halaqas d where d.donor_id = auth.uid() and d.active = true) into has_link;
  if p_halaqa is not null and not public.contest_donor_linked(p_halaqa) then
    raise exception 'halaqa not linked to this donor' using errcode = '42501';
  end if;
  if p_halaqa is null and has_link then raise exception 'choose a halaqa' using errcode = '22023'; end if;
  insert into public.donor_contests (donor_id, halaqa_id, name, title, conditions, reward, starts_on, ends_on)
  values (auth.uid(), p_halaqa, btrim(p_name), btrim(p_title), btrim(p_conditions), btrim(p_reward), p_starts_on, p_ends_on)
  returning id into cid;
  select full_name into dname from public.profiles where id = auth.uid();
  select name into hname from public.halaqas where id = p_halaqa;
  for admin_row in select id from public.profiles where role::text = 'admin' and active = true and approval_status = 'approved' loop
    insert into public.notifications (recipient_id, sender_id, halaqa_id, kind, title, body, source_key, deep_link, data)
    values (admin_row.id, auth.uid(), p_halaqa, 'contest_pending',
            'مسابقة جديدة بانتظار قرارك',
            coalesce(dname, 'مانح') || ' — ' || left(btrim(p_name), 120) || coalesce(' — ' || hname, ' — الحلقة غير محددة'),
            'contest:' || cid::text || ':pending', 'contests', jsonb_build_object('contest_id', cid))
    on conflict (recipient_id, source_key) do nothing;
  end loop;
  return cid;
end $$;

create or replace function public.contest_my() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if not public.contest_is_donor() then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', c.name, 'title', c.title, 'conditions', c.conditions, 'reward', c.reward, 'status', c.status,
      'reject_reason', c.reject_reason, 'halaqa_id', c.halaqa_id, 'halaqa_name', h.name,
      'starts_on', c.starts_on, 'ends_on', c.ends_on, 'created_at', c.created_at
    ) order by c.created_at desc)
    from public.donor_contests c left join public.halaqas h on h.id = c.halaqa_id
    where c.donor_id = auth.uid()), '[]'::jsonb);
end $$;

create or replace function public.contest_admin_list() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if not public.is_admin() then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', c.name, 'title', c.title, 'conditions', c.conditions, 'reward', c.reward, 'status', c.status,
      'reject_reason', c.reject_reason, 'halaqa_id', c.halaqa_id, 'halaqa_name', h.name, 'donor_id', c.donor_id,
      'donor_name', p.full_name, 'starts_on', c.starts_on, 'ends_on', c.ends_on, 'created_at', c.created_at
    ) order by (c.status = 'pending') desc, c.created_at desc)
    from public.donor_contests c
    left join public.halaqas h on h.id = c.halaqa_id
    left join public.profiles p on p.id = c.donor_id), '[]'::jsonb);
end $$;

-- Approved contests of the halaqat the caller is connected to (teacher / supervisor / guardian / student).
create or replace function public.contest_visible() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if auth.uid() is null then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', c.name, 'title', c.title, 'conditions', c.conditions, 'reward', c.reward, 'status', c.status,
      'halaqa_id', c.halaqa_id, 'halaqa_name', h.name, 'donor_name', p.full_name,
      'starts_on', c.starts_on, 'ends_on', c.ends_on, 'created_at', c.created_at
    ) order by c.decided_at desc nulls last, c.created_at desc)
    from public.donor_contests c
    join public.halaqas h on h.id = c.halaqa_id
    left join public.profiles p on p.id = c.donor_id
    where c.status = 'approved' and c.halaqa_id in (select public.contest_user_halaqas())), '[]'::jsonb);
end $$;

-- Admin decision. Only pending -> approved | rejected. Returns false when the contest was already decided.
create or replace function public.contest_decide(
  p_id uuid, p_status text, p_reason text default null, p_halaqa uuid default null, p_link_donor boolean default false
) returns boolean
language plpgsql security definer set search_path = public, pg_temp as $$
declare c public.donor_contests%rowtype; target uuid; hname text; reason text := nullif(btrim(coalesce(p_reason, '')), ''); r record;
begin
  if not public.is_admin() then raise exception 'not allowed' using errcode = '42501'; end if;
  if p_status not in ('approved', 'rejected') then raise exception 'bad status' using errcode = '22023'; end if;
  select * into c from public.donor_contests where id = p_id for update;
  if not found or c.status <> 'pending' then return false; end if;
  target := coalesce(p_halaqa, c.halaqa_id);
  if p_status = 'rejected' and (reason is null or char_length(reason) < 3) then raise exception 'reason required' using errcode = '22023'; end if;
  if p_status = 'approved' and target is null then raise exception 'halaqa required' using errcode = '22023'; end if;
  if target is not null and not exists(select 1 from public.halaqas h where h.id = target and h.active = true) then
    raise exception 'unknown halaqa' using errcode = '22023';
  end if;
  update public.donor_contests
     set status = p_status, halaqa_id = target, reject_reason = case when p_status = 'rejected' then reason else null end,
         decided_by = auth.uid(), decided_at = now(), updated_at = now()
   where id = p_id;
  if p_link_donor and target is not null then
    insert into public.donor_halaqas (donor_id, halaqa_id, active, updated_at) values (c.donor_id, target, true, now())
    on conflict (donor_id, halaqa_id) do update set active = true, updated_at = now();
  end if;
  select name into hname from public.halaqas where id = target;
  insert into public.notifications (recipient_id, sender_id, halaqa_id, kind, title, body, source_key, deep_link, data)
  values (c.donor_id, auth.uid(), target, 'contest_decision',
          case p_status when 'approved' then 'اعتُمدت مسابقتك' else 'لم تُعتمد مسابقتك' end,
          left(c.name, 120) || case p_status when 'approved' then coalesce(' — ' || hname, '') else coalesce(' — ' || reason, '') end,
          'contest:' || p_id::text || ':decision', 'contests', jsonb_build_object('contest_id', p_id, 'status', p_status))
  on conflict (recipient_id, source_key) do nothing;
  if p_status = 'approved' then
    for r in
      select distinct u.uid from (
        select ht.teacher_id as uid from public.halaqa_teachers ht where ht.halaqa_id = target
        union select hs.supervisor_id from public.halaqa_supervisors hs where hs.halaqa_id = target
        union select h.supervisor_id from public.halaqas h where h.id = target and h.supervisor_id is not null
        union select s.user_id from public.students s where s.halaqa_id = target and s.user_id is not null and s.status = 'active'
        union select sg.guardian_id from public.student_guardians sg join public.students s on s.id = sg.student_id
              where s.halaqa_id = target and s.status = 'active'
      ) u where u.uid is not null and u.uid <> c.donor_id
    loop
      insert into public.notifications (recipient_id, sender_id, halaqa_id, kind, title, body, source_key, deep_link, data)
      values (r.uid, auth.uid(), target, 'contest_approved', 'مسابقة جديدة في حلقتك',
              left(c.name, 120) || coalesce(' — ' || hname, ''),
              'contest:' || p_id::text || ':approved', 'contests', jsonb_build_object('contest_id', p_id))
      on conflict (recipient_id, source_key) do nothing;
    end loop;
  end if;
  return true;
end $$;

revoke all on function public.contest_is_donor() from public, anon;
revoke all on function public.contest_donor_linked(uuid) from public, anon;
revoke all on function public.contest_user_halaqas() from public, anon;
revoke all on function public.contest_donor_halaqas() from public, anon;
revoke all on function public.contest_submit(text, text, text, text, uuid, date, date) from public, anon;
revoke all on function public.contest_my() from public, anon;
revoke all on function public.contest_admin_list() from public, anon;
revoke all on function public.contest_visible() from public, anon;
revoke all on function public.contest_decide(uuid, text, text, uuid, boolean) from public, anon;
grant execute on function public.contest_is_donor() to authenticated;
grant execute on function public.contest_donor_linked(uuid) to authenticated;
grant execute on function public.contest_user_halaqas() to authenticated;
grant execute on function public.contest_donor_halaqas() to authenticated;
grant execute on function public.contest_submit(text, text, text, text, uuid, date, date) to authenticated;
grant execute on function public.contest_my() to authenticated;
grant execute on function public.contest_admin_list() to authenticated;
grant execute on function public.contest_visible() to authenticated;
grant execute on function public.contest_decide(uuid, text, text, uuid, boolean) to authenticated;
