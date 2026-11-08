package com.mio.os.core

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Icon Studio model: named icon profiles plus per-app custom images.
 *
 * Custom icons map package name -> image content URI and are stored as one
 * query-style string ([MioSettings.customIcons]) so no database is needed.
 * URIs are taken with persistable read permission when picked, so they
 * survive reboots.
 */
data class IconProfile(
    val id: String,
    val name: String,
    val shape: String,
    val scale: Float,
    val bgAlpha: Float,
)

val IconProfiles = listOf(
    IconProfile("neon", "Segawa Neon", "squircle", 1f, 0.55f),
    IconProfile("glass", "Glass", "rounded", 1f, 0.22f),
    IconProfile("amoled", "AMOLED", "circle", 0.95f, 0.10f),
    IconProfile("minimal", "Minimal", "circle", 0.9f, 0f),
    IconProfile("bold", "Bold Gradient", "squircle", 1.1f, 0.75f),
)

object IconStudio {
    fun encode(map: Map<String, String>): String =
        map.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    fun decode(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split("&").mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else dec(part.substring(0, i)) to dec(part.substring(i + 1))
        }.toMap()
    }

    fun profile(id: String): IconProfile =
        IconProfiles.firstOrNull { it.id == id } ?: IconProfiles.first()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun dec(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
