-- REVIEW-ONLY activation step; NOT part of the fixture or automatic deployment.
-- Run only after phase_four_lifecycle.sql passes full-schema staging, lock/load
-- checks and owner approval. Do not change the server-wide cron timezone.
-- This server uses GMT. Palestine is UTC+2 in winter and UTC+3 in summer.
-- Two candidate UTC ticks, one actual closure at local midnight per day;
-- the other tick is a no-op. A private daily ledger prevents double execution.
begin;
do $timezone$
begin
  if current_setting('cron.timezone',true) is distinct from 'GMT'
     and current_setting('cron.timezone',true) is distinct from 'UTC' then
    raise exception 'Unexpected cron timezone; review midnight schedule before enabling';
  end if;
end
$timezone$;
select cron.schedule(
  'halaqati-phase4-close-past-sessions',
  '0 21,22 * * *',
  'select halaqati_lifecycle_private.close_at_gaza_midnight()'
);
commit;
-- To stop automation (does not revert or reopen historical sessions):
-- select cron.unschedule('halaqati-phase4-close-past-sessions');
