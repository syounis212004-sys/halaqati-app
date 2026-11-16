-- Halaqati RC22 v2.58.0
-- Additive program metadata; safe for new environments after RC21 v2570.

begin;

alter table public.halaqas
  add column if not exists program_kind text,
  add column if not exists program_specialization text,
  add column if not exists program_level text,
  add column if not exists assessment_type text,
  add column if not exists affects_primary_plan boolean not null default false;

comment on column public.halaqas.program_kind is 'Program nature/kind for non-regular halaqas';
comment on column public.halaqas.program_specialization is 'Program specialization such as hifz, tajweed, tilawah, training';
comment on column public.halaqas.program_level is 'Program level such as beginner/intermediate/advanced/mixed';
comment on column public.halaqas.assessment_type is 'Assessment model: none, attendance, rubric, exam, ijazah';
comment on column public.halaqas.affects_primary_plan is 'Whether sessions in this program may affect the student primary smart plan';

create index if not exists halaqas_program_specialization_idx
  on public.halaqas(program_specialization)
  where program_specialization is not null;

commit;
