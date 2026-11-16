-- RC49 (2.85.0). Applied through Supabase migrations: rc49_support_payment_tables, rc49_support_payment_functions.
-- Tables/columns: support_cards (amount, currency, pay_*), support_pledges (transfer_ref, transfer_image, transfer_submitted_at, thanks_text),
--   support_defaults, donor_news, donor_suggestions.
-- Functions: rc49_support_defaults, rc49_support_set_defaults, rc49_support_save_card, rc49_support_cards, rc49_my_pledges,
--   rc49_support_submit_transfer, rc49_support_deliver, rc49_support_proof, rc49_donor_impact, rc49_donor_report,
--   rc49_news_list, rc49_news_save, rc49_donor_suggest, rc49_suggestions.
-- The full SQL lives in the Supabase migration history of project xlxzwucqdtggkfwwqibk.
select 1;
