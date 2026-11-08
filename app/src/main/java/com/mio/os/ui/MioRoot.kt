package com.mio.os.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.mio.os.core.Mio
import com.mio.os.core.MioNav
import com.mio.os.core.MioScreen
import com.mio.os.ui.apps.AboutScreen
import com.mio.os.ui.apps.AiScreen
import com.mio.os.ui.apps.CalculatorScreen
import com.mio.os.ui.apps.CalendarScreen
import com.mio.os.ui.apps.ClockScreen
import com.mio.os.ui.apps.FilesScreen
import com.mio.os.ui.apps.GalleryScreen
import com.mio.os.ui.apps.GamingScreen
import com.mio.os.ui.apps.HiddenAppsScreen
import com.mio.os.ui.apps.LockScreen
import com.mio.os.ui.apps.MusicScreen
import com.mio.os.ui.apps.NotesScreen
import com.mio.os.ui.apps.ThemesScreen
import com.mio.os.ui.apps.WeatherScreen
import com.mio.os.ui.control.ControlCenter
import com.mio.os.ui.drawer.AppDrawer
import com.mio.os.ui.home.HomeScreen
import com.mio.os.ui.settings.AppearanceScreen
import com.mio.os.ui.settings.HomeScreenSettings
import com.mio.os.ui.settings.PrivacyScreen
import com.mio.os.ui.settings.SettingsScreen
import com.mio.os.ui.setup.WelcomeScreen
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioTheme

@Composable
fun MioRoot() {
    val settings by Mio.prefs.state.collectAsState()
    val apps by Mio.apps.apps.collectAsState()
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        Mio.apps.ensureLoaded()
        Mio.media.load()
    }

    MioTheme(settings) {
        CompositionLocalProvider(
            LocalApps provides apps,
            LocalDensity provides Density(density.density, (density.fontScale * settings.fontScale).coerceIn(0.7f, 1.6f)),
        ) {
            // No root background fill: every surface paints its own baked wallpaper,
            // so one fewer full-screen layer is composited each frame.
            Box(Modifier.fillMaxSize()) {
                if (MioNav.isHomeLike) {
                    HomeScreen()
                } else {
                    AnimatedContent(
                        targetState = MioNav.screen,
                        // Slide instead of cross-fade: an alpha animation over a
                        // full-screen surface forces an offscreen buffer per frame.
                        transitionSpec = {
                            slideInHorizontally(tween(260)) { it / 6 } togetherWith
                                slideOutHorizontally(tween(180)) { -it / 12 }
                        },
                        label = "screen",
                    ) { screen ->
                        when (screen) {
                            MioScreen.WELCOME -> WelcomeScreen()
                            MioScreen.SETTINGS -> SettingsScreen()
                            MioScreen.APPEARANCE -> AppearanceScreen()
                            MioScreen.HOME_SETTINGS -> HomeScreenSettings()
                            MioScreen.PRIVACY -> PrivacyScreen()
                            MioScreen.ABOUT -> AboutScreen()
                            MioScreen.AI -> AiScreen()
                            MioScreen.MUSIC -> MusicScreen()
                            MioScreen.FILES -> FilesScreen()
                            MioScreen.GALLERY -> GalleryScreen()
                            MioScreen.THEMES -> ThemesScreen()
                            MioScreen.NOTES -> NotesScreen()
                            MioScreen.CALENDAR -> CalendarScreen()
                            MioScreen.CLOCK -> ClockScreen()
                            MioScreen.CALCULATOR -> CalculatorScreen()
                            MioScreen.WEATHER -> WeatherScreen()
                            MioScreen.GAMING -> GamingScreen()
                            MioScreen.HIDDEN -> HiddenAppsScreen()
                            MioScreen.LOCK -> LockScreen()
                            else -> HomeScreen()
                        }
                    }
                }

                AnimatedVisibility(
                    visible = MioNav.screen == MioScreen.DRAWER,
                    enter = slideInVertically(animationSpec = tween(280)) { it },
                    exit = slideOutVertically(animationSpec = tween(240)) { it },
                ) { AppDrawer() }

                AnimatedVisibility(
                    visible = MioNav.screen == MioScreen.CONTROL,
                    enter = slideInVertically(animationSpec = tween(280)) { -it },
                    exit = slideOutVertically(animationSpec = tween(240)) { -it },
                ) { ControlCenter() }
            }

            BackHandler(enabled = true) {
                when (MioNav.screen) {
                    MioScreen.WELCOME -> Unit
                    MioScreen.HOME, MioScreen.DRAWER, MioScreen.CONTROL -> {
                        // A launcher must never exit on Back from the home screen.
                        MioNav.home()
                    }
                    else -> MioNav.pop()
                }
            }
        }
    }
}
