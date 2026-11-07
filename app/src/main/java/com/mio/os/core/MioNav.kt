package com.mio.os.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MioScreen {
    HOME, DRAWER, CONTROL,
    SETTINGS, APPEARANCE, HOME_SETTINGS, PRIVACY, ABOUT,
    AI, MUSIC, FILES, GALLERY, THEMES, NOTES, CALENDAR, CLOCK, CALCULATOR, WEATHER, GAMING,
    WELCOME, LOCK, HIDDEN, ALL_APPS,
}

/**
 * Tiny hand-rolled navigator: a launcher needs a predictable back stack and must
 * never "exit" when the user presses Back on the home screen.
 */
object MioNav {

    var screen by mutableStateOf(MioScreen.WELCOME)
        private set

    private val stack = ArrayDeque<MioScreen>()

    /** Set by MainActivity so "Home" gestures reset overlays. */
    private val _homeSignal = MutableStateFlow(0)
    val homeSignal: StateFlow<Int> = _homeSignal

    fun start(initial: MioScreen) {
        stack.clear()
        screen = initial
    }

    fun push(next: MioScreen) {
        if (next == screen) return
        if (screen != MioScreen.DRAWER && screen != MioScreen.CONTROL) stack.addLast(screen)
        screen = next
    }

    fun replace(next: MioScreen) {
        screen = next
    }

    fun pop(): Boolean {
        if (screen == MioScreen.DRAWER || screen == MioScreen.CONTROL) {
            screen = MioScreen.HOME
            return true
        }
        val prev = stack.removeLastOrNull()
        screen = prev ?: MioScreen.HOME
        return prev != null
    }

    fun home() {
        stack.clear()
        screen = MioScreen.HOME
    }

    fun signalHome() {
        _homeSignal.value = _homeSignal.value + 1
    }

    val isHomeLike: Boolean
        get() = screen == MioScreen.HOME || screen == MioScreen.DRAWER || screen == MioScreen.CONTROL
}

/** Notification indicators fed by [com.mio.os.service.MioNotificationListener]. */
object MioNotifications {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    private val _apps = MutableStateFlow<Set<String>>(emptySet())
    val apps: StateFlow<Set<String>> = _apps

    fun publish(count: Int, packages: Set<String>) {
        _count.value = count
        _apps.value = packages
    }

    fun clear() = publish(0, emptySet())
}
