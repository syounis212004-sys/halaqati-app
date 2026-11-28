package com.mohammedsamir.halaqati.data

import android.app.Application
import androidx.room.Room
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EducationOutboxPersistenceTest {
    private val path = "/rest/v1/rpc/rc_save_education_score_v12"
    private val key = "education:session:child:general"
    private fun body(score: Int, bulk: Boolean) = JSONObject().put("p_score",score).put("p_only_unrated",bulk).toString()
    private fun score(row: OfflineQueue.SnapshotItem) = JSONObject(row.body!!).getInt("p_score")
    private fun open(name: String) = Room.databaseBuilder(RuntimeEnvironment.getApplication(), HalaqatiOfflineDatabase::class.java, name)
        .allowMainThreadQueries().build()

    @Test fun bulkDoesNotReplacePendingOrBlockedIndividualAndSurvivesRestart() {
        val context = RuntimeEnvironment.getApplication()
        val name = "education-outbox-persistence-test.db"
        context.deleteDatabase(name)
        try {
            open(name).use { db ->
                val queue = OfflineQueue(db)
                queue.add("account-a","POST",path,body(5,false),key)
                queue.add("account-a","POST",path,body(2,true),key,preserveExisting=true)
                val original = queue.snapshot("account-a").single()
                assertEquals(5,score(original))
                queue.markBlocked(original.id,"account-a","permission denied")
                queue.add("account-a","POST",path,body(1,true),key,preserveExisting=true)
                queue.add("account-b","POST",path,body(3,true),key,preserveExisting=true)
                assertTrue(queue.snapshot("account-a").single().blocked)
            }
            open(name).use { db ->
                val queue = OfflineQueue(db)
                val blocked = queue.snapshot("account-a").single()
                assertTrue(blocked.blocked)
                assertEquals(5,score(blocked))
                assertEquals(3,score(queue.snapshot("account-b").single()))
                queue.retryBlocked(blocked.id,"account-a")
                assertFalse(queue.snapshot("account-a").single().blocked)
                queue.done(blocked.id,"account-b")
                assertEquals(1,queue.size("account-a"))
                assertEquals(1,queue.size("account-b"))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun acknowledgementOfOldBulkCannotDiscardNewerIndividualEdit() {
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HalaqatiOfflineDatabase::class.java)
            .allowMainThreadQueries().build().use { db ->
                val queue = OfflineQueue(db)
                queue.add("account","POST",path,body(2,true),key,preserveExisting=true)
                val inFlight = queue.all("account").single()
                queue.add("account","POST",path,body(4,false),key)
                val manual = queue.snapshot("account").single()
                assertNotEquals(inFlight.id,manual.id)
                queue.done(inFlight.id,"account")
                assertEquals(4,score(queue.snapshot("account").single()))
                queue.done(manual.id,"account")
                assertEquals(0,queue.size("account"))
            }
    }
}
