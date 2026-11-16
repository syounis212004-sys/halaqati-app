package com.mohammedsamir.halaqati

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mohammedsamir.halaqati.ui.HalaqatiApp
import com.mohammedsamir.halaqati.ui.components.HalaqatiLaunchSplash
import com.mohammedsamir.halaqati.ui.components.HalaqatiSuccessHost
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.ui.theme.HalaqatiTheme

class MainActivity : ComponentActivity() {
    private val deepLink = mutableStateOf<String?>(null)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent?.dataString ?: intent?.getStringExtra("deep_link")
        requestNotificationPermissionIfNeeded()
        setContent {
            val uiPreferences by AppGraph.uiPreferences.state.collectAsState()
            HalaqatiTheme(uiPreferences) {
                // RC27.12: the motion splash plays once per cold start (not when opened from a notification link).
                var showSplash by rememberSaveable { mutableStateOf(deepLink.value == null) }
                Box(Modifier.fillMaxSize()) {
                    // RC27.35: a newer published version must be installed before the app opens.
                    com.mohammedsamir.halaqati.ui.update.ForcedUpdateGate {
                        HalaqatiApp(initialDeepLink = deepLink.value)
                        HalaqatiSuccessHost()
                    }
                    HalaqatiLaunchSplash(visible = showSplash, onDone = { showSplash = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.dataString ?: intent.getStringExtra("deep_link")
    }

    override fun onResume() {
        super.onResume()
        runCatching { AppGraph.connectivity.onForegroundReady() }
    }

    override fun onStop() {
        runCatching { AppGraph.connectivity.onForegroundStopped() }
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
