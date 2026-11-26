# Decisions (brainstorm during build; the owner delegated all open questions)

| # | Question | Options considered | Decision and why |
|---|---|---|---|
| D1 | Who approves contests made by teachers and supervisors? | admin only / the halaqa supervisor / auto-approve | Admin only. One reviewer, same screen already built; admin's own contests are approved at once. |
| D2 | Do guardians see payment details? | same as donors / only after pledging | Same as donors (the existing card JSON); the admin controls what the card shows. |
| D3 | Which cards does a guardian see? | all open cards / only their children's halaqat | Only cards of their children's halaqat; cards with no halaqa are hidden from guardians. |
| D4 | Reminder defaults | off until chosen / on | On by default, weekly Thursday 18:00 (Asia/Gaza), per role text; switch-off and time change in the panel; permission asked when first enabled. |
| D5 | Exact vs approximate timing | AlarmManager / WorkManager | WorkManager one-time chain: no special permission; minutes of drift are fine for a reminder. |
| D6 | Admin reminder? | none / contest reminder | Admin gets the contest reminder like staff (propose a contest). |
| D7 | Teacher with no halaqa | block / allow without halaqa | Block with a clear message; admin must pick a halaqa too (an approved contest needs one). |
| D8 | Page name and place | new bottom tab / drawer + More | Drawer/More entry "المساهمة" for four roles; Home shortcut is a later step. |
| D9 | Creator column naming | rename donor_id / keep | Keep `donor_id` (creator id) and add `creator_role`; no rename migration risk. |
