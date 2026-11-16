-- RC32 (2.68.0) — منصب «المراقب» (read-only observer + requests to the system admin).
-- Design: the observer gets NO new table-level read access. Everything goes through three SECURITY DEFINER RPCs that
-- return aggregates + names only (no contact details, identity numbers or notes). Requests live in their own table.
-- Safe to run more than once. Take a database backup first.

alter type public.app_role add value if not exists 'observer';

create or replace function public.rc32_is_observer() returns boolean
language sql stable security definer set search_path to 'public' as $$
  select exists(select 1 from public.profiles p
    where p.id = auth.uid() and p.active = true and p.approval_status = 'approved' and p.role::text = 'observer')
$$;

create table if not exists public.observer_requests (
  id uuid primary key default gen_random_uuid(),
  observer_id uuid not null references public.profiles(id) on delete cascade,
  student_id uuid references public.students(id) on delete set null,
  halaqa_id uuid references public.halaqas(id) on delete set null,
  kind text not null check (kind in ('reward','honor','follow_up','other')),
  note text not null check (char_length(btrim(note)) between 3 and 1000),
  status text not null default 'pending' check (status in ('pending','approved','declined')),
  admin_note text,
  resolved_by uuid references public.profiles(id) on delete set null,
  resolved_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists observer_requests_observer_idx on public.observer_requests (observer_id, created_at desc);
create index if not exists observer_requests_status_idx on public.observer_requests (status, created_at desc);
create index if not exists observer_requests_student_idx on public.observer_requests (student_id);
create index if not exists observer_requests_halaqa_idx on public.observer_requests (halaqa_id);
create index if not exists observer_requests_resolved_by_idx on public.observer_requests (resolved_by);

alter table public.observer_requests enable row level security;
drop policy if exists observer_requests_select on public.observer_requests;
create policy observer_requests_select on public.observer_requests for select to authenticated
  using (observer_id = (select auth.uid()) or public.is_admin());
revoke all on public.observer_requests from anon, authenticated;
grant select on public.observer_requests to authenticated;

-- Overview: per halaqa and per student, for a date range (default last 30 days).
create or replace function public.rc32_observer_overview(p_from date default (current_date - 29), p_to date default current_date)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare result jsonb;
begin
  if not (public.rc32_is_observer() or public.is_admin()) then
    raise exception 'not allowed' using errcode = '42501';
  end if;
  with rec as (
    select re.student_id, count(*) as n, coalesce(sum(re.pages_count),0) as pages,
           avg(re.safety_percentage) filter (where not (re.evaluation = 'not_rated' and coalesce(re.pages_count,0) = 0 and coalesce(re.safety_percentage,0) = 0)) as safety,
           max(s.session_date) as last_date
    from public.recitation_entries re
    join public.sessions s on s.id = re.session_id
    where s.session_date between p_from and p_to
    group by re.student_id
  ), stu as (
    select st.id, st.full_name, st.halaqa_id, coalesce(r.n,0) n, coalesce(r.pages,0) pages, r.safety, r.last_date
    from public.students st left join rec r on r.student_id = st.id
    where st.status = 'active'
  )
  select jsonb_build_object(
    'from', p_from, 'to', p_to,
    'halaqas', coalesce((
      select jsonb_agg(jsonb_build_object(
        'halaqa_id', h.id, 'name', h.name,
        'students', (select count(*) from stu where stu.halaqa_id = h.id),
        'recitations', (select coalesce(sum(n),0) from stu where stu.halaqa_id = h.id),
        'pages', (select coalesce(sum(pages),0) from stu where stu.halaqa_id = h.id),
        'safety', (select round(avg(safety)::numeric,1) from stu where stu.halaqa_id = h.id and safety is not null)
      ) order by h.name)
      from public.halaqas h where h.active = true), '[]'::jsonb),
    'students', coalesce((
      select jsonb_agg(jsonb_build_object(
        'student_id', stu.id, 'name', stu.full_name, 'halaqa_id', stu.halaqa_id,
        'recitations', stu.n, 'pages', stu.pages, 'safety', round(stu.safety::numeric,1), 'last_date', stu.last_date
      ) order by stu.pages desc, stu.full_name)
      from stu), '[]'::jsonb)
  ) into result;
  return result;
end $$;

create or replace function public.rc32_observer_submit_request(p_student uuid, p_kind text, p_note text)
returns uuid language plpgsql security definer set search_path to 'public' as $$
declare rid uuid; sname text; hid uuid; oname text; admin_row record;
begin
  if not public.rc32_is_observer() then raise exception 'not allowed' using errcode = '42501'; end if;
  if p_kind not in ('reward','honor','follow_up','other') then raise exception 'bad kind'; end if;
  select st.full_name, st.halaqa_id into sname, hid from public.students st where st.id = p_student;
  select full_name into oname from public.profiles where id = auth.uid();
  insert into public.observer_requests (observer_id, student_id, halaqa_id, kind, note)
  values (auth.uid(), p_student, hid, p_kind, btrim(p_note)) returning id into rid;
  for admin_row in select id from public.profiles where role = 'admin' and active = true and approval_status = 'approved' loop
    insert into public.notifications (recipient_id, sender_id, halaqa_id, student_id, kind, title, body, source_key, deep_link, data)
    values (admin_row.id, auth.uid(), hid, p_student, 'observer_request',
            'طلب من المراقب ' || coalesce(oname, ''),
            coalesce(sname, 'طالب') || ' — ' || left(btrim(p_note), 160),
            'obs:' || rid::text, 'observer_requests', jsonb_build_object('request_id', rid, 'kind', p_kind))
    on conflict (recipient_id, source_key) do nothing;
  end loop;
  return rid;
end $$;

create or replace function public.rc32_observer_requests()
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not (public.rc32_is_observer() or public.is_admin()) then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', r.id, 'kind', r.kind, 'note', r.note, 'status', r.status, 'admin_note', r.admin_note,
      'created_at', r.created_at, 'resolved_at', r.resolved_at,
      'student_id', r.student_id, 'student_name', st.full_name,
      'halaqa_name', h.name, 'observer_name', p.full_name
    ) order by (r.status = 'pending') desc, r.created_at desc)
    from public.observer_requests r
    left join public.students st on st.id = r.student_id
    left join public.halaqas h on h.id = r.halaqa_id
    left join public.profiles p on p.id = r.observer_id
    where public.is_admin() or r.observer_id = auth.uid()), '[]'::jsonb);
end $$;

create or replace function public.rc32_observer_resolve(p_id uuid, p_status text, p_note text default null)
returns boolean language plpgsql security definer set search_path to 'public' as $$
declare obs uuid; sname text;
begin
  if not public.is_admin() then raise exception 'not allowed' using errcode = '42501'; end if;
  if p_status not in ('approved','declined') then raise exception 'bad status'; end if;
  update public.observer_requests set status = p_status, admin_note = nullif(btrim(coalesce(p_note,'')),''),
         resolved_by = auth.uid(), resolved_at = now()
   where id = p_id and status = 'pending' returning observer_id into obs;
  if obs is null then return false; end if;
  select st.full_name into sname from public.observer_requests r left join public.students st on st.id = r.student_id where r.id = p_id;
  insert into public.notifications (recipient_id, sender_id, kind, title, body, source_key, deep_link)
  values (obs, auth.uid(), 'observer_request_result',
          case p_status when 'approved' then 'تمت الموافقة على طلبك' else 'لم تتم الموافقة على طلبك' end,
          coalesce(sname,'') || coalesce(' — ' || nullif(btrim(coalesce(p_note,'')),''), ''),
          'obsres:' || p_id::text, 'home')
  on conflict (recipient_id, source_key) do nothing;
  return true;
end $$;

revoke all on function public.rc32_is_observer() from public, anon;
revoke all on function public.rc32_observer_overview(date, date) from public, anon;
revoke all on function public.rc32_observer_submit_request(uuid, text, text) from public, anon;
revoke all on function public.rc32_observer_requests() from public, anon;
revoke all on function public.rc32_observer_resolve(uuid, text, text) from public, anon;
grant execute on function public.rc32_is_observer() to authenticated;
grant execute on function public.rc32_observer_overview(date, date) to authenticated;
grant execute on function public.rc32_observer_submit_request(uuid, text, text) to authenticated;
grant execute on function public.rc32_observer_requests() to authenticated;
grant execute on function public.rc32_observer_resolve(uuid, text, text) to authenticated;
