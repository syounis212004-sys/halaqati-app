-- RC35 (2.71.0) — OPTIONAL data cleanup, to be run only with the owner's approval after a backup.
-- 33 old rows (7–23 September 2026) are placeholders: an unrated recitation with 0 pages saved with safety_percentage = 0.
-- They drag every server-side average (guardian overview, guardian dashboard range, plan archive, badges) down.
-- The app (2.71.0) already ignores them; this makes the server agree. The rows themselves are kept; only the false 0 is cleared.
--
-- Check first (expected 33):
--   select count(*) from public.recitation_entries
--   where evaluation = 'not_rated' and coalesce(pages_count, 0) = 0 and safety_percentage = 0;

update public.recitation_entries
   set safety_percentage = null
 where evaluation = 'not_rated'
   and coalesce(pages_count, 0) = 0
   and safety_percentage = 0;
