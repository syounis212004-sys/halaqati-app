# Donor contests (المسابقات) - design note

Status: implementation note written before coding. Migration: `source/migration/20261125_v2920_donor_contests.sql` (file only, not applied).

## Facts found in the repo / live schema (read-only metadata)
- Donor = `UserRole.OBSERVER` (profiles.role = 'observer'). Existing donor<->halaqa link: table `donor_halaqas(donor_id, halaqa_id, active, updated_at)`, PK (donor_id, halaqa_id), edited by admin through `rc48_set_donor_halaqas`. No new link table is needed. (The repo's rc48 migration file is a stub `select 1`; the column list above was read from the live schema's metadata only, no data and no writes.)
- Halaqa membership: teachers `halaqa_teachers(halaqa_id, teacher_id)`; supervisors `halaqa_supervisors(halaqa_id, supervisor_id)` plus legacy `halaqas.supervisor_id`; students `students.halaqa_id` (+ `student_halaqa_memberships`) and `students.user_id` (student account); guardians `student_guardians(student_id, guardian_id)`.
- Notifications: row in `notifications(recipient_id, sender_id, halaqa_id, kind, title, body, source_key, deep_link, data)`, unique `(recipient_id, source_key)` used with `on conflict do nothing`; written from SECURITY DEFINER RPCs (pattern: `rc32_observer_submit_request`).
- Existing RPCs use `security definer set search_path to 'public'`; new ones use `set search_path = public, pg_temp`.
- Navigation: one `AppDestination` + `RoleNavigationPolicy` lists per role + `HalaqatiApp.FeatureRouter` route switch + `GoldenShell.drawerGroupOf`.

## Data
`donor_contests(id, donor_id, halaqa_id null, name, title, conditions, reward, status pending|approved|rejected, reject_reason, decided_by, decided_at, starts_on, ends_on, created_at, updated_at)`.
Checks: lengths (name 3-80, title 3-120, conditions 10-2000, reward 3-500), `ends_on >= starts_on`, rejected => reject_reason present. (approved => halaqa not null is enforced in `contest_decide`, not by a check, so a deleted halaqa (`on delete set null`) does not break old rows.)

## RLS (enabled; table grants limited to authenticated select/insert/update)
- donor insert: `donor_id = auth.uid()`, role observer, status pending, undecided, halaqa null or linked (helper `contest_donor_linked`).
- donor select own; donor update own while `pending` (with check keeps status pending, undecided, halaqa linked).
- admin select all, update all (decide).
- related users select only `status='approved'` rows whose halaqa is in `contest_user_halaqas()` (definer helper: teacher, supervisor, guardian-of-student, student account).
- No delete policy.
Membership cannot be expressed with plain policy joins (RLS on the joined tables would hide rows), so helpers are SECURITY DEFINER with `set search_path = public, pg_temp`.

## RPCs (all SECURITY DEFINER, search_path pinned, revoked from public/anon)
- `contest_donor_halaqas()` -> jsonb [{id,name,linked}] : donor = his linked active halaqat; admin = all active (for assignment).
- `contest_submit(name,title,conditions,reward,halaqa,starts_on,ends_on)` -> uuid; notifies admins. Halaqa may be null only when the donor has no link at all; otherwise it must be linked.
- `contest_my()` -> donor's own rows (with halaqa name).
- `contest_admin_list()` -> admin: all rows with donor + halaqa names, pending first.
- `contest_decide(id, status, reason, halaqa, link_donor)` admin only; pending -> approved|rejected only; approve needs a halaqa (admin may set/override; `link_donor` also upserts donor_halaqas so the donor is linked); reject needs a reason; notifies donor; on approval notifies teachers, supervisors, guardians, student accounts of that halaqa.
- `contest_visible()` -> approved contests for the caller's halaqat (read-only list for teacher/supervisor/guardian/student; donor sees his own through `contest_my`).

## Flows
Donor: Contests page -> list with status chips (all / pending / approved / rejected, rejection reason shown) -> "new contest" form (halaqa picker limited to linked halaqat). No link: notice "سيحدد مدير النظام حلقتك" and submit is still possible (halaqa unassigned).
Admin: Contests page = review list, filter pending by default; approve (pick/confirm halaqa, required; checkbox to link donor) or reject (reason required).
Teacher/supervisor/guardian/student: same route, read-only list; small home card shows the count and opens the list.

## Edge cases
Offline: lists come from the stored RPC cache (storedRpcList), write actions say they need a connection. RPC missing (migration not applied): friendly Arabic message. Double decision: server returns false -> "تم البت في المسابقة سابقًا". Donor unlinked after submit: row stays; admin must pick a halaqa when approving. Deleted halaqa: FK `on delete set null`; such approved rows are visible to nobody but the admin until reassigned.
No Quran text is stored or shown.
