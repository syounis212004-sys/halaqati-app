-- REVIEW-ONLY proposal. Do not deploy or schedule until full-schema staging and owner approval.
-- Existing association payment history must never disappear through a parent deletion.
begin;
alter table public.association_payments
  drop constraint association_payments_association_id_fkey,
  add constraint association_payments_association_id_fkey
    foreign key (association_id) references public.associations(id) on delete restrict;

-- Keep manual reopen state outside client-editable activity_data; old clients cannot erase it.
create schema if not exists halaqati_lifecycle_private;
revoke all on schema halaqati_lifecycle_private from public, anon, authenticated;
create table halaqati_lifecycle_private.session_reopen_exemptions (
  session_id uuid not null references public.sessions(id) on delete cascade,
  session_date date not null,
  primary key(session_id,session_date)
);
alter table halaqati_lifecycle_private.session_reopen_exemptions enable row level security;
revoke all on halaqati_lifecycle_private.session_reopen_exemptions from public,anon,authenticated;

create function halaqati_lifecycle_private.record_manual_reopen()
returns trigger language plpgsql security definer set search_path=''
as $reopen$
begin
  if old.status='closed' and new.status='open' then
    insert into halaqati_lifecycle_private.session_reopen_exemptions(session_id,session_date)
    values(new.id,new.session_date) on conflict do nothing;
  end if;
  return new;
end
$reopen$;
revoke all on function halaqati_lifecycle_private.record_manual_reopen() from public,anon,authenticated;
create trigger phase4_record_session_reopen
after update of status on public.sessions
for each row execute function halaqati_lifecycle_private.record_manual_reopen();

-- Only started, unarchived sessions from before today's Gaza calendar date.
-- No inserts into attendance, no cancellations/deletes, no synthetic sessions.
-- Planned sessions were not actually started and remain planned for review.
-- Process in bounded batches; a nightly transaction waits briefly for concurrent edits
-- instead of silently skipping locked sessions until tomorrow.
create index phase4_open_sessions_by_date_idx on public.sessions(session_date,id) where status='open';
create function halaqati_lifecycle_private.close_past_sessions(
  p_now timestamptz default now(), p_limit integer default 500
) returns integer language plpgsql security invoker set search_path=''
as $close$
declare changed integer;
begin
  if p_now is null or p_limit is null or p_limit not between 1 and 500 then
    raise exception 'invalid automatic closure batch' using errcode='22023';
  end if;
  with eligible as (
    select s.id from public.sessions s
    where s.status='open' and s.session_date < (p_now at time zone 'Asia/Gaza')::date
      and coalesce(s.activity_data->>'archived','false') <> 'true'
      and not exists (
        select 1 from halaqati_lifecycle_private.session_reopen_exemptions e
        where e.session_id=s.id and e.session_date=s.session_date
      )
    order by s.session_date,s.id
    limit p_limit for update of s
  )
  update public.sessions s set status='closed' from eligible e
    where s.id=e.id and s.status='open';
  get diagnostics changed = row_count;
  return changed;
end
$close$;
revoke all on function halaqati_lifecycle_private.close_past_sessions(timestamptz,integer) from public,anon,authenticated;

-- Exactly one committed closure per Gaza calendar day. The GMT scheduler has
-- two candidate UTC times (21:00/22:00), but the non-midnight call is a no-op.
create table halaqati_lifecycle_private.daily_session_closure (
  local_day date primary key,
  closed_count integer not null default 0
);
alter table halaqati_lifecycle_private.daily_session_closure enable row level security;
revoke all on halaqati_lifecycle_private.daily_session_closure from public,anon,authenticated;
create function halaqati_lifecycle_private.close_at_gaza_midnight(p_now timestamptz default now())
returns integer language plpgsql security invoker set search_path='' set lock_timeout='15s'
as $midnight$
declare
  local_time timestamp;
  batch_count integer;
  total_count integer := 0;
begin
  if p_now is null then raise exception 'missing closure time' using errcode='22023'; end if;
  local_time := p_now at time zone 'Asia/Gaza';
  -- Permit a slightly delayed execution, but not any other local hour.
  if extract(hour from local_time) <> 0 then return 0; end if;
  insert into halaqati_lifecycle_private.daily_session_closure(local_day)
    values(local_time::date) on conflict do nothing;
  if not found then return 0; end if;
  loop
    batch_count := halaqati_lifecycle_private.close_past_sessions(p_now,500);
    total_count := total_count + batch_count;
    exit when batch_count < 500;
  end loop;
  update halaqati_lifecycle_private.daily_session_closure set closed_count=total_count
    where local_day=local_time::date;
  return total_count;
end
$midnight$;
revoke all on function halaqati_lifecycle_private.close_at_gaza_midnight(timestamptz) from public,anon,authenticated;
commit;
