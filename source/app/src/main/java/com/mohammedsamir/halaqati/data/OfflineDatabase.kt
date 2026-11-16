package com.mohammedsamir.halaqati.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import org.json.JSONObject

/** RC7 single source of truth for durable, account-scoped offline state. */
@Entity(
    tableName = "cache_entries",
    primaryKeys = ["ownerUserId", "cacheKey"],
    indices = [Index(value = ["ownerUserId", "savedAt"])],
)
data class CacheEntryEntity(
    val ownerUserId: String,
    val cacheKey: String,
    val jsonValue: String,
    val savedAt: Long,
)

@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["ownerUserId", "dedupe"], unique = true),
        Index(value = ["ownerUserId", "state", "id"]),
    ],
)
data class OutboxEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUserId: String,
    val method: String,
    val path: String,
    val body: String?,
    val createdAt: Long,
    val attempts: Int = 0,
    val dedupe: String,
    val state: String = OutboxState.PENDING,
    val lastError: String? = null,
    val baseUpdatedAt: String? = null,
    val entityTable: String? = null,
    val entityId: String? = null,
    val serverJson: String? = null,
    val forceLocal: Boolean = false,
)

object OutboxState {
    const val PENDING = "pending"
    const val BLOCKED = "blocked"
    const val CONFLICT = "conflict"
}

@Entity(
    tableName = "students_local",
    primaryKeys = ["ownerUserId", "id"],
    indices = [
        Index(value = ["ownerUserId", "fullName"]),
        Index(value = ["ownerUserId", "status"]),
        Index(value = ["ownerUserId", "track"]),
        Index(value = ["ownerUserId", "halaqaId"]),
        Index(value = ["ownerUserId", "categoryId"]),
    ],
)
data class StudentLocalEntity(
    val ownerUserId: String,
    val id: String,
    val fullName: String,
    val status: String = "active",
    val track: String = "general_hifz",
    val categoryId: String? = null,
    val halaqaId: String? = null,
    val primaryTeacherId: String? = null,
    val guardianName: String? = null,
    val nationalId: String? = null,
    val phone: String? = null,
    val dateOfBirth: String? = null,
    val schoolGrade: String? = null,
    val guardianPhone: String? = null,
    val guardianEmail: String? = null,
    val gender: String? = null,
    val enrollmentDate: String? = null,
    val programName: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val photoPath: String? = null,
    val targetPagesWeekly: Double? = null,
    val targetReviewPagesWeekly: Double? = null,
    val remoteUpdatedAt: String? = null,
    val localUpdatedAt: Long = System.currentTimeMillis(),
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val rawJson: String,
) {
    fun asJson(): JSONObject = runCatching { JSONObject(rawJson) }.getOrElse { JSONObject() }
        .put("id", id)
        .put("full_name", fullName)
        .put("status", status)
        .put("track_code", track)
        .putNullable("category_id", categoryId)
        .putNullable("halaqa_id", halaqaId)
        .putNullable("primary_teacher_id", primaryTeacherId)
        .putNullable("national_id", nationalId)
        .putNullable("phone", phone)
        .putNullable("date_of_birth", dateOfBirth)
        .putNullable("school_grade", schoolGrade)
        .putNullable("guardian_phone", guardianPhone)
        .putNullable("guardian_email", guardianEmail)
        .putNullable("gender", gender)
        .putNullable("enrollment_date", enrollmentDate)
        .putNullable("program_name", programName)
        .putNullable("address", address)
        .putNullable("notes", notes)
        .putNullable("photo_path", photoPath)
        .also { json ->
            // guardian_name never existed in the production students schema. Older local
            // drafts may still contain it in rawJson; strip it before every queued write.
            json.remove("guardian_name")
            targetPagesWeekly?.let { json.put("target_pages_weekly", it) }
            targetReviewPagesWeekly?.let { json.put("target_review_pages_weekly", it) }
            remoteUpdatedAt?.let { json.put("updated_at", it) }
        }

    companion object {
        fun fromJson(owner: String, json: JSONObject, dirty: Boolean = false): StudentLocalEntity {
            fun nullable(key: String): String? {
                if (!json.has(key) || json.isNull(key)) return null
                return json.optString(key).trim().takeUnless { it.isBlank() || it == "null" }
            }
            fun nullableDouble(key: String): Double? = if (!json.has(key) || json.isNull(key)) null else json.optDouble(key)
            return StudentLocalEntity(
                ownerUserId = owner,
                id = json.optString("id"),
                fullName = json.optString("full_name").ifBlank { "طالب" },
                status = json.optString("status", "active"),
                track = StudentTrackRules.canonicalStored(json.optString("track_code", "general_hifz")),
                categoryId = nullable("category_id"),
                halaqaId = nullable("halaqa_id"),
                primaryTeacherId = nullable("primary_teacher_id"),
                guardianName = nullable("guardian_name"),
                nationalId = nullable("national_id"),
                phone = nullable("phone"),
                dateOfBirth = nullable("date_of_birth"),
                schoolGrade = nullable("school_grade"),
                guardianPhone = nullable("guardian_phone"),
                guardianEmail = nullable("guardian_email"),
                gender = nullable("gender"),
                enrollmentDate = nullable("enrollment_date"),
                programName = nullable("program_name"),
                address = nullable("address"),
                notes = nullable("notes"),
                photoPath = nullable("photo_path"),
                targetPagesWeekly = nullableDouble("target_pages_weekly"),
                targetReviewPagesWeekly = nullableDouble("target_review_pages_weekly"),
                remoteUpdatedAt = nullable("updated_at"),
                dirty = dirty,
                rawJson = json.toString(),
            )
        }
    }
}

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @androidx.room.PrimaryKey val ownerUserId: String,
    val lastPushAt: Long? = null,
    val lastPullAt: Long? = null,
    val lastError: String? = null,
)

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun put(row: CacheEntryEntity)
    @Query("SELECT * FROM cache_entries WHERE ownerUserId=:owner AND cacheKey=:key LIMIT 1") fun get(owner: String, key: String): CacheEntryEntity?
    @Query("DELETE FROM cache_entries WHERE ownerUserId=:owner AND cacheKey LIKE :prefix || '%'") fun invalidate(owner: String, prefix: String)
    @Query("DELETE FROM cache_entries WHERE ownerUserId=:owner") fun clearOwner(owner: String)
}

@Dao
interface OutboxDao {
    @Query("DELETE FROM outbox WHERE ownerUserId=:owner AND dedupe=:dedupe") fun deleteDedupe(owner: String, dedupe: String)
    @Query("SELECT COUNT(*) FROM outbox WHERE ownerUserId=:owner AND state='pending' AND dedupe IN (:dedupes)")
    fun countPendingDedupes(owner: String, dedupes: List<String>): Int
    @Query("DELETE FROM outbox WHERE ownerUserId=:owner AND state='pending' AND dedupe IN (:dedupes)")
    fun deletePendingDedupes(owner: String, dedupes: List<String>)
    @Transaction
    fun deletePendingDedupesIfComplete(owner: String, dedupes: List<String>): Boolean {
        val unique = dedupes.distinct()
        if (unique.isEmpty() || countPendingDedupes(owner, unique) != unique.size) return false
        deletePendingDedupes(owner, unique)
        return true
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insert(row: OutboxEntity): Long
    @Transaction fun replaceDedupe(row: OutboxEntity): Long { deleteDedupe(row.ownerUserId, row.dedupe); return insert(row) }
    @Query("SELECT COUNT(*) FROM outbox WHERE ownerUserId=:owner AND state=:state") fun count(owner: String, state: String): Int
    @Query("SELECT * FROM outbox WHERE ownerUserId=:owner AND state='pending' ORDER BY id LIMIT :limit") fun pending(owner: String, limit: Int): List<OutboxEntity>
    /** RC31: creation time of the oldest change that is still waiting to be uploaded (null when nothing waits). */
    @Query("SELECT MIN(createdAt) FROM outbox WHERE ownerUserId=:owner AND state='pending'") fun oldestPendingAt(owner: String): Long?
    @Query("SELECT * FROM outbox WHERE ownerUserId=:owner ORDER BY CASE state WHEN 'conflict' THEN 0 WHEN 'blocked' THEN 1 ELSE 2 END,id LIMIT :limit") fun snapshot(owner: String, limit: Int): List<OutboxEntity>
    @Query("SELECT * FROM outbox WHERE ownerUserId=:owner AND id=:id LIMIT 1") fun byId(owner: String, id: Long): OutboxEntity?
    @Query("DELETE FROM outbox WHERE ownerUserId=:owner AND id=:id") fun delete(owner: String, id: Long)
    @Query("UPDATE outbox SET attempts=attempts+1, lastError=:error WHERE ownerUserId=:owner AND id=:id") fun incrementAttempts(owner: String, id: Long, error: String)
    @Query("UPDATE outbox SET state='blocked', lastError=:error WHERE ownerUserId=:owner AND id=:id") fun block(owner: String, id: Long, error: String)
    @Query("UPDATE outbox SET state='conflict', lastError=:error, serverJson=:serverJson WHERE ownerUserId=:owner AND id=:id") fun conflict(owner: String, id: Long, error: String, serverJson: String?)
    @Query("UPDATE outbox SET state='pending', lastError=NULL, serverJson=NULL WHERE ownerUserId=:owner AND id=:id") fun retry(owner: String, id: Long)
    @Query("UPDATE outbox SET state='pending', forceLocal=1, lastError=NULL, serverJson=NULL WHERE ownerUserId=:owner AND id=:id") fun forceLocal(owner: String, id: Long)
}

@Dao
interface StudentLocalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsert(row: StudentLocalEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertAll(rows: List<StudentLocalEntity>)
    @Query("SELECT * FROM students_local WHERE ownerUserId=:owner AND deleted=0 ORDER BY fullName COLLATE NOCASE") fun all(owner: String): List<StudentLocalEntity>
    @Query("SELECT * FROM students_local WHERE ownerUserId=:owner AND id=:id LIMIT 1") fun byId(owner: String, id: String): StudentLocalEntity?
    @Query("SELECT * FROM students_local WHERE ownerUserId=:owner AND dirty=1") fun dirty(owner: String): List<StudentLocalEntity>
    @Query("DELETE FROM students_local WHERE ownerUserId=:owner AND dirty=0") fun clearClean(owner: String)
    @Query("DELETE FROM students_local WHERE ownerUserId=:owner AND id=:id AND dirty=0") fun deleteClean(owner: String, id: String)
    @Query("DELETE FROM students_local WHERE ownerUserId=:owner AND id=:id") fun deleteAny(owner: String, id: String)
    @Query("UPDATE students_local SET dirty=0, deleted=0, remoteUpdatedAt=:updatedAt, rawJson=:rawJson, localUpdatedAt=:now WHERE ownerUserId=:owner AND id=:id")
    fun markClean(owner: String, id: String, updatedAt: String?, rawJson: String, now: Long = System.currentTimeMillis())
    @Query("UPDATE students_local SET deleted=1, dirty=1, localUpdatedAt=:now WHERE ownerUserId=:owner AND id=:id") fun tombstone(owner: String, id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE ownerUserId=:owner LIMIT 1") fun get(owner: String): SyncMetaEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun put(row: SyncMetaEntity)
}

@Database(
    entities = [CacheEntryEntity::class, OutboxEntity::class, StudentLocalEntity::class, SyncMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HalaqatiOfflineDatabase : RoomDatabase() {
    abstract fun cache(): CacheDao
    abstract fun outbox(): OutboxDao
    abstract fun students(): StudentLocalDao
    abstract fun syncMeta(): SyncMetaDao

    companion object {
        @Volatile private var instance: HalaqatiOfflineDatabase? = null
        fun get(context: Context): HalaqatiOfflineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HalaqatiOfflineDatabase::class.java,
                "halaqati-rc7.db",
            ).build().also { instance = it }
        }
    }
}

private fun JSONObject.putNullable(key: String, value: String?): JSONObject = apply {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}
