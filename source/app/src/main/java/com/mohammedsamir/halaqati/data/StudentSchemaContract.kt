package com.mohammedsamir.halaqati.data

import org.json.JSONObject

/** Production students-table write contract.
 * Keep account relationships in student_guardians, never ad-hoc columns on students. */
object StudentSchemaContract {
    val writableColumns: Set<String> = setOf(
        "id", "full_name", "halaqa_id", "primary_teacher_id", "date_of_birth", "phone",
        "guardian_phone", "enrollment_date", "status", "photo_path", "notes",
        "target_pages_weekly", "target_review_pages_weekly", "guardian_email", "gender",
        "national_id", "program_name", "school_grade", "address", "category_id", "track_code",
    )

    fun sanitize(input: JSONObject): JSONObject = JSONObject().also { output ->
        writableColumns.forEach { key ->
            if (input.has(key)) output.put(key, input.opt(key))
        }
    }

    fun containsUnsupported(input: JSONObject): Boolean = input.keys().asSequence().any { it !in writableColumns }
}
