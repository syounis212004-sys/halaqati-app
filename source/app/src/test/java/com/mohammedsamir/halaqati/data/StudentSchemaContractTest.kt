package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentSchemaContractTest {
    @Test fun guardianNameIsNotPartOfStudentsWriteContract() {
        assertFalse("guardian_name" in StudentSchemaContract.writableColumns)
        assertTrue("guardian_email" in StudentSchemaContract.writableColumns)
        assertTrue("guardian_phone" in StudentSchemaContract.writableColumns)
    }

    @Test fun serverOnlyAndUnknownFieldsAreNotWritable() {
        assertFalse("created_at" in StudentSchemaContract.writableColumns)
        assertFalse("updated_at" in StudentSchemaContract.writableColumns)
        assertFalse("mystery" in StudentSchemaContract.writableColumns)
    }
}
