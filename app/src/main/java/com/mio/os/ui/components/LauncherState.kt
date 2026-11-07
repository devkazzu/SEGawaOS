package com.mio.os.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mio.os.core.LauncherHelper

/**
 * Live "is MIO OS the default home app?" state – refreshed every time the
 * activity comes back to the foreground, so returning from the Android Home-app
 * chooser updates the UI immediately.
 */
@Composable
fun rememberIsDefaultLauncher(): State<Boolean> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(LauncherHelper.isDefault(context)) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                state.value = LauncherHelper.isDefault(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        state.value = LauncherHelper.isDefault(context)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}
