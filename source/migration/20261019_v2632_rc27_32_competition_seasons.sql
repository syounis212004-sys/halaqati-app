-- Halaqati Native v2.63.32 RC27.32
-- Competition seasons between halaqas (or between categories). Additive; safe to run twice.
-- Programs (tilawah/tajweed/courses) never compete with halaqas: the app only compares regular halaqas.

begin;

create table if not exists public.competition_seasons (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  level text not null default 'halaqa',
  start_date date not null,
  end_date date not null,
  status text not null default 'active',
  winners jsonb not null default '[]'::jsonb,
  created_by uuid null default auth.uid(),
  created_at timestamptz not null default now(),
  closed_at timestamptz null,
  constraint competition_seasons_level_check check (level in ('halaqa','category')),
  constraint competition_seasons_status_check check (status in ('active','closed')),
  constraint competition_seasons_dates_check check (end_date >= start_date),
  constraint competition_seasons_name_check check (length(btrim(name)) >= 2)
);

create index if not exists competition_seasons_status_start_idx
  on public.competition_seasons(status, start_date desc);

-- Only one active season at a time.
create unique index if not exists competition_seasons_one_active_idx
  on public.competition_seasons((status)) where status = 'active';

alter table public.competition_seasons enable row level security;

drop policy if exists competition_seasons_select on public.competition_seasons;
create policy competition_seasons_select on public.competition_seasons
  for select to authenticated using (true);

drop policy if exists competition_seasons_insert on public.competition_seasons;
create policy competition_seasons_insert on public.competition_seasons
  for insert to authenticated
  with check (public.is_admin() or public.current_role()::text = 'supervisor');

drop policy if exists competition_seasons_update on public.competition_seasons;
create policy competition_seasons_update on public.competition_seasons
  for update to authenticated
  using (public.is_admin() or public.current_role()::text = 'supervisor')
  with check (public.is_admin() or public.current_role()::text = 'supervisor');

drop policy if exists competition_seasons_delete on public.competition_seasons;
create policy competition_seasons_delete on public.competition_seasons
  for delete to authenticated using (public.is_admin());

commit;

-- Read-only check after running:
-- select id, name, level, status from public.competition_seasons;
