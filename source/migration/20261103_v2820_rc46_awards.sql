-- RC46 (2.82.0) — الجزء الثاني: دوال اعتماد التكريم وصفحة المانح. شغّله مرة واحدة في SQL Editor.
-- الجزء الأول (الجداول ودالة الحساب rc46_compute_awards) مطبَّق مسبقًا.

create or replace function public.rc46_awards_payload(p_month date, p_scope text, p_private boolean)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare m date := date_trunc('month', p_month)::date; sc text := case when p_scope = 'category' then 'category' else 'halaqa' end; result jsonb;
begin
  with base as (
    select a.*, s.full_name, s.guardian_phone, s.donor_contact_allowed, h.name as halaqa_name, c.name as category_name,
           case when sc = 'halaqa' then a.halaqa_id end as gh
      from public.monthly_awards a
      join public.students s on s.id = a.student_id
      left join public.halaqas h on h.id = a.halaqa_id
      left join public.student_categories c on c.id = a.category_id
     where a.month = m and a.scope = sc and a.track = 'general_hifz'
  ), groups as (
    select distinct gh, category_id from base
  )
  select jsonb_build_object(
    'month', m, 'scope', sc,
    'status', (select status from public.monthly_award_months where month = m),
    'groups', coalesce((
      select jsonb_agg(jsonb_build_object(
        'halaqa_id', g.gh,
        'halaqa_name', (select max(b.halaqa_name) from base b where b.gh is not distinct from g.gh and b.category_id is not distinct from g.category_id and sc = 'halaqa'),
        'category_id', g.category_id,
        'category_name', coalesce((select max(b.category_name) from base b where b.category_id is not distinct from g.category_id), 'غير مصنف'),
        'students', (select count(*) from base b where b.gh is not distinct from g.gh and b.category_id is not distinct from g.category_id),
        'winners', coalesce((select jsonb_agg(jsonb_build_object(
              'award_id', w.id, 'student_id', w.student_id, 'name', w.full_name, 'rank', w.rank, 'score', w.total_score,
              'hifz_pct', w.hifz_pct, 'sard_pct', w.sard_pct, 'attendance_pct', w.attendance_pct, 'behavior', w.behavior_score,
              'quality', w.quality_score, 'pages', w.pages, 'halaqa_name', w.halaqa_name,
              'contact_allowed', w.donor_contact_allowed,
              'phone', case when p_private or w.donor_contact_allowed then w.guardian_phone end) order by w.rank)
            from base w where w.gh is not distinct from g.gh and w.category_id is not distinct from g.category_id and w.rank is not null), '[]'::jsonb),
        'best_miss', case when p_private then (select jsonb_build_object('name', x.full_name, 'score', x.total_score, 'reason', x.reason)
                from base x where x.gh is not distinct from g.gh and x.category_id is not distinct from g.category_id and x.rank is null
                order by x.total_score desc nulls last limit 1) end
      ) order by g.gh, g.category_id) from groups g), '[]'::jsonb),
    'tathbit', coalesce((
      select jsonb_agg(jsonb_build_object('student_id', a.student_id, 'name', s.full_name, 'halaqa_name', h.name, 'category_name', coalesce(c.name, 'غير مصنف'),
             'pct', a.tathbit_pct, 'pages', a.pages, 'contact_allowed', s.donor_contact_allowed,
             'phone', case when p_private or s.donor_contact_allowed then s.guardian_phone end) order by a.tathbit_pct desc)
        from public.monthly_awards a join public.students s on s.id = a.student_id
        left join public.halaqas h on h.id = a.halaqa_id left join public.student_categories c on c.id = a.category_id
       where a.month = m and a.scope = 'halaqa' and a.track = 'tathbit' and coalesce(a.tathbit_pct, 0) >= 90), '[]'::jsonb)
  ) into result;
  return result;
end $$;
revoke all on function public.rc46_awards_payload(date, text, boolean) from public, anon, authenticated;

create or replace function public.rc46_admin_awards(p_month date, p_scope text default 'halaqa')
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not public.is_admin() then raise exception 'not allowed' using errcode = '42501'; end if;
  return public.rc46_awards_payload(p_month, p_scope, true);
end $$;

create or replace function public.rc46_donor_awards(p_month date, p_scope text default 'halaqa')
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not (public.rc32_is_observer() or public.is_admin()) then raise exception 'not allowed' using errcode = '42501'; end if;
  if not public.is_admin() and not exists (select 1 from public.monthly_award_months where month = date_trunc('month', p_month)::date and status = 'approved') then
    raise exception 'month not approved' using errcode = 'P0001';
  end if;
  return public.rc46_awards_payload(p_month, p_scope, false);
end $$;

create or replace function public.rc46_award_months()
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not (public.rc32_is_observer() or public.is_admin()) then raise exception 'not allowed' using errcode = '42501'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('month', am.month, 'status', am.status, 'approved_at', am.approved_at, 'computed_at', am.computed_at) order by am.month desc)
    from public.monthly_award_months am where public.is_admin() or am.status = 'approved'), '[]'::jsonb);
end $$;

create or replace function public.rc46_approve_awards(p_month date)
returns boolean language plpgsql security definer set search_path to 'public' as $$
declare m date := date_trunc('month', p_month)::date; o record;
begin
  if not public.is_admin() then raise exception 'not allowed' using errcode = '42501'; end if;
  update public.monthly_award_months set status = 'approved', approved_by = auth.uid(), approved_at = now() where month = m and status = 'draft';
  if not found then return false; end if;
  for o in select id from public.profiles where role::text = 'observer' and active = true and approval_status = 'approved' loop
    insert into public.notifications (recipient_id, sender_id, kind, title, body, source_key, deep_link)
    values (o.id, auth.uid(), 'awards_approved', 'اعتُمد تكريم شهر ' || to_char(m, 'YYYY-MM'),
            'اطّلع على أفضل الطلاب في كل فئة لهذا الشهر.', 'awards:' || m::text, 'home')
    on conflict (recipient_id, source_key) do nothing;
  end loop;
  return true;
end $$;

create or replace function public.rc46_exclude_award(p_month date, p_student uuid, p_reason text)
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare m date := date_trunc('month', p_month)::date;
begin
  if not public.is_admin() then raise exception 'not allowed' using errcode = '42501'; end if;
  if char_length(btrim(coalesce(p_reason, ''))) < 3 then raise exception 'reason required' using errcode = 'P0001'; end if;
  insert into public.monthly_award_exclusions(month, student_id, reason, excluded_by) values (m, p_student, btrim(p_reason), auth.uid())
    on conflict (month, student_id) do update set reason = excluded.reason, excluded_by = excluded.excluded_by, created_at = now();
  return public.rc46_compute_awards(m);
end $$;

create or replace function public.rc46_set_donor_contact(p_student uuid, p_allowed boolean)
returns boolean language plpgsql security definer set search_path to 'public' as $$
begin
  if not public.is_admin() then raise exception 'not allowed' using errcode = '42501'; end if;
  update public.students set donor_contact_allowed = coalesce(p_allowed, false) where id = p_student;
  return found;
end $$;

create or replace function public.rc46_cron_compute()
returns jsonb language sql security definer set search_path to 'public' as $$
  select public.rc46_compute_awards((date_trunc('month', current_date - interval '1 day'))::date)
$$;

revoke all on function public.rc46_admin_awards(date, text), public.rc46_donor_awards(date, text), public.rc46_award_months(),
  public.rc46_approve_awards(date), public.rc46_exclude_award(date, uuid, text), public.rc46_set_donor_contact(uuid, boolean),
  public.rc46_cron_compute() from public, anon;
grant execute on function public.rc46_admin_awards(date, text), public.rc46_donor_awards(date, text), public.rc46_award_months(),
  public.rc46_approve_awards(date), public.rc46_exclude_award(date, uuid, text), public.rc46_set_donor_contact(uuid, boolean) to authenticated;

-- الحساب الآلي: الساعة 01:15 بتوقيت UTC من اليوم الأول لكل شهر (فجرًا بتوقيت غزة) للشهر المنتهي.
select cron.schedule('rc46-monthly-awards', '15 1 1 * *', $$select public.rc46_cron_compute()$$);

-- اختياري: حذف دالة الاختبار التي أنشأتُها
-- drop function if exists public.rc46_ping();
