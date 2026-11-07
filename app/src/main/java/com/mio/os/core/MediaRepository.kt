package com.mio.os.core

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumId: Long,
) {
    val isDemo: Boolean get() = id < 0
}

/** Local audio library (MediaStore) + album art cache + a generated demo pad. */
class MediaRepository(private val context: Context) {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks

    private val artCache = LruCache<String, ImageBitmap>(24)

    suspend fun load(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!force && _tracks.value.isNotEmpty()) return@withContext
        val list = ArrayList<Track>()
        runCatching {
            val proj = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
            )
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { c ->
                val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albIdI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idI)
                    list += Track(
                        id = id,
                        title = c.getString(titleI) ?: "Unknown",
                        artist = c.getString(artistI) ?: "Unknown artist",
                        album = c.getString(albumI) ?: "Unknown album",
                        durationMs = c.getLong(durI),
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        ).toString(),
                        albumId = c.getLong(albIdI),
                    )
                }
            }
        }
        _tracks.value = list
    }

    fun albumArtUri(albumId: Long): Uri =
        Uri.parse("content://media/external/audio/albumart/$albumId")

    fun albumArt(track: Track): ImageBitmap? {
        artCache.get("a${track.albumId}")?.let { return it }
        val bmp: Bitmap? = runCatching {
            context.contentResolver.openInputStream(albumArtUri(track.albumId))?.use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull() ?: runCatching {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, Uri.parse(track.uri))
            val bytes = mmr.embeddedPicture
            mmr.release()
            if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
        }.getOrNull()
        val image = bmp?.asImageBitmap() ?: return null
        artCache.put("a${track.albumId}", image)
        return image
    }

    val demoTrack: Track
        get() = Track(
            id = -1,
            title = "Khaayal",
            artist = "MIO Ambient",
            album = "MIO OS Sessions",
            durationMs = 24_000,
            uri = "",
            albumId = -1,
        )

    fun tracksWithDemo(): List<Track> = _tracks.value.ifEmpty { listOf(demoTrack) }

    /** Synthesises a gentle 24s pad so playback is testable on any device. */
    fun ensureDemoFile(): File {
        val file = File(context.cacheDir, "mio_ambient.wav")
        if (file.exists() && file.length() > 1000) return file
        val sampleRate = 22_050
        val seconds = 24
        val frames = sampleRate * seconds
        val pcm = ShortArray(frames)
        val notes = doubleArrayOf(220.0, 277.18, 329.63, 440.0)
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for ((n, f) in notes.withIndex()) {
                val env = 0.5 + 0.5 * sin(2 * PI * (0.07 + n * 0.013) * t)
                v += sin(2 * PI * f * t) * 0.16 * env
            }
            val slow = 0.55 + 0.45 * sin(2 * PI * 0.05 * t)
            val fade = when {
                t < 1.2 -> t / 1.2
                t > seconds - 2.0 -> (seconds - t) / 2.0
                else -> 1.0
            }
            pcm[i] = (v * slow * fade * Short.MAX_VALUE * 0.55).toInt().toShort()
        }
        val bytes = ByteArray(frames * 2)
        for (i in pcm.indices) {
            val s = pcm[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        file.outputStream().use { out ->
            val dataLen = bytes.size
            out.write("RIFF".toByteArray())
            out.write(intLe(36 + dataLen))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intLe(16))
            out.write(shortLe(1))
            out.write(shortLe(1))
            out.write(intLe(sampleRate))
            out.write(intLe(sampleRate * 2))
            out.write(shortLe(2))
            out.write(shortLe(16))
            out.write("data".toByteArray())
            out.write(intLe(dataLen))
            out.write(bytes)
        }
        return file
    }

    private fun intLe(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
    )

    private fun shortLe(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
}

/** Small, honest equalizer wrapper around android.media.audiofx.Equalizer. */
object MioEqualizer {

    data class Band(val label: String, val levelMb: Int, val min: Int, val max: Int)

    private var fx: android.media.audiofx.Equalizer? = null
    private var sessionId = 0

    fun attach(audioSessionId: Int): Boolean = runCatching {
        if (fx != null && sessionId == audioSessionId) return true
        release()
        sessionId = audioSessionId
        fx = android.media.audiofx.Equalizer(0, audioSessionId).also { it.enabled = true }
        true
    }.getOrDefault(false)

    fun release() {
        runCatching { fx?.release() }
        fx = null
    }

    fun bands(): List<Band> = runCatching {
        val eq = fx ?: return emptyList()
        val n = eq.numberOfBands.toInt()
        (0 until n).map { i ->
            val b = eq.getBand(i.toShort())
            Band(
                label = "${(eq.getCenterFreq(b).toInt() / 1000)}Hz",
                levelMb = eq.getBandLevel(b).toInt(),
                min = eq.bandLevelRange[0].toInt(),
                max = eq.bandLevelRange[1].toInt(),
            )
        }
    }.getOrDefault(emptyList())

    fun setBandLevel(index: Int, milliBel: Int) = runCatching {
        fx?.setBandLevel(index.toShort(), milliBel.toShort())
    }.getOrDefault(Unit)

    fun usePreset(index: Int) = runCatching {
        fx?.usePreset(index.toShort())
    }.getOrDefault(Unit)

    val isAvailable: Boolean get() = fx != null
}
