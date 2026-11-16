BEGIN;

-- Server-only hotfix for Halaqati 2.63.50. No APK update required.
-- PL/pgSQL prevents the planner from expanding nested phase/plan RLS at every
-- reference in INSERT ... ON CONFLICT. Keep SECURITY INVOKER: underlying RLS,
-- guardian privacy restrictions, and scope-validation triggers still apply.
CREATE OR REPLACE FUNCTION public.rc2750_can_manage_plan_phase(p_phase_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $function$
BEGIN
  RETURN (is_admin() OR (EXISTS ( SELECT 1
   FROM (smart_plan_phases p
     JOIN smart_plans sp ON ((sp.id = p.plan_id)))
  WHERE ((p.id = p_phase_id) AND ((sp.created_by = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
           FROM halaqa_teachers ht
          WHERE ((ht.halaqa_id = sp.halaqa_id) AND (ht.teacher_id = ( SELECT auth.uid() AS uid))))) OR (EXISTS ( SELECT 1
           FROM halaqa_supervisors hs
          WHERE ((hs.halaqa_id = sp.halaqa_id) AND (hs.supervisor_id = ( SELECT auth.uid() AS uid))))))))));
END;
$function$;
REVOKE ALL ON FUNCTION public.rc2750_can_manage_plan_phase(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.rc2750_can_manage_plan_phase(uuid) TO authenticated;

ALTER POLICY smart_plan_phase_states_manage_access
ON public.smart_plan_phase_states
USING (public.rc2750_can_manage_plan_phase(phase_id))
WITH CHECK (public.rc2750_can_manage_plan_phase(phase_id));

ALTER POLICY smart_plan_phase_states_select_access
ON public.smart_plan_phase_states
USING (
  public.rc2750_can_manage_plan_phase(phase_id)
  OR EXISTS (
    SELECT 1 FROM public.student_guardians sg
    WHERE sg.student_id = smart_plan_phase_states.student_id
      AND sg.guardian_id = (SELECT auth.uid())
  )
);

COMMIT;
