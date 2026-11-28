@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.theme.calmContentSize
import com.mohammedsamir.halaqati.ui.theme.motionEnter
import com.mohammedsamir.halaqati.ui.theme.motionExit
import com.mohammedsamir.halaqati.ui.scope.*
import com.mohammedsamir.halaqati.ui.components.HalaqaScopeBar
import com.mohammedsamir.halaqati.ui.components.WorkKindToggle
import com.mohammedsamir.halaqati.ui.components.rememberWorkKind
import com.mohammedsamir.halaqati.ui.components.MonthSectionHeader
import com.mohammedsamir.halaqati.ui.components.HalaqaOverviewCard
import com.mohammedsamir.halaqati.ui.components.PlanHistoryLine
import com.mohammedsamir.halaqati.plans.PlanHistoryRules
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import com.mohammedsamir.halaqati.ui.components.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.PlanSnapshot
import com.mohammedsamir.halaqati.data.SafetyScoring
import com.mohammedsamir.halaqati.data.StudentGuardianRules
import com.mohammedsamir.halaqati.data.StudentEnrollmentRules
import com.mohammedsamir.halaqati.data.OfflineQueue
import com.mohammedsamir.halaqati.model.*
import com.mohammedsamir.halaqati.quran.QuranCore
import com.mohammedsamir.halaqati.quran.QuranLocation
import com.mohammedsamir.halaqati.quran.QuranMetadata
import com.mohammedsamir.halaqati.quran.TilawahCategory
import com.mohammedsamir.halaqati.quran.TilawahMark
import com.mohammedsamir.halaqati.quran.TilawahMarks
import com.mohammedsamir.halaqati.quran.TilawahMarkJson
import com.mohammedsamir.halaqati.quran.TilawahDraftJson
import kotlinx.coroutines.sync.withLock
import com.mohammedsamir.halaqati.quran.QuranRangeRequest
import com.mohammedsamir.halaqati.quran.QuranRangeResult
import com.mohammedsamir.halaqati.quran.QuranSelection
import com.mohammedsamir.halaqati.quran.QuranTraversalDirection
import com.mohammedsamir.halaqati.quran.RecitationParityRules
import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import com.mohammedsamir.halaqati.plans.SmartPlanUiRules
import com.mohammedsamir.halaqati.plans.SmartPlanHealthRules
import com.mohammedsamir.halaqati.plans.SmartPlanActivityRules
import com.mohammedsamir.halaqati.ui.components.*
import com.mohammedsamir.halaqati.ui.sessions.SessionWorkflowRules
import com.mohammedsamir.halaqati.ui.sessions.EducationScoreRules
import com.mohammedsamir.halaqati.ui.sessions.SessionScheduleRules
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import com.mohammedsamir.halaqati.data.hifzSpanEntryOf


@Composable
internal fun RecitationDialog(
    student: Student,
    session: SessionRow,
    close: () -> Unit,
    onSaved: (String) -> Unit,
    existing: RecitationRow? = null,
    prefillStartPage: Int? = null,
    prefillEndPage: Int? = null,
    prefillPages: Double? = null,
    smartPlanId: String? = null,
    smartPlanPhaseId: String? = null,
    smartPlanType: String? = null,
    smartPlanDate: String? = null,
    qcfLineKeys: List<String> = emptyList(),
    activityTypeOverride: String? = null,
) {
    val scope = rememberCoroutineScope()
    val recordKey = existing?.id
    var start by remember(recordKey, prefillStartPage) { mutableStateOf((existing?.startPage ?: prefillStartPage)?.toString().orEmpty()) }
    var end by remember(recordKey, prefillEndPage) { mutableStateOf((existing?.endPage ?: prefillEndPage)?.toString().orEmpty()) }
    var pages by remember(recordKey, prefillPages) { mutableStateOf((existing?.pages ?: prefillPages)?.let(::plainNumber) ?: "0") }
    var mistakes by remember(recordKey) { mutableStateOf((existing?.mistakes ?: 0).toString()) }
    var prompts by remember(recordKey) { mutableStateOf((existing?.prompts ?: 0).toString()) }
    var note by remember(recordKey) { mutableStateOf(existing?.teacherNote.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showQuranPicker by remember { mutableStateOf(false) }
    // interactive mushaf: the marks live here while the form is open; a new range starts a fresh session (as on the web)
    var showMushaf by remember { mutableStateOf(false) }
    var mushafEpoch by remember { mutableIntStateOf(0) }
    var mushafTick by remember { mutableIntStateOf(0) }
    var mushafStartPage by remember(recordKey) { mutableIntStateOf(1) }
    val mushafSession = remember(student.id, session.id, recordKey, mushafEpoch) { com.mohammedsamir.halaqati.quran.MushafEngine.Session(mushafStartPage) }
    var selectedQuranRange by remember(student.id, session.id, recordKey) {
        mutableStateOf(initialRecitationRange(existing, student))
    }
    var effectiveQcfLineKeys by remember(recordKey, qcfLineKeys) { mutableStateOf(existing?.qcfLineKeys?.takeIf { it.isNotEmpty() } ?: qcfLineKeys) }
    var resolvedActivityType by remember(student.id, session.id, activityTypeOverride, recordKey) {
        mutableStateOf(initialRecitationActivity(existing, student, session, activityTypeOverride))
    }

    val existingTilawah = existing?.tilawahAssessment
    val baseTilawahRevision = existingTilawah?.optString("revision")?.takeIf { it.isNotBlank() && it != "null" }
    // Do not silently convert old assessments to the new scoring contract during an edit.
    val legacyTilawah = existing != null && (existingTilawah?.optInt("version", 1) ?: 1) < 2
    var tilawahMarkOriginal by remember(student.id, session.id, recordKey) { mutableStateOf(existingTilawah?.optJSONArray("marks")) }
    var tilawahMarks by remember(student.id, session.id, recordKey) { mutableStateOf(TilawahMarkJson.decode(tilawahMarkOriginal)) }
    val draftOwner = remember(student.id, session.id, recordKey) { AppGraph.repo.currentUserId() }
    var tilawahClientId by remember(student.id, session.id, recordKey) { mutableStateOf(existing?.id ?: java.util.UUID.randomUUID().toString()) }
    fun initialManual(key: String, category: TilawahCategory): Int {
        val assessment = existingTilawah ?: return 0
        return assessment.optInt("manual_$key", (assessment.optInt(key, 0) - TilawahMarks.count(tilawahMarks, category)).coerceAtLeast(0)).coerceAtLeast(0)
    }
    val jaliWordsState = remember(student.id, session.id, recordKey) { mutableIntStateOf(initialManual("jali_words", TilawahCategory.WORD)) }
    var jaliWords by jaliWordsState
    val jaliLettersState = remember(student.id, session.id, recordKey) { mutableIntStateOf(initialManual("jali_letters", TilawahCategory.LETTER)) }
    var jaliLetters by jaliLettersState
    val jaliHarakatState = remember(student.id, session.id, recordKey) { mutableIntStateOf(initialManual("jali_harakat", TilawahCategory.HARAKAH)) }
    var jaliHarakat by jaliHarakatState
    val totalJaliWords = TilawahMarks.total(jaliWords, tilawahMarks, TilawahCategory.WORD)
    val totalJaliLetters = TilawahMarks.total(jaliLetters, tilawahMarks, TilawahCategory.LETTER)
    val totalJaliHarakat = TilawahMarks.total(jaliHarakat, tilawahMarks, TilawahCategory.HARAKAH)
    val markedMinorCount = tilawahMarks.filterNot { it.category.major }.sumOf { it.occurrences }
    val wardReviewedState = remember(recordKey) { mutableStateOf(existingTilawah?.takeIf { it.has("ward_reviewed") && !it.isNull("ward_reviewed") }?.optBoolean("ward_reviewed")) }
    var wardReviewed by wardReviewedState
    val correctionAdherenceState = remember(recordKey) { mutableStateOf(existingTilawah?.takeIf { it.has("correction_adherence") && !it.isNull("correction_adherence") }?.optInt("correction_adherence")?.coerceIn(0, 5)) }
    var correctionAdherence by correctionAdherenceState
    val issueOptions = TilawahCategory.entries.map { it.key to it.label }
    var previousIssues by remember(student.id, session.id, recordKey) {
        val arr = existingTilawah?.optJSONArray("previous_issue_keys")
        mutableStateOf(if (arr == null) emptySet<String>() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.toSet())
    }
    var previousLoading by remember(student.id, session.id, recordKey) { mutableStateOf(false) }
    var previousUnavailable by remember(student.id, session.id, recordKey) { mutableStateOf(existingTilawah?.optString("previous_lookup_status") == "unavailable") }
    val previousAcknowledgedState = remember(student.id, session.id, recordKey) { mutableStateOf(existing != null) }
    var previousAcknowledged by previousAcknowledgedState
    val previousRetryState = remember(student.id, session.id, recordKey) { mutableIntStateOf(0) }
    var previousRetry by previousRetryState
    var previousRecordId by remember(student.id, session.id, recordKey) { mutableStateOf(existingTilawah?.optString("previous_record_id")?.takeIf { it.isNotBlank() && it != "null" }) }
    var previousMarks by remember(student.id, session.id, recordKey) { mutableStateOf(TilawahMarkJson.decode(existingTilawah?.optJSONArray("previous_marks"))) }
    val followedRefsState = remember(student.id, session.id, recordKey) { mutableStateOf(TilawahDraftJson.strings(existingTilawah?.optJSONArray("followed_refs")).toSet()) }
    var followedRefs by followedRefsState
    val previousRefs = previousRecordId?.let { TilawahMarks.previousRefs(it, previousMarks) }.orEmpty()
    val majorErrors = if (legacyTilawah) 0 else (totalJaliWords.toLong() + totalJaliLetters + totalJaliHarakat).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    val tilawahRiwayahState = remember(recordKey) { mutableStateOf(existingTilawah?.optString("riwayah", "hafs")?.takeIf { it.isNotBlank() } ?: "hafs") }
    var tilawahRiwayah by tilawahRiwayahState
    val lahnJaliState = remember(recordKey) { mutableStateOf(existingTilawah?.optInt("lahn_jali", 0)?.coerceAtLeast(0) ?: 0) }
    var lahnJali by lahnJaliState
    val lahnKhafiState = remember(recordKey) { mutableStateOf(existingTilawah?.optInt("lahn_khafi", 0)?.coerceAtLeast(0) ?: 0) }
    var lahnKhafi by lahnKhafiState
    val makharijRatingState = remember(recordKey) { mutableStateOf(initialTilawahRating(existingTilawah, "makharij")) }
    var makharijRating by makharijRatingState
    val tajweedRatingState = remember(recordKey) { mutableStateOf(initialTilawahRating(existingTilawah, "tajweed")) }
    var tajweedRating by tajweedRatingState
    val waqfRatingState = remember(recordKey) { mutableStateOf(initialTilawahRating(existingTilawah, "waqf_ibtida")) }
    var waqfRating by waqfRatingState
    val fluencyRatingState = remember(recordKey) { mutableStateOf(initialTilawahRating(existingTilawah, "fluency")) }
    var fluencyRating by fluencyRatingState
    val tilawahIssuesState = remember(recordKey) {
        val arr = existingTilawah?.optJSONArray("manual_issue_keys") ?: existingTilawah?.optJSONArray("issue_keys")
        mutableStateOf(
            if (arr == null) emptySet() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.toSet()
        )
    }
    var tilawahIssues by tilawahIssuesState

    val manualPageRange = remember(start, end, resolvedActivityType, student.track, effectiveQcfLineKeys) {
        if (effectiveQcfLineKeys.isNotEmpty()) null else runCatching {
            val a = start.toIntOrNull() ?: return@runCatching null
            val b = end.toIntOrNull() ?: return@runCatching null
            QuranCore.engine().calculate(
                QuranRangeRequest(student.track, resolvedActivityType, QuranSelection.Pages(a, b)),
            )
        }.getOrNull()
    }
    val pageValue = when {
        effectiveQcfLineKeys.isNotEmpty() -> effectiveQcfLineKeys.distinct().size.toDouble() / 15.0
        selectedQuranRange != null -> selectedQuranRange?.exactPages ?: 0.0
        manualPageRange != null -> manualPageRange?.exactPages ?: 0.0
        else -> 0.0
    }
    val mistakeValue = mistakes.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val promptValue = prompts.toIntOrNull()?.coerceAtLeast(0) ?: 0
    // a page the marks put over the per-page limit makes the whole recitation "repeat"; without a mushaf session an old entry keeps its own flag
    val overLimitPages = remember(mushafTick, mushafSession, recordKey) {
        if (!mushafSession.isEmpty) mushafSession.overLimitPages().map { it.page } else existing?.overLimitPages.orEmpty()
    }
    val safety = SafetyScoring.calculate(pageValue, mistakeValue, promptValue, overLimitPage = overLimitPages.isNotEmpty())
    val evaluation = SafetyScoring.inferredEvaluation(safety, pageValue > 0.0)
    val isTilawah = resolvedActivityType == "tilawah"
    val tilawahBaseScore = ((makharijRating + tajweedRating + waqfRating + fluencyRating) / 20.0) * 100.0
    val tilawahScore = if (legacyTilawah) (tilawahBaseScore - (lahnJali * 8.0) - (lahnKhafi * 3.0)).coerceIn(0.0, 100.0)
        else com.mohammedsamir.halaqati.plans.TilawahScoreV2.score(makharijRating, tajweedRating, waqfRating,
            correctionAdherence.takeIf { previousIssues.isNotEmpty() }, wardReviewed == true, majorErrors)
    val effectiveSafety = if (isTilawah) tilawahScore else safety
    val effectiveEvaluation = if (isTilawah) when {
        tilawahScore >= 90.0 -> "excellent"
        tilawahScore >= 80.0 -> "very_good"
        tilawahScore >= 70.0 -> "good"
        else -> "repeat"
    } else evaluation
    val prefillEndpoints = remember(effectiveQcfLineKeys) {
        effectiveQcfLineKeys.takeIf { it.isNotEmpty() }?.let { keys ->
            runCatching { QuranCore.engine().endpointsForLineKeys(keys) }.getOrNull()
        }
    }
    val mushafRangeStartPage = selectedQuranRange?.startPage
        ?: prefillEndpoints?.first?.let { runCatching { QuranMetadata.pageFor(it.surah, it.ayah) }.getOrNull() }
        ?: manualPageRange?.startPage ?: start.toIntOrNull()?.takeIf { it in 1..604 }
    val visibleRangeLabel = selectedQuranRange?.displayLabel ?: prefillEndpoints?.let { (a, b) ->
        RecitationParityRules.quranRangeLabel(a.surah, a.ayah, b.surah, b.ayah, start.toIntOrNull(), end.toIntOrNull())
    } ?: manualPageRange?.displayLabel

    // One durable form per account/session/student/record. The exact range lives in the same payload.
    val markRange = selectedQuranRange ?: manualPageRange
    val markStart = markRange?.start ?: prefillEndpoints?.first
    val markEnd = markRange?.end ?: prefillEndpoints?.second
    val tilawahDraftKey = if (isTilawah && !legacyTilawah)
        "tilawah-form-v2:${session.halaqaId}:${session.id}:${student.id}:${existing?.id ?: "new"}:" else null
    var loadedDraftKey by remember(student.id, session.id, recordKey) { mutableStateOf<String?>(null) }
    var draftReadError by remember(student.id, session.id, recordKey) { mutableStateOf<String?>(null) }
    var draftWriteError by remember(student.id, session.id, recordKey) { mutableStateOf<String?>(null) }
    var draftRetry by remember(student.id, session.id, recordKey) { mutableIntStateOf(0) }
    var restoredDraft by remember(student.id, session.id, recordKey) { mutableStateOf(false) }
    var restoredHistory by remember(student.id, session.id, recordKey) { mutableStateOf(false) }
    val draftMutex = remember(student.id, session.id, recordKey) { kotlinx.coroutines.sync.Mutex() }
    val formReady = tilawahDraftKey != null && tilawahDraftKey == loadedDraftKey && draftReadError == null
    val draftReady = formReady && pageValue > 0.0 && markStart != null && markEnd != null

    fun insideTilawahRange(surah: Int, ayah: Int, page: Int): Boolean {
        val pgStart = mushafRangeStartPage ?: return false
        val pgEnd = markRange?.endPage ?: end.toIntOrNull() ?: return false
        if (page !in minOf(pgStart, pgEnd)..maxOf(pgStart, pgEnd)) return false
        val id = runCatching { QuranMetadata.globalAyah(surah, ayah) }.getOrNull() ?: return false
        fun contains(a: com.mohammedsamir.halaqati.quran.QuranLocation, b: com.mohammedsamir.halaqati.quran.QuranLocation): Boolean {
            val x = QuranMetadata.globalAyah(a.surah, a.ayah)
            val y = QuranMetadata.globalAyah(b.surah, b.ayah)
            return id in minOf(x, y)..maxOf(x, y)
        }
        val segments = markRange?.orderedSegments.orEmpty()
        if (segments.isNotEmpty()) return segments.any { contains(it.start, it.end) }
        return markStart != null && markEnd != null && contains(markStart, markEnd)
    }

    fun formPayload(marks: List<TilawahMark> = tilawahMarks): JSONObject = JSONObject()
        .put("client_id", tilawahClientId).put("base_revision", baseTilawahRevision ?: JSONObject.NULL)
        .put("start", start).put("end", end)
        .put("range", markRange?.let(TilawahDraftJson::encodeRange) ?: JSONObject.NULL)
        .put("lines", org.json.JSONArray(effectiveQcfLineKeys))
        .put("marks", TilawahMarkJson.encode(marks, tilawahMarkOriginal))
        .put("words", jaliWords).put("letters", jaliLetters).put("harakat", jaliHarakat)
        .put("makharij", makharijRating).put("tajweed", tajweedRating).put("waqf", waqfRating)
        .put("ward", wardReviewed ?: JSONObject.NULL).put("adherence", correctionAdherence ?: JSONObject.NULL)
        .put("note", note).put("issues", org.json.JSONArray(tilawahIssues.sorted()))
        .put("history_ready", !previousLoading && (restoredHistory || previousAcknowledged || existing != null))
        .put("history_unavailable", previousUnavailable).put("history_ack", previousAcknowledged)
        .put("previous_record_id", previousRecordId ?: JSONObject.NULL)
        .put("previous_issues", org.json.JSONArray(previousIssues.sorted()))
        .put("previous_marks", TilawahMarkJson.encode(previousMarks))
        .put("followed_refs", org.json.JSONArray(followedRefs.sorted()))

    suspend fun persistForm(payload: String) {
        val owner = requireNotNull(draftOwner)
        val key = requireNotNull(tilawahDraftKey)
        draftMutex.withLock {
            withContext(Dispatchers.IO) {
                check(AppGraph.repo.currentUserId() == owner) { "تغير الحساب" }
                AppGraph.cache.put(owner, key, payload)
            }
        }
    }

    LaunchedEffect(tilawahDraftKey, draftOwner, draftRetry) {
        val key = tilawahDraftKey ?: return@LaunchedEffect
        loadedDraftKey = null
        draftReadError = null
        restoredDraft = false
        restoredHistory = false
        try {
            val owner = requireNotNull(draftOwner) { "لا يوجد حساب نشط" }
            val cached = withContext(Dispatchers.IO) {
                check(AppGraph.repo.currentUserId() == owner) { "تغير الحساب" }
                AppGraph.cache.get(owner, key)?.let { JSONObject(it) }
            }
            if (cached != null) {
                val rows = cached.getJSONArray("marks")
                val restoredId = java.util.UUID.fromString(cached.getString("client_id")).toString()
                require(existing == null || restoredId == existing.id) { "معرف المسودة غير مطابق" }
                require(cached.optString("base_revision").takeIf { it.isNotBlank() && it != "null" } == baseTilawahRevision) {
                    "تغير تقييم الخادم منذ إنشاء المسودة. المسودة محفوظة؛ راجع مركز المزامنة قبل الاستبدال."
                }
                val range = cached.optJSONObject("range")?.let(TilawahDraftJson::decodeRange)
                start = cached.optString("start"); end = cached.optString("end")
                selectedQuranRange = range
                effectiveQcfLineKeys = range?.qcfLineKeys ?: TilawahDraftJson.strings(cached.optJSONArray("lines"))
                tilawahMarkOriginal = rows
                tilawahMarks = TilawahMarkJson.decode(rows)
                tilawahClientId = restoredId
                jaliWords = cached.optInt("words").coerceAtLeast(0)
                jaliLetters = cached.optInt("letters").coerceAtLeast(0)
                jaliHarakat = cached.optInt("harakat").coerceAtLeast(0)
                makharijRating = cached.optInt("makharij", 5).coerceIn(1,5)
                tajweedRating = cached.optInt("tajweed", 5).coerceIn(1,5)
                waqfRating = cached.optInt("waqf", 5).coerceIn(1,5)
                wardReviewed = cached.takeUnless { it.isNull("ward") }?.optBoolean("ward")
                correctionAdherence = cached.takeUnless { it.isNull("adherence") }?.optInt("adherence")?.coerceIn(0,5)
                note = cached.optString("note")
                tilawahIssues = TilawahDraftJson.strings(cached.optJSONArray("issues")).toSet()
                previousRecordId = cached.optString("previous_record_id").takeIf { it.isNotBlank() && it != "null" }
                previousIssues = TilawahDraftJson.strings(cached.optJSONArray("previous_issues")).toSet()
                previousMarks = TilawahMarkJson.decode(cached.optJSONArray("previous_marks"))
                followedRefs = TilawahDraftJson.strings(cached.optJSONArray("followed_refs")).toSet()
                previousUnavailable = cached.optBoolean("history_unavailable")
                previousAcknowledged = cached.optBoolean("history_ack")
                restoredHistory = cached.optBoolean("history_ready")
                restoredDraft = true
            }
            loadedDraftKey = key
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { draftReadError = e.message ?: "تعذر استعادة المسودة" }
    }

    LaunchedEffect(formReady, previousRetry, resolvedActivityType) {
        if (!formReady || existing != null || !isTilawah || (restoredHistory && previousRetry == 0)) return@LaunchedEffect
        previousLoading = true
        previousUnavailable = false
        previousAcknowledged = false
        try {
            val validIds = listOf(student.id, session.halaqaId).all { id ->
                runCatching { java.util.UUID.fromString(id).toString().equals(id, ignoreCase = true) }.getOrDefault(false)
            }
            LocalDate.parse(session.date)
            require(validIds)
            val scoped = withContext(Dispatchers.IO) {
                AppGraph.repo.previousTilawahBatch(session.id, listOf(student.id), forceRefresh = previousRetry > 0)[student.id]
            }
            requireNotNull(scoped) { "تعذر التحقق من التصحيحات السابقة" }
            val id = scoped.optString("record_id").takeIf { it.isNotBlank() && it != "null" }
            val assessment = scoped.optJSONObject("assessment")
            val marks = TilawahMarkJson.decode(assessment?.optJSONArray("marks"))
            val issues = TilawahDraftJson.strings(assessment?.optJSONArray("issue_keys")).toSet() + marks.filterNot { it.corrected }.map { it.category.key }
            if (previousRecordId != id || previousIssues != issues) {
                correctionAdherence = null
                followedRefs = emptySet()
            }
            previousRecordId = id
            previousIssues = issues
            previousMarks = marks
            restoredHistory = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { previousUnavailable = true }
        finally { previousLoading = false }
    }

    val localPayload = if (formReady) formPayload().toString() else null
    LaunchedEffect(localPayload, saving) {
        if (localPayload == null || saving) return@LaunchedEffect
        try {
            persistForm(localPayload)
            draftWriteError = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { draftWriteError = e.message ?: "تعذر حفظ المسودة محليًا" }
    }
    fun closeKeepingDraft() {
        if (saving) return
        if (!isTilawah || legacyTilawah) { close(); return }
        if (!formReady) { close(); return }
        saving = true
        scope.launch {
            try {
                persistForm(formPayload().toString())
                close()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { draftWriteError = e.message ?: "تعذر حفظ المسودة محليًا" }
            finally { saving = false }
        }
    }

    val recitationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { closeKeepingDraft() },
        sheetState = recitationSheetState,
        modifier = Modifier.fillMaxHeight(0.97f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (existing == null) "تسميع ${student.fullName}" else "تعديل تسميع ${student.fullName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${session.date} · ${sessionTypeAr(session.type)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { closeKeepingDraft() }, enabled = !saving, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { StateNotice(it, kind = "error") }
                draftWriteError?.let { StateNotice(it, kind = "error") }
                draftReadError?.let { StateNotice(it, kind = "error"); TextButton(onClick = { draftRetry++ }) { Text("إعادة محاولة استعادة المسودة") } }
                if (restoredDraft) Text("تمت استعادة نموذج التقييم المحلي ونطاق القراءة.", style = MaterialTheme.typography.bodySmall)
                Text("نوع التسميع", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

                val recitationTypes = (if (com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(student.track)) listOf(
                    Triple("tathbit", "تثبيت", Icons.Default.Replay), Triple("tilawah", "تلاوة", Icons.Default.AutoStories),
                ) else listOf(
                    Triple("new_hifz", "حفظ", Icons.Default.MenuBook), Triple("review_near", "مراجعة", Icons.Default.Replay),
                    Triple("sard", "سرد", Icons.Default.RecordVoiceOver), Triple("tilawah", "تلاوة", Icons.Default.AutoStories),
                )).plus(Triple("test", "اختبار", Icons.Default.CheckCircle)).filter { option ->
                    option.first == resolvedActivityType || SessionWorkflowRules.matchesRecitation(session.type, option.first)
                }
                ResponsiveChipGrid(items = recitationTypes, minItemWidth = 105.dp, maxColumns = 4) { option, itemModifier ->
                    val (value, label, icon) = option
                    ProFilterChip(
                        selected = resolvedActivityType == value,
                        onClick = {
                            if (value != resolvedActivityType && tilawahMarks.isEmpty() && !isTilawah) {
                                resolvedActivityType = value; selectedQuranRange = null; effectiveQcfLineKeys = emptyList(); pages = "0"
                            }
                        },
                        label = label,
                        modifier = itemModifier.fillMaxWidth(),
                        showCheckIcon = false,
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .85f), modifier = Modifier.size(42.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("المقرر القرآني", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(visibleRangeLabel ?: "لم يتم اختيار نطاق بعد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3)
                            }
                        }
                        if (pageValue > 0.0001) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                StatusPill("${plainNumber(pageValue)} صفحة", kind = "info")
                                selectedQuranRange?.let {
                                    StatusPill("${it.ayahCount} آية", kind = "info")
                                }
                            }
                        }
                        Button(
                            onClick = { showQuranPicker = true },
                            enabled = tilawahMarks.isEmpty() && !saving && (!isTilawah || legacyTilawah || formReady),
                            modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                        ) {
                            Icon(Icons.Default.AutoStories, contentDescription = "اختيار النطاق من المصحف")
                            Spacer(Modifier.width(7.dp))
                            Text(if (visibleRangeLabel == null) "اختيار المقرر من المصحف" else "تعديل المقرر")
                        }
                        if (isTilawah && !legacyTilawah && pageValue > 0.0001) {
                            if (restoredDraft) Text("العلامات المستعادة: ${tilawahMarks.size}", style = MaterialTheme.typography.bodySmall)
                            draftReadError?.let { msg ->
                                Text(msg, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { draftRetry++ }) { Text("إعادة محاولة استعادة المسودة") }
                            }
                            if (tilawahMarks.isNotEmpty()) Text("امحُ العلامات أولاً إذا أردت تغيير نطاق القراءة.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(
                                onClick = { mushafStartPage = mushafRangeStartPage ?: 1; mushafEpoch++; showMushaf = true },
                                enabled = draftReady && !saving,
                                modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text("تعليم أخطاء التلاوة والتصحيحات")
                            }
                        } else if (!isTilawah && pageValue > 0.0001) {
                            OutlinedButton(
                                onClick = { mushafStartPage = mushafRangeStartPage ?: 1; if (mushafSession.isEmpty) mushafEpoch++; showMushaf = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text("تعليم الأخطاء والتلقين على المصحف")
                            }
                        }
                    }
                }

                if (isTilawah) {
                    TilawahAssessmentCard(
                        legacyTilawah = legacyTilawah,
                        tilawahMarks = tilawahMarks,
                        markedMinorCount = markedMinorCount,
                        totalJaliWords = totalJaliWords,
                        totalJaliLetters = totalJaliLetters,
                        totalJaliHarakat = totalJaliHarakat,
                        previousLoading = previousLoading,
                        previousUnavailable = previousUnavailable,
                        existing = existing,
                        previousIssues = previousIssues,
                        issueOptions = issueOptions,
                        previousMarks = previousMarks,
                        previousRefs = previousRefs,
                        tilawahRiwayahState = tilawahRiwayahState,
                        lahnJaliState = lahnJaliState,
                        lahnKhafiState = lahnKhafiState,
                        makharijRatingState = makharijRatingState,
                        tajweedRatingState = tajweedRatingState,
                        waqfRatingState = waqfRatingState,
                        fluencyRatingState = fluencyRatingState,
                        jaliWordsState = jaliWordsState,
                        jaliLettersState = jaliLettersState,
                        jaliHarakatState = jaliHarakatState,
                        previousRetryState = previousRetryState,
                        previousAcknowledgedState = previousAcknowledgedState,
                        followedRefsState = followedRefsState,
                        correctionAdherenceState = correctionAdherenceState,
                        wardReviewedState = wardReviewedState,
                        tilawahIssuesState = tilawahIssuesState,
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            RecitationCounter("الأخطاء", mistakeValue, { mistakes = (mistakeValue - 1).coerceAtLeast(mushafSession.totals().mistakes).toString() }, { mistakes = (mistakeValue + 1).toString() })
                        }
                        Box(Modifier.weight(1f)) {
                            RecitationCounter("التلقين", promptValue, { prompts = (promptValue - 1).coerceAtLeast(mushafSession.totals().prompts).toString() }, { prompts = (promptValue + 1).toString() })
                        }
                    }
                }

                Surface(
                    color = if (effectiveEvaluation == "repeat") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (effectiveEvaluation == "repeat") Icons.Default.WarningAmber else Icons.Default.Verified, contentDescription = null)
                            Column {
                                Text(if (isTilawah) "التقييم العام للتلاوة" else "تقييم تلقائي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(recitationEvaluationAr(effectiveEvaluation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (isTilawah) "النتيجة" else "السلامة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${plainNumber(effectiveSafety)}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة المحفظ (اختياري)") },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { closeKeepingDraft() },
                    enabled = !saving,
                    modifier = Modifier.weight(.8f).heightIn(min = 52.dp),
                ) { Text(if (isTilawah && !legacyTilawah) "حفظ مسودة" else "إلغاء") }
                Button(
                    enabled = !saving && pageValue > 0.0 && (!isTilawah || legacyTilawah || (wardReviewed != null && !previousLoading && (!previousUnavailable || previousAcknowledged) && (previousIssues.isEmpty() || correctionAdherence != null) && draftReady)),
                    modifier = Modifier.weight(1.4f).heightIn(min = 52.dp),
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                if (existing == null && SessionWorkflowRules.usesRecitationTools(session.type))
                                    require(SessionWorkflowRules.matchesRecitation(session.type, resolvedActivityType)) { "نوع التسميع لا يطابق نوع الجلسة" }
                                val startPage = start.toIntOrNull()
                                val endPage = end.toIntOrNull()
                                if (startPage != null && startPage !in 1..604) error("صفحة البداية يجب أن تكون بين 1 و604")
                                if (endPage != null && endPage !in 1..604) error("صفحة النهاية يجب أن تكون بين 1 و604")
                                val authoritativeQuranRange = selectedQuranRange ?: manualPageRange ?: runCatching {
                                    if (startPage != null && endPage != null) {
                                        QuranCore.engine().calculate(
                                            QuranRangeRequest(
                                                track = student.track,
                                                activityType = resolvedActivityType,
                                                selection = QuranSelection.Pages(startPage, endPage),
                                            ),
                                        )
                                    } else null
                                }.getOrElse { throw it }
                                val authoritativeQcfLineKeys = authoritativeQuranRange?.qcfLineKeys ?: effectiveQcfLineKeys
                                val authoritativePages = if (authoritativeQcfLineKeys.isNotEmpty()) authoritativeQcfLineKeys.distinct().size.toDouble() / 15.0 else pageValue
                                val savedId = draftMutex.withLock { withContext(Dispatchers.IO) {
                                    if (isTilawah && !legacyTilawah) {
                                        check(draftReady && AppGraph.repo.currentUserId() == draftOwner) { "تعذر تأكيد الحساب أو المسودة" }
                                        require(tilawahMarks.all { mark -> com.mohammedsamir.halaqati.quran.MushafEngine.parseWordId(mark.wordId)?.let { insideTilawahRange(it.first, it.second, mark.page) } == true }) { "توجد علامات خارج المقرر" }
                                    }
                                    val id = AppGraph.repo.saveRecitation(
                                        RecitationDraft(
                                            studentId = student.id,
                                            sessionId = session.id,
                                            activityType = resolvedActivityType,
                                            startPage = authoritativeQuranRange?.startPage ?: startPage,
                                            endPage = authoritativeQuranRange?.endPage ?: endPage,
                                            pages = authoritativePages,
                                            mistakes = if (isTilawah) (if (legacyTilawah) lahnJali + lahnKhafi else majorErrors) else mistakeValue,
                                            prompts = if (isTilawah) 0 else promptValue,
                                            evaluation = effectiveEvaluation,
                                            notes = note.trim(),
                                            smartPlanId = smartPlanId,
                                            smartPlanPhaseId = smartPlanPhaseId,
                                            smartPlanType = smartPlanType,
                                            smartPlanDate = smartPlanDate,
                                            qcfLineKeys = authoritativeQuranRange?.qcfLineKeys ?: effectiveQcfLineKeys,
                                            inputMethod = authoritativeQuranRange?.inputMethod ?: existing?.inputMethod ?: "pages",
                                            clientId = if (isTilawah && !legacyTilawah) tilawahClientId else existing?.id,
                                            startSurah = authoritativeQuranRange?.start?.surah,
                                            startAyah = authoritativeQuranRange?.start?.ayah,
                                            endSurah = authoritativeQuranRange?.end?.surah,
                                            endAyah = authoritativeQuranRange?.end?.ayah,
                                            ayahsCount = authoritativeQuranRange?.ayahCount ?: 0,
                                            surahsCount = authoritativeQuranRange?.surahCount ?: 0,
                                            selectedSurahs = authoritativeQuranRange?.selectedSurahs.orEmpty(),
                                            selectedJuz = authoritativeQuranRange?.selectedJuz.orEmpty(),
                                            traversalDirection = authoritativeQuranRange?.resolvedTraversal?.name,
                                            quranSegments = authoritativeQuranRange?.orderedSegments?.map { it.compactKey() }.orEmpty(),
                                            tilawahAssessment = if (isTilawah && legacyTilawah) JSONObject(existingTilawah?.toString() ?: "{}")
                                                .put("version", 1)
                                                .put("riwayah", tilawahRiwayah)
                                                .put("lahn_jali", lahnJali)
                                                .put("lahn_khafi", lahnKhafi)
                                                .put("makharij", makharijRating)
                                                .put("tajweed", tajweedRating)
                                                .put("waqf_ibtida", waqfRating)
                                                .put("fluency", fluencyRating)
                                                .put("issue_keys", org.json.JSONArray(tilawahIssues.sorted()))
                                                .put("overall_score", kotlin.math.round(tilawahScore * 10.0) / 10.0)
                                                .put("manual_teacher_assessment", true)
                                            else if (isTilawah) JSONObject(existingTilawah?.toString() ?: "{}")
                                                .put("version", 2)
                                                .put("revision", java.util.UUID.randomUUID().toString())
                                                .put("base_revision", baseTilawahRevision ?: JSONObject.NULL)
                                                .put("riwayah", "hafs")
                                                .put("lahn_jali", majorErrors)
                                                .put("lahn_khafi", markedMinorCount)
                                                .put("jali_words", totalJaliWords)
                                                .put("jali_letters", totalJaliLetters)
                                                .put("jali_harakat", totalJaliHarakat)
                                                .put("manual_jali_words", jaliWords)
                                                .put("manual_jali_letters", jaliLetters)
                                                .put("manual_jali_harakat", jaliHarakat)
                                                .put("marks", TilawahMarkJson.encode(tilawahMarks, tilawahMarkOriginal))
                                                .put("makharij", makharijRating)
                                                .put("tajweed", tajweedRating)
                                                .put("waqf_ibtida", waqfRating)
                                                .put("ward_reviewed", wardReviewed == true)
                                                .put("correction_adherence", correctionAdherence.takeIf { previousIssues.isNotEmpty() } ?: JSONObject.NULL)
                                                .put("previous_lookup_status", if (previousUnavailable) "unavailable" else "available")
                                                .put("previous_issue_keys", org.json.JSONArray(previousIssues.sorted()))
                                                .put("previous_record_id", previousRecordId ?: JSONObject.NULL)
                                                .put("previous_marks", TilawahMarkJson.encode(previousMarks))
                                                .put("followed_refs", org.json.JSONArray(followedRefs.sorted()))
                                                .put("manual_issue_keys", org.json.JSONArray(tilawahIssues.sorted()))
                                                .put("issue_keys", org.json.JSONArray((tilawahIssues + tilawahMarks.filterNot { it.corrected }.map { it.category.key }).sorted()))
                                                .put("overall_score", kotlin.math.round(tilawahScore * 10.0) / 10.0)
                                                .put("manual_teacher_assessment", true)
                                            else null,
                                            overLimitPages = if (isTilawah) emptyList() else overLimitPages,
                                            createdAt = existing?.createdAt,
                                        ),
                                    )
                                    // The durable existing outbox owns the complete record now. Clearing a
                                    // local draft is best effort; the stable client id makes retry idempotent.
                                    if (isTilawah && !legacyTilawah && draftOwner != null && tilawahDraftKey != null)
                                        runCatching { AppGraph.cache.invalidate(draftOwner, tilawahDraftKey) }
                                    id
                                } }
                                onSaved(savedId)
                            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                                error = e.message ?: "تعذر حفظ التسميع"
                            } finally { saving = false }
                        }
                    },
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (saving) "جارٍ الحفظ…" else if (existing == null) "حفظ التسميع" else "حفظ التعديل")
                }
            }
        }
    }

    if (showQuranPicker) {
        QuranRangePickerDialog(
            initialStartPage = start.toIntOrNull(),
            initialEndPage = end.toIntOrNull(),
            initialStartSurah = selectedQuranRange?.start?.surah ?: prefillEndpoints?.first?.surah,
            initialStartAyah = selectedQuranRange?.start?.ayah ?: prefillEndpoints?.first?.ayah,
            initialEndSurah = selectedQuranRange?.end?.surah ?: prefillEndpoints?.second?.surah,
            initialEndAyah = selectedQuranRange?.end?.ayah ?: prefillEndpoints?.second?.ayah,
            track = student.track,
            activityType = resolvedActivityType,
            onDismiss = { showQuranPicker = false },
            onConfirm = { selected ->
                selectedQuranRange = selected
                effectiveQcfLineKeys = selected.qcfLineKeys
                start = selected.startPage?.toString().orEmpty()
                end = selected.endPage?.toString().orEmpty()
                pages = plainNumber(selected.exactPages)
                showQuranPicker = false
                mushafEpoch++; mushafTick++   // a new range starts a fresh mushaf session
            },
        )
    }
    if (showMushaf) {
        com.mohammedsamir.halaqati.ui.screens.MushafMarkingDialog(
            session = mushafSession,
            rangeStartPage = mushafRangeStartPage,
            rangeEndPage = selectedQuranRange?.endPage ?: end.toIntOrNull(),
            counters = { if (isTilawah) majorErrors to markedMinorCount else mistakeValue to promptValue },
            score = { MushafScore(effectiveSafety, recitationEvaluationAr(effectiveEvaluation), pageValue) },
            inRange = { s, a, pg ->
                if (isTilawah) insideTilawahRange(s, a, pg) else {
                val rng = selectedQuranRange
                val rs = rng?.start; val re = rng?.end
                when {
                    rng != null && rng.selectedSurahs.isNotEmpty() -> s in rng.selectedSurahs
                    rs != null && re != null -> (s > rs.surah || (s == rs.surah && a >= rs.ayah)) && (s < re.surah || (s == re.surah && a <= re.ayah))
                    else -> {
                        val sp = start.toIntOrNull(); val ep = end.toIntOrNull()
                        sp != null && ep != null && pg in kotlin.math.min(sp, ep)..kotlin.math.max(sp, ep)
                    }
                }
                }
            },
            onMark = { dm, dp ->
                mistakes = ((mistakes.toIntOrNull() ?: 0) + dm).coerceAtLeast(0).toString()
                prompts = ((prompts.toIntOrNull() ?: 0) + dp).coerceAtLeast(0).toString()
                mushafTick++
            },
            onDismiss = { showMushaf = false; mushafTick++ },
            tilawahMarks = tilawahMarks.takeIf { isTilawah && !legacyTilawah },
            previousTilawahRefs = previousRefs,
            onTilawahMarksChange = { next ->
                check(draftReady)
                val rows = TilawahMarkJson.encode(next, tilawahMarkOriginal)
                persistForm(formPayload(next).toString())
                tilawahMarkOriginal = rows
                tilawahMarks = next
            },
        )
    }
}

@Composable
private fun TilawahAssessmentCard(
    legacyTilawah: Boolean,
    tilawahMarks: List<TilawahMark>,
    markedMinorCount: Int,
    totalJaliWords: Int,
    totalJaliLetters: Int,
    totalJaliHarakat: Int,
    previousLoading: Boolean,
    previousUnavailable: Boolean,
    existing: RecitationRow?,
    previousIssues: Set<String>,
    issueOptions: List<Pair<String, String>>,
    previousMarks: List<TilawahMark>,
    previousRefs: Map<String, String>,
    tilawahRiwayahState: MutableState<String>,
    lahnJaliState: MutableState<Int>,
    lahnKhafiState: MutableState<Int>,
    makharijRatingState: MutableState<Int>,
    tajweedRatingState: MutableState<Int>,
    waqfRatingState: MutableState<Int>,
    fluencyRatingState: MutableState<Int>,
    jaliWordsState: MutableState<Int>,
    jaliLettersState: MutableState<Int>,
    jaliHarakatState: MutableState<Int>,
    previousRetryState: MutableState<Int>,
    previousAcknowledgedState: MutableState<Boolean>,
    followedRefsState: MutableState<Set<String>>,
    correctionAdherenceState: MutableState<Int?>,
    wardReviewedState: MutableState<Boolean?>,
    tilawahIssuesState: MutableState<Set<String>>,
) {
    var tilawahRiwayah by tilawahRiwayahState
    var lahnJali by lahnJaliState
    var lahnKhafi by lahnKhafiState
    var makharijRating by makharijRatingState
    var tajweedRating by tajweedRatingState
    var waqfRating by waqfRatingState
    var fluencyRating by fluencyRatingState
    var jaliWords by jaliWordsState
    var jaliLetters by jaliLettersState
    var jaliHarakat by jaliHarakatState
    var previousRetry by previousRetryState
    var previousAcknowledged by previousAcknowledgedState
    var followedRefs by followedRefsState
    var correctionAdherence by correctionAdherenceState
    var wardReviewed by wardReviewedState
    var tilawahIssues by tilawahIssuesState
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .46f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("تقييم التلاوة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text("تقييم يدوي من المحفّظ؛ لا يعتمد على كشف آلي لأحكام التجويد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (legacyTilawah) {
                Text("الرواية / القراءة", style = MaterialTheme.typography.labelLarge)
                ResponsiveChipGrid(
                    items = listOf("hafs" to "حفص عن عاصم", "warsh" to "ورش عن نافع", "qalun" to "قالون عن نافع"),
                    minItemWidth = 118.dp,
                    maxColumns = 2,
                ) { pair, itemModifier ->
                    ProFilterChip(
                        selected = tilawahRiwayah == pair.first,
                        onClick = { tilawahRiwayah = pair.first },
                        label = pair.second,
                        modifier = itemModifier.fillMaxWidth(),
                        showCheckIcon = false,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        RecitationCounter("لحن جلي", lahnJali, { lahnJali = (lahnJali - 1).coerceAtLeast(0) }, { lahnJali += 1 })
                    }
                    Box(Modifier.weight(1f)) {
                        RecitationCounter("لحن خفي", lahnKhafi, { lahnKhafi = (lahnKhafi - 1).coerceAtLeast(0) }, { lahnKhafi += 1 })
                    }
                }
                TilawahRatingRow("المخارج والصفات", makharijRating) { makharijRating = it }
                TilawahRatingRow("أحكام التجويد", tajweedRating) { tajweedRating = it }
                TilawahRatingRow("الوقف والابتداء", waqfRating) { waqfRating = it }
                TilawahRatingRow("الطلاقة والترتيل", fluencyRating) { fluencyRating = it }
            } else {
                Text("أخطاء اللحن الجلي (علامات المصحف + الإضافات اليدوية)", style = MaterialTheme.typography.labelLarge)
                if (tilawahMarks.isNotEmpty()) Text("على المصحف: ${tilawahMarks.size} علامة · اللحن الخفي: $markedMinorCount. العدادات أدناه تضيف أخطاء غير معلّمة فقط.", style = MaterialTheme.typography.bodySmall)
                ResponsiveChipGrid(items = listOf("word", "letter", "harakah"), minItemWidth = 104.dp, maxColumns = 3) { key, itemModifier ->
                    Box(itemModifier.fillMaxWidth()) {
                        when (key) {
                            "word" -> RecitationCounter("الكلمات", totalJaliWords, { jaliWords = (jaliWords - 1).coerceAtLeast(0) }, { if (jaliWords < Int.MAX_VALUE) jaliWords++ })
                            "letter" -> RecitationCounter("الحروف", totalJaliLetters, { jaliLetters = (jaliLetters - 1).coerceAtLeast(0) }, { if (jaliLetters < Int.MAX_VALUE) jaliLetters++ })
                            else -> RecitationCounter("الحركات", totalJaliHarakat, { jaliHarakat = (jaliHarakat - 1).coerceAtLeast(0) }, { if (jaliHarakat < Int.MAX_VALUE) jaliHarakat++ })
                        }
                    }
                }
                TilawahRatingRow("المخارج والصفات", makharijRating) { makharijRating = it }
                TilawahRatingRow("أحكام التجويد", tajweedRating) { tajweedRating = it }
                TilawahRatingRow("الوقف والابتداء", waqfRating) { waqfRating = it }
                Text("التصحيحات السابقة", style = MaterialTheme.typography.labelLarge)
                when {
                    previousLoading -> Text("جارٍ تحميل التصحيحات السابقة…", style = MaterialTheme.typography.bodySmall)
                    previousUnavailable -> {
                        Text("تعذر التحقق من التصحيحات السابقة. لا يعني ذلك عدم وجود تصحيحات.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (existing == null) {
                            TextButton(onClick = { previousRetry++ }) { Text("إعادة المحاولة") }
                            ProFilterChip(previousAcknowledged, { previousAcknowledged = !previousAcknowledged }, "متابعة دون التحقق", Modifier.fillMaxWidth(), showCheckIcon = true)
                        }
                    }
                    previousIssues.isEmpty() -> Text("لم تتوفر تصحيحات سابقة في البيانات المتاحة · لا يدخل المعيار في النسبة", style = MaterialTheme.typography.bodySmall)
                    else -> {
                        Text(previousIssues.map { key -> issueOptions.firstOrNull { it.first == key }?.second ?: key }.joinToString("، "), style = MaterialTheme.typography.bodyMedium)
                        previousMarks.filterNot { it.corrected }.forEach { mark ->
                            val ref = previousRefs[mark.markId]
                            if (ref != null) {
                                ProFilterChip(ref in followedRefs, { followedRefs = if (ref in followedRefs) followedRefs - ref else followedRefs + ref },
                                    "صُحح: ${mark.category.label} · ص ${mark.page} · ${mark.wordId}", Modifier.fillMaxWidth(), showCheckIcon = true)
                            }
                        }
                        Text("مدى الالتزام بتصحيحات الشيخ السابقة", style = MaterialTheme.typography.labelLarge)
                        if (correctionAdherence == null) TextButton(onClick = { correctionAdherence = 5 }) { Text("بدء تقييم الالتزام") }
                        correctionAdherence?.let { rating ->
                            TilawahRatingRow("الالتزام", rating, minValue = 0) { correctionAdherence = it }
                        }
                    }
                }
                Text("هل راجع الطالب الورد؟", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProFilterChip(wardReviewed == true, { wardReviewed = true }, "نعم", Modifier.weight(1f), showCheckIcon = true)
                    ProFilterChip(wardReviewed == false, { wardReviewed = false }, "لا", Modifier.weight(1f), showCheckIcon = true)
                }
            }
            Text("مواضع تحتاج متابعة", style = MaterialTheme.typography.labelLarge)
            val tilawahIssueOptions = listOf(
                "madd" to "المدود",
                "ghunnah" to "الغنة",
                "ikhfa_idgham" to "الإخفاء والإدغام",
                "qalqalah" to "القلقلة",
                "makharij" to "المخارج",
                "waqf" to "الوقف والابتداء",
            )
            ResponsiveChipGrid(items = tilawahIssueOptions, minItemWidth = 126.dp, maxColumns = 2) { pair, itemModifier ->
                ProFilterChip(
                    selected = pair.first in tilawahIssues,
                    onClick = { tilawahIssues = if (pair.first in tilawahIssues) tilawahIssues - pair.first else tilawahIssues + pair.first },
                    label = pair.second,
                    modifier = itemModifier.fillMaxWidth(),
                    showCheckIcon = false,
                )
            }
        }
    }
}

private fun initialRecitationRange(existing: RecitationRow?, student: Student): QuranRangeResult? =
    existing?.let { row ->
    if (row.qcfLineKeys.isNotEmpty() || row.startSurah == null || row.startAyah == null || row.endSurah == null || row.endAyah == null) null
    else runCatching {
        QuranCore.engine().calculate(
            QuranRangeRequest(
                track = student.track,
                activityType = row.activityType,
                selection = QuranSelection.Ayahs(
                    com.mohammedsamir.halaqati.quran.QuranLocation(row.startSurah, row.startAyah),
                    com.mohammedsamir.halaqati.quran.QuranLocation(row.endSurah, row.endAyah),
                ),
            ),
        )
    }.getOrNull()
}

private fun initialRecitationActivity(existing: RecitationRow?, student: Student, session: SessionRow, activityTypeOverride: String?): String =
    existing?.activityType?.let { old -> runCatching { com.mohammedsamir.halaqati.data.TrackActivityPolicy.recitationActivityFor(student.track, old) }.getOrDefault(old) }
    ?: activityTypeOverride?.let { runCatching { com.mohammedsamir.halaqati.data.TrackActivityPolicy.recitationActivityFor(student.track, it) }.getOrNull() }
    ?: when (session.type) {
    "sard" -> "sard"
    "review" -> if (com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(student.track)) "tathbit" else "review_near"
    "test" -> "test"
    "tajweed", "tilawah" -> "tilawah"
    else -> if (com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(student.track)) "tathbit" else "new_hifz"
}

private fun initialTilawahRating(assessment: JSONObject?, key: String): Int =
    assessment?.optInt(key, 5)?.coerceIn(1, 5) ?: 5
