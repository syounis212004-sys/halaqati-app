-- RC27.45 — read-only audit. Run each block ALONE (the SQL editor shows only the last result). Nothing here changes data.

-- 1) Tables with row-level security OFF (should be empty, or only harmless lookup tables)
select c.relname as table_name, c.relrowsecurity as rls_on,
       (select count(*) from pg_policy p where p.polrelid = c.oid) as policies
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by c.relrowsecurity, policies, c.relname;

-- 2) Policies that allow everything (qual = true) — check each one is intentional
select tablename, policyname, cmd, roles, qual
from pg_policies
where schemaname = 'public' and (qual = 'true' or qual is null) and cmd in ('SELECT', 'ALL', 'UPDATE', 'DELETE')
order by tablename, policyname;

-- 3) What the logged-out (anon) role can touch — should be empty
select table_name, privilege_type
from information_schema.role_table_grants
where grantee = 'anon' and table_schema = 'public'
order by table_name, privilege_type;

-- 4) Functions callable by anon (should be empty or only the sign-in helpers)
select p.proname
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and has_function_privilege('anon', p.oid, 'execute')
order by p.proname;

-- 5) Push: triggers on notifications (is anything sending a push when a row is inserted?)
select tgname, pg_get_triggerdef(oid) as definition
from pg_trigger
where not tgisinternal and tgrelid = 'public.notifications'::regclass;

-- 6) Push: which tables hold devices/tokens (the app registers through the halaqati-notify edge function)
select table_name from information_schema.tables
where table_schema = 'public' and (table_name ilike '%push%' or table_name ilike '%device%' or table_name ilike '%token%');
