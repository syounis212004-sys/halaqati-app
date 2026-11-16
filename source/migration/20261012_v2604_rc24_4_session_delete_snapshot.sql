-- RC24.4: safety net for session deletion.
-- Deleting a session cascades to recitation_entries, attendance_records, student_daily_notes and
-- notifications. Before any session that still has child rows is deleted, keep a JSON snapshot
-- so the rows can be restored. Purely additive; safe to run more than once.

create table if not exists public.deleted_session_snapshots (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null,
  deleted_at timestamptz not null default now(),
  deleted_by uuid default auth.uid(),
  session_row jsonb not null,
  recitation_entries jsonb not null default '[]'::jsonb,
  attendance_records jsonb not null default '[]'::jsonb,
  student_daily_notes jsonb not null default '[]'::jsonb,
  notifications jsonb not null default '[]'::jsonb
);
create index if not exists deleted_session_snapshots_session_idx on public.deleted_session_snapshots(session_id);
alter table public.deleted_session_snapshots enable row level security;

drop policy if exists deleted_session_snapshots_admin_select on public.deleted_session_snapshots;
create policy deleted_session_snapshots_admin_select on public.deleted_session_snapshots
  for select to authenticated using (public.is_admin());

create or replace function public.rc24_4_snapshot_session_before_delete()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $fn$
begin
  insert into public.deleted_session_snapshots(session_id, session_row, recitation_entries, attendance_records, student_daily_notes, notifications)
  select old.id, to_jsonb(old), d.rec, d.att, d.notes, d.notif
  from (
    select
      (select coalesce(jsonb_agg(to_jsonb(r)), '[]'::jsonb) from public.recitation_entries r where r.session_id = old.id) as rec,
      (select coalesce(jsonb_agg(to_jsonb(a)), '[]'::jsonb) from public.attendance_records a where a.session_id = old.id) as att,
      (select coalesce(jsonb_agg(to_jsonb(n)), '[]'::jsonb) from public.student_daily_notes n where n.session_id = old.id) as notes,
      (select coalesce(jsonb_agg(to_jsonb(x)), '[]'::jsonb) from public.notifications x where x.session_id = old.id) as notif
  ) d
  where d.rec <> '[]'::jsonb or d.att <> '[]'::jsonb or d.notes <> '[]'::jsonb or d.notif <> '[]'::jsonb;
  return old;
end
$fn$;

drop trigger if exists rc24_4_snapshot_session_before_delete on public.sessions;
create trigger rc24_4_snapshot_session_before_delete
  before delete on public.sessions
  for each row execute function public.rc24_4_snapshot_session_before_delete();
