-- RC25 (2.61.0): scale/performance hardening. Additive and idempotent; run once in the Supabase SQL Editor.
-- 1) RLS policies re-evaluated auth.uid() for every row. Wrap it in (select auth.uid()) so it is computed once.
-- 2) Missing foreign-key indexes (deleting a session scanned the whole notifications table).
-- 3) One duplicate index removed.
-- No policy meaning changes: only the auth.uid() calls are wrapped.

do $$
declare
  r record;
  q text;
  w text;
  stmt text;
  changed int := 0;
begin
  for r in
    select schemaname, tablename, policyname, qual, with_check
    from pg_policies
    where schemaname = 'public'
      and policyname = any (array[
        'profiles_update','halaqas_insert','guardians_select',
        'forum_posts_insert','forum_posts_update','forum_posts_delete',
        'forum_comments_insert','forum_comments_update','forum_comments_delete',
        'user_devices_delete_own','user_devices_select_own','user_devices_insert_own','user_devices_update_own',
        'notification_preferences_select_own','notification_preferences_insert_own','notification_preferences_update_own',
        'notifications_select_access','notifications_update_read_own',
        'students_select','sessions_delete','halaqa_supervisors_select'
      ])
  loop
    q := case when r.qual is null then null
              else regexp_replace(r.qual, '(?<!select )auth\.uid\(\)', '(select auth.uid())', 'g') end;
    w := case when r.with_check is null then null
              else regexp_replace(r.with_check, '(?<!select )auth\.uid\(\)', '(select auth.uid())', 'g') end;
    if q is distinct from r.qual or w is distinct from r.with_check then
      stmt := format('alter policy %I on %I.%I', r.policyname, r.schemaname, r.tablename);
      if q is not null then stmt := stmt || ' using (' || q || ')'; end if;
      if w is not null then stmt := stmt || ' with check (' || w || ')'; end if;
      execute stmt;
      changed := changed + 1;
    end if;
  end loop;
  raise notice 'RC25: % policies updated', changed;
end $$;

create index if not exists notifications_session_id_idx on public.notifications(session_id);
create index if not exists notifications_student_id_idx on public.notifications(student_id);
create index if not exists notifications_halaqa_id_idx on public.notifications(halaqa_id);

-- identical to student_guardians_guardian_student_idx
drop index if exists public.student_guardians_guardian_id_idx;

-- ---------------------------------------------------------------------------
-- RC25: one-tap restore of a deleted session from its snapshot (admin only).
-- Depends on 20261012_v2604 (deleted_session_snapshots). Run v2604 first.
-- ---------------------------------------------------------------------------
alter table public.deleted_session_snapshots add column if not exists restored_at timestamptz;

create or replace function public.rc25_restore_deleted_session(p_snapshot_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  s public.deleted_session_snapshots%rowtype;
  v_sess public.sessions%rowtype;
  v_target uuid;
  v_rec int := 0; v_att int := 0; v_notes int := 0;
begin
  if not public.is_admin() then
    raise exception 'admin only' using errcode = '42501';
  end if;
  select * into s from public.deleted_session_snapshots where id = p_snapshot_id for update;
  if not found then raise exception 'snapshot not found'; end if;

  v_sess := jsonb_populate_record(null::public.sessions, s.session_row);

  -- if a live session already occupies the same halaqa/day/type, restore the data into it
  select id into v_target from public.sessions
   where halaqa_id = v_sess.halaqa_id and session_date = v_sess.session_date
     and session_type = v_sess.session_type and status <> 'cancelled'
   limit 1;

  if v_target is null then
    if v_sess.status = 'cancelled' then v_sess.status := 'open'; end if;
    insert into public.sessions select (v_sess).* on conflict (id) do nothing;
    v_target := v_sess.id;
  end if;

  insert into public.recitation_entries
    select (jsonb_populate_record(null::public.recitation_entries,
             e || jsonb_build_object('session_id', v_target))).*
    from jsonb_array_elements(s.recitation_entries) e
  on conflict (id) do nothing;
  get diagnostics v_rec = row_count;

  insert into public.attendance_records
    select (jsonb_populate_record(null::public.attendance_records,
             e || jsonb_build_object('session_id', v_target))).*
    from jsonb_array_elements(s.attendance_records) e
  on conflict (id) do nothing;
  get diagnostics v_att = row_count;

  insert into public.student_daily_notes
    select (jsonb_populate_record(null::public.student_daily_notes,
             e || jsonb_build_object('session_id', v_target))).*
    from jsonb_array_elements(s.student_daily_notes) e
  on conflict (id) do nothing;
  get diagnostics v_notes = row_count;

  update public.deleted_session_snapshots set restored_at = now() where id = s.id;
  return jsonb_build_object('session_id', v_target, 'recitations', v_rec, 'attendance', v_att, 'notes', v_notes);
end $$;

revoke all on function public.rc25_restore_deleted_session(uuid) from public, anon;
grant execute on function public.rc25_restore_deleted_session(uuid) to authenticated;
