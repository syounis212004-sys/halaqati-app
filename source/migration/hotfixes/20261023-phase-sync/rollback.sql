BEGIN;

-- Restore original policies before removing the helper.
ALTER POLICY smart_plan_phase_states_manage_access ON public.smart_plan_phase_states
USING ((is_admin() OR (EXISTS ( SELECT 1
   FROM (smart_plan_phases p
     JOIN smart_plans sp ON ((sp.id = p.plan_id)))
  WHERE ((p.id = smart_plan_phase_states.phase_id) AND ((sp.created_by = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
           FROM halaqa_teachers ht
          WHERE ((ht.halaqa_id = sp.halaqa_id) AND (ht.teacher_id = ( SELECT auth.uid() AS uid))))) OR (EXISTS ( SELECT 1
           FROM halaqa_supervisors hs
          WHERE ((hs.halaqa_id = sp.halaqa_id) AND (hs.supervisor_id = ( SELECT auth.uid() AS uid))))))))))) WITH CHECK ((is_admin() OR (EXISTS ( SELECT 1
   FROM (smart_plan_phases p
     JOIN smart_plans sp ON ((sp.id = p.plan_id)))
  WHERE ((p.id = smart_plan_phase_states.phase_id) AND ((sp.created_by = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
           FROM halaqa_teachers ht
          WHERE ((ht.halaqa_id = sp.halaqa_id) AND (ht.teacher_id = ( SELECT auth.uid() AS uid))))) OR (EXISTS ( SELECT 1
           FROM halaqa_supervisors hs
          WHERE ((hs.halaqa_id = sp.halaqa_id) AND (hs.supervisor_id = ( SELECT auth.uid() AS uid)))))))))));
ALTER POLICY smart_plan_phase_states_select_access ON public.smart_plan_phase_states
USING ((is_admin() OR (EXISTS ( SELECT 1
   FROM (smart_plan_phases p
     JOIN smart_plans sp ON ((sp.id = p.plan_id)))
  WHERE ((p.id = smart_plan_phase_states.phase_id) AND ((sp.created_by = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
           FROM halaqa_teachers ht
          WHERE ((ht.halaqa_id = sp.halaqa_id) AND (ht.teacher_id = ( SELECT auth.uid() AS uid))))) OR (EXISTS ( SELECT 1
           FROM halaqa_supervisors hs
          WHERE ((hs.halaqa_id = sp.halaqa_id) AND (hs.supervisor_id = ( SELECT auth.uid() AS uid))))))))) OR (EXISTS ( SELECT 1
   FROM student_guardians sg
  WHERE ((sg.student_id = smart_plan_phase_states.student_id) AND (sg.guardian_id = ( SELECT auth.uid() AS uid)))))));
DROP FUNCTION public.rc2750_can_manage_plan_phase(uuid);

COMMIT;
