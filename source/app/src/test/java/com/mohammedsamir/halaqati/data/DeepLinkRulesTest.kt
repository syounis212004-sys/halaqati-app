package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkRulesTest {
    private val studentId = "11111111-2222-3333-4444-555555555555"

    @Test fun parsesStudentNotificationLink() {
        assertEquals(DeepLinkRules.Destination("profile", studentId), DeepLinkRules.navigation("student:$studentId"))
    }

    @Test fun parsesCustomSchemeStudentLink() {
        assertEquals(DeepLinkRules.Destination("profile", studentId), DeepLinkRules.navigation("halaqati://student/$studentId"))
    }

    @Test fun parsesDirectFeatureLink() {
        assertEquals(DeepLinkRules.Destination("reports"), DeepLinkRules.navigation("halaqati://reports"))
        assertEquals(DeepLinkRules.Destination("notifications"), DeepLinkRules.navigation("notifications"))
    }

    @Test fun doesNotStealAuthCallback() {
        assertNull(DeepLinkRules.navigation("com.mohammedsamir.halaqati://auth/callback#access_token=x&type=recovery"))
        assertNull(DeepLinkRules.navigation("halaqati://auth/callback"))
    }

    @Test fun rejectsUnknownRoute() {
        assertNull(DeepLinkRules.navigation("halaqati://not-a-route"))
    }

    @Test fun rejectsMalformedStudentIds() {
        assertNull(DeepLinkRules.navigation("student:not-a-uuid"))
        assertNull(DeepLinkRules.navigation("halaqati://student/not-a-uuid"))
    }
}
