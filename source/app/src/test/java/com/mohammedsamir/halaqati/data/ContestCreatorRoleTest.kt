package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ContestCreatorRoleTest {
    @Test fun creatorRoleComesFromTheServerAndDefaultsToDonor() {
        val base = org.json.JSONObject("{\"id\":\"1\",\"name\":\"ن\",\"title\":\"ع\",\"conditions\":\"ش\",\"reward\":\"م\",\"status\":\"approved\"}")
        assertEquals("donor", Contest.fromJson(base).creatorRole)
        assertEquals("teacher", Contest.fromJson(base.put("creator_role", "teacher")).creatorRole)
    }
}
