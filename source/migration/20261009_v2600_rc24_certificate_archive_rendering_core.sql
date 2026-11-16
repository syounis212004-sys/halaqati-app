-- Halaqati v2.60.0 RC24 — Certificate Archive & Rendering Core
-- Additive migration. Existing legacy certificate numbers are preserved byte-for-byte.

begin;

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- 1) Configurable official numbering series.
-- ---------------------------------------------------------------------------
create table if not exists public.certificate_series (
  series_key text primary key,
  code text not null unique check (code ~ '^[A-Z0-9]{1,6}$'),
  arabic_label text not null,
  active boolean not null default true,
  created_by uuid references public.profiles(id) on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

insert into public.certificate_series(series_key,code,arabic_label,active) values
  ('general','G','عام / تقدير',true),
  ('sard','S','السرد',true),
  ('khatm','K','ختم القرآن',true),
  ('monthly','M','التميز الشهري',true),
  ('ideal','I','الطالب المثالي',true),
  ('rank','R','المراكز / التفوق',true),
  ('custom','C','مخصص',true)
on conflict(series_key) do nothing;

-- ---------------------------------------------------------------------------
-- 2) Evolve the old yearly counter into independent (year, series) counters.
-- Existing rows become legacy counters; they are not used for RC24 allocations.
-- ---------------------------------------------------------------------------
alter table public.certificate_number_counters
  add column if not exists series_key text;
update public.certificate_number_counters set series_key='legacy' where series_key is null;
alter table public.certificate_number_counters alter column series_key set not null;

do $$
begin
  if exists (
    select 1 from pg_constraint
    where conrelid='public.certificate_number_counters'::regclass
      and contype='p' and conname='certificate_number_counters_pkey'
  ) then
    alter table public.certificate_number_counters drop constraint certificate_number_counters_pkey;
  end if;
exception when undefined_object then null;
end $$;

alter table public.certificate_number_counters
  add constraint certificate_number_counters_pkey primary key(issue_year,series_key);

-- ---------------------------------------------------------------------------
-- 3) Certificate identity additions and immutable revision history.
-- ---------------------------------------------------------------------------
alter table public.certificates add column if not exists series_key text;
alter table public.certificates add column if not exists series_code_at_issue text;
alter table public.certificates add column if not exists current_revision_id uuid;
alter table public.certificates add column if not exists revision_count integer not null default 0;
alter table public.certificates add column if not exists legacy_number boolean not null default false;
alter table public.certificates add column if not exists legacy_partial boolean not null default false;
alter table public.certificates add column if not exists void_reason text;

update public.certificates
set legacy_number = true
where certificate_number ~ '^HAL-CERT-[0-9]{4}-[0-9]{5,}$';

update public.certificates
set series_key = coalesce(series_key,
  case certificate_type when 'sard' then 'sard' when 'hifz' then 'khatm' else 'general' end
)
where series_key is null;

create table if not exists public.certificate_revisions (
  id uuid primary key default gen_random_uuid(),
  certificate_id uuid not null references public.certificates(id) on delete cascade,
  revision_no integer not null check (revision_no > 0),
  render_snapshot jsonb not null,
  content_hash text not null,
  note text,
  created_by uuid references public.profiles(id) on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  unique(certificate_id, revision_no),
  unique(certificate_id, content_hash)
);

create index if not exists certificate_revisions_certificate_idx
  on public.certificate_revisions(certificate_id, revision_no desc);
create index if not exists certificates_series_date_idx
  on public.certificates(series_key, issue_date desc);

-- Backfill an audit revision for legacy/current certificates without inventing missing body/style.
insert into public.certificate_revisions(certificate_id,revision_no,render_snapshot,content_hash,created_by,created_at,note)
select c.id,1,
       jsonb_build_object(
         'schema','halaqati_certificate_render_v2',
         'student_id',c.student_id::text,
         'student_name',coalesce(c.snapshot->>'student_name',''),
         'center_name',coalesce(c.snapshot->>'center_name',''),
         'halaqa_name',coalesce(c.snapshot->>'halaqa_name',''),
         'certificate_type',c.certificate_type,
         'series_key',coalesce(c.series_key,'general'),
         'title',coalesce(c.snapshot->>'title',c.title,''),
         'intro',coalesce(c.snapshot->>'intro',''),
         'achievement',coalesce(c.snapshot->>'achievement',''),
         'dua',coalesce(c.snapshot->>'dua',''),
         'issue_date',coalesce(c.issue_date::text,''),
         'official_number',coalesce(c.certificate_number,''),
         'teacher_name',coalesce(c.snapshot->>'teacher_name',''),
         'supervisor_name',coalesce(c.snapshot->>'supervisor_name',''),
         'style',coalesce(c.snapshot->'style','{}'::jsonb),
         'asset_refs',jsonb_build_object('policy','local_only'),
         'source',coalesce(c.snapshot,'{}'::jsonb),
         'renderer_version','legacy-import'
       ) as snap,
       encode(digest((jsonb_build_object(
         'certificate_id',c.id::text,
         'legacy_snapshot',coalesce(c.snapshot,'{}'::jsonb),
         'number',coalesce(c.certificate_number,'')
       ))::text,'sha256'),'hex'),
       c.created_by,coalesce(c.issued_at,c.created_at),'RC24 legacy audit snapshot'
from public.certificates c
where not exists(select 1 from public.certificate_revisions r where r.certificate_id=c.id);

update public.certificates c
set current_revision_id = r.id,
    revision_count = greatest(c.revision_count,1),
    legacy_partial = case
      when coalesce(r.render_snapshot->>'student_name','')=''
        or coalesce(r.render_snapshot->>'title','')=''
        or coalesce(r.render_snapshot->>'achievement','')=''
      then true else c.legacy_partial end
from public.certificate_revisions r
where r.certificate_id=c.id and r.revision_no=1 and c.current_revision_id is null;

-- Certificate 00009 and peers remain exactly numbered; no UPDATE touches certificate_number.

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname='certificates_current_revision_id_fkey'
      and conrelid='public.certificates'::regclass
  ) then
    alter table public.certificates
      add constraint certificates_current_revision_id_fkey foreign key(current_revision_id)
      references public.certificate_revisions(id) on delete set null;
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- 4) Helpers and authorization.
-- ---------------------------------------------------------------------------
create or replace function public.rc24_can_manage_certificate(p_certificate_id uuid)
returns boolean
language sql
stable
security definer
set search_path=pg_catalog, public
as $$
  select exists(
    select 1 from public.certificates c
    where c.id=p_certificate_id and (
      public.is_admin()
      or c.created_by=(select auth.uid())
      or exists(select 1 from public.halaqa_teachers ht where ht.halaqa_id=c.halaqa_id and ht.teacher_id=(select auth.uid()))
      or exists(select 1 from public.halaqa_supervisors hs where hs.halaqa_id=c.halaqa_id and hs.supervisor_id=(select auth.uid()))
    )
  );
$$;

create or replace function public.rc24_next_certificate_number(p_series_key text, p_issue_date date default current_date)
returns text
language plpgsql
security definer
set search_path=pg_catalog, public
as $$
declare
  v_year integer := extract(year from coalesce(p_issue_date,current_date))::integer;
  v_code text;
  v_seq bigint;
begin
  if not (public.is_admin() or public.current_role()::text in ('teacher','supervisor')) then
    raise exception 'RC24: certificate numbering is restricted to staff';
  end if;
  select code into v_code from public.certificate_series where series_key=p_series_key and active=true;
  if v_code is null then raise exception 'RC24: certificate series disabled or unknown'; end if;

  insert into public.certificate_number_counters(issue_year,series_key,last_value,updated_at)
  values(v_year,p_series_key,1,now())
  on conflict(issue_year,series_key) do update set
    last_value=public.certificate_number_counters.last_value+1,
    updated_at=now()
  returning last_value into v_seq;
  return 'HAL-CERT-'||v_year::text||'-'||v_code||'-'||lpad(v_seq::text,5,'0');
end;
$$;

create or replace function public.rc24_issue_certificate(
  p_certificate_id uuid,
  p_series_key text,
  p_issue_date date,
  p_render_snapshot jsonb
)
returns jsonb
language plpgsql
security definer
set search_path=pg_catalog, public
as $$
declare
  c public.certificates%rowtype;
  v_number text;
  v_code text;
  v_snapshot jsonb;
  v_hash text;
  v_revision_id uuid;
begin
  select * into c from public.certificates where id=p_certificate_id for update;
  if not found then raise exception 'RC24: certificate not found'; end if;
  if not public.rc24_can_manage_certificate(p_certificate_id) then raise exception 'RC24: certificate access denied'; end if;
  if c.status='voided' then raise exception 'RC24: voided certificate cannot be issued'; end if;
  if c.status='issued' then
    return to_jsonb(c);
  end if;
  if coalesce(p_render_snapshot->>'schema','') <> 'halaqati_certificate_render_v2' then
    raise exception 'RC24: invalid render snapshot schema';
  end if;
  if coalesce(p_render_snapshot->>'student_name','')='' or coalesce(p_render_snapshot->>'title','')='' or coalesce(p_render_snapshot->>'achievement','')='' then
    raise exception 'RC24: incomplete required render content';
  end if;
  if coalesce(p_render_snapshot->>'student_id','') <> c.student_id::text then raise exception 'RC24: snapshot student mismatch'; end if;
  if coalesce(p_render_snapshot->>'series_key','') <> p_series_key then raise exception 'RC24: snapshot series mismatch'; end if;
  if extract(year from p_issue_date)::integer <> extract(year from coalesce((p_render_snapshot->>'issue_date')::date,p_issue_date))::integer then
    raise exception 'RC24: snapshot issue year mismatch';
  end if;

  select code into v_code from public.certificate_series where series_key=p_series_key and active=true;
  if v_code is null then raise exception 'RC24: certificate series disabled or unknown'; end if;
  v_number := public.rc24_next_certificate_number(p_series_key,p_issue_date);
  v_snapshot := jsonb_set(p_render_snapshot,'{official_number}',to_jsonb(v_number),true);
  v_hash := encode(digest(v_snapshot::text,'sha256'),'hex');

  insert into public.certificate_revisions(certificate_id,revision_no,render_snapshot,content_hash,note,created_by)
  values(c.id,1,v_snapshot,v_hash,'الإصدار الأول',auth.uid()) returning id into v_revision_id;

  update public.certificates
  set certificate_number=v_number,
      issue_date=p_issue_date,
      status='issued',
      issued_by=auth.uid(),
      issued_at=now(),
      series_key=p_series_key,
      series_code_at_issue=v_code,
      current_revision_id=v_revision_id,
      revision_count=1,
      legacy_number=false,
      legacy_partial=false,
      snapshot=jsonb_build_object(
        'schema','halaqati_certificate_archive_lite_v2',
        'student_name',v_snapshot->>'student_name',
        'series_key',p_series_key,
        'revision_no',1,
        'render_schema','halaqati_certificate_render_v2',
        'storage_policy','metadata_only_no_pdf_no_assets'
      ),
      updated_at=now()
  where id=c.id
  returning * into c;
  return to_jsonb(c) || jsonb_build_object('revision_id',v_revision_id,'revision_no',1,'render_snapshot',v_snapshot);
end;
$$;

create or replace function public.rc24_create_certificate_revision(
  p_certificate_id uuid,
  p_render_snapshot jsonb,
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path=pg_catalog, public
as $$
declare
  c public.certificates%rowtype;
  v_no integer;
  v_hash text;
  v_id uuid;
  v_snapshot jsonb;
begin
  select * into c from public.certificates where id=p_certificate_id for update;
  if not found then raise exception 'RC24: certificate not found'; end if;
  if not public.rc24_can_manage_certificate(p_certificate_id) then raise exception 'RC24: certificate access denied'; end if;
  if c.status <> 'issued' then raise exception 'RC24: only issued certificates can be revised'; end if;
  if coalesce(p_render_snapshot->>'schema','') <> 'halaqati_certificate_render_v2' then raise exception 'RC24: invalid render snapshot schema'; end if;
  if coalesce(p_render_snapshot->>'student_id','') <> c.student_id::text then raise exception 'RC24: student identity cannot change in a revision'; end if;
  if coalesce(p_render_snapshot->>'series_key','') <> c.series_key then raise exception 'RC24: series cannot change in a revision'; end if;
  if extract(year from (p_render_snapshot->>'issue_date')::date)::integer <> extract(year from c.issue_date)::integer then raise exception 'RC24: issue year cannot change in a revision'; end if;
  if coalesce(p_render_snapshot->>'student_name','')='' or coalesce(p_render_snapshot->>'title','')='' or coalesce(p_render_snapshot->>'achievement','')='' then raise exception 'RC24: incomplete required render content'; end if;

  v_snapshot := jsonb_set(p_render_snapshot,'{official_number}',to_jsonb(c.certificate_number),true);
  v_hash := encode(digest(v_snapshot::text,'sha256'),'hex');
  select coalesce(max(revision_no),0)+1 into v_no from public.certificate_revisions where certificate_id=c.id;

  begin
    insert into public.certificate_revisions(certificate_id,revision_no,render_snapshot,content_hash,note,created_by)
    values(c.id,v_no,v_snapshot,v_hash,nullif(trim(coalesce(p_note,'')),''),auth.uid()) returning id into v_id;
  exception when unique_violation then
    select id,revision_no into v_id,v_no from public.certificate_revisions where certificate_id=c.id and content_hash=v_hash limit 1;
  end;

  update public.certificates set current_revision_id=v_id,revision_count=(select count(*) from public.certificate_revisions where certificate_id=c.id),updated_at=now() where id=c.id;
  return jsonb_build_object('id',v_id,'certificate_id',c.id,'revision_no',v_no,'render_snapshot',v_snapshot,'content_hash',v_hash,'note',p_note,'created_by',auth.uid(),'created_at',now());
end;
$$;

create or replace function public.rc24_void_certificate(p_certificate_id uuid, p_reason text)
returns jsonb
language plpgsql
security definer
set search_path=pg_catalog, public
as $$
declare c public.certificates%rowtype;
begin
  select * into c from public.certificates where id=p_certificate_id for update;
  if not found then raise exception 'RC24: certificate not found'; end if;
  if not public.rc24_can_manage_certificate(p_certificate_id) then raise exception 'RC24: certificate access denied'; end if;
  if c.status='draft' then raise exception 'RC24: delete draft instead of voiding'; end if;
  update public.certificates set status='voided',void_reason=nullif(trim(coalesce(p_reason,'')),''),updated_at=now() where id=c.id returning * into c;
  return to_jsonb(c);
end;
$$;

create or replace function public.rc24_import_legacy_certificate(
  p_student_id uuid,
  p_halaqa_id uuid,
  p_certificate_type text,
  p_series_key text,
  p_issue_date date,
  p_historical_number text,
  p_render_snapshot jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path=pg_catalog, public
as $$
declare c public.certificates%rowtype; v_id uuid; v_snap jsonb; v_hash text; v_partial boolean;
begin
  if not (public.is_admin() or public.current_role()::text in ('teacher','supervisor')) then raise exception 'RC24: legacy import restricted to staff'; end if;
  if trim(coalesce(p_historical_number,''))='' then raise exception 'RC24: historical number required'; end if;
  if exists(select 1 from public.certificates where certificate_number=p_historical_number) then raise exception 'RC24: historical number already exists'; end if;
  v_snap := coalesce(p_render_snapshot, jsonb_build_object('schema','halaqati_certificate_render_v2','student_id',p_student_id::text,'student_name','','center_name','','halaqa_name','','certificate_type',p_certificate_type,'series_key',p_series_key,'title','','intro','','achievement','','dua','','issue_date',p_issue_date::text,'official_number',p_historical_number,'teacher_name','','supervisor_name','','style','{}'::jsonb,'asset_refs',jsonb_build_object('policy','local_only'),'source',jsonb_build_object('legacy_import',true),'renderer_version','legacy-import'));
  v_partial := coalesce(v_snap->>'student_name','')='' or coalesce(v_snap->>'title','')='' or coalesce(v_snap->>'achievement','')='';
  insert into public.certificates(certificate_number,student_id,halaqa_id,certificate_type,title,issue_date,status,series_key,series_code_at_issue,legacy_number,legacy_partial,snapshot,issued_by,issued_at,created_by)
  values(p_historical_number,p_student_id,p_halaqa_id,p_certificate_type,coalesce(v_snap->>'title',''),p_issue_date,'issued',p_series_key,null,true,v_partial,jsonb_build_object('schema','halaqati_certificate_archive_lite_v2','student_name',coalesce(v_snap->>'student_name',''),'legacy_import',true,'storage_policy','metadata_only_no_pdf_no_assets'),auth.uid(),now(),auth.uid()) returning * into c;
  v_hash:=encode(digest(v_snap::text,'sha256'),'hex');
  insert into public.certificate_revisions(certificate_id,revision_no,render_snapshot,content_hash,note,created_by) values(c.id,1,v_snap,v_hash,'سجل أرشيف تاريخي',auth.uid()) returning id into v_id;
  update public.certificates set current_revision_id=v_id,revision_count=1 where id=c.id returning * into c;
  return to_jsonb(c) || jsonb_build_object('revision_id',v_id,'revision_no',1,'render_snapshot',v_snap);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5) RLS. Revisions inherit visibility from the parent certificate.
-- ---------------------------------------------------------------------------
alter table public.certificate_series enable row level security;
alter table public.certificate_revisions enable row level security;

revoke all on public.certificate_series from anon;
revoke all on public.certificate_revisions from anon;
grant select on public.certificate_series to authenticated;
grant select on public.certificate_revisions to authenticated;
grant insert,update,delete on public.certificate_series to authenticated;

create policy certificate_series_read on public.certificate_series for select to authenticated using (true);
create policy certificate_series_manage on public.certificate_series for all to authenticated
using (public.is_admin() or public.current_role()::text='supervisor')
with check (public.is_admin() or public.current_role()::text='supervisor');

create policy certificate_revisions_read on public.certificate_revisions for select to authenticated using (
  exists(select 1 from public.certificates c where c.id=certificate_revisions.certificate_id and (
    public.is_admin()
    or c.created_by=(select auth.uid())
    or exists(select 1 from public.halaqa_teachers ht where ht.halaqa_id=c.halaqa_id and ht.teacher_id=(select auth.uid()))
    or exists(select 1 from public.halaqa_supervisors hs where hs.halaqa_id=c.halaqa_id and hs.supervisor_id=(select auth.uid()))
    or (c.status='issued' and exists(select 1 from public.student_guardians sg where sg.student_id=c.student_id and sg.guardian_id=(select auth.uid())))
  ))
);

-- Direct revision writes are intentionally not granted; use RPCs.

revoke all on function public.rc24_can_manage_certificate(uuid) from public, anon, authenticated;
revoke all on function public.rc24_next_certificate_number(text,date) from public, anon, authenticated;
revoke all on function public.rc24_issue_certificate(uuid,text,date,jsonb) from public, anon;
revoke all on function public.rc24_create_certificate_revision(uuid,jsonb,text) from public, anon;
revoke all on function public.rc24_void_certificate(uuid,text) from public, anon;
revoke all on function public.rc24_import_legacy_certificate(uuid,uuid,text,text,date,text,jsonb) from public, anon;
-- Internal helpers are deliberately NOT executable through PostgREST; issue/revision RPCs call them as the function owner.
grant execute on function public.rc24_issue_certificate(uuid,text,date,jsonb) to authenticated;
grant execute on function public.rc24_create_certificate_revision(uuid,jsonb,text) to authenticated;
grant execute on function public.rc24_void_certificate(uuid,text) to authenticated;
grant execute on function public.rc24_import_legacy_certificate(uuid,uuid,text,text,date,text,jsonb) to authenticated;

-- Keep SECURITY DEFINER functions off mutable/default search paths.
alter function public.rc24_can_manage_certificate(uuid) set search_path=pg_catalog, public;
alter function public.rc24_next_certificate_number(text,date) set search_path=pg_catalog, public;
alter function public.rc24_issue_certificate(uuid,text,date,jsonb) set search_path=pg_catalog, public;
alter function public.rc24_create_certificate_revision(uuid,jsonb,text) set search_path=pg_catalog, public;
alter function public.rc24_void_certificate(uuid,text) set search_path=pg_catalog, public;
alter function public.rc24_import_legacy_certificate(uuid,uuid,text,text,date,text,jsonb) set search_path=pg_catalog, public;

commit;
