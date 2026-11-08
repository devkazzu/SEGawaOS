package com.mio.os.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.sin
import kotlin.random.Random

/**
 * SEGAWA live wallpaper engine for the *system* wallpaper.
 *
 * Modes (stored in the shared launcher prefs so the Themes screen and the
 * engine always agree): "petals" (drifting sakura), "neon" (animated aurora
 * blobs) and "video" (user video, muted and looping). Battery rules: the draw
 * loop only runs while the wallpaper is visible, video pauses when hidden,
 * and low-memory signals halve the frame rate and particle count.
 */
class SegaWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SegaEngine()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        lowMemory = level >= TRIM_MEMORY_RUNNING_LOW
    }

    @Volatile
    var lowMemory = false
        private set

    inner class SegaEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var mode = "off"
        private var videoUri: String? = null
        private var player: MediaPlayer? = null
        private var frame = 0L
        private var xOffset = 0.5f
        private val petals = ArrayList<Petal>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val drawLoop = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                frame++
                handler.postDelayed(this, if (lowMemory) 66L else 33L)
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(true)
            readSettings()
            repeat(42) { petals += Petal.random() }
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawLoop)
            releasePlayer()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                readSettings()
                ensurePlayer()
                handler.removeCallbacks(drawLoop)
                handler.post(drawLoop)
                runCatching { if (mode == "video") player?.start() }
            } else {
                handler.removeCallbacks(drawLoop)
                runCatching { if (player?.isPlaying == true) player?.pause() }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            ensurePlayer()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(drawLoop)
            releasePlayer()
            super.onSurfaceDestroyed(holder)
        }

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int,
        ) {
            this.xOffset = xOffset
        }

        private fun readSettings() {
            val sp = applicationContext.getSharedPreferences("mio_prefs_v1", Context.MODE_PRIVATE)
            mode = sp.getString("liveWall", "off") ?: "off"
            videoUri = sp.getString("liveWallVideo", null)?.ifBlank { null }
            if (mode == "video" && videoUri == null) mode = "petals"
        }

        private fun ensurePlayer() {
            if (mode != "video") {
                releasePlayer()
                return
            }
            val uri = videoUri ?: return
            if (player != null) return
            player = runCatching {
                MediaPlayer().apply {
                    setDataSource(applicationContext, android.net.Uri.parse(uri))
                    setSurface(surfaceHolder.surface)
                    isLooping = true
                    setVolume(0f, 0f)
                    setOnPreparedListener {
                        if (visible && mode == "video") runCatching { start() }
                    }
                    setOnErrorListener { _, _, _ ->
                        releasePlayer()
                        mode = "petals"
                        true
                    }
                    prepareAsync()
                }
            }.getOrNull()
        }

        private fun releasePlayer() {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
        }

        private fun drawFrame() {
            if (mode == "video" && player != null) return // MediaPlayer owns the surface
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) render(canvas)
            } catch (_: Throwable) {
                // Surface gone mid-frame – the next loop iteration retries.
            } finally {
                runCatching { if (canvas != null) holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun render(c: Canvas) {
            val w = c.width.toFloat()
            val h = c.height.toFloat()
            if (w <= 0 || h <= 0) return
            val shift = (xOffset - 0.5f) * w * 0.12f
            paint.shader = LinearGradient(
                shift, 0f, w * 0.5f + shift, h,
                intArrayOf(0xFF120A24.toInt(), 0xFF07040F.toInt()),
                null, Shader.TileMode.CLAMP,
            )
            paint.alpha = 255
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            if (mode == "neon") drawNeon(c, w, h) else drawPetals(c, w, h)
        }

        private fun drawNeon(c: Canvas, w: Float, h: Float) {
            val t = frame / 30f
            blob(c, w * (0.5f + 0.3f * sin(t * 0.4f)), h * 0.3f, w * 0.7f, 0x66FF4FA3)
            blob(c, w * (0.5f + 0.3f * sin(t * 0.3f + 2f)), h * 0.7f, w * 0.8f, 0x667C5CFF)
            blob(c, w * (0.5f + 0.25f * sin(t * 0.23f + 4f)), h * 0.5f, w * 0.6f, 0x4400E5FF)
        }

        private fun blob(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
            paint.shader = RadialGradient(
                x, y, r, intArrayOf(color, 0x00000000),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(x, y, r, paint)
            paint.shader = null
        }

        private fun drawPetals(c: Canvas, w: Float, h: Float) {
            blob(c, w * 0.78f, h * 0.2f, h * 0.3f, 0x33F472B6)
            paint.color = 0xFFF9A8D4.toInt()
            val scale = (w / 1080f).coerceAtLeast(0.5f)
            val n = if (lowMemory) petals.size / 2 else petals.size
            for (i in 0 until n) {
                val p = petals[i]
                p.y += p.speed
                p.x += sin((frame + p.seed) * 0.02f) * 0.6f
                if (p.y > h + 12) {
                    p.y = -12f
                    p.x = Random.nextFloat() * w
                }
                paint.alpha = (p.alpha * 255).toInt().coerceIn(40, 220)
                c.drawCircle(p.x * scale + (xOffset - 0.5f) * 40f * p.depth, p.y, p.r, paint)
            }
            paint.alpha = 255
        }

        private data class Petal(
            var x: Float, var y: Float, val speed: Float, val r: Float,
            val alpha: Float, val depth: Float, val seed: Int,
        ) {
            companion object {
                fun random() = Petal(
                    x = Random.nextFloat() * 1080f,
                    y = Random.nextFloat() * 2000f - 200f,
                    speed = 1.2f + Random.nextFloat() * 2.4f,
                    r = 2f + Random.nextFloat() * 5f,
                    alpha = 0.35f + Random.nextFloat() * 0.5f,
                    depth = 0.4f + Random.nextFloat() * 0.6f,
                    seed = Random.nextInt(1000),
                )
            }
        }
    }
}
