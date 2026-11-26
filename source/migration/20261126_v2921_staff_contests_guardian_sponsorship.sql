-- v2.92.1 — Staff contests + guardian sponsorship.
--  (1) Contests: teachers, supervisors and the admin create contests too (donor_contests.donor_id stays the creator id; creator_role says who).
--      The admin's own contests are approved at once; teachers' and supervisors' wait for the admin. A creator can only target a halaqa he works in.
--  (2) Sponsorship: guardians use the RC48/49 pledge stack for the halaqat of their own children (gate rc50_is_sponsor / rc50_guardian_halaqas).
-- Idempotent. Definer functions pin search_path = public, pg_temp. Decisions: docs/discovery/roles-contests-sponsorship-reminders/DECISIONS.md

-- 1) Contests ------------------------------------------------------------------------------------------------------------------
alter table public.donor_contests add column if not exists creator_role text not null default 'donor';
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'donor_contests_creator_role_ok') then
    alter table public.donor_contests add constraint donor_contests_creator_role_ok check (creator_role in ('donor','teacher','supervisor','admin'));
  end if;
end $$;

create or replace function public.contest_creator_role() returns text
language sql stable security definer set search_path = public, pg_temp as $$
  select case when public.is_admin() then 'admin' else (
    select case p.role::text when 'supervisor' then 'supervisor' when 'teacher' then 'teacher' when 'observer' then 'donor' else null end
    from public.profiles p where p.id = auth.uid() and p.active = true and p.approval_status = 'approved') end
$$;

-- The halaqat the caller may target with a contest.
create or replace function public.contest_creator_halaqas() returns setof uuid
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare r text := public.contest_creator_role();
begin
  if r = 'admin' then
    return query select h.id from public.halaqas h where h.active = true;
  elsif r = 'donor' then
    return query select d.halaqa_id from public.donor_halaqas d join public.halaqas h on h.id = d.halaqa_id
                 where d.donor_id = auth.uid() and d.active = true and h.active = true;
  elsif r = 'teacher' then
    return query select ht.halaqa_id from public.halaqa_teachers ht join public.halaqas h on h.id = ht.halaqa_id
                 where ht.teacher_id = auth.uid() and h.active = true;
  elsif r = 'supervisor' then
    return query select hs.halaqa_id from public.halaqa_supervisors hs join public.halaqas h on h.id = hs.halaqa_id
                 where hs.supervisor_id = auth.uid() and h.active = true
                 union select h.id from public.halaqas h where h.supervisor_id = auth.uid() and h.active = true;
  end if;
end $$;

create or replace function public.contest_donor_halaqas() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if public.contest_creator_role() is null then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('id', h.id, 'name', h.name) order by h.name)
                   from public.halaqas h where h.id in (select public.contest_creator_halaqas())), '[]'::jsonb);
end $$;

-- Tells everyone connected to the halaqa that a contest was approved (internal helper, not callable by clients).
create or replace function public.contest_notify_related(p_id uuid, p_target uuid, p_name text, p_hname text, p_skip uuid) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare r record;
begin
  for r in
    select distinct u.uid from (
      select ht.teacher_id as uid from public.halaqa_teachers ht where ht.halaqa_id = p_target
      union select hs.supervisor_id from public.halaqa_supervisors hs where hs.halaqa_id = p_target
      union select h.supervisor_id from public.halaqas h where h.id = p_target and h.supervisor_id is not null
      union select s.user_id from public.students s where s.halaqa_id = p_target and s.user_id is not null and s.status = 'active'
      union select sg.guardian_id from public.student_guardians sg join public.students s on s.id = sg.student_id
            where s.halaqa_id = p_target and s.status = 'active'
    ) u where u.uid is not null and u.uid <> coalesce(p_skip, '00000000-0000-0000-0000-000000000000'::uuid)
  loop
    insert into public.notifications (recipient_id, sender_id, halaqa_id, kind, title, body, source_key, deep_link, data)
    values (r.uid, auth.uid(), p_target, 'contest_approved', 'مسابقة جديدة في حلقتك',
            left(p_name, 120) || coalesce(' — ' || p_hname, ''),
            'contest:' || p_id::text || ':approved', 'contests', jsonb_build_object('contest_id', p_id))
    on conflict (recipient_id, source_key) do nothing;
  end loop;
end $$;

create or replace function public.contest_submit(
  p_name text, p_title text, p_conditions text, p_reward text,
  p_halaqa uuid default null, p_starts_on date default null, p_ends_on date default null
) returns uuid
language plpgsql security definer set search_path = public, pg_temp as $$
declare cid uuid; crole text := public.contest_creator_role(); dname text; hname text; admin_row record; has_halaqa boolean; label text;
begin
  if crole is null then raise exception 'not allowed' using errcode = '42501'; end if;
  select exists(select 1 from public.contest_creator_halaqas()) into has_halaqa;
  if p_halaqa is not null and not exists(select 1 from public.contest_creator_halaqas() as x(id) where x.id = p_halaqa) then
    raise exception 'halaqa not allowed for this account' using errcode = '42501';
  end if;
  -- a donor with no linked halaqa may submit without one (the admin assigns it); everybody else must name a halaqa
  if p_halaqa is null and (crole <> 'donor' or has_halaqa) then raise exception 'choose a halaqa' using errcode = '22023'; end if;
  insert into public.donor_contests (donor_id, halaqa_id, name, title, conditions, reward, starts_on, ends_on, creator_role, status, decided_by, decided_at)
  values (auth.uid(), p_halaqa, btrim(p_name), btrim(p_title), btrim(p_conditions), btrim(p_reward), p_starts_on, p_ends_on, crole,
          case when crole = 'admin' then 'approved' else 'pending' end,
          case when crole = 'admin' then auth.uid() end, case when crole = 'admin' then now() end)
  returning id into cid;
  select name into hname from public.halaqas where id = p_halaqa;
  if crole = 'admin' then
    perform public.contest_notify_related(cid, p_halaqa, btrim(p_name), hname, auth.uid());
  else
    select full_name into dname from public.profiles where id = auth.uid();
    label := case crole when 'teacher' then 'محفظ' when 'supervisor' then 'مشرف' else 'مانح' end;
    for admin_row in select id from public.profiles where role::text = 'admin' and active = true and approval_status = 'approved' loop
      insert into public.notifications (recipient_id, sender_id, halaqa_id, kind, title, body, source_key, deep_link, data)
      values (admin_row.id, auth.uid(), p_halaqa, 'contest_pending', 'مسابقة جديدة بانتظار قرارك',
              coalesce(dname, label) || ' (' || label || ') — ' || left(btrim(p_name), 120) || coalesce(' — ' || hname, ' — الحلقة غير محددة'),
              'contest:' || cid::text || ':pending', 'contests', jsonb_build_object('contest_id', cid))
      on conflict (recipient_id, source_key) do nothing;
    end loop;
  end if;
  return cid;
end $$;

create or replace function public.contest_my() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if public.contest_creator_role() is null then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', c.name, 'title', c.title, 'conditions', c.conditions, 'reward', c.reward, 'status', c.status,
      'reject_reason', c.reject_reason, 'halaqa_id', c.halaqa_id, 'halaqa_name', h.name, 'creator_role', c.creator_role,
      'starts_on', c.starts_on, 'ends_on', c.ends_on, 'created_at', c.created_at
    ) order by c.created_at desc)
    from public.donor_contests c left join public.halaqas h on h.id = c.halaqa_id
    where c.donor_id = auth.uid()), '[]'::jsonb);
end $$;

create or replace function public.contest_admin_list() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if not public.is_admin() then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', c.name, 'title', c.title, 'conditions', c.conditions, 'reward', c.reward, 'status', c.status,
      'reject_reason', c.reject_reason, 'halaqa_id', c.halaqa_id, 'halaqa_name', h.name, 'donor_id', c.donor_id,
      'donor_name', p.full_name, 'creator_role', c.creator_role, 'starts_on', c.starts_on, 'ends_on', c.ends_on, 'created_at', c.created_at
    ) order by (c.status = 'pending') desc, c.created_at desc)
    from public.donor_contests c
    left join public.halaqas h on h.id = c.halaqa_id
    left join public.profiles p on p.id = c.donor_id), '[]'::jsonb);
end $$;

create or replace function public.contest_visible() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if auth.uid() is null then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', c.name, 'title', c.title, 'conditions', c.conditions, 'reward', c.reward, 'status', c.status,
      'halaqa_id', c.halaqa_id, 'halaqa_name', h.name, 'donor_name', p.full_name, 'creator_role', c.creator_role,
      'starts_on', c.starts_on, 'ends_on', c.ends_on, 'created_at', c.created_at
    ) order by c.decided_at desc nulls last, c.created_at desc)
    from public.donor_contests c
    join public.halaqas h on h.id = c.halaqa_id
    left join public.profiles p on p.id = c.donor_id
    where c.status = 'approved' and c.halaqa_id in (select public.contest_user_halaqas())), '[]'::jsonb);
end $$;

create or replace function public.contest_decide(
  p_id uuid, p_status text, p_reason text default null, p_halaqa uuid default null, p_link_donor boolean default false
) returns boolean
language plpgsql security definer set search_path = public, pg_temp as $$
declare c public.donor_contests%rowtype; target uuid; hname text; reason text := nullif(btrim(coalesce(p_reason, '')), '');
begin
  if not public.is_admin() then raise exception 'not allowed' using errcode = '42501'; end if;
  if p_status not in ('approved', 'rejected') then raise exception 'bad status' using errcode = '22023'; end if;
  select * into c from public.donor_contests where id = p_id for update;
  if not found or c.status <> 'pending' then return false; end if;
  target := coalesce(p_halaqa, c.halaqa_id);
  if p_status = 'rejected' and (reason is null or char_length(reason) < 3) then raise exception 'reason required' using errcode = '22023'; end if;
  if p_status = 'approved' and target is null then raise exception 'halaqa required' using errcode = '22023'; end if;
  if target is not null and not exists(select 1 from public.halaqas h where h.id = target and h.active = true) then
    raise exception 'unknown halaqa' using errcode = '22023';
  end if;
  update public.donor_contests
     set status = p_status, halaqa_id = target, reject_reason = case when p_status = 'rejected' then reason else null end,
         decided_by = auth.uid(), decided_at = now(), updated_at = now()
   where id = p_id;
  -- only a donor is linked to the halaqa by an approval; a teacher or supervisor already works in his halaqa
  if p_link_donor and target is not null and c.creator_role = 'donor' then
    insert into public.donor_halaqas (donor_id, halaqa_id, active, updated_at) values (c.donor_id, target, true, now())
    on conflict (donor_id, halaqa_id) do update set active = true, updated_at = now();
  end if;
  select name into hname from public.halaqas where id = target;
  insert into public.notifications (recipient_id, sender_id, halaqa_id, kind, title, body, source_key, deep_link, data)
  values (c.donor_id, auth.uid(), target, 'contest_decision',
          case p_status when 'approved' then 'اعتُمدت مسابقتك' else 'لم تُعتمد مسابقتك' end,
          left(c.name, 120) || case p_status when 'approved' then coalesce(' — ' || hname, '') else coalesce(' — ' || reason, '') end,
          'contest:' || p_id::text || ':decision', 'contests', jsonb_build_object('contest_id', p_id, 'status', p_status))
  on conflict (recipient_id, source_key) do nothing;
  if p_status = 'approved' then perform public.contest_notify_related(p_id, target, c.name, hname, c.donor_id); end if;
  return true;
end $$;

-- 2) Guardian sponsorship ------------------------------------------------------------------------------------------------------
create or replace function public.rc50_is_guardian() returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists(select 1 from public.profiles p where p.id = auth.uid() and p.active = true and p.approval_status = 'approved' and p.role::text = 'guardian')
$$;

create or replace function public.rc50_is_sponsor() returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists(select 1 from public.profiles p where p.id = auth.uid() and p.active = true and p.approval_status = 'approved' and p.role::text in ('observer', 'guardian'))
$$;

-- Halaqat of the caller's own (active) children.
create or replace function public.rc50_guardian_halaqas() returns setof uuid
language sql stable security definer set search_path = public, pg_temp as $$
  select s.halaqa_id from public.student_guardians sg join public.students s on s.id = sg.student_id
   where sg.guardian_id = auth.uid() and s.status = 'active' and s.halaqa_id is not null
  union select m.halaqa_id from public.student_halaqa_memberships m
          join public.students s on s.id = m.student_id join public.student_guardians sg on sg.student_id = s.id
   where sg.guardian_id = auth.uid() and s.status = 'active' and m.status = 'active' and m.ended_at is null
$$;

create or replace function public.rc48_donor_scope_ok(p_halaqa uuid) returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select case
    when public.is_admin() then true
    when public.rc50_is_guardian() then p_halaqa in (select public.rc50_guardian_halaqas())
    when not public.rc32_is_observer() then false
    when not exists (select 1 from public.donor_halaqas d where d.donor_id = auth.uid() and d.active) then true
    else exists (select 1 from public.donor_halaqas d where d.donor_id = auth.uid() and d.active and d.halaqa_id = p_halaqa)
  end
$$;

create or replace function public.rc48_support_pledge(p_card uuid, p_periods text[], p_anonymous boolean default false) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare c record; per text; n int := 0; v_id uuid; adm record; who text;
begin
  if not public.rc50_is_sponsor() then raise exception 'not allowed' using errcode = '42501'; end if;
  select * into c from public.support_cards where id = p_card and status = 'open' for update;
  if not found then raise exception 'card closed' using errcode = 'P0001'; end if;
  if c.halaqa_id is not null and not public.rc48_donor_scope_ok(c.halaqa_id) then raise exception 'not allowed' using errcode = '42501'; end if;
  if c.halaqa_id is null and public.rc50_is_guardian() then raise exception 'not allowed' using errcode = '42501'; end if;
  who := case when public.rc50_is_guardian() then 'ولي أمر' else 'مانح' end;
  foreach per in array coalesce(p_periods, '{}'::text[]) loop
    if not (per = any(c.periods)) then continue; end if;
    if (select count(*) from public.support_pledges where card_id = c.id and period = per and status <> 'cancelled') >= c.slots then continue; end if;
    insert into public.support_pledges(card_id, donor_id, period, anonymous) values (c.id, auth.uid(), per, coalesce(p_anonymous,false))
      on conflict (card_id, donor_id, period) do update set status = 'pledged', anonymous = excluded.anonymous, created_at = now(),
        confirmed_at = null, delivered_at = null, proof_text = null, proof_image = null
      where public.support_pledges.status = 'cancelled'
    returning id into v_id;
    if v_id is not null then
      n := n + 1;
      for adm in select id from public.profiles where role::text = 'admin' and active = true loop
        insert into public.notifications(recipient_id, sender_id, kind, title, body, source_key, deep_link)
        values (adm.id, auth.uid(), 'support_pledge', 'كفالة جديدة: ' || c.title, 'تعهّد ' || who || ' بـ «' || per || '». تواصلوا معه لاستلام الدعم.', 'pledge:' || v_id::text, 'support')
        on conflict (recipient_id, source_key) do nothing;
      end loop;
      v_id := null;
    end if;
  end loop;
  return jsonb_build_object('created', n);
end $$;

create or replace function public.rc49_support_cards() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare v_admin boolean := public.is_admin(); v_guardian boolean := public.rc50_is_guardian(); d record;
begin
  if not (v_admin or public.rc50_is_sponsor()) then raise exception 'not allowed' using errcode = '42501'; end if;
  select * into d from public.support_defaults where id = 1;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id, 'kind', c.kind, 'title', c.title, 'description', c.description, 'amount_text', c.amount_text,
      'amount', c.amount, 'currency', c.currency,
      'recurrence', c.recurrence, 'slots', c.slots, 'beneficiaries', c.beneficiaries, 'status', c.status,
      'halaqa_id', c.halaqa_id, 'halaqa_name', h.name,
      'uses_default_payment', c.pay_account is null,
      'pay', jsonb_build_object(
          'method', case when c.pay_account is not null then c.pay_method else d.pay_method end,
          'provider', case when c.pay_account is not null then c.pay_provider else d.pay_provider end,
          'account', case when c.pay_account is not null then c.pay_account else d.pay_account end,
          'holder', case when c.pay_account is not null then c.pay_holder else d.pay_holder end,
          'note', case when c.pay_account is not null then c.pay_note else d.pay_note end),
      'periods', coalesce((select jsonb_agg(jsonb_build_object(
            'period', per,
            'taken', (select count(*) from public.support_pledges sp where sp.card_id = c.id and sp.period = per and sp.status <> 'cancelled'),
            'mine', (select sp.id from public.support_pledges sp where sp.card_id = c.id and sp.period = per and sp.donor_id = auth.uid() and sp.status <> 'cancelled' limit 1),
            'mine_status', (select sp.status from public.support_pledges sp where sp.card_id = c.id and sp.period = per and sp.donor_id = auth.uid() and sp.status <> 'cancelled' limit 1)
          ) order by ord) from unnest(c.periods) with ordinality as t(per, ord)), '[]'::jsonb),
      'pledges', case when v_admin then coalesce((select jsonb_agg(jsonb_build_object(
            'id', sp.id, 'donor_id', sp.donor_id, 'donor_name', pr.full_name, 'period', sp.period, 'anonymous', sp.anonymous,
            'status', sp.status, 'created_at', sp.created_at, 'proof_text', sp.proof_text, 'has_image', sp.proof_image is not null,
            'transfer_ref', sp.transfer_ref, 'has_transfer_image', sp.transfer_image is not null,
            'transfer_submitted_at', sp.transfer_submitted_at, 'thanks_text', sp.thanks_text
          ) order by sp.created_at) from public.support_pledges sp join public.profiles pr on pr.id = sp.donor_id
          where sp.card_id = c.id and sp.status <> 'cancelled'), '[]'::jsonb) end
    ) order by c.status, c.created_at desc)
    from public.support_cards c left join public.halaqas h on h.id = c.halaqa_id
    where v_admin or (c.status = 'open' and (
      (c.halaqa_id is null and not v_guardian) or (c.halaqa_id is not null and public.rc48_donor_scope_ok(c.halaqa_id))))
  ), '[]'::jsonb);
end $$;

create or replace function public.rc49_my_pledges() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare d record;
begin
  if not public.rc50_is_sponsor() then raise exception 'not allowed' using errcode = '42501'; end if;
  select * into d from public.support_defaults where id = 1;
  return coalesce((select jsonb_agg(jsonb_build_object(
      'id', sp.id, 'card_id', c.id, 'title', c.title, 'kind', c.kind, 'period', sp.period, 'status', sp.status,
      'anonymous', sp.anonymous, 'beneficiaries', c.beneficiaries, 'created_at', sp.created_at, 'confirmed_at', sp.confirmed_at,
      'delivered_at', sp.delivered_at, 'proof_text', sp.proof_text, 'has_image', sp.proof_image is not null,
      'amount', c.amount, 'currency', c.currency,
      'transfer_ref', sp.transfer_ref, 'has_transfer_image', sp.transfer_image is not null,
      'transfer_submitted_at', sp.transfer_submitted_at, 'thanks_text', sp.thanks_text,
      'pay', jsonb_build_object(
          'method', case when c.pay_account is not null then c.pay_method else d.pay_method end,
          'provider', case when c.pay_account is not null then c.pay_provider else d.pay_provider end,
          'account', case when c.pay_account is not null then c.pay_account else d.pay_account end,
          'holder', case when c.pay_account is not null then c.pay_holder else d.pay_holder end,
          'note', case when c.pay_account is not null then c.pay_note else d.pay_note end)
    ) order by sp.created_at desc)
    from public.support_pledges sp join public.support_cards c on c.id = sp.card_id
    where sp.donor_id = auth.uid() and sp.status <> 'cancelled'), '[]'::jsonb);
end $$;

create or replace function public.rc49_support_submit_transfer(p_pledge uuid, p_ref text, p_image text default null) returns boolean
language plpgsql security definer set search_path = public, pg_temp as $$
declare sp record; ttl text; adm record;
begin
  if not public.rc50_is_sponsor() then raise exception 'not allowed' using errcode = '42501'; end if;
  if char_length(btrim(coalesce(p_ref,''))) < 2 and p_image is null then raise exception 'proof required' using errcode = 'P0001'; end if;
  if p_image is not null and char_length(p_image) > 1500000 then raise exception 'image too large' using errcode = 'P0001'; end if;
  update public.support_pledges set transfer_ref = nullif(btrim(coalesce(p_ref,'')),''), transfer_image = p_image, transfer_submitted_at = now()
   where id = p_pledge and donor_id = auth.uid() and status in ('pledged','received') returning * into sp;
  if not found then return false; end if;
  select title into ttl from public.support_cards where id = sp.card_id;
  for adm in select id from public.profiles where role::text = 'admin' and active = true loop
    insert into public.notifications(recipient_id, sender_id, kind, title, body, source_key, deep_link)
    values (adm.id, auth.uid(), 'support_transfer', 'إثبات تحويل جديد: ' || coalesce(ttl,''), 'رُفع إثبات تحويل لـ «' || sp.period || '». راجعه وأكّد الاستلام.', 'transfer:' || sp.id::text || ':' || extract(epoch from now())::bigint::text, 'support')
    on conflict (recipient_id, source_key) do nothing;
  end loop;
  return true;
end $$;

-- 3) Grants --------------------------------------------------------------------------------------------------------------------
revoke all on function public.contest_creator_role() from public, anon;
revoke all on function public.contest_creator_halaqas() from public, anon;
revoke all on function public.contest_notify_related(uuid, uuid, text, text, uuid) from public, anon, authenticated;
revoke all on function public.rc50_is_guardian() from public, anon;
revoke all on function public.rc50_is_sponsor() from public, anon;
revoke all on function public.rc50_guardian_halaqas() from public, anon;
grant execute on function public.contest_creator_role() to authenticated;
grant execute on function public.contest_creator_halaqas() to authenticated;
grant execute on function public.rc50_is_guardian() to authenticated;
grant execute on function public.rc50_is_sponsor() to authenticated;
grant execute on function public.rc50_guardian_halaqas() to authenticated;
-- redefined functions keep their existing grants (create or replace); re-assert the intended ones
grant execute on function public.contest_donor_halaqas() to authenticated;
grant execute on function public.contest_submit(text, text, text, text, uuid, date, date) to authenticated;
grant execute on function public.contest_my() to authenticated;
grant execute on function public.contest_admin_list() to authenticated;
grant execute on function public.contest_visible() to authenticated;
grant execute on function public.contest_decide(uuid, text, text, uuid, boolean) to authenticated;

-- 4) Review fixes (applied as a second step) -------------------------------------------------------------------------------------
-- 4a) The direct chat with the administrator (rc54_thread_open -> rc52_thread_owner) was donor-only; guardians who sponsor need it too.
create or replace function public.rc52_thread_owner(p_kind text, p_ref uuid) returns uuid
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare d uuid;
begin
  if p_kind = 'reward' then select donor_id into d from public.reward_requests where id = p_ref;
  elsif p_kind = 'pledge' then select donor_id into d from public.support_pledges where id = p_ref;
  elsif p_kind = 'direct' then select id into d from public.profiles where id = p_ref and role::text in ('observer', 'guardian') and active = true;
  end if;
  return d;
end $$;

-- 4b) creator_role is shown on cards and drives the approval logic: a donor writing straight through the table must not claim another role.
drop policy if exists donor_contests_donor_insert on public.donor_contests;
create policy donor_contests_donor_insert on public.donor_contests for insert to authenticated
  with check (
    donor_id = (select auth.uid()) and public.contest_is_donor() and creator_role = 'donor'
    and status = 'pending' and decided_by is null and decided_at is null and reject_reason is null
    and (halaqa_id is null or public.contest_donor_linked(halaqa_id))
  );
drop policy if exists donor_contests_donor_update on public.donor_contests;
create policy donor_contests_donor_update on public.donor_contests for update to authenticated
  using (donor_id = (select auth.uid()) and status = 'pending')
  with check (
    donor_id = (select auth.uid()) and creator_role = 'donor' and status = 'pending' and decided_by is null and decided_at is null and reject_reason is null
    and (halaqa_id is null or public.contest_donor_linked(halaqa_id))
  );

-- 4c) Notify the people who reach the halaqa through a membership too (the same people who can see the contest).
create or replace function public.contest_notify_related(p_id uuid, p_target uuid, p_name text, p_hname text, p_skip uuid) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare r record;
begin
  for r in
    select distinct u.uid from (
      select ht.teacher_id as uid from public.halaqa_teachers ht where ht.halaqa_id = p_target
      union select hs.supervisor_id from public.halaqa_supervisors hs where hs.halaqa_id = p_target
      union select h.supervisor_id from public.halaqas h where h.id = p_target and h.supervisor_id is not null
      union select s.user_id from public.students s where s.halaqa_id = p_target and s.user_id is not null and s.status = 'active'
      union select sg.guardian_id from public.student_guardians sg join public.students s on s.id = sg.student_id
            where s.halaqa_id = p_target and s.status = 'active'
      union select s.user_id from public.student_halaqa_memberships m join public.students s on s.id = m.student_id
            where m.halaqa_id = p_target and m.status = 'active' and m.ended_at is null and s.user_id is not null
      union select sg.guardian_id from public.student_halaqa_memberships m join public.students s on s.id = m.student_id
            join public.student_guardians sg on sg.student_id = s.id
            where m.halaqa_id = p_target and m.status = 'active' and m.ended_at is null
    ) u where u.uid is not null and u.uid <> coalesce(p_skip, '00000000-0000-0000-0000-000000000000'::uuid)
  loop
    insert into public.notifications (recipient_id, sender_id, halaqa_id, kind, title, body, source_key, deep_link, data)
    values (r.uid, auth.uid(), p_target, 'contest_approved', 'مسابقة جديدة في حلقتك',
            left(p_name, 120) || coalesce(' — ' || p_hname, ''),
            'contest:' || p_id::text || ':approved', 'contests', jsonb_build_object('contest_id', p_id))
    on conflict (recipient_id, source_key) do nothing;
  end loop;
end $$;
revoke all on function public.contest_notify_related(uuid, uuid, text, text, uuid) from public, anon, authenticated;
