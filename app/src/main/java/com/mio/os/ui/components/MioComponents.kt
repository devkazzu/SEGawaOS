package com.mio.os.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import com.mio.os.R
import com.mio.os.core.MioApp
import com.mio.os.core.MioSettings
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType
import com.mio.os.ui.theme.hueTint

/* ------------------------------------------------------------------------- */
/* Wallpaper                                                                  */
/* ------------------------------------------------------------------------- */

@Composable
fun MioWallpaper(settings: MioSettings, extraDim: Float = 0f, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    // quantised so that neighbouring screens (home, control centre, settings)
    // can share one baked bitmap instead of three
    val dim = (((settings.dim + extraDim).coerceIn(0f, 0.8f)) * 5f).toInt() * 0.2f
    var size by remember { mutableStateOf(IntSize.Zero) }

    // The wallpaper, its vignette, the accent glow and the dim level are rasterised
    // once into a single bitmap. Drawing one texture per frame instead of three
    // full-screen gradients keeps the home screen smooth on low-end GPUs
    // (measured on a GPU-less emulator: 89 ms → 44 ms median frame time).
    val key = "${settings.wallpaperId}|${settings.wallpaperUri}|${size.width}x${size.height}|$dim|${accent.toArgb()}"
    val baked by produceState<ImageBitmap?>(initialValue = WallpaperCache.get(key), key) {
        value = WallpaperCache.get(key) ?: withContext(Dispatchers.Default) {
            renderWallpaper(context, settings.wallpaperId, settings.wallpaperUri, size.width, size.height, dim, accent.toArgb())
        }?.also { WallpaperCache.put(key, it) }
    }

    Box(
        modifier.fillMaxSize().onSizeChanged { size = it }
            .then(if (baked == null) Modifier.background(MioColors.Bg) else Modifier)
    ) {
        baked?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Small LRU of baked wallpapers (~4.5 MB each) so navigation never re-rasterises. */
private object WallpaperCache {
    private val entries = object : LinkedHashMap<String, ImageBitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > 3
    }

    fun get(key: String): ImageBitmap? = synchronized(entries) { entries[key] }

    fun put(key: String, bitmap: ImageBitmap) {
        synchronized(entries) { entries[key] = bitmap }
    }
}

/** Rasterises image + gradients + dim into one bitmap (runs off the main thread). */
private fun renderWallpaper(
    context: Context,
    id: String,
    uri: String?,
    widthPx: Int,
    heightPx: Int,
    dim: Float,
    accent: Int,
): ImageBitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    // cap the texture at 1080 px wide: a 1440p phone does not need more and the
    // memory saving matters on a 2 GB device
    val scale = (1080f / widthPx).coerceAtMost(1f)
    val w = (widthPx * scale).toInt().coerceAtLeast(1)
    val h = (heightPx * scale).toInt().coerceAtLeast(1)
    val target = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(target)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    val bounds = Rect(0, 0, w, h)

    val source: Bitmap? = when {
        uri != null -> decodeUri(context, uri)
        id == "violet" -> null
        else -> BitmapFactory.decodeResource(
            context.resources,
            when (id) {
                "anime" -> R.drawable.wall_anime
                "aurora" -> R.drawable.wall_aurora
                else -> R.drawable.wall_mountain
            },
        )
    }

    if (source != null) {
        val srcRect = centerCrop(source.width, source.height, w, h)
        canvas.drawBitmap(source, srcRect, bounds, paint)
        source.recycle()
    } else {
        // procedural "violet field" wallpaper
        val base = Paint()
        base.shader = LinearGradient(
            0f, 0f, w * 0.45f, h.toFloat(),
            intArrayOf(0xFF2A1352.toInt(), 0xFF140A26.toInt(), 0xFF07040F.toInt()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), base)
        val halo = Paint()
        halo.shader = RadialGradient(
            w * 0.72f, h * 0.28f, h * 0.45f,
            intArrayOf(0x667C5CFF, 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), halo)
    }

    // vignette: keeps white text legible over any wallpaper
    val vignette = Paint()
    vignette.shader = LinearGradient(
        0f, 0f, 0f, h.toFloat(),
        intArrayOf(0x8C000000.toInt(), 0x1F000000, 0x47000000, 0xDB000000.toInt()),
        floatArrayOf(0f, 0.22f, 0.62f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignette)

    // accent glow in the top-left corner
    val glow = Paint()
    glow.shader = RadialGradient(
        0f, 0f, maxOf(w, h) * 0.95f,
        intArrayOf((accent and 0x00FFFFFF) or 0x29000000, 0x00000000),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow)

    if (dim > 0.01f) canvas.drawColor(android.graphics.Color.argb((dim * 255).toInt(), 0, 0, 0))
    return target.asImageBitmap()
}

private fun centerCrop(sw: Int, sh: Int, dw: Int, dh: Int): Rect {
    if (sw <= 0 || sh <= 0) return Rect(0, 0, 1, 1)
    val srcRatio = sw.toFloat() / sh
    val dstRatio = dw.toFloat() / dh
    return if (srcRatio > dstRatio) {
        val cropW = (sh * dstRatio).toInt()
        val left = (sw - cropW) / 2
        Rect(left, 0, left + cropW, sh)
    } else {
        val cropH = (sw / dstRatio).toInt()
        val top = (sh - cropH) / 2
        Rect(0, top, sw, top + cropH)
    }
}

private fun decodeUri(context: Context, uri: String): Bitmap? = runCatching {
    context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
}.getOrNull()


/* ------------------------------------------------------------------------- */
/* Glass primitives                                                           */
/* ------------------------------------------------------------------------- */

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    alpha: Float = 0.075f,
    borderAlpha: Float = 0.12f,
    tint: Color = Color.White,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(corner),
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = 0.55f + 0.45f * LocalSettings.current.glass
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = alpha * glass),
                        tint.copy(alpha = alpha * 0.35f * glass),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = borderAlpha * glass), shape)
    ) {
        content()
    }
}

@Composable
fun MioLogo(modifier: Modifier = Modifier, color: Color = LocalAccent.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.20f, h * 0.78f)
            lineTo(w * 0.20f, h * 0.24f)
            lineTo(w * 0.50f, h * 0.62f)
            lineTo(w * 0.80f, h * 0.24f)
            lineTo(w * 0.80f, h * 0.78f)
        }
        drawPath(
            path,
            brush = Brush.verticalGradient(listOf(Color.White, MioColors.PurpleSoft, color)),
            style = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.94f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "bounce",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

@Composable
fun IconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    tint: Color = MioColors.Text,
    background: Color = Color.White.copy(alpha = 0.07f),
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.bounceClick(onClick = onClick) else modifier
    Box(
        m.size(size).clip(CircleShape).background(background)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = MioType.label.copy(letterSpacing = 1.6.sp, fontSize = 11.sp))
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun GlassRow(
    title: String,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val m = if (onClick != null) modifier.bounceClick(scaleDown = 0.98f, onClick = onClick) else modifier
    Row(
        m.fillMaxWidth().clip(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MioType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MioType.body.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun RowScope.RowSpacer() = Spacer(Modifier.weight(1f))

/* ------------------------------------------------------------------------- */
/* App icons                                                                  */
/* ------------------------------------------------------------------------- */

@Composable
fun AppIconView(app: MioApp, size: Dp = 52.dp, iconBitmap: ImageBitmap? = app.icon) {
    val tint = hueTint(app.packageName)
    val shape = when (com.mio.os.ui.theme.LocalSettings.current.iconShape) {
        "circle" -> CircleShape
        "rounded" -> RoundedCornerShape(size * 0.22f)
        else -> RoundedCornerShape(size * 0.30f)
    }
    Box(
        Modifier.size(size).clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.18f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap, contentDescription = app.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size * 0.055f),
            )
        } else {
            Text(
                app.name.take(1).uppercase(),
                style = TextStyle(
                    color = Color.White, fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
fun AppShortcut(
    app: MioApp,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    showBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier.width(iconSize + 22.dp).bounceClick(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIconView(app, iconSize)
            if (showBadge) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape)
                        .background(LocalAccent.current)
                        .border(1.5.dp, MioColors.Bg, CircleShape)
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(7.dp))
            Text(
                app.name,
                style = MioType.micro.copy(color = Color.White.copy(alpha = 0.88f), fontSize = 10.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Controls                                                                   */
/* ------------------------------------------------------------------------- */

@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    active: Color = LocalAccent.current,
    track: Color = Color.White.copy(alpha = 0.13f),
    leadingIcon: ImageVector? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> onValueChange((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val r = size.height / 2f
            drawRoundRect(track, cornerRadius = CornerRadius(r, r))
            val w = (size.width * value.coerceIn(0f, 1f))
            if (w > 1f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(active.copy(alpha = 0.65f), active)),
                    size = Size(w.coerceAtLeast(r * 2), size.height),
                    cornerRadius = CornerRadius(r, r),
                )
            }
            val knobR = size.height * 0.62f
            val cx = w.coerceIn(knobR, size.width - knobR)
            drawCircle(Color.White.copy(alpha = 0.22f), radius = knobR * 1.9f, center = Offset(cx, size.height / 2))
            drawCircle(Color.White, radius = knobR, center = Offset(cx, size.height / 2))
        }
        if (leadingIcon != null) {
            Icon(
                leadingIcon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 6.dp).size(16.dp),
            )
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    color: Color = LocalAccent.current,
    track: Color = Color.White.copy(alpha = 0.10f),
) {
    Canvas(modifier) {
        val w = strokeWidth.toPx()
        val inset = w / 2
        drawArc(
            track, 0f, 360f, false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - w, size.height - w),
            style = Stroke(w, cap = StrokeCap.Round),
        )
        drawArc(
            Brush.sweepGradient(listOf(color.copy(alpha = 0.5f), color, MioColors.PurpleSoft)),
            -90f, 360f * progress.coerceIn(0f, 1f), false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - w, size.height - w),
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun AmbientOrb(modifier: Modifier = Modifier, color: Color = LocalAccent.current) {
    val transition = rememberInfiniteTransition(label = "orb")
    val rot by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot",
    )
    val pulse by transition.animateFloat(
        0.94f, 1.06f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }) {
        val r = size.minDimension / 2f
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.10f), Color.Transparent),
                center = center, radius = r * 1.5f,
            ),
            radius = r * 1.5f, center = center,
        )
        rotate(rot, center) {
            drawArc(
                Brush.sweepGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.9f), MioColors.PurpleSoft, Color.Transparent)
                ),
                0f, 300f, false,
                topLeft = Offset(center.x - r * 0.9f, center.y - r * 0.9f),
                size = Size(r * 1.8f, r * 1.8f),
                style = Stroke(width = r * 0.10f, cap = StrokeCap.Round),
            )
        }
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.95f), color, color.copy(alpha = 0.35f)),
                center = center - Offset(r * 0.12f, r * 0.12f), radius = r * 0.62f,
            ),
            radius = r * 0.62f, center = center,
        )
        drawCircle(
            Brush.radialGradient(
                listOf(MioColors.PurpleSoft.copy(alpha = 0.55f), Color.Transparent),
                center = center, radius = r * 0.62f,
            ),
            radius = r * 0.62f, center = center,
        )
    }
}

@Composable
fun PageDots(count: Int, index: Int, modifier: Modifier = Modifier, color: Color = Color.White) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtLeast(1)) { i ->
            val active = i == index
            val w by animateFloatAsState(if (active) 18f else 6f, tween(220), label = "dot")
            Box(
                Modifier.height(6.dp).width(w.dp).clip(CircleShape)
                    .background(if (active) color else color.copy(alpha = 0.30f))
            )
        }
    }
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(42.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
    }
}

@Composable
fun StatBar(
    label: String,
    value: Float,
    caption: String,
    modifier: Modifier = Modifier,
    color: Color = LocalAccent.current,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MioType.label)
            Spacer(Modifier.weight(1f))
            Text(caption, style = MioType.label.copy(color = MioColors.Text))
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                Modifier.fillMaxWidth(value.coerceIn(0.02f, 1f)).height(7.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)))
            )
        }
    }
}

@Composable
fun GradientPillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.95f), MioColors.PurpleSoft)))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MioType.title.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MioLogo(Modifier.size(46.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MioType.h2)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MioType.body, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Slide-in helper used by every app screen for a consistent feel. */
@Composable
fun ScreenEnter(content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { a.animateTo(1f, tween(260)) }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = a.value
            translationY = (1f - a.value) * 40f
        }
    ) { content() }
}
