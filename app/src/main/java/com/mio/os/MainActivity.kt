package com.mio.os

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mio.os.core.MioNav
import com.mio.os.ui.MioRoot

/**
 * The actual HOME activity. When MIO OS is the default home app, Android starts
 * this activity (with FLAG_ACTIVITY_NEW_TASK) every time the user presses the
 * Home key/gesture, and [onNewIntent] brings any open overlay back to the home
 * screen – exactly like a stock launcher.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.setBackgroundDrawable(ColorDrawable(0xFF04030A.toInt()))
        setContent { MioRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Pressing Home while MIO OS is already visible must return home.
        MioNav.home()
        MioNav.signalHome()
    }
}
