@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui

import com.mohammedsamir.halaqati.ui.theme.motionEnter
import com.mohammedsamir.halaqati.ui.theme.motionExit
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mohammedsamir.halaqati.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.mohammedsamir.halaqati.data.DeepLinkRules
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.SyncState
import com.mohammedsamir.halaqati.ui.components.SyncBanner
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.navEnter
import com.mohammedsamir.halaqati.ui.components.navExit
import com.mohammedsamir.halaqati.ui.components.navPopEnter
import com.mohammedsamir.halaqati.ui.components.navPopExit
import com.mohammedsamir.halaqati.ui.components.HalaqatiLoader
import com.mohammedsamir.halaqati.ui.components.GoldenBottomBar
import com.mohammedsamir.halaqati.ui.components.GoldenDrawerContent
import com.mohammedsamir.halaqati.ui.components.GoldenShellActions
import com.mohammedsamir.halaqati.ui.components.LocalGoldenShellActions
import com.mohammedsamir.halaqati.ui.components.roleTitle
import com.mohammedsamir.halaqati.ui.navigation.AppDestination
import com.mohammedsamir.halaqati.ui.navigation.RoleNavigationPolicy
import com.mohammedsamir.halaqati.ui.screens.*
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HalaqatiApp(
    initialDeepLink: String? = null,
    vm: AppViewModel = viewModel(),
) {
    val auth by vm.auth.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()

    LaunchedEffect(initialDeepLink) { vm.handleDeepLink(initialDeepLink) }

    // RC27.16: a short welcome after a fresh sign-in (not after every cold start of an already signed-in session).
    var sawSignedOut by remember { mutableStateOf(false) }
    LaunchedEffect(auth) {
        when (auth) {
            is AuthState.SignedOut, is AuthState.Error -> sawSignedOut = true
            is AuthState.Ready -> if (sawSignedOut) {
                sawSignedOut = false
                com.mohammedsamir.halaqati.ui.components.HalaqatiMotionEvents.welcome()
            }
            else -> Unit
        }
    }

    // RC55: no stretch/bounce at the end of a list or sheet. The Android 12 stretch effect made lists and the phases sheet
    // "shake" up and down when the finger reached the top or the bottom.
    CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollFactory provides null) {
    when (val state = auth) {
        AuthState.Loading -> BrandLoadingScreen()

        AuthState.SignedOut -> AuthScreen(
            onLogin = vm::login,
            onRegister = vm::register,
            onGoogleIdToken = vm::loginWithGoogleIdToken,
            onGoogleOAuthUrl = vm::googleOAuthUrl,
            onResetPassword = vm::requestPasswordReset,
        )
        is AuthState.Error -> AuthScreen(
            error = state.message,
            onLogin = vm::login,
            onRegister = vm::register,
            onGoogleIdToken = vm::loginWithGoogleIdToken,
            onGoogleOAuthUrl = vm::googleOAuthUrl,
            onResetPassword = vm::requestPasswordReset,
        )

        is AuthState.NeedsSetup -> CompleteSignupScreen(state.profile, vm::completeSignup, vm::logout)
        is AuthState.Pending -> PendingApprovalScreen(state.profile, vm::refreshApproval, vm::logout)
        is AuthState.Frozen -> FrozenAccountScreen(state.profile, vm::refreshApproval, vm::logout)
        is AuthState.Recovery -> PasswordRecoveryScreen(
            email = state.email,
            onSubmit = vm::completePasswordRecovery,
            onCancel = vm::cancelPasswordRecovery,
        )
        is AuthState.Ready -> MainShell(state.profile, sync, vm::sync, vm::logout, initialDeepLink)
    }
    }
}


@Composable
private fun BrandLoadingScreen() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = motionEnter(fadeIn(tween(320)) + scaleIn(tween(360), initialScale = .96f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Image(
                        painter = painterResource(R.drawable.launcher_legacy),
                        contentDescription = "شعار حلقاتي",
                        modifier = Modifier.padding(8.dp).size(76.dp).clip(RoundedCornerShape(20.dp)),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "حلقاتي",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                HalaqatiLoader(size = 56.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "صُنع بأيدٍ شرقية",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Designed by : Dr. Mohammed Samir Abu Younis",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MainShell(
    profile: Profile,
    sync: SyncState,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    initialDeepLink: String?,
) {
    val uiPrefs by com.mohammedsamir.halaqati.data.AppGraph.uiPreferences.state.collectAsStateWithLifecycle()
    val reducedMotion = uiPrefs.motion == com.mohammedsamir.halaqati.data.MotionLevel.REDUCED
    // RC17 role isolation: the guardian graph never contains teacher/staff-only routes.
    val guardianMode = profile.role == com.mohammedsamir.halaqati.model.UserRole.GUARDIAN
    val nav = rememberNavController()
    val TAB_ROUTES = remember { setOf(AppDestination.Home, AppDestination.Sessions, AppDestination.Students, AppDestination.Plans, AppDestination.Rankings, AppDestination.Community, AppDestination.GuardianProfile, AppDestination.GuardianAchievements, AppDestination.Halaqas, AppDestination.Supervision).map { it.route }.toSet() }
    val destinations = remember(profile.role) { RoleNavigationPolicy.destinations(profile.role) }
    val destinationRoutes = remember(destinations) { destinations.map { it.route }.toSet() }
    val tabOrder = remember(destinations) { destinations.map { it.route } }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var deepLinkedStudentId by remember { mutableStateOf<String?>(null) }
    val current = nav.currentBackStackEntryAsState().value?.destination?.route ?: AppDestination.Home.route
    remember(current) { com.mohammedsamir.halaqati.ui.components.HalaqatiEnterClock.screenOpened(); current }

    androidx.compose.runtime.SideEffect { com.mohammedsamir.halaqati.documents.LogoPolicy.builtIn = profile.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN }

    var quickAddOpen by remember { mutableStateOf(false) }
    val sessionExpired by com.mohammedsamir.halaqati.data.SessionExpiry.expired.collectAsStateWithLifecycle()
    // A new expiry (false -> true) always shows the prompt again, even if an earlier one was dismissed with «لاحقًا».
    var expiredDismissed by remember(sessionExpired) { mutableStateOf(false) }
    if (sessionExpired && !expiredDismissed) {
        com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog(
            onDismissRequest = { expiredDismissed = true },
            title = { androidx.compose.material3.Text("انتهت جلسة الدخول") },
            text = { androidx.compose.material3.Text("انتهت جلسة الدخول على هذا الجهاز (تظل جلسة الويب تعمل). سجّل الدخول من جديد لعرض أحدث البيانات ومتابعة الرفع. ما لم يُرفع بعد يبقى محفوظًا على جهازك.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = onLogout) { androidx.compose.material3.Text("تسجيل الدخول") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { expiredDismissed = true }) { androidx.compose.material3.Text("لاحقًا") } },
        )
    }

    fun navigateTo(destination: AppDestination) {
        if (!RoleNavigationPolicy.canOpen(profile.role, destination)) return
        // RC27.24: only the bottom-bar tabs reset the stack to Home. Every other page (settings, notifications, sync,
        // reports, drawer pages...) is pushed on top of the page it was opened from, so Back returns to that page
        // instead of jumping to Home.
        if (destination.route !in TAB_ROUTES) {
            nav.navigate(destination.route) { launchSingleTop = true }
            return
        }
        // RC27.38: no saveState/restoreState here. With them, a stack like [Home, Students, Settings] was saved under
        // "students" when another tab was tapped, and tapping Students later restored that stack and landed on Settings
        // instead of Students. A tab tap must always land exactly on the tapped tab.
        nav.navigate(destination.route) {
            launchSingleTop = true
            popUpTo(AppDestination.Home.route)
        }
    }

    fun runQuickAction(what: String) {
        quickAddOpen = false
        val bus = com.mohammedsamir.halaqati.ui.navigation.AddActionBus
        when (what) {
            "session" -> { bus.request("session"); if (current != AppDestination.Sessions.route) navigateTo(AppDestination.Sessions) }
            "today" -> navigateTo(AppDestination.SessionToday)
            "student" -> { bus.request("student"); if (current != AppDestination.Students.route) navigateTo(AppDestination.Students) }
            "announcement" -> { bus.request("announcement"); if (current != AppDestination.Community.route) navigateTo(AppDestination.Community) }
            "quick" -> navigateTo(AppDestination.QuickMemorization)
            "visit" -> { bus.request("visit"); navigateTo(AppDestination.Visits) }
            "alert" -> { bus.request("alert"); navigateTo(AppDestination.Supervision) }
        }
    }

    fun drawerNavigate(destination: AppDestination) {
        scope.launch { drawerState.close() }
        navigateTo(destination)
    }

    // RC60: tapping a chat push (deep_link = "chat:<thread id>") opens that conversation straight away.
    var chatDeepLinkId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(profile.id) {
        // The system owner sits above every association, so his role label reads "مسؤول النظام" everywhere.
        com.mohammedsamir.halaqati.ui.components.PlatformOwnerState.isOwner = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.mohammedsamir.halaqati.data.AppGraph.repo.table("profiles", "select=id,is_platform_owner&id=eq.${profile.id}&limit=1").sRows().firstOrNull()?.optBoolean("is_platform_owner", false) }.getOrNull() == true
        }
    }
    LaunchedEffect(initialDeepLink, profile.role) {
        val chatId = initialDeepLink?.trim()?.takeIf { it.startsWith("chat:") }?.substringAfter("chat:")?.trim()?.takeIf { it.length == 36 }
        if (chatId != null && (profile.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN || profile.role == com.mohammedsamir.halaqati.model.UserRole.OBSERVER)) chatDeepLinkId = chatId
    }
    chatDeepLinkId?.let { id ->
        com.mohammedsamir.halaqati.ui.screens.ChatSheet(id, if (profile.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN) "محادثة مع مانح" else "محادثة مع المدير") { chatDeepLinkId = null }
    }
    LaunchedEffect(initialDeepLink, profile.role) {
        val parsed = DeepLinkRules.navigation(initialDeepLink)
        deepLinkedStudentId = parsed?.studentId
        AppDestination.fromRoute(parsed?.route)
            ?.takeIf { RoleNavigationPolicy.canOpen(profile.role, it) }
            ?.let { nav.navigate(it.route) { launchSingleTop = true } }
    }

    // RC27.10: work mode (halaqa / program) follows the halaqat the person can reach and the scope-bar choice.
    val workModeState by com.mohammedsamir.halaqati.data.WorkModeHolder.state.collectAsStateWithLifecycle()
    val modeScopes by com.mohammedsamir.halaqati.data.AppGraph.halaqaScope.selected.collectAsStateWithLifecycle()
    val modeEpoch by com.mohammedsamir.halaqati.data.AppGraph.connectivity.syncEpoch.collectAsStateWithLifecycle()
    LaunchedEffect(profile.role, modeScopes, modeEpoch) {
        if (profile.role == com.mohammedsamir.halaqati.model.UserRole.GUARDIAN || profile.role == com.mohammedsamir.halaqati.model.UserRole.OBSERVER) return@LaunchedEffect
        val (typesAndNames, activeIds) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val arr = com.mohammedsamir.halaqati.data.AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type,program_specialization,affects_primary_plan,starts_on,ends_on&order=name.asc&limit=200")
            val rows = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            (rows.associate { it.optString("id") to it.optString("halaqa_type", "regular") } to rows.associate { it.optString("id") to it.optString("name", "حلقة") }) to rows.filter { it.optBoolean("active", true) }.map { it.optString("id") }
        }
        val userId = com.mohammedsamir.halaqati.data.AppGraph.store.load()?.userId
        val sel = com.mohammedsamir.halaqati.ui.scope.HalaqaScopeRules.resolve(
            profile.role.name.lowercase(), activeIds, com.mohammedsamir.halaqati.data.AppGraph.halaqaScope.savedFor(userId),
        ).selectedId
        com.mohammedsamir.halaqati.data.WorkModeHolder.update(typesAndNames.first, activeIds, sel, typesAndNames.second)
    }

    // RC27.10: warm the heavy reads in the background (after login and after every sync) so Plans / Sessions / Students
    // open instantly from memory instead of computing on first tap. Timings go to the diagnostics log.
    val warmEpoch by com.mohammedsamir.halaqati.data.AppGraph.connectivity.syncEpoch.collectAsStateWithLifecycle()
    LaunchedEffect(profile.role, warmEpoch) {
        if (profile.role == com.mohammedsamir.halaqati.model.UserRole.GUARDIAN || profile.role == com.mohammedsamir.halaqati.model.UserRole.OBSERVER) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val repo = com.mohammedsamir.halaqati.data.AppGraph.repo
            val today = java.time.LocalDate.now()
            com.mohammedsamir.halaqati.data.PerfLog.time("warm.planSnapshots") { runCatching { repo.planSnapshots(today) } }
            com.mohammedsamir.halaqati.data.PerfLog.time("warm.monthSummary") {
                runCatching { repo.reportSummary(today.withDayOfMonth(1).toString(), today.toString(), "all", "all", "all") }
            }
        }
    }

    CompositionLocalProvider(
        LocalGoldenShellActions provides GoldenShellActions(
            roleLabel = roleTitle(profile.role),
            online = sync.online,
            pending = sync.pending,
            openDrawer = { scope.launch { drawerState.open() } },
            openNotifications = { navigateTo(AppDestination.Notifications) },
            openSyncCenter = { navigateTo(AppDestination.SyncCenter) },
        ),
    ) {
    if (quickAddOpen) {
        com.mohammedsamir.halaqati.ui.components.QuickAddSheet(
            showQuickMemorization = workModeState.mode == com.mohammedsamir.halaqati.plans.WorkMode.REGULAR,
            supervisor = profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR,
            onDismiss = { quickAddOpen = false },
            onPick = ::runQuickAction,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            // RC27.29: the menu is a full-screen page (width and height); the content adds the system-bar insets itself.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                shape = androidx.compose.ui.graphics.RectangleShape,
            ) {
                GoldenDrawerContent(
                    profile = profile,
                    currentRoute = current,
                    onNavigate = ::drawerNavigate,
                    onClose = { scope.launch { drawerState.close() } },
                    onLogout = onLogout,
                )
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                GoldenBottomBar(
                    role = profile.role,
                    currentRoute = current,
                    drawerOpen = drawerState.isOpen,
                    onNavigate = ::navigateTo,
                    onMore = { scope.launch { drawerState.open() } },
                    onAdd = { quickAddOpen = true },
                    mode = workModeState.mode,
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                SyncBanner(
                    sync.online,
                    sync.pending,
                    sync.blocked,
                    sync.error,
                    onSync,
                    onDetails = { navigateTo(AppDestination.SyncCenter) },
                    stalePendingMinutes = sync.stalePendingMinutes,
                )
                SubscriptionBanner(profile.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN || profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR, sync.online)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                // RC28: keep operational pages quiet; decorative art stays in splash/certificates.

                NavHost(
                    navController = nav,
                    startDestination = AppDestination.Home.route,
                    // RC98: on tablets the page is a centred 720dp column instead of cards stretched to both edges.
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    enterTransition = { navEnter(tabOrder, reducedMotion) },
                    exitTransition = { navExit(tabOrder, reducedMotion) },
                    popEnterTransition = { navPopEnter(reducedMotion) },
                    popExitTransition = { navPopExit(reducedMotion) },
                ) {
                    destinations.forEach { destination ->
                        composable(destination.route) {
                            FeatureRouter(
                                destination,
                                profile,
                                sync,
                                onSync,
                                { raw ->
                                    when {
                                        raw.startsWith("student:") -> {
                                            val id = raw.substringAfter("student:").takeIf { it.isNotBlank() }
                                            if (id != null && AppDestination.Students.route in destinationRoutes) {
                                                nav.navigate("student/$id") { launchSingleTop = true }
                                            }
                                        }
                                        raw == "follow_up" -> {
                                            if (!guardianMode) nav.navigate("follow_up") { launchSingleTop = true }
                                        }
                                        raw.startsWith("students:") -> {
                                            deepLinkedStudentId = raw.substringAfter("students:").takeIf { it.isNotBlank() }
                                            AppDestination.Students.takeIf { it.route in destinationRoutes }?.let(::navigateTo)
                                        }
                                        else -> AppDestination.fromRoute(raw)?.takeIf { it.route in destinationRoutes }?.let(::navigateTo)
                                    }
                                },
                                onLogout,
                                deepLinkedStudentId,
                                { deepLinkedStudentId = null },
                            )
                        }
                    }
                    if (!guardianMode) composable("follow_up") {
                        StudentFollowUpScreen(
                            profile = profile,
                            sync = sync,
                            onBack = { nav.popBackStack() },
                            navigate = { raw ->
                                if (raw.startsWith("student:")) {
                                    val id = raw.substringAfter("student:").takeIf { it.isNotBlank() }
                                    if (id != null) nav.navigate("student/$id") { launchSingleTop = true }
                                }
                            },
                        )
                    }
                    if (!guardianMode) composable(
                        route = "student/{studentId}",
                        arguments = listOf(navArgument("studentId") { type = NavType.StringType }),
                    ) { entry ->
                        StudentProfileScreen(
                            studentId = entry.arguments?.getString("studentId").orEmpty(),
                            pending = sync.pending,
                            onBack = { nav.popBackStack() },
                            navigate = { raw ->
                                if (raw.startsWith("students:")) {
                                    deepLinkedStudentId = raw.substringAfter("students:").takeIf { it.isNotBlank() }
                                    AppDestination.Students.takeIf { it.route in destinationRoutes }?.let(::navigateTo)
                                } else {
                                    AppDestination.fromRoute(raw)?.takeIf { it.route in destinationRoutes }?.let(::navigateTo)
                                }
                            },
                        )
                    }
                }
                }
            }
        }
    }
    }
}

@Composable
private fun FeatureRouter(destination:AppDestination,profile:Profile,sync:SyncState,onSync:()->Unit,navigate:(String)->Unit,onLogout:()->Unit,deepLinkedStudentId:String?,onStudentFocusConsumed:()->Unit){
    when(destination.route){
        "home"->if (profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR) SupervisorHomeScreen(profile,sync.pending,onSync,navigate) else if (profile.role == com.mohammedsamir.halaqati.model.UserRole.OBSERVER) com.mohammedsamir.halaqati.ui.screens.DonorHomeScreen(profile,sync.pending,onSync,navigate) else if (profile.role == com.mohammedsamir.halaqati.model.UserRole.GUARDIAN) com.mohammedsamir.halaqati.ui.guardian.GuardianHomeScreen(navigate) else DashboardScreen(profile,sync,navigate,onSync)
        "observer_requests"->ObserverRequestsAdminScreen(profile)
        "students"->StudentsScreen(sync.pending,onSync,initialStudentId=deepLinkedStudentId,onInitialStudentConsumed=onStudentFocusConsumed,openStudentProfile={ navigate("student:$it") },role=profile.role.name.lowercase())
        "sessions"->if (profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR) SupervisorSessionsScreen(profile,sync.pending,onSync) else SessionsScreen(sync.pending,onSync,role=profile.role.name.lowercase())
        "session_today"->SessionsScreen(sync.pending,onSync,openTodayOnLaunch=true,role=profile.role.name.lowercase())
        "quick_memorization"->QuickMemorizationScreen(sync.pending,onSync)
        "plans"->PlansScreen(profile,sync.pending,onSync)
        "reports"->ReportsScreen(profile,sync.pending,onSync)
        "org"->com.mohammedsamir.halaqati.ui.screens.OrgScreen(profile,sync.pending,onSync,navigate)
        "team"->com.mohammedsamir.halaqati.ui.screens.TeamScreen(profile,sync.pending,onSync,navigate)
        "exams"->com.mohammedsamir.halaqati.ui.screens.ExamsScreen(profile,sync.pending,onSync,navigate)
        "certificates"->CertificatesScreen(profile,sync.pending,onSync)
        "notifications"->NotificationsNativeScreen(profile,sync.pending,onSync)
        "halaqas"->if (profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR) SupervisorHalaqasScreen(profile,sync.pending,onSync,navigate) else HalaqasManagementScreen(profile,sync.pending,onSync)
        "community"->CommunityNativeScreen(profile,sync.pending,onSync)
        "rankings"->RankingsScreen(profile,sync.pending,onSync)
        "guardian_competition"->GuardianCompetitionNativeScreen(profile,sync.pending,onSync)
        "guardian_achievements"->GuardianAchievementsNativeScreen(profile,sync.pending,onSync)
        "importexport"->ImportExportScreen(profile,sync.pending,onSync)
        "sync_center"->SyncCenterScreen(sync,onSync)
        "supervision"->StaffMonitorScreen(profile,sync.pending,onSync,navigate)
        "visits"->SupervisorVisitsScreen(profile,sync.pending,onSync)
        "users"->UsersScreen(sync.pending,onSync)
        "approvals"->ApprovalsScreen(profile)
        "quran"->QuranCalculatorScreen()
        "awards"->com.mohammedsamir.halaqati.ui.screens.AwardsScreen(profile,sync.pending,onSync,navigate)
        "donor_awards"->com.mohammedsamir.halaqati.ui.screens.DonorAwardsPage(profile,sync.pending,onSync)
        "donor_support"->com.mohammedsamir.halaqati.ui.screens.DonorSupportPage(profile,sync.pending,onSync)
        "donor_impact"->com.mohammedsamir.halaqati.ui.screens.DonorImpactPage(profile,sync.pending,onSync)
        "donor_stats"->com.mohammedsamir.halaqati.ui.screens.ObserverScreen(profile,sync.pending,onSync)
        "support"->com.mohammedsamir.halaqati.ui.screens.SupportAdminScreen(profile,sync.pending,onSync)
        "donor_chats"->com.mohammedsamir.halaqati.ui.screens.DonorChatsScreen(profile,sync.pending,onSync)
        "insights"->if (profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR) SupervisorInsightsScreen(profile,sync.pending,onSync,navigate) else com.mohammedsamir.halaqati.ui.screens.InsightsScreen(profile,sync.pending,onSync,navigate)
        "guardian_profile"->GuardianProfileNativeScreen(profile,sync.online,sync.pending,onSync,initialStudentId=deepLinkedStudentId)
        "settings"->SettingsScreen(profile,onLogout)
        else->UnknownRouteScreen(destination.route){navigate(AppDestination.Home.route)}
    }
}

@Composable
private fun UnknownRouteScreen(route: String, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("صفحة غير متاحة") })
        EmptyState(
            text = "تعذر فتح المسار: $route",
            actionLabel = "العودة للرئيسية",
            onAction = onHome,
            animated = false,
        )
    }
}


fun iconFor(key: String) = when (key) {
    "home" -> Icons.Default.Home
    "notifications" -> Icons.Default.Notifications
    "group" -> Icons.Default.Groups
    "forum" -> Icons.Default.Forum
    "event" -> Icons.Default.Event
    "person" -> Icons.Default.Person
    "timeline" -> Icons.Default.Timeline
    "award" -> Icons.Default.EmojiEvents
    "report" -> Icons.Default.Assessment
    "book" -> Icons.Default.MenuBook
    "leaderboard" -> Icons.Default.Leaderboard
    "competition" -> Icons.Default.BarChart
    "importexport" -> Icons.Default.ImportExport
    "sync" -> Icons.Default.Sync
    "supervisor" -> Icons.Default.SupervisorAccount
    "users" -> Icons.Default.ManageAccounts
    "profile" -> Icons.Default.AccountCircle
    "settings" -> Icons.Default.Settings
    "sessions" -> Icons.Default.Event
    "students" -> Icons.Default.Groups
    "plans" -> Icons.Default.Timeline
    "reports" -> Icons.Default.Assessment
    "more" -> Icons.Default.MoreHoriz
    else -> Icons.Default.Apps
}
