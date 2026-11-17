@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import com.mohammedsamir.halaqati.ui.theme.motionEnter
import com.mohammedsamir.halaqati.ui.theme.motionExit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import com.mohammedsamir.halaqati.R
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.PatternLevel
import com.mohammedsamir.halaqati.ui.components.nourPattern
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens
import com.mohammedsamir.halaqati.ui.auth.NativeGoogleSignIn
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    error: String? = null,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String, String) -> Unit,
    onGoogleIdToken: (String, String) -> Unit,
    onGoogleOAuthUrl: () -> String,
    onResetPassword: (String, (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var requestedRole by remember { mutableStateOf("guardian") }
    var resetDialog by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var googleBusy by remember { mutableStateOf(false) }
    var brandVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { brandVisible = true }

    Surface(color = MaterialTheme.colorScheme.background) {
        // RC27.20: modern sign-in: a full-width brand band with a large logo, then the form, then the credits.
        // The whole page scrolls (short/old phones) and follows the keyboard.
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .nourPattern(PatternLevel.Hero)
                    .statusBarsPadding(),
            ) {
                com.mohammedsamir.halaqati.ui.components.HalaqatiAmbient(
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = 90.dp).size(260.dp),
                    onBand = true,
                    strength = 0.18f,
                )
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(
                        visible = brandVisible,
                        enter = motionEnter(fadeIn(tween(260)) + scaleIn(tween(360), initialScale = .86f)),
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(34.dp)).background(Color.White.copy(alpha = .12f)).padding(8.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.launcher_legacy),
                                contentDescription = "شعار حلقاتي",
                                modifier = Modifier.size(104.dp).clip(RoundedCornerShape(27.dp)),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("حلقاتي", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "أدوات متابعة القرآن الكريم",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = .9f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        "الحلقات · التسميع · الخطط · ولي الأمر",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = .7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Text(
                        if (register) "إنشاء حساب" else "أهلًا بك في حلقاتي",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (register) "أدخل بياناتك وسيظهر طلبك لمسؤول الحلقة للاعتماد." else "رحلة القرآن، أقرب إليك. استخدم حسابك للوصول إلى الحلقة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    error?.let { StateNotice(it, kind = "error") }
                    info?.let {
                        StateNotice(
                            it,
                            kind = when {
                                it.startsWith("تعذر") -> "error"
                                it.startsWith("تم إلغاء") -> "warning"
                                else -> "success"
                            },
                        )
                    }

                    if (register) {
                        Text(
                            "إنشاء الحساب لا يمنح الصلاحية مباشرة؛ ينتظر اعتماد المسؤول.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("الاسم الكامل") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("رقم الجوال") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                            singleLine = true,
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                "guardian" to "ولي أمر",
                                "teacher" to "محفّظ الحلقة",
                                "supervisor" to "مشرف الحلقات",
                                "observer" to "مانح",
                            ).forEach { (value, label) ->
                                ProFilterChip(
                                    selected = requestedRole == value,
                                    onClick = { requestedRole = value },
                                    label = label,
                                )
                            }
                        }
                        // RC27.15: each account type shows its own Eastern picture.
                        androidx.compose.runtime.key(requestedRole) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                com.mohammedsamir.halaqati.ui.components.HalaqatiRoleArt(
                                    when (requestedRole) {
                                        "teacher" -> UserRole.TEACHER
                                        "supervisor" -> UserRole.SUPERVISOR
                                        "observer" -> UserRole.OBSERVER
                                        else -> UserRole.GUARDIAN
                                    },
                                    size = 84.dp,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("البريد الإلكتروني") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        singleLine = true,
                    )

                    Button(
                        onClick = {
                            if (register) onRegister(email.trim(), password, name.trim(), phone.trim(), requestedRole)
                            else onLogin(email.trim(), password)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.ActionHeight),
                        shape = RoundedCornerShape(16.dp),
                        enabled = email.isNotBlank() && password.length >= 6 && (!register || name.trim().length >= 3),
                    ) { Text(if (register) "إرسال طلب إنشاء الحساب" else "دخول") }

                    if (!register) {
                        OutlinedButton(
                            onClick = {
                                if (googleBusy) return@OutlinedButton
                                googleBusy = true
                                info = null
                                scope.launch {
                                    runCatching { NativeGoogleSignIn.launch(context) }
                                        .onSuccess { result -> onGoogleIdToken(result.idToken, result.rawNonce) }
                                        .onFailure { e ->
                                            googleBusy = false
                                            if (NativeGoogleSignIn.shouldFallbackToOAuth(e)) {
                                                runCatching {
                                                    NativeGoogleSignIn.launchOAuthFallback(context, onGoogleOAuthUrl())
                                                }.onSuccess {
                                                    info = "اختر حساب Google من النافذة الآمنة لإكمال الدخول."
                                                }.onFailure { fallbackError ->
                                                    info = "تعذر فتح تسجيل Google: ${fallbackError.message ?: "خطأ"}"
                                                }
                                            } else {
                                                info = when {
                                                    e.message?.contains("canceled", ignoreCase = true) == true -> "تم إلغاء اختيار حساب Google."
                                                    else -> "تعذر تسجيل الدخول بحساب Google: ${e.message ?: "خطأ"}"
                                                }
                                            }
                                        }
                                }
                            },
                            enabled = !googleBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.ActionHeight),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (googleBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("جارٍ فتح حسابات Google…")
                            } else {
                                Text("G", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Text("الدخول بحساب Google")
                            }
                        }
                        TextButton(onClick = { resetDialog = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("نسيت كلمة المرور؟")
                        }
                    }

                    TextButton(onClick = { register = !register }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (register) "لدي حساب بالفعل" else "إنشاء حساب جديد")
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Default.Handyman, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("صُنع بأيدٍ شرقية", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        Text("Designed by : Dr. Mohammed Samir Abu Younis", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
            }
    }

    if (resetDialog) {
        var resetEmail by remember(resetDialog) { mutableStateOf(email) }
        HalaqatiAlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("استرجاع كلمة المرور") },
            text = {
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    label = { Text("البريد الإلكتروني") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = resetEmail.contains('@'),
                    onClick = {
                        onResetPassword(resetEmail) { result -> info = result }
                        resetDialog = false
                    },
                ) { Text("إرسال الرابط") }
            },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
fun PendingApprovalScreen(profile: Profile, onRefresh: () -> Unit, onLogout: () -> Unit) {
    // RC27.42: which step the request is at (teacher / supervisor / admin); null on servers without the new SQL.
    var stageText by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(profile.id, profile.approvalStatus, refreshTick) {
        stageText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.mohammedsamir.halaqati.data.AppGraph.repo.mySignupStatusText() }.getOrNull()
        }
    }
    val rejected = profile.approvalStatus == "rejected"
    val blocked = profile.approvalStatus == "approved" && !profile.active
    val roleLabel = when (profile.requestedRole.takeIf { it != UserRole.UNKNOWN } ?: profile.role) {
        UserRole.GUARDIAN -> "ولي أمر"
        UserRole.TEACHER -> "محفّظ الحلقة"
        UserRole.SUPERVISOR -> "مشرف الحلقات"
        UserRole.ADMIN -> "مدير"
        UserRole.OBSERVER -> "مانح"
        else -> "مستخدم"
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(26.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    when {
                        rejected -> "لم تتم الموافقة على الحساب"
                        blocked -> "الحساب موقوف"
                        else -> "الحساب قيد اعتماد المسؤول"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                com.mohammedsamir.halaqati.ui.components.HalaqatiRoleArt(profile.requestedRole.takeIf { it != UserRole.UNKNOWN } ?: profile.role, size = 96.dp)
                Text(profile.fullName)
                Text("نوع الحساب المطلوب: $roleLabel")
                Text(
                    when {
                        rejected -> profile.rejectionReason.ifBlank { "راجع مسؤول النظام لمعرفة سبب رفض الطلب." }
                        blocked -> "تم إيقاف الحساب من إدارة النظام. تواصل مع مسؤول الحلقات."
                        else -> stageText?.let { "$it. الوصول إلى بيانات الحلقة لن يفتح حتى يكتمل الاعتماد." }
                            ?: "تم التحقق من بيانات الدخول، لكن الوصول إلى بيانات الحلقة لن يفتح حتى يعتمد المسؤول الحساب."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!blocked && (profile.requestedRole == UserRole.GUARDIAN || profile.role == UserRole.GUARDIAN) && !rejected) {
                    // RC27.42: a wrong id/birth date or a rejected link can be retried without contacting the admin.
                    AddChildCard(retry = true)
                }
                Button(onClick = { refreshTick++; onRefresh() }) { Text("تحديث حالة الاعتماد") }
                TextButton(onClick = onLogout) { Text("تسجيل الخروج") }
            }
        }
    }
}

@Composable
fun PasswordRecoveryScreen(
    email: String,
    onSubmit: (String, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = password.length >= 6 && password == confirm

    Surface {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(Modifier.widthIn(max = 520.dp)) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("تعيين كلمة مرور جديدة", style = MaterialTheme.typography.headlineSmall)
                    if (email.isNotBlank()) {
                        Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "اكتب كلمة المرور الجديدة مرتين. بعد نجاح التغيير سيتم إنهاء جلسة الاسترجاع ويمكنك تسجيل الدخول بالكلمة الجديدة.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    error?.let { StateNotice(it, kind = "error") }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("كلمة المرور الجديدة") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; error = null },
                        label = { Text("تأكيد كلمة المرور") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = confirm.isNotBlank() && password != confirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (confirm.isNotBlank() && password != confirm) {
                        Text(
                            "كلمتا المرور غير متطابقتين.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = {
                            busy = true
                            error = null
                            onSubmit(password) { message ->
                                busy = false
                                error = message
                            }
                        },
                        enabled = valid && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (busy) "جارٍ الحفظ…" else "حفظ كلمة المرور")
                    }
                    TextButton(
                        onClick = onCancel,
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("إلغاء والعودة لتسجيل الدخول") }
                }
            }
        }
    }
}


internal fun normalizeBirthDate(raw: String): String? {
    val text = raw.trim().map { c -> if (c in '\u0660'..'\u0669') '0' + (c - '\u0660') else c }.joinToString("").replace('/', '-')
    return runCatching { java.time.LocalDate.parse(text).toString() }.getOrNull()
}

/**
 * RC27.42: first screen after a new sign-in (Google or e-mail): confirm the name, pick the account type and, for a
 * guardian, identify the child by national id + date of birth. The request then waits for approval
 * (guardian: the child's teacher, then the system admin · teacher: a supervisor or the admin · supervisor: the admin).
 */
@Composable
fun CompleteSignupScreen(
    profile: Profile,
    onSubmit: (String, String, String, String, String, (String?) -> Unit) -> Unit,
    onLogout: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.fullName.takeUnless { it == "مستخدم جديد" || it.contains("@") }.orEmpty()) }
    var phone by remember { mutableStateOf("") }
    var role by remember {
        mutableStateOf(
            when (profile.requestedRole) {
                UserRole.TEACHER -> "teacher"
                UserRole.SUPERVISOR -> "supervisor"
                UserRole.OBSERVER -> "observer"
                else -> "guardian"
            },
        )
    }
    var nationalId by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val birthDate = remember(birth) { normalizeBirthDate(birth) }
    val valid = name.trim().length >= 3 &&
        (role != "guardian" || (nationalId.count { it.isDigit() } >= 5 && birthDate != null))

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Text("إكمال بيانات الحساب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "أخبرنا من أنت لنرسل طلبك للجهة المناسبة. لن يُفتح الحساب إلا بعد اعتماده.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { StateNotice(it, kind = "error") }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("الاسم الكامل") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الجوال (اختياري)") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    singleLine = true,
                )
                Text("نوع الحساب", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("guardian" to "ولي أمر", "teacher" to "محفّظ الحلقة", "supervisor" to "مشرف الحلقات", "observer" to "مانح").forEach { (value, label) ->
                        ProFilterChip(selected = role == value, onClick = { role = value; error = null }, label = label)
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    com.mohammedsamir.halaqati.ui.components.HalaqatiRoleArt(
                        when (role) { "teacher" -> UserRole.TEACHER; "supervisor" -> UserRole.SUPERVISOR; "observer" -> UserRole.OBSERVER; else -> UserRole.GUARDIAN },
                        size = 72.dp,
                    )
                }
                when (role) {
                    "guardian" -> {
                        Text(
                            "للربط بابنك: اكتب رقم هوية الطالب وتاريخ ميلاده كما هما مسجّلان في الحلقة. يوافق محفّظ الحلقة أولًا ثم يؤكد مسؤول النظام.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = nationalId,
                            onValueChange = { nationalId = it; error = null },
                            label = { Text("رقم هوية الطالب") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                            singleLine = true,
                        )
                        BirthDateField(
                            label = "تاريخ ميلاد الطالب",
                            value = birth,
                            onChange = { birth = it; error = null },
                        )
                    }
                    "teacher" -> Text(
                        "يوافق على طلبك مشرف الحلقات أو مسؤول النظام.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    "observer" -> Text(
                        "حساب المانح لدعم المكرَّمين والكفالات. يوافق على طلبك مسؤول النظام، وتصلك رسالة عند الاعتماد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        "يوافق على طلبك مسؤول النظام.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        busy = true
                        error = null
                        onSubmit(name.trim(), phone.trim(), role, nationalId.trim(), birthDate.orEmpty()) { message ->
                            busy = false
                            error = message
                        }
                    },
                    enabled = valid && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (busy) "جارٍ الإرسال…" else "إرسال الطلب") }
                TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
            }
        }
    }
}

/** RC53: shown instead of the app while an administrator keeps the account frozen. */
@Composable
fun FrozenAccountScreen(profile: Profile, onRefresh: () -> Unit, onLogout: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(26.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("الحساب مجمّد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                com.mohammedsamir.halaqati.ui.components.HalaqatiRoleArt(profile.role, size = 96.dp)
                Text(profile.fullName, fontWeight = FontWeight.SemiBold)
                Text(
                    "تم تجميد هذا الحساب مؤقتًا. تواصل مع مسؤول النظام، وسيُفتح لك الوصول بمجرد فك التجميد.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { tick++; onRefresh() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("تحديث الحالة") }
                TextButton(onClick = onLogout, modifier = Modifier.heightIn(min = 48.dp)) { Text("تسجيل الخروج") }
            }
        }
    }
}
