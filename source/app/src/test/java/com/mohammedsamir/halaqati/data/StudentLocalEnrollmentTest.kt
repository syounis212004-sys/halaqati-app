package com.mohammedsamir.halaqati.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StudentLocalEnrollmentTest {
    @Test fun explicitNoTrackSurvivesRoomSerialization() {
        val raw = JSONObject().put("id", "student").put("full_name", "طالب")
            .put("track_code", JSONObject.NULL)
            .put("target_pages_weekly", 0).put("target_review_pages_weekly", 0)
        val restored = StudentLocalEntity.fromJson("owner", raw, dirty = true).asJson()
        assertTrue(restored.has("track_code"))
        assertTrue(restored.isNull("track_code"))
        assertEquals(0.0, restored.getDouble("target_pages_weekly"), 0.0)
        assertEquals(0.0, restored.getDouble("target_review_pages_weekly"), 0.0)
    }
    @Test fun realMemorizationTrackIsStillPreserved() {
        val raw = JSONObject().put("id", "student").put("full_name", "طالب")
            .put("track_code", "tathbit")
        assertEquals("tathbit", StudentLocalEntity.fromJson("owner", raw).asJson().getString("track_code"))
    }
}
