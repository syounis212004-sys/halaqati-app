-- Halaqati Native v2.63.33 RC27.33
-- Notify everyone when a competition season is closed (top 3 in the message).
-- Run AFTER 20261019_v2632_rc27_32_competition_seasons.sql. Safe to run twice.
-- The trigger never blocks closing a season: any notification error is swallowed.

begin;

create or replace function public.rc27_notify_season_closed()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_body text;
begin
  if old.status = 'active' and new.status = 'closed' then
    v_body := 'انتهى موسم «' || new.name || '». ' ||
      coalesce('الأول: ' || nullif(new.winners->0->>'name', ''), 'لا نتائج') ||
      coalesce(' · الثاني: ' || nullif(new.winners->1->>'name', ''), '') ||
      coalesce(' · الثالث: ' || nullif(new.winners->2->>'name', ''), '');
    begin
      insert into public.notifications (recipient_id, kind, title, body, source_key, data)
      select p.id, 'announcement', 'نتائج مسابقة ' || new.name, v_body,
             'season-closed:' || new.id::text,
             jsonb_build_object('season_id', new.id)
      from public.profiles p
      on conflict (recipient_id, source_key) do nothing;
    exception when others then
      null; -- never block closing the season
    end;
  end if;
  return new;
end
$$;

drop trigger if exists rc27_notify_season_closed_trg on public.competition_seasons;
create trigger rc27_notify_season_closed_trg
  after update on public.competition_seasons
  for each row execute function public.rc27_notify_season_closed();

commit;
