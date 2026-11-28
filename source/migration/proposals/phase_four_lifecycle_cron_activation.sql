-- REVIEW-ONLY activation step; NOT part of the fixture or automatic deployment.
-- Run only as the reviewed database scheduler role after phase_four_lifecycle.sql
-- passes full-schema staging, lock/load checks and owner approval.
-- Every five minutes, evaluate the calendar date in Asia/Gaza inside the function.
select cron.schedule(
  'halaqati-phase4-close-past-sessions',
  '*/5 * * * *',
  'select halaqati_lifecycle_private.close_past_sessions()'
);
-- To stop automation (does not revert or reopen historical sessions):
-- select cron.unschedule('halaqati-phase4-close-past-sessions');
