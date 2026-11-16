import com.mohammedsamir.halaqati.ui.presentation.SyncPresentationRules

fun main() {
    check(SyncPresentationRules.title("POST", "/rest/v1/rpc/rc112_transition_plan_students") == "نقل الطلاب بين مراحل الخطة")
    check(SyncPresentationRules.title("POST", "https://example.test/rest/v1/rpc/rc112_reconcile_plan_phase_states?token=private") == "تحديث حالات مراحل الخطة")
    check(SyncPresentationRules.title("PATCH", "/rest/v1/students?id=eq.private") == "تعديل · بيانات الطلاب")
    check(SyncPresentationRules.title("POST", "rpc/future_internal_rpc?private=1") == "عملية بيانات")
    check(SyncPresentationRules.title("DELETE", "/rest/v1/private_table") == "حذف · بيانات التطبيق")
    check(SyncPresentationRules.title("", "") == "بيانات التطبيق")
    println("Sync presentation: 6 checks passed")
}
