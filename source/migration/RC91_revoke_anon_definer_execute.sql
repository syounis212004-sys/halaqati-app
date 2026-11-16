-- RC91 (2.90.1): applied live as migration rc91_revoke_anon_definer_execute.
do $$
declare r record;
begin
  for r in select p.oid::regprocedure as sig, p.prorettype::regtype::text as rt
           from pg_proc p join pg_namespace n on n.oid=p.pronamespace
           where n.nspname='public' and p.prosecdef
             and (p.proname ~ '^(rc86_|rc90_|rc53_|rc66_my_subscription)' or p.proname='stamp_association')
  loop
    if r.rt='trigger' then execute format('revoke execute on function %s from public, anon, authenticated', r.sig);
    else execute format('revoke execute on function %s from public, anon', r.sig);
         execute format('grant execute on function %s to authenticated, service_role', r.sig);
    end if;
  end loop;
end $$;
