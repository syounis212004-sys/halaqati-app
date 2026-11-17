-- RC100 (2.91.0) security hardening.  NOT APPLIED AUTOMATICALLY - review, then run once in the Supabase SQL editor.
-- Rollback: 20261117_v2910_rc100_security_hardening_ROLLBACK.sql
-- Note: a manual SQL-editor change of these columns needs  set local halaqati.review_ok = '1';  first (same rule as before for role fields).
--
-- 1) profiles: platform-scope fields.
--    protect_profile_security_fields() guarded role/active/approval/frozen but not `is_platform_owner`
--    and `association_id`, and UPDATE is granted on every column.  is_platform_owner() is
--    "flag AND role = admin", so an association administrator could raise himself above every association.
--    From now on is_platform_owner changes only by the platform owner, and association_id only by the platform
--    owner or by an association administrator for OTHER people joining/leaving HIS OWN association.
--    Compatible with the clients: the only writers of profiles.association_id are the organisation screens
--    (Android OrgScreen, web rc85 portal), which do exactly that; no SQL function updates either column.
-- 2) anon: remove write privileges that no policy ever allowed (defence in depth; RLS already blocks them).
--    SELECT is left untouched so no client call changes behaviour.
-- 3) anon/PUBLIC: stop exposing the assoc_of_* lookup helpers as unauthenticated RPCs.
--    is_platform_owner(), my_association(), assoc_ok() and assoc_writable() stay executable because the
--    `centers_read` policy is evaluated for the anon role.

begin;

create or replace function public.protect_profile_security_fields()
 returns trigger
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
begin
  if coalesce(auth.role(),'') <> 'service_role' then
    -- is_platform_owner: platform owner only.
    if new.is_platform_owner is distinct from old.is_platform_owner
       and not public.is_platform_owner()
       and coalesce(current_setting('halaqati.review_ok', true), '') <> '1' then
      raise exception 'هذا الحقل لا يغيره إلا مالك المنصة' using errcode = '42501';
    end if;
    -- association_id: the platform owner may move anybody. An association administrator may only
    --   * attach ANOTHER person to his own association, or
    --   * detach ANOTHER person who is currently in his own association
    -- (that is what the organisation screens of the Android app and the web portal do).
    -- Nobody may change his own association, and ordinary users may not change it at all.
    if new.association_id is distinct from old.association_id
       and not public.is_platform_owner()
       and coalesce(current_setting('halaqati.review_ok', true), '') <> '1' then
      if not (
        public.is_admin()
        and old.id is distinct from auth.uid()
        and public.my_association() is not null
        and (
          new.association_id = public.my_association()
          or (new.association_id is null and old.association_id = public.my_association())
        )
      ) then
        raise exception 'تغيير الجمعية مسموح لمالك المنصة، أو لمسؤول الجمعية على أعضاء جمعيته فقط' using errcode = '42501';
      end if;
    end if;
    if not public.is_admin()
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
  end if;
  new.updated_at := now();
  return new;
end $function$;

revoke insert, update, delete, truncate, references, trigger
  on public.app_support_info, public.association_payments, public.associations, public.centers,
     public.exam_examiners, public.exam_settings, public.student_exams
  from anon;

revoke execute on function
  public.assoc_of_certificate(uuid), public.assoc_of_halaqa(uuid), public.assoc_of_plan(uuid),
  public.assoc_of_post(uuid), public.assoc_of_profile(uuid), public.assoc_of_session(uuid),
  public.assoc_of_student(uuid), public.default_association()
  from public, anon;
grant execute on function
  public.assoc_of_certificate(uuid), public.assoc_of_halaqa(uuid), public.assoc_of_plan(uuid),
  public.assoc_of_post(uuid), public.assoc_of_profile(uuid), public.assoc_of_session(uuid),
  public.assoc_of_student(uuid), public.default_association()
  to authenticated, service_role;

commit;

-- Verification (read-only), expected results in comments:
-- select has_table_privilege('anon','public.student_exams','insert');              -- false
-- select has_table_privilege('anon','public.centers','select');                     -- true (unchanged)
-- select has_function_privilege('anon','public.assoc_of_student(uuid)','execute');  -- false
-- select has_function_privilege('authenticated','public.assoc_of_student(uuid)','execute'); -- true
-- select prosrc like '%is_platform_owner is distinct%' and prosrc like '%my_association()%' from pg_proc where proname='protect_profile_security_fields'; -- true
