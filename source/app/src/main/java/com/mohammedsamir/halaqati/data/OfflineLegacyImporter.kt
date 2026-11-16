package com.mohammedsamir.halaqati.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * One-shot RC6 -> RC7 safety importer. Old databases are left untouched so rollback is possible.
 * Legacy unscoped/quarantined queue rows are deliberately not replayed.
 */
object OfflineLegacyImporter {
    private const val MARKER = ".rc7-legacy-sqlite-imported-v1"

    fun importIfNeeded(context: Context, db: HalaqatiOfflineDatabase) {
        val marker = java.io.File(context.filesDir, MARKER)
        if (marker.exists()) return
        runCatching { importCache(context, db) }
            .onFailure { Log.w("RC7Migration", "Legacy cache import failed", it) }
        runCatching { importQueue(context, db) }
            .onFailure { Log.w("RC7Migration", "Legacy outbox import failed", it) }
        runCatching { marker.writeText("ok") }
    }

    private fun importCache(context: Context, db: HalaqatiOfflineDatabase) {
        val file = context.getDatabasePath("halaqati-cache.db")
        if (!file.exists()) return
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { old ->
            old.rawQuery("SELECT owner_user_id,cache_key,json_value,saved_at FROM cache_entries", null).use { c ->
                while (c.moveToNext()) {
                    val owner = c.getString(0).orEmpty()
                    val key = c.getString(1).orEmpty()
                    val json = c.getString(2).orEmpty()
                    if (owner.isNotBlank() && key.isNotBlank() && json.isNotBlank()) {
                        db.cache().put(CacheEntryEntity(owner, key, json, c.getLong(3)))
                    }
                }
            }
        }
    }

    private fun importQueue(context: Context, db: HalaqatiOfflineDatabase) {
        val file = context.getDatabasePath("halaqati-offline.db")
        if (!file.exists()) return
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { old ->
            val columns = old.rawQuery("PRAGMA table_info(q)", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(1)) }
            }
            if ("owner_user_id" !in columns) return
            old.rawQuery(
                "SELECT owner_user_id,method,path,body,created,attempts,dedupe,blocked,last_error FROM q WHERE owner_user_id IS NOT NULL AND owner_user_id<>''",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val owner = c.getString(0).orEmpty()
                    val path = c.getString(2).orEmpty()
                    if (owner.isBlank() || path.isBlank()) continue
                    val body = if (c.isNull(3)) null else c.getString(3)
                    val dedupe = if (c.isNull(6)) null else c.getString(6)
                    val fallbackKey = "${c.getString(1)}:$path:${body.orEmpty().hashCode()}"
                    db.outbox().replaceDedupe(
                        OutboxEntity(
                            ownerUserId = owner,
                            method = c.getString(1),
                            path = path,
                            body = body,
                            createdAt = c.getLong(4),
                            attempts = c.getInt(5),
                            dedupe = dedupe ?: fallbackKey,
                            state = if (c.getInt(7) == 1) OutboxState.BLOCKED else OutboxState.PENDING,
                            lastError = if (c.isNull(8)) null else c.getString(8),
                        ),
                    )
                }
            }
        }
    }
}
