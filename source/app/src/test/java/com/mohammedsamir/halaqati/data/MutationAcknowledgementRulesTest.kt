package com.mohammedsamir.halaqati.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MutationAcknowledgementRulesTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    @Test fun onlySingleRowPhaseFourPatchesNeedAcknowledgement() {
        assertEquals(id,MutationAcknowledgementRules.expectedPatchId("PATCH","/rest/v1/sessions?id=eq.$id"))
        assertNull(MutationAcknowledgementRules.expectedPatchId("PATCH","/rest/v1/sessions?halaqa_id=eq.$id"))
        assertNull(MutationAcknowledgementRules.expectedPatchId("POST","/rest/v1/rpc/rc_save_education_score_v12"))
        assertNull(MutationAcknowledgementRules.expectedPatchId("DELETE","/rest/v1/sessions?id=eq.$id"))
    }
    @Test fun successWithoutTheExactRowCannotDrainOutbox() {
        assertFalse(MutationAcknowledgementRules.confirmed(id,"[]"))
        assertFalse(MutationAcknowledgementRules.confirmed(id,""))
        assertFalse(MutationAcknowledgementRules.confirmed(id,"[{\"id\":\"other\"}]"))
        assertFalse(MutationAcknowledgementRules.confirmed(id,"[{\"id\":\"$id\"},{\"id\":\"other\"}]"))
        assertTrue(MutationAcknowledgementRules.confirmed(id,"[{\"id\":\"$id\"}]"))
    }
}
