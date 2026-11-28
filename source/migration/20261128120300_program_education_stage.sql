-- Proposed forward migration; apply to staging before release. No data backfill by name.
begin;
alter table public.halaqas add column program_stage text;
alter table public.halaqas add constraint halaqas_program_stage_check check (
  program_stage is null or program_stage in (
    'reading_foundations','noorani','golden_rule','introductory','qualifying','advanced','sanad_preparation','sanad_ijazah','qiraat'
  )
);
comment on column public.halaqas.program_stage is 'Optional institution-defined educational stage; independent of delivery kind and general level.';
commit;
