package com.mio.os

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mio.os.core.Mio
import com.mio.os.core.MioNav
import com.mio.os.core.MioScreen
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
        applyDebugIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Pressing Home while MIO OS is already visible must return home.
        if (!applyDebugIntent(intent)) {
            MioNav.home()
            MioNav.signalHome()
        }
    }

    /**
     * DEBUG BUILDS ONLY. Lets automated QA (and curious developers) open a
     * specific screen without simulating taps:
     *
     *   adb shell am start -n com.mio.os.debug/com.mio.os.MainActivity \
     *       --ez mio_skip_setup true --es mio_screen settings
     *
     * Release builds ignore both extras completely.
     */
    private fun applyDebugIntent(intent: Intent?): Boolean {
        if (!BuildConfig.DEBUG || intent == null) return false
        var handled = false

        if (intent.getBooleanExtra(EXTRA_SKIP_SETUP, false)) {
            Mio.prefs.edit { it.copy(onboarded = true) }
            MioNav.home()
            handled = true
        }

        val target = intent.getStringExtra(EXTRA_SCREEN) ?: return handled
        val screen = when (target) {
            "home" -> MioScreen.HOME
            "drawer" -> MioScreen.DRAWER
            "control" -> MioScreen.CONTROL
            "settings" -> MioScreen.SETTINGS
            "appearance" -> MioScreen.APPEARANCE
            "home_settings" -> MioScreen.HOME_SETTINGS
            "privacy" -> MioScreen.PRIVACY
            "about" -> MioScreen.ABOUT
            "ai" -> MioScreen.AI
            "music" -> MioScreen.MUSIC
            "files" -> MioScreen.FILES
            "gallery" -> MioScreen.GALLERY
            "themes" -> MioScreen.THEMES
            "notes" -> MioScreen.NOTES
            "calendar" -> MioScreen.CALENDAR
            "clock" -> MioScreen.CLOCK
            "calculator" -> MioScreen.CALCULATOR
            "weather" -> MioScreen.WEATHER
            "gaming" -> MioScreen.GAMING
            "hidden" -> MioScreen.HIDDEN
            "lock" -> MioScreen.LOCK
            "setup" -> MioScreen.WELCOME
            else -> null
        }
        if (screen != null) {
            MioNav.home()
            when (screen) {
                MioScreen.HOME -> Unit
                MioScreen.WELCOME -> MioNav.replace(MioScreen.WELCOME)
                else -> MioNav.push(screen)
            }
            handled = true
        }
        return handled
    }

    companion object {
        const val EXTRA_SKIP_SETUP = "mio_skip_setup"
        const val EXTRA_SCREEN = "mio_screen"
    }
}
