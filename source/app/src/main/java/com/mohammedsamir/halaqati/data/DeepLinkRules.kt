package com.mohammedsamir.halaqati.data

import java.net.URI
import java.util.UUID

/** Pure navigation parsing for notification/app links. Auth callbacks stay owned by AppViewModel. */
object DeepLinkRules {
    data class Destination(val route: String, val studentId: String? = null)

    private val directRoutes = setOf(
        "home",
        "notifications",
        "community",
        "reports",
        "plans",
        "rankings",
        "certificates",
        "sessions",
        "students",
        "sync_center",
        "settings",
        "guardian_competition",
        "guardian_achievements",
        "guardian_profile",
        "contribute",
        "contests",
    )

    fun navigation(raw: String?): Destination? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null

        if (value.startsWith("student:")) {
            val id = value.substringAfter("student:").substringBefore('?').substringBefore('#').trim()
            return validStudentId(id)?.let { Destination("profile", it) }
        }

        if (value == "profile") return Destination("profile")
        if (value in directRoutes) return Destination(value)
        if (!value.startsWith("halaqati://", ignoreCase = true)) return null

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("halaqati", ignoreCase = true) || uri.userInfo != null || uri.port != -1) return null
        val host = uri.host.orEmpty().lowercase()
        val parts = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }

        if (host == "auth") return null
        if (host == "student") {
            val id = parts.firstOrNull().orEmpty()
            return validStudentId(id)?.let { Destination("profile", it) }
        }
        if (host == "profile") return Destination("profile")
        if (host in directRoutes) return Destination(host)

        val pathRoute = parts.firstOrNull().orEmpty()
        if (pathRoute == "profile") return Destination("profile")
        return pathRoute.takeIf { it in directRoutes }?.let { Destination(it) }
    }

    private fun validStudentId(value: String): String? = runCatching {
        UUID.fromString(value).toString()
    }.getOrNull()
}
