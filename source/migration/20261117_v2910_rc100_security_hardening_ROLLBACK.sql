-- Rollback of 20261117_v2910_rc100_security_hardening.sql: restores the exact state read from production on 2026-11-16.
begin;

create or replace function public.protect_profile_security_fields()
 returns trigger
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
begin
  if coalesce(auth.role(),'') <> 'service_role'
     and not public.is_admin()
     and coalesce(current_setting('halaqati.review_ok', true), '') <> '1' then
    if new.role is distinct from old.role
       or new.active is distinct from old.active
       or new.approval_status is distinct from old.approval_status
       or new.approved_at is distinct from old.approved_at
       or new.approved_by is distinct from old.approved_by
       or new.rejection_reason is distinct from old.rejection_reason
       or new.onboarded is distinct from old.onboarded
       or new.frozen is distinct from old.frozen
       or new.frozen_reason is distinct from old.frozen_reason then
      raise exception 'هذه الحقول لا يغيرها إلا مسؤول النظام';
    end if;
  end if;
  new.updated_at := now();
  return new;
end $function$;

grant insert, update, delete, truncate, references, trigger
  on public.app_support_info, public.association_payments, public.associations, public.centers,
     public.exam_examiners, public.exam_settings, public.student_exams
  to anon;

grant execute on function
  public.assoc_of_certificate(uuid), public.assoc_of_halaqa(uuid), public.assoc_of_plan(uuid),
  public.assoc_of_post(uuid), public.assoc_of_profile(uuid), public.assoc_of_session(uuid),
  public.assoc_of_student(uuid), public.default_association()
  to public, anon;

commit;
