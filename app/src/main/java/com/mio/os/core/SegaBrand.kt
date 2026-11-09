package com.mio.os.core

/**
 * Single source of truth for the SEGAWA OS product identity.
 *
 * The application ID stays `com.mio.os` (and `com.mio.os.debug` for debug
 * builds) so v1.0 installs upgrade in place; only user-facing strings live
 * here. Internal package, class and preference names are intentionally
 * untouched.
 */
object SegaBrand {
    const val PRODUCT = "SEGAWA OS"
    const val TAGLINE = "Your Anime. Your World. Your OS."
    const val AI_NAME = "Segawa"
    const val MUSIC = "SEGAWA Music"
    const val DEVELOPER = "Raju Mahato"
    const val BRAND = "RJNX / Devkazzu"
    const val VERSION = "2.0.0"
    const val CODENAME = "Sakura"
    const val VERSION_LABEL = "v2.0.0 (Sakura)"
}
