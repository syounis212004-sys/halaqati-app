-- Halaqati v2.47.1-RC12.1
-- RC11 stability consolidation + server-backed Certificates Core.
-- Requires RC11.3 migration chain first.

begin;

-- ---------------------------------------------------------------------------
-- 1) Consolidate the RC11.3 bulk-cycle hotfix that was applied live.
-- Qualify smart_plan_phases columns so PL/pgSQL variable plan_id cannot shadow them.
-- ---------------------------------------------------------------------------
do $$
declare
  ddl text;
begin
  select pg_get_functiondef(p.oid)
    into ddl
  from pg_proc p
  join pg_namespace n on n.oid=p.pronamespace
  where n.nspname='public'
    and p.proname='rc113_bulk_configure_plan_cycles'
    and pg_get_function_identity_arguments(p.oid)='p_rows jsonb'
  limit 1;

  if ddl is not null then
    ddl := regexp_replace(
      ddl,
      'select id[[:space:]]+into hifz_id[[:space:]]+from public\\.smart_plan_phases[[:space:]]+where plan_id = sp\\.id[[:space:]]+and phase_type = ''hifz''[[:space:]]+and status <> ''archived''[[:space:]]+order by phase_order',
      'select spp.id
       into hifz_id
       from public.smart_plan_phases spp
       where spp.plan_id = sp.id
         and spp.phase_type = ''hifz''
         and spp.status <> ''archived''
       order by spp.phase_order',
      'g'
    );
    ddl := regexp_replace(
      ddl,
      'select id[[:space:]]+into sard_id[[:space:]]+from public\\.smart_plan_phases[[:space:]]+where plan_id = sp\\.id[[:space:]]+and phase_type = ''sard''[[:space:]]+and status <> ''archived''[[:space:]]+order by phase_order',
      'select spp.id
       into sard_id
       from public.smart_plan_phases spp
       where spp.plan_id = sp.id
         and spp.phase_type = ''sard''
         and spp.status <> ''archived''
       order by spp.phase_order',
      'g'
    );
    execute ddl;
  end if;
end
$$;

-- ---------------------------------------------------------------------------
-- 2) Repair legacy/imported Smart Plan tags.
-- phase_id is the authoritative FK. Mirror its parent plan into selection_data
-- so older Android builds and exports no longer see JSON null / "null" as an ID.
-- ---------------------------------------------------------------------------
update public.recitation_entries re
set selection_data =
  jsonb_set(
    jsonb_set(
      coalesce(re.selection_data, '{}'::jsonb),
      '{smart_plan_id}',
      to_jsonb(p.plan_id::text),
      true
    ),
    '{smart_plan_phase_id}',
    to_jsonb(p.id::text),
    true
  )
from public.smart_plan_phases p
where re.phase_id = p.id
  and (
    re.selection_data is null
    or coalesce(re.selection_data->>'smart_plan_id','') in ('', 'null', 'undefined')
    or coalesce(re.selection_data->>'smart_plan_phase_id','') in ('', 'null', 'undefined')
    or re.selection_data->>'smart_plan_id' is distinct from p.plan_id::text
    or re.selection_data->>'smart_plan_phase_id' is distinct from p.id::text
  );

-- ---------------------------------------------------------------------------
-- 3) Certificates Core schema.
-- ---------------------------------------------------------------------------
create table if not exists public.certificate_templates (
  id uuid primary key default gen_random_uuid(),
  halaqa_id uuid references public.halaqas(id) on delete cascade,
  name text not null,
  certificate_type text check (certificate_type is null or certificate_type in ('hifz','sard','achievement')),
  config jsonb not null default '{}'::jsonb,
  active boolean not null default true,
  created_by uuid references public.profiles(id) on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.certificates (
  id uuid primary key default gen_random_uuid(),
  certificate_number text unique,
  student_id uuid not null references public.students(id) on delete restrict,
  halaqa_id uuid references public.halaqas(id) on delete set null,
  certificate_type text not null check (certificate_type in ('hifz','sard','achievement')),
  title text not null default '',
  issue_date date,
  status text not null default 'draft' check (status in ('draft','issued','voided')),
  source_plan_id uuid references public.smart_plans(id) on delete set null,
  source_phase_id uuid references public.smart_plan_phases(id) on delete set null,
  template_id uuid references public.certificate_templates(id) on delete set null,
  parent_certificate_id uuid references public.certificates(id) on delete set null,
  start_surah smallint check (start_surah is null or start_surah between 1 and 114),
  start_ayah integer check (start_ayah is null or start_ayah > 0),
  end_surah smallint check (end_surah is null or end_surah between 1 and 114),
  end_ayah integer check (end_ayah is null or end_ayah > 0),
  start_page numeric(8,2) check (start_page is null or start_page >= 0),
  end_page numeric(8,2) check (end_page is null or end_page >= 0),
  pages_count numeric(10,2) check (pages_count is null or pages_count >= 0),
  juz_count numeric(8,2) check (juz_count is null or juz_count >= 0),
  snapshot jsonb not null default '{}'::jsonb,
  created_by uuid references public.profiles(id) on delete set null default auth.uid(),
  issued_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  issued_at timestamptz,
  updated_at timestamptz not null default now()
);

create table if not exists public.certificate_number_counters (
  issue_year integer primary key check (issue_year between 2000 and 2200),
  last_value bigint not null default 0 check (last_value >= 0),
  updated_at timestamptz not null default now()
);

create index if not exists certificates_student_date_idx on public.certificates(student_id, issue_date desc);
create index if not exists certificates_halaqa_date_idx on public.certificates(halaqa_id, issue_date desc);
create index if not exists certificates_status_idx on public.certificates(status, created_at desc);
create index if not exists certificate_templates_halaqa_active_idx on public.certificate_templates(halaqa_id, active);

-- Keep updated_at deterministic.
create or replace function public.rc121_touch_updated_at()
returns trigger
language plpgsql
set search_path=public
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists trg_certificate_templates_touch on public.certificate_templates;
create trigger trg_certificate_templates_touch
before update on public.certificate_templates
for each row execute function public.rc121_touch_updated_at();

drop trigger if exists trg_certificates_touch on public.certificates;
create trigger trg_certificates_touch
before update on public.certificates
for each row execute function public.rc121_touch_updated_at();

-- Keep certificate scope tied to the real student/halaqa and source phase.
create or replace function public.rc121_certificate_scope_guard()
returns trigger
language plpgsql
set search_path=public
as $$
declare
  student_halaqa uuid;
  source_plan uuid;
  source_type text;
begin
  select s.halaqa_id into student_halaqa from public.students s where s.id=new.student_id;
  if not found then raise exception 'RC12.1: certificate student not found'; end if;
  if new.halaqa_id is null then
    new.halaqa_id := student_halaqa;
  elsif student_halaqa is distinct from new.halaqa_id then
    raise exception 'RC12.1: certificate halaqa does not match student halaqa';
  end if;

  if new.source_phase_id is not null then
    select p.plan_id,p.phase_type into source_plan,source_type
    from public.smart_plan_phases p where p.id=new.source_phase_id;
    if not found then raise exception 'RC12.1: source phase not found'; end if;
    if new.source_plan_id is null then new.source_plan_id := source_plan;
    elsif new.source_plan_id is distinct from source_plan then
      raise exception 'RC12.1: source phase does not belong to source plan';
    end if;
    if new.certificate_type in ('hifz','sard') and source_type is distinct from new.certificate_type then
      raise exception 'RC12.1: certificate type does not match source phase';
    end if;
  end if;

  if new.source_plan_id is not null and not exists (
    select 1 from public.smart_plans sp
    where sp.id=new.source_plan_id
      and sp.halaqa_id=student_halaqa
      and (sp.student_id is null or sp.student_id=new.student_id)
  ) then
    raise exception 'RC12.1: source plan does not belong to certificate student/halaqa';
  end if;

  if tg_op='INSERT' and new.status='draft' and new.certificate_number is not null then
    raise exception 'RC12.1: draft certificate numbers are allocated only at issue time';
  end if;
  if tg_op='UPDATE' and old.status='draft' and new.status='draft'
     and new.certificate_number is distinct from old.certificate_number then
    raise exception 'RC12.1: draft certificate number cannot be edited directly';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_certificates_scope_guard on public.certificates;
create trigger trg_certificates_scope_guard
before insert or update on public.certificates
for each row execute function public.rc121_certificate_scope_guard();

-- Once issued, the historical snapshot/identity is immutable. Voiding is allowed.
create or replace function public.rc121_protect_issued_certificate()
returns trigger
language plpgsql
set search_path=public
as $$
begin
  if old.status='issued' then
    if new.certificate_number is distinct from old.certificate_number
       or new.student_id is distinct from old.student_id
       or new.halaqa_id is distinct from old.halaqa_id
       or new.certificate_type is distinct from old.certificate_type
       or new.title is distinct from old.title
       or new.issue_date is distinct from old.issue_date
       or new.source_plan_id is distinct from old.source_plan_id
       or new.source_phase_id is distinct from old.source_phase_id
       or new.template_id is distinct from old.template_id
       or new.start_surah is distinct from old.start_surah
       or new.start_ayah is distinct from old.start_ayah
       or new.end_surah is distinct from old.end_surah
       or new.end_ayah is distinct from old.end_ayah
       or new.start_page is distinct from old.start_page
       or new.end_page is distinct from old.end_page
       or new.pages_count is distinct from old.pages_count
       or new.juz_count is distinct from old.juz_count
       or new.snapshot is distinct from old.snapshot
    then
      raise exception 'RC12.1: issued certificate content is immutable; create a replacement certificate instead';
    end if;
    if new.status not in ('issued','voided') then
      raise exception 'RC12.1: issued certificate can only remain issued or be voided';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_certificates_protect_issued on public.certificates;
create trigger trg_certificates_protect_issued
before update on public.certificates
for each row execute function public.rc121_protect_issued_certificate();

-- ---------------------------------------------------------------------------
-- 4) RLS: staff manage their halaqas; guardians can read issued certificates
-- for linked students only. Drafts are staff-only.
-- ---------------------------------------------------------------------------
alter table public.certificate_templates enable row level security;
alter table public.certificates enable row level security;
alter table public.certificate_number_counters enable row level security;

revoke all on public.certificate_templates from anon;
revoke all on public.certificates from anon;
revoke all on public.certificate_number_counters from anon, authenticated;
grant select, insert, update, delete on public.certificate_templates to authenticated;
grant select, insert, update, delete on public.certificates to authenticated;

drop policy if exists certificate_templates_select_access on public.certificate_templates;
create policy certificate_templates_select_access on public.certificate_templates
for select to authenticated using (
  public.is_admin()
  or created_by=(select auth.uid())
  or halaqa_id is null
  or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=certificate_templates.halaqa_id and ht.teacher_id=(select auth.uid()))
  or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=certificate_templates.halaqa_id and hs.supervisor_id=(select auth.uid()))
);

drop policy if exists certificate_templates_manage_access on public.certificate_templates;
create policy certificate_templates_manage_access on public.certificate_templates
for all to authenticated
using (
  public.is_admin()
  or created_by=(select auth.uid())
  or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=certificate_templates.halaqa_id and ht.teacher_id=(select auth.uid()))
  or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=certificate_templates.halaqa_id and hs.supervisor_id=(select auth.uid()))
)
with check (
  public.is_admin()
  or created_by=(select auth.uid())
  or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=certificate_templates.halaqa_id and ht.teacher_id=(select auth.uid()))
  or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=certificate_templates.halaqa_id and hs.supervisor_id=(select auth.uid()))
);

drop policy if exists certificates_select_access on public.certificates;
create policy certificates_select_access on public.certificates
for select to authenticated using (
  public.is_admin()
  or created_by=(select auth.uid())
  or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=certificates.halaqa_id and ht.teacher_id=(select auth.uid()))
  or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=certificates.halaqa_id and hs.supervisor_id=(select auth.uid()))
  or (
    status='issued'
    and exists (
      select 1 from public.student_guardians sg
      where sg.student_id=certificates.student_id and sg.guardian_id=(select auth.uid())
    )
  )
);

drop policy if exists certificates_manage_access on public.certificates;
create policy certificates_manage_access on public.certificates
for all to authenticated
using (
  public.is_admin()
  or created_by=(select auth.uid())
  or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=certificates.halaqa_id and ht.teacher_id=(select auth.uid()))
  or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=certificates.halaqa_id and hs.supervisor_id=(select auth.uid()))
)
with check (
  public.is_admin()
  or created_by=(select auth.uid())
  or exists (select 1 from public.halaqa_teachers ht where ht.halaqa_id=certificates.halaqa_id and ht.teacher_id=(select auth.uid()))
  or exists (select 1 from public.halaqa_supervisors hs where hs.halaqa_id=certificates.halaqa_id and hs.supervisor_id=(select auth.uid()))
);

-- ---------------------------------------------------------------------------
-- 5) Atomic server numbering and issuance.
-- ---------------------------------------------------------------------------
create or replace function public.rc121_next_certificate_number(p_issue_date date default current_date)
returns text
language plpgsql
security definer
set search_path=public
as $$
declare
  y integer := extract(year from coalesce(p_issue_date,current_date))::integer;
  seq_value bigint;
begin
  if not (
    public.is_admin()
    or public.current_role()::text in ('teacher','supervisor')
  ) then
    raise exception 'RC12.1: certificate numbering is restricted to staff';
  end if;

  insert into public.certificate_number_counters(issue_year,last_value,updated_at)
  values(y,1,now())
  on conflict(issue_year) do update set
    last_value=public.certificate_number_counters.last_value+1,
    updated_at=now()
  returning last_value into seq_value;

  return 'HAL-CERT-'||y::text||'-'||lpad(seq_value::text,5,'0');
end;
$$;

create or replace function public.rc121_issue_certificate(
  p_certificate_id uuid,
  p_issue_date date default current_date
)
returns jsonb
language plpgsql
security definer
set search_path=public
as $$
declare
  c public.certificates%rowtype;
  issued public.certificates%rowtype;
  number text;
  allowed boolean := false;
begin
  select * into c from public.certificates where id=p_certificate_id for update;
  if not found then raise exception 'RC12.1: certificate not found'; end if;

  allowed := public.is_admin()
    or c.created_by=auth.uid()
    or exists(select 1 from public.halaqa_teachers ht where ht.halaqa_id=c.halaqa_id and ht.teacher_id=auth.uid())
    or exists(select 1 from public.halaqa_supervisors hs where hs.halaqa_id=c.halaqa_id and hs.supervisor_id=auth.uid());
  if not allowed then raise exception 'RC12.1: certificate access denied'; end if;

  if c.status='issued' then return to_jsonb(c); end if;
  if c.status='voided' then raise exception 'RC12.1: voided certificate cannot be issued'; end if;

  number := coalesce(c.certificate_number, public.rc121_next_certificate_number(coalesce(p_issue_date,current_date)));
  update public.certificates
  set certificate_number=number,
      issue_date=coalesce(p_issue_date,current_date),
      status='issued',
      issued_by=auth.uid(),
      issued_at=now(),
      updated_at=now()
  where id=p_certificate_id
  returning * into issued;

  return to_jsonb(issued);
end;
$$;

revoke all on function public.rc121_next_certificate_number(date) from public, anon;
revoke all on function public.rc121_issue_certificate(uuid,date) from public, anon;
grant execute on function public.rc121_next_certificate_number(date) to authenticated;
grant execute on function public.rc121_issue_certificate(uuid,date) to authenticated;

create or replace function public.rc121_capabilities()
returns jsonb
language sql
stable
security invoker
set search_path=public
as $$
  select jsonb_build_object(
    'rc121', true,
    'certificates_core', to_regclass('public.certificates') is not null,
    'certificate_templates', to_regclass('public.certificate_templates') is not null,
    'phase_fk_authoritative', true,
    'legacy_plan_tag_repair', true,
    'issue_rpc', to_regprocedure('public.rc121_issue_certificate(uuid,date)') is not null
  );
$$;
grant execute on function public.rc121_capabilities() to authenticated;

-- Global default template metadata; RC12.2 owns the full visual editor.
insert into public.certificate_templates(id,halaqa_id,name,certificate_type,config,active,created_by)
values(
  '12100000-0000-4000-8000-000000000001'::uuid,
  null,
  'القالب الافتراضي',
  null,
  jsonb_build_object('renderer','native_vector','paper','A4-landscape','rtl',true,'version','RC12.1'),
  true,
  null
)
on conflict(id) do update set
  name=excluded.name,
  config=excluded.config,
  active=true,
  updated_at=now();

commit;

notify pgrst, 'reload schema';
notify pgrst, 'reload config';
