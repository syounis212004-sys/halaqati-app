package com.mohammedsamir.halaqati.data

import androidx.room.migration.Migration

/**
 * Room migrations for halaqati-rc7.db. Version 1 is the baseline (app/schemas/.../1.json is committed).
 * Add a `Migration(from, to)` here with every schema bump instead of relying on destructive fallback,
 * so cached rows and the pending outbox survive an app update.
 */
object HalaqatiMigrations {
    val ALL: Array<Migration> = emptyArray()
}
