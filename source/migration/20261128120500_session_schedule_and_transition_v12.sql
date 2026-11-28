-- Proposed forward migration; do not apply without staging verification.
begin;
alter table public.halaqas add column session_weekdays smallint[];
alter table public.halaqas add column auto_session_enabled boolean;
alter table public.halaqas add constraint halaqas_session_weekdays_check check (
    session_weekdays is null or (cardinality(session_weekdays) <= 7 and array_position(session_weekdays,null) is null and session_weekdays <@ array[1,2,3,4,5,6,7]::smallint[])
);
comment on column public.halaqas.session_weekdays is 'ISO weekday numbers Mon=1..Sun=7; NULL uses legacy default. Independent of memorization plan schedules.';
comment on column public.halaqas.auto_session_enabled is 'NULL: legacy default, true for regular and false for programs. Manual sessions unaffected.';
create function public.rc_transition_session_v12(p_session_id uuid,p_expected_status text,p_status text)
returns jsonb language plpgsql security invoker set search_path=pg_catalog,public as $f$
declare s public.sessions%rowtype; h public.halaqas%rowtype;
begin
    if auth.uid() is null or not exists(
        select 1 from public.profiles p where p.id=auth.uid() and p.active and not coalesce(p.frozen,false)
        and p.approval_status='approved' and p.role::text in ('admin','teacher','supervisor')
    ) then raise exception 'لا تملك صلاحية إدارة الجلسة' using errcode='42501'; end if;
    select * into s from public.sessions where id=p_session_id for update;
    if not found then raise exception 'الجلسة غير متاحة' using errcode='42501'; end if;
    select * into h from public.halaqas where id=s.halaqa_id;
    if not found or h.association_id is null or not h.active or h.archived_at is not null
       or not public.can_manage_halaqa(h.id)
       or not public.assoc_writable(h.association_id)
       or not (public.is_platform_owner() or h.association_id=public.my_association()) then
        raise exception 'لا تملك صلاحية إدارة الجلسة' using errcode='42501';
    end if;
    if coalesce((s.activity_data->>'archived')::boolean,false) then
        raise exception 'استعد الجلسة المؤرشفة أولًا' using errcode='22023';
    end if;
    if not coalesce(((p_expected_status='open' and p_status='closed') or
       (p_expected_status in ('planned','closed') and p_status='open')),false) then
        raise exception 'انتقال حالة الجلسة غير صالح' using errcode='22023';
    end if;
    if s.status=p_status then return jsonb_build_object('ok',true,'session',to_jsonb(s)); end if;
    if s.status is distinct from p_expected_status then
        raise exception 'تغيرت حالة الجلسة؛ حدّث القائمة' using errcode='40001';
    end if;
    update public.sessions set status=p_status where id=s.id returning * into s;
    if not found then raise exception 'لم يؤكد الخادم تغيير الحالة' using errcode='42501'; end if;
    return jsonb_build_object('ok',true,'session',to_jsonb(s));
end $f$;
revoke all on function public.rc_transition_session_v12(uuid,text,text) from public,anon,authenticated;
grant execute on function public.rc_transition_session_v12(uuid,text,text) to authenticated;
commit;
