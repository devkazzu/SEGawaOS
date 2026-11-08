package com.mio.os.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import com.mio.os.service.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide music engine: MediaPlayer + platform MediaSession so the track
 * shows up on the lock screen and in the media notification.
 */
object MioPlayer {

    private var mp: MediaPlayer? = null
    private var session: MediaSession? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private var queue: List<Track> = emptyList()
    private var index = -1

    fun ensureSession(context: Context) {
        if (session != null) return
        session = runCatching {
            MediaSession(context, "SEGAWA OS").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() = play(context)
                    override fun onPause() = pause()
                    override fun onSkipToNext() = next(context)
                    override fun onSkipToPrevious() = previous(context)
                    override fun onSeekTo(pos: Long) = seekTo(pos)
                    override fun onStop() = pause()
                })
                isActive = true
            }
        }.getOrNull()
    }

    fun sessionToken(): MediaSession.Token? = session?.sessionToken
    fun session(): MediaSession? = session

    fun playQueue(context: Context, tracks: List<Track>, startAt: Int) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startAt.coerceIn(0, tracks.lastIndex)
        start(context, queue[index])
    }

    fun enqueue(context: Context, track: Track, all: List<Track>) {
        queue = all.ifEmpty { listOf(track) }
        index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        start(context, track)
    }

    private fun start(context: Context, track: Track) {
        ensureSession(context)
        runCatching {
            mp?.release()
            mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setOnPreparedListener(MediaPlayer.OnPreparedListener { player ->
                    durationMsInternal = player.duration.toLong()
                    _duration.value = durationMsInternal
                    player.start()
                    _playing.value = true
                    MioEqualizer.attach(player.audioSessionId)
                    startTicker()
                    publishState()
                })
                setOnCompletionListener(MediaPlayer.OnCompletionListener { next(context, auto = true) })
                setOnErrorListener(MediaPlayer.OnErrorListener { _, _, _ ->
                    _playing.value = false
                    publishState()
                    true
                })
                if (track.isDemo) {
                    setDataSource(Mio.media.ensureDemoFile().absolutePath)
                } else {
                    setDataSource(context, Uri.parse(track.uri))
                }
                prepareAsync()
            }
            _current.value = track
            _position.value = 0
            _duration.value = track.durationMs
            requestFocus(context)
            publishState()
            Mio.prefs.edit { it.copy(lastSong = track.title) }
            MusicService.start(context)
        }
    }

    private var durationMsInternal = 0L

    fun toggle(context: Context) {
        if (_playing.value) pause() else {
            val track = _current.value ?: return
            if (mp == null) {
                enqueue(context, track, queue.ifEmpty { listOf(track) })
            } else {
                play(context)
            }
        }
    }

    fun play(context: Context) {
        mp?.let {
            runCatching { it.start() }
            _playing.value = true
            requestFocus(context)
            startTicker()
            publishState()
            MusicService.start(context)
        } ?: run {
            val track = _current.value
            if (track != null) enqueue(context, track, queue.ifEmpty { listOf(track) })
        }
    }

    fun pause() {
        runCatching { mp?.takeIf { it.isPlaying }?.pause() }
        _playing.value = false
        ticker?.cancel()
        publishState()
    }

    fun next(context: Context, auto: Boolean = false) {
        if (queue.isEmpty()) return
        index = (index + 1) % queue.size
        start(context, queue[index])
    }

    fun previous(context: Context) {
        if (queue.isEmpty()) return
        index = if (index - 1 < 0) queue.lastIndex else index - 1
        start(context, queue[index])
    }

    fun seekTo(ms: Long) {
        runCatching { mp?.seekTo(ms.toInt()) }
        _position.value = ms
        publishState()
    }

    fun stop() {
        runCatching { mp?.stop(); mp?.release() }
        mp = null
        _playing.value = false
        ticker?.cancel()
        publishState()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                runCatching {
                    val p = mp
                    if (p != null && _playing.value) {
                        _position.value = p.currentPosition.toLong()
                        _duration.value = p.duration.toLong().coerceAtLeast(1)
                    }
                }
                delay(500)
            }
        }
    }

    private fun requestFocus(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        }
    }

    fun publishState() {
        val s = session ?: return
        val track = _current.value
        runCatching {
            val state = if (_playing.value) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(state, _position.value, if (_playing.value) 1f else 0f)
                    .build()
            )
            if (track != null) {
                val b = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, _duration.value)
                val art = Mio.media.albumArt(track)?.asAndroidBitmap()
                if (art != null) b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                s.setMetadata(b.build())
            }
        }
    }

}
