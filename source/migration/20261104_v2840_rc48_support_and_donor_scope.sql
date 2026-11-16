-- RC48 (2.84.0). Applied to production through Supabase migrations:
--   rc48_donor_halaqa_scope, rc48_scope_observer_functions, rc48_support_cards_tables, rc48_support_cards_functions
-- Objects: donor_halaqas, rc48_donor_scope_ok(uuid), rc48_donors(), rc48_set_donor_halaqas(uuid, uuid[]),
--   support_cards, support_pledges, rc48_support_save_card(jsonb), rc48_support_cards(), rc48_support_pledge(uuid, text[], boolean),
--   rc48_support_cancel(uuid), rc48_support_confirm(uuid), rc48_support_deliver(uuid, text, text), rc48_my_pledges(), rc48_support_proof(uuid).
-- Re-created with the donor halaqa scope: rc32_observer_overview, rc40_observer_breakdown, rc42_observer_student_detail, rc46_awards_payload.
-- The full SQL lives in the Supabase migration history of project xlxzwucqdtggkfwwqibk.
select 1;
