-- REVIEW-ONLY, deliberately not an exposed RPC.
-- Depends on phase_four_lifecycle.sql (payment FK RESTRICT).
-- Do not activate in production until independent-scope authorization is verified for
-- every role, legacy RPC and table, and archived associations are hidden from live UI.
-- Retirement is logical: retain the association row as the financial history anchor.
begin;
alter table public.associations add column retired_at timestamptz;
comment on column public.associations.retired_at is 'Retired organization; retain for payment audit, not active organization selection.';
create schema if not exists halaqati_lifecycle_private;
revoke all on schema halaqati_lifecycle_private from public,anon,authenticated;

create function halaqati_lifecycle_private.retire_association(p_association uuid)
returns jsonb language plpgsql security invoker set search_path=''
as $retire$
declare
  a public.associations%rowtype;
  n_centers integer; n_halaqas integer; n_students integer; n_profiles integer; n_payments integer;
begin
  if auth.uid() is null or not exists(
    select 1 from public.profiles p where p.id=auth.uid()
      and p.role::text='admin' and p.is_platform_owner and p.active
      and not p.frozen and p.approval_status='approved'
  ) then raise exception 'platform owner required' using errcode='42501'; end if;
  if not exists(select 1 from pg_catalog.pg_constraint c
    where c.conrelid='public.association_payments'::regclass
    and c.conname='association_payments_association_id_fkey' and c.confdeltype='r') then
    raise exception 'payment protection must be installed first' using errcode='55000';
  end if;
  -- Fail rather than wait behind concurrent edits. This lock footprint needs staging load tests.
  lock table public.associations,public.centers,public.halaqas,public.profiles,public.students,
    public.halaqa_teachers,public.halaqa_supervisors,public.student_halaqa_memberships,
    public.student_guardians,public.association_payments in share row exclusive mode nowait;
  select * into a from public.associations where id=p_association for update;
  if not found then raise exception 'association not found' using errcode='22023'; end if;
  if a.retired_at is not null then return jsonb_build_object('ok',true,'already_retired',true); end if;

  -- A center, group or student must not silently move data owned by another association.
  if exists(select 1 from public.halaqas h join public.centers c on c.id=h.center_id
      where (h.association_id=p_association or c.association_id=p_association)
        and h.association_id is distinct from c.association_id)
    or exists(select 1 from public.students s join public.halaqas h on h.id=s.halaqa_id
      where (s.association_id=p_association or h.association_id=p_association)
        and s.association_id is distinct from h.association_id)
    or exists(select 1 from public.students s where s.association_id=p_association and s.halaqa_id is null)
  then raise exception 'inconsistent organization hierarchy; repair before retirement' using errcode='23514'; end if;

  -- Cross-association links can lose access under the old restrictive policies.
  -- Do not delete them, rewrite them or silently widen permissions: abort for a reviewed transfer.
  if exists(select 1 from public.halaqa_teachers t join public.halaqas h on h.id=t.halaqa_id
      join public.profiles p on p.id=t.teacher_id
      where (h.association_id=p_association or p.association_id=p_association)
        and h.association_id is distinct from p.association_id)
    or exists(select 1 from public.halaqa_supervisors t join public.halaqas h on h.id=t.halaqa_id
      join public.profiles p on p.id=t.supervisor_id
      where (h.association_id=p_association or p.association_id=p_association)
        and h.association_id is distinct from p.association_id)
    or exists(select 1 from public.halaqas h join public.profiles p on p.id=h.supervisor_id
      where (h.association_id=p_association or p.association_id=p_association)
        and h.association_id is distinct from p.association_id)
    or exists(select 1 from public.centers c join public.profiles p on p.id=c.manager_id
      where (c.association_id=p_association or p.association_id=p_association)
        and c.association_id is distinct from p.association_id)
    or exists(select 1 from public.students s join public.profiles p on p.id=s.primary_teacher_id
      where (s.association_id=p_association or p.association_id=p_association)
        and s.association_id is distinct from p.association_id)
    or exists(select 1 from public.student_halaqa_memberships m join public.students s on s.id=m.student_id
      join public.halaqas h on h.id=m.halaqa_id
      where (s.association_id=p_association or h.association_id=p_association)
        and s.association_id is distinct from h.association_id)
    or exists(select 1 from public.student_guardians g join public.students s on s.id=g.student_id
      join public.profiles p on p.id=g.guardian_id
      where (s.association_id=p_association or p.association_id=p_association)
        and s.association_id is distinct from p.association_id)
  then raise exception 'cross-scope relationships require reviewed access transfer' using errcode='23514'; end if;

  select count(*) into n_centers from public.centers where association_id=p_association;
  select count(*) into n_halaqas from public.halaqas where association_id=p_association;
  select count(*) into n_students from public.students where association_id=p_association;
  select count(*) into n_profiles from public.profiles where association_id=p_association;
  select count(*) into n_payments from public.association_payments where association_id=p_association;

  -- Preserve center_id, halaqa_id, roles, all assignments, membership IDs and guardian links.
  update public.centers set association_id=null where association_id=p_association;
  update public.halaqas set association_id=null where association_id=p_association;
  update public.students set association_id=null where association_id=p_association;
  update public.profiles set association_id=null where association_id=p_association;
  if exists(select 1 from public.centers where association_id=p_association)
    or exists(select 1 from public.halaqas where association_id=p_association)
    or exists(select 1 from public.students where association_id=p_association)
    or exists(select 1 from public.profiles where association_id=p_association)
    or (select count(*) from public.association_payments where association_id=p_association)<>n_payments
  then raise exception 'retirement preservation check failed' using errcode='23514'; end if;
  update public.associations set retired_at=now(),status='suspended' where id=p_association;
  return jsonb_build_object('ok',true,'centers',n_centers,'halaqas',n_halaqas,'students',n_students,
    'profiles',n_profiles,'payments_preserved',n_payments);
end
$retire$;
revoke all on function halaqati_lifecycle_private.retire_association(uuid) from public,anon,authenticated;
commit;
