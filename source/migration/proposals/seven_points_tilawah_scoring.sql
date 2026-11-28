-- Additive, review-only. Do not rewrite historical recitations.
-- Runs alphabetically after the existing V7 trigger, and only handles V2 tilawah.
begin;
create or replace function public.rc_tilawah_score_guard_v2()
returns trigger language plpgsql security invoker set search_path='' as $f$
declare
  a jsonb := new.selection_data->'tilawah_assessment';
  old_a jsonb;
  key text;
  rating_sum numeric := 0;
  rating_count integer := 3;
  major numeric := 0;
  score numeric;
  pg numeric := greatest(coalesce(new.pages_count,0),0);
  revision text;
begin
  if tg_op='UPDATE' then
    old_a := old.selection_data->'tilawah_assessment';
    if old.activity_type::text='tilawah' and old_a->>'version'='2' then
      if new.activity_type::text is distinct from 'tilawah' or a->>'version' is distinct from '2' then
        raise exception 'V2 tilawah assessment cannot be overwritten by an older client' using errcode='PT409';
      end if;
    end if;
  end if;
  if new.activity_type::text is distinct from 'tilawah' or a->>'version' is distinct from '2' then
    return new;
  end if;
  revision := a->>'revision';
  if revision is null or revision !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
    raise exception 'missing tilawah revision; update app before editing' using errcode='PT409';
  end if;
  foreach key in array array['makharij','tajweed','waqf_ibtida'] loop
    if jsonb_typeof(a->key) is distinct from 'number' or (a->>key) !~ '^[1-5]$' then
      raise exception 'invalid tilawah rating %',key using errcode='23514';
    end if;
    rating_sum:=rating_sum+(a->>key)::numeric;
  end loop;
  if a->'correction_adherence' is not null and a->'correction_adherence'<>'null'::jsonb then
    if jsonb_typeof(a->'correction_adherence') is distinct from 'number' or (a->>'correction_adherence') !~ '^[0-5]$' then
      raise exception 'invalid correction adherence' using errcode='23514';
    end if;
    rating_sum:=rating_sum+(a->>'correction_adherence')::numeric;
    rating_count:=4;
  end if;
  if jsonb_typeof(a->'ward_reviewed') is distinct from 'boolean' then
    raise exception 'ward review must be explicit' using errcode='23514';
  end if;
  foreach key in array array['jali_words','jali_letters','jali_harakat'] loop
    if jsonb_typeof(a->key) is distinct from 'number' or (a->>key) !~ '^[0-9]{1,10}$' or (a->>key)::numeric>2147483647 then
      raise exception 'invalid major error count %',key using errcode='23514';
    end if;
    major:=major+(a->>key)::numeric;
  end loop;
  major:=least(major,2147483647);
  if pg<=0 then raise exception 'tilawah requires a nonempty range' using errcode='23514'; end if;
  score:=round(greatest(0,least(100,90*rating_sum/(rating_count*5)+case when (a->>'ward_reviewed')::boolean then 10 else 0 end-8*major)),1);
  a:=a||jsonb_build_object('overall_score',score,'lahn_jali',major);
  if tg_op='UPDATE' and old.activity_type::text='tilawah' and old_a->>'version'='2' then
    if old_a->>'revision'=revision then
      -- Replaying exactly the same revision is idempotent; different content is not.
      if old_a is distinct from a or old.session_id is distinct from new.session_id
          or old.student_id is distinct from new.student_id or old.pages_count is distinct from new.pages_count
          or old.teacher_note is distinct from new.teacher_note
          or (old.selection_data-'tilawah_assessment') is distinct from (new.selection_data-'tilawah_assessment') then
        raise exception 'tilawah revision reused with different content' using errcode='PT409';
      end if;
    elsif (a->>'base_revision') is distinct from (old_a->>'revision') then
      raise exception 'tilawah changed on another device; reload before editing' using errcode='PT409';
    end if;
  elsif tg_op='UPDATE' and old.activity_type::text='tilawah' and old_a->>'version' is distinct from '2' then
    raise exception 'historical tilawah must retain its original assessment version' using errcode='PT409';
  end if;
  new.selection_data:=jsonb_set(new.selection_data,'{tilawah_assessment}',a,true);
  new.mistakes:=major::integer;
  new.prompts:=0;
  new.safety_percentage:=score;
  new.evaluation:=case when score>=90 then 'excellent' when score>=80 then 'very_good' when score>=70 then 'good' else 'repeat' end;
  new.points:=round(pg*score/100,2);
  new.penalty_units:=major;
  new.penalty_per_page:=round(major/pg,3);
  new.scoring_version:='TILAWAH_V2';
  return new;
end $f$;
revoke all on function public.rc_tilawah_score_guard_v2() from public,anon,authenticated;
drop trigger if exists zz_tilawah_score_guard_v2 on public.recitation_entries;
create trigger zz_tilawah_score_guard_v2 before insert or update on public.recitation_entries
for each row execute function public.rc_tilawah_score_guard_v2();
-- Keep historical review sessions editable; prevent new independent review sessions.
create or replace function public.rc_no_new_review_sessions_v2()
returns trigger language plpgsql security invoker set search_path='' as $f$
begin
  if new.session_type::text='review' then
    if tg_op='INSERT' then
      if not exists(select 1 from public.sessions s where s.id=new.id and s.session_type::text='review') then
        raise exception 'review belongs to tasmee sessions' using errcode='23514';
      end if;
    elsif old.session_type::text is distinct from 'review' then
      raise exception 'review belongs to tasmee sessions' using errcode='23514';
    end if;
  end if;
  return new;
end $f$;
revoke all on function public.rc_no_new_review_sessions_v2() from public,anon,authenticated;
drop trigger if exists zz_no_new_review_sessions_v2 on public.sessions;
create trigger zz_no_new_review_sessions_v2 before insert or update of session_type on public.sessions
for each row execute function public.rc_no_new_review_sessions_v2();
commit;
