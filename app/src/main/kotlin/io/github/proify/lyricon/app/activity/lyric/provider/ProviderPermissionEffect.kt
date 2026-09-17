package io.github.proify.lyricon.app.activity.lyric.provider

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val READ_APPS_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

internal fun Context.needsProviderPermission(): Boolean {
    val supported = try {
        packageManager.getPermissionInfo(READ_APPS_PERMISSION, 0).packageName == "com.lbe.security.miui"
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
    return supported && ContextCompat.checkSelfPermission(this, READ_APPS_PERMISSION) !=
        PackageManager.PERMISSION_GRANTED
}

/** Request once per page entry, then refresh after the permission result or returning from settings. */
@Composable
fun ProviderPermissionEffect(viewModel: LyricProviderViewModel, otherLabel: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var requestPending by rememberSaveable { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestPending = false
        viewModel.loadProviders(otherLabel)
    }

    LaunchedEffect(viewModel, otherLabel) {
        entered = true
        if (!requestPending) {
            if (context.needsProviderPermission()) {
                requestPending = true
                launcher.launch(READ_APPS_PERMISSION)
            } else {
                viewModel.loadProviders(otherLabel)
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel, otherLabel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && entered && !requestPending) {
                viewModel.loadProviders(otherLabel)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
