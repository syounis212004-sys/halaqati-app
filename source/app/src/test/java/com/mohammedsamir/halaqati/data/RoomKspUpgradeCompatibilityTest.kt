package com.mohammedsamir.halaqati.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Open a real on-disk legacy KAPT schema with the KSP-generated implementation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomKspUpgradeCompatibilityTest {
    private inline fun withDb(db: HalaqatiOfflineDatabase, block: (HalaqatiOfflineDatabase) -> Unit) {
        try { block(db) } finally { db.close() }
    }

    @Test fun originalVersionOneDatabaseRetainsPendingBlockedAndOtherAccountRows() {
        val context = RuntimeEnvironment.getApplication()
        val name = "ksp-legacy-upgrade-fixture.db"
        val file = context.getDatabasePath(name)
        context.deleteDatabase(name)
        file.parentFile?.mkdirs()
        val schemaPath = "com.mohammedsamir.halaqati.data.HalaqatiOfflineDatabase/1.json"
        val schema = requireNotNull(javaClass.classLoader?.getResourceAsStream(schemaPath)).bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        assertEquals(1, schema.getInt("version"))
        assertEquals("d792bf2c454b8220073d218edc360386", schema.getString("identityHash"))
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val e = entities.getJSONObject(i)
                    val table = e.getString("tableName")
                    db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = e.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                db.version = 1
                db.execSQL("INSERT INTO cache_entries(ownerUserId,cacheKey,jsonValue,savedAt) VALUES('a','draft','legacy-json',1)")
                db.execSQL("INSERT INTO outbox(ownerUserId,method,path,body,createdAt,attempts,dedupe,state,forceLocal) VALUES('a','POST','/fixture','pending-json',1,0,'pending','pending',0)")
                db.execSQL("INSERT INTO outbox(ownerUserId,method,path,body,createdAt,attempts,dedupe,state,forceLocal) VALUES('a','POST','/fixture','blocked-json',2,1,'blocked','blocked',0)")
                db.execSQL("INSERT INTO outbox(ownerUserId,method,path,body,createdAt,attempts,dedupe,state,forceLocal) VALUES('b','POST','/fixture','other-json',3,0,'pending','pending',0)")
            }
            withDb(Room.databaseBuilder(context, HalaqatiOfflineDatabase::class.java, name)
                .addMigrations(*HalaqatiMigrations.ALL).allowMainThreadQueries().build()) { db ->
                    assertEquals("legacy-json", db.cache().get("a","draft")?.jsonValue)
                    assertNull(db.cache().get("b","draft"))
                    val queue = OfflineQueue(db)
                    assertEquals("pending-json", queue.all("a").single().body)
                    assertEquals("blocked-json", queue.snapshot("a").single { it.blocked }.body)
                    assertEquals("other-json", queue.all("b").single().body)
                    queue.add("a","POST","/fixture","new-json","new")
                }
            withDb(Room.databaseBuilder(context, HalaqatiOfflineDatabase::class.java, name)
                .addMigrations(*HalaqatiMigrations.ALL).allowMainThreadQueries().build()) { db ->
                    val queue = OfflineQueue(db)
                    assertEquals(2, queue.size("a"))
                    assertEquals(1, queue.blockedSize("a"))
                    assertEquals(1, queue.size("b"))
                }
        } finally { context.deleteDatabase(name) }
    }
}
