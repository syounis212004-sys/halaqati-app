-- RC24.1 hotfix: "function digest(text, unknown) does not exist" when issuing/saving a certificate.
-- Supabase installs pgcrypto in the "extensions" schema, but the RC24 certificate functions run with
-- search_path = pg_catalog, public, so digest() was not found. Safe to run more than once.
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

alter function public.rc24_issue_certificate(uuid,text,date,jsonb) set search_path = pg_catalog, public, extensions;
alter function public.rc24_create_certificate_revision(uuid,jsonb,text) set search_path = pg_catalog, public, extensions;
alter function public.rc24_import_legacy_certificate(uuid,uuid,text,text,date,text,jsonb) set search_path = pg_catalog, public, extensions;

-- sanity check: must return a 64-char hex string
select encode(extensions.digest('halaqati'::text,'sha256'),'hex') as digest_ok;
