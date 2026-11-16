-- RC27.49 — مركز الإشعارات: إحصائيات الإرسال + من قرأ ومن لم يقرأ
-- آمن للتشغيل أكثر من مرة. لا يغيّر أي بيانات موجودة.

-- فهارس جزئية صغيرة: تخص إشعارات المركز فقط (source_key يبدأ بـ bc:)
create index if not exists notifications_bc_created_idx
  on public.notifications (created_at desc) where source_key like 'bc:%';
create index if not exists notifications_bc_key_idx
  on public.notifications (source_key) where source_key like 'bc:%';

-- 1) قائمة الإرسالات (مجمّعة على السيرفر: صفوف قليلة وصغيرة)
create or replace function public.rc_broadcast_stats(p_limit int default 30)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid  uuid := auth.uid();
  v_role text;
begin
  select role::text into v_role from public.profiles
   where id = v_uid and active = true and approval_status = 'approved';
  if v_role is null or v_role not in ('admin','supervisor') then
    return '[]'::jsonb;
  end if;

  return coalesce((
    select jsonb_agg(to_jsonb(t) order by t.created_at desc)
    from (
      select n.source_key,
             max(left(n.title, 120))            as title,
             max(left(n.body, 200))             as body,
             min(n.created_at)                  as created_at,
             count(*)::int                      as total,
             count(n.read_at)::int              as read,
             (count(*) filter (where n.push_status = 'sent'))::int as push_sent,
             max(n.data ->> 'label')            as label,
             max(n.data ->> 'audience')         as audience
        from public.notifications n
       where n.source_key like 'bc:%'
         and n.created_at > now() - interval '120 days'
         and (v_role = 'admin' or n.sender_id = v_uid)
       group by n.source_key
       order by min(n.created_at) desc
       limit least(greatest(coalesce(p_limit, 30), 1), 50)
    ) t
  ), '[]'::jsonb);
end;
$$;

-- 2) تفاصيل إرسال واحد (تُطلب فقط عند فتحه): من قرأ ومن لم يقرأ
create or replace function public.rc_broadcast_detail(p_source_key text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid  uuid := auth.uid();
  v_role text;
  v_ok   boolean;
begin
  select role::text into v_role from public.profiles
   where id = v_uid and active = true and approval_status = 'approved';
  if v_role is null or v_role not in ('admin','supervisor') or p_source_key is null or p_source_key not like 'bc:%' then
    return jsonb_build_object('ok', false, 'error', 'غير مسموح');
  end if;

  select (v_role = 'admin') or exists (
           select 1 from public.notifications
            where source_key = p_source_key and sender_id = v_uid)
    into v_ok;
  if not coalesce(v_ok, false) then
    return jsonb_build_object('ok', false, 'error', 'غير مسموح');
  end if;

  return jsonb_build_object(
    'ok', true,
    'recipients', coalesce((
      select jsonb_agg(r order by r.read_at is not null, r.name)
      from (
        select p.full_name as name,
               p.role::text as role,
               n.read_at,
               n.push_status,
               (select string_agg(s.full_name, '، ')
                  from (select st.full_name
                          from public.student_guardians sg
                          join public.students st on st.id = sg.student_id
                         where sg.guardian_id = n.recipient_id
                         limit 3) s) as children
          from public.notifications n
          join public.profiles p on p.id = n.recipient_id
         where n.source_key = p_source_key
         limit 1500
      ) r
    ), '[]'::jsonb)
  );
end;
$$;

revoke all on function public.rc_broadcast_stats(int) from public, anon;
revoke all on function public.rc_broadcast_detail(text) from public, anon;
grant execute on function public.rc_broadcast_stats(int) to authenticated;
grant execute on function public.rc_broadcast_detail(text) to authenticated;

-- تحقق سريع (يجب أن يرجع true):
select (select count(*) from pg_proc where proname in ('rc_broadcast_stats','rc_broadcast_detail')) = 2 as ready;
