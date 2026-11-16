-- Halaqati Native v2.56.0 RC20 — UI/Document Consistency support
-- Additive, idempotent. Adds soft-archive metadata for halaqas; no historical data is deleted.
begin;
alter table public.halaqas add column if not exists archived_at timestamptz null;
create index if not exists halaqas_archived_at_idx on public.halaqas(archived_at);
comment on column public.halaqas.archived_at is 'Soft archive timestamp. Archived halaqas remain available for historical reports and can be restored.';
commit;
