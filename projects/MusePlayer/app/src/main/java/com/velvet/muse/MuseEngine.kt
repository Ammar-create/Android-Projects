package com.velvet.muse

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Track(val key: Long, val title: String, val artist: String)

data class Node(
    val id: String,
    val title: String,
    val subtitle: String,
    val playable: Boolean,
    val browsable: Boolean
)

object MuseEngine {

    const val TAG = "Muse"
    private const val PKG = "com.metrolist.music"
    private const val CLS = "com.metrolist.music.playback.MusicService"

    private val main = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var browser: MediaBrowser? = null
    private var ctrl: MediaController? = null

    var status by mutableStateOf("not connected")
    var connected by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var album by mutableStateOf("")
    var art by mutableStateOf<Bitmap?>(null)
    var playing by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    var speed by mutableStateOf(0f)
    var shuffleOn by mutableStateOf(false)
    var repeatMode by mutableStateOf(0)
    var queue by mutableStateOf<List<Track>>(emptyList())
    var queueKey by mutableStateOf(-1L)
    var children by mutableStateOf<List<Node>>(emptyList())
    var path by mutableStateOf<List<Node>>(listOf(Node("root", "Library", "", false, true)))
    var busy by mutableStateOf(false)
    var lastSearch by mutableStateOf("")

    private var stamp = 0L

    fun log(msg: String) { Log.i(TAG, msg) }

    fun livePosition(): Long {
        if (!playing) return positionMs
        val delta = (SystemClock.elapsedRealtime() - stamp) * speed
        return (positionMs + delta.toLong()).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
    }

    fun describe(): String =
        "state=$status connected=$connected track=\"$title\" / \"$artist\" playing=$playing " +
        "pos=${positionMs} dur=${durationMs} speed=$speed queue=${queue.size} index=$queueKey"

    fun connect(context: Context, onReady: (() -> Unit)? = null) {
        appCtx = context.applicationContext
        if (ctrl != null) { onReady?.invoke(); return }
        val cn = ComponentName(PKG, CLS)
        log("connecting...")
        status = "connecting"
        val callback = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val b = browser ?: return
                val token = b.sessionToken
                if (token == null) { status = "no session token"; return }
                try {
                    val c = MediaController(context, token)
                    c.registerCallback(controllerCallback)
                    ctrl = c
                    connected = true
                    status = "connected"
                    log("CONNECTED svc=" + b.serviceComponent?.packageName)
                    refreshQueue()
                    refresh()
                    onReady?.invoke()
                } catch (e: Exception) {
                    status = "controller failed: " + e.message
                    log("controller failed: " + e.message)
                }
            }

            override fun onConnectionFailed() {
                status = "connection failed"
                connected = false
                log("CONNECTION FAILED")
            }

            override fun onConnectionSuspended() {
                status = "suspended"
                connected = false
                log("CONNECTION SUSPENDED")
            }
        }
        browser = MediaBrowser(context, cn, callback, null)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state == null) return
            playing = state.state == PlaybackState.STATE_PLAYING
            speed = state.playbackSpeed
            positionMs = state.position.coerceAtLeast(0L)
            stamp = SystemClock.elapsedRealtime()
            state.errorMessage?.let { status = it }
        }

        override fun onMetadataChanged(md: MediaMetadata?) {
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            if (artist.isEmpty()) artist = md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            art = pullArt(md)
        }

        override fun onQueueChanged(q: MutableList<MediaSession.QueueItem>?) {
            queue = (q ?: emptyList()).mapNotNull { item ->
                val d = item.description ?: return@mapNotNull null
                Track(item.queueId, d.title?.toString() ?: "(untitled)", d.subtitle?.toString() ?: "")
            }
            refreshQueue()
        }

        override fun onSessionDestroyed() {
            connected = false
            status = "session destroyed"
            ctrl = null
        }

        override fun onSessionEvent(event: String?, extras: Bundle?) {}
    }

    private fun pullArt(md: MediaMetadata?): Bitmap? {
        if (md == null) return null
        val keys = intArrayOf(
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
        )
        for (k in keys) {
            try { md.getBitmap(k)?.let { return it } } catch (_: Exception) {}
        }
        val uriKeys = intArrayOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        for (k in uriKeys) {
            val s = md.getString(k) ?: continue
            val bmp = decodeUri(s)
            if (bmp != null) return bmp
        }
        return null
    }

    private fun decodeUri(s: String): Bitmap? {
        val ctx = appCtx ?: return null
        return try {
            if (s.startsWith("content://") || s.startsWith("file://")) {
                ctx.contentResolver.openInputStream(Uri.parse(s))?.use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (_: Exception) { null }
    }

    fun refresh() {
        val c = ctrl ?: return
        val md = c.metadata
        if (md != null) {
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: title
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: artist
            album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: album
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
            art = pullArt(md)
        }
        val st = c.playbackState
        if (st != null) {
            playing = st.state == PlaybackState.STATE_PLAYING
            speed = st.playbackSpeed
            positionMs = st.position.coerceAtLeast(0L)
            stamp = SystemClock.elapsedRealtime()
        }
        shuffleOn = c.shuffleMode == PlaybackState.SHUFFLE_MODE_ALL ||
            c.shuffleMode == PlaybackState.SHUFFLE_MODE_GROUP
        repeatMode = c.repeatMode
        refreshQueue()
    }

    private fun refreshQueue() {
        val q = ctrl?.queue ?: return
        if (q.isEmpty()) return
        queue = q.mapNotNull { item ->
            val d = item.description ?: return@mapNotNull null
            Track(item.queueId, d.title?.toString() ?: "(untitled)", d.subtitle?.toString() ?: "")
        }
        val idx = ctrl?.playbackState?.activeQueueItemId ?: -1L
        if (idx >= 0) queueKey = idx
    }

    // ---------- transport ----------

    fun play() { ctrl?.transportControls?.play(); playing = true }
    fun pause() { ctrl?.transportControls?.pause(); playing = false }
    fun toggle() { if (playing) pause() else play() }
    fun next() = ctrl?.transportControls?.skipToNext()
    fun prev() = ctrl?.transportControls?.skipToPrevious()
    fun stop() = ctrl?.transportControls?.stop()
    fun seekTo(ms: Long) {
        ctrl?.transportControls?.seekTo(ms)
        positionMs = ms
        stamp = SystemClock.elapsedRealtime()
    }
    fun skipToIndex(i: Int) {
        val t = queue.getOrNull(i) ?: return
        ctrl?.transportControls?.skipToQueueItem(t.key)
        queueKey = t.key
    }
    fun toggleShuffle() {
        val next = if (shuffleOn) PlaybackState.SHUFFLE_MODE_NONE else PlaybackState.SHUFFLE_MODE_ALL
        ctrl?.transportControls?.setShuffleMode(next)
        shuffleOn = !shuffleOn
    }
    fun cycleRepeat() {
        val next = when (repeatMode) {
            PlaybackState.REPEAT_MODE_OFF -> PlaybackState.REPEAT_MODE_ALL
            PlaybackState.REPEAT_MODE_ALL -> PlaybackState.REPEAT_MODE_ONE
            else -> PlaybackState.REPEAT_MODE_OFF
        }
        ctrl?.transportControls?.setRepeatMode(next)
        repeatMode = next
    }
    fun playFromSearch(q: String) {
        lastSearch = q
        ctrl?.transportControls?.playFromSearch(q, null)
    }
    fun playFromMediaId(id: String) {
        ctrl?.transportControls?.playFromMediaId(id, null)
    }

    // ---------- browse ----------

    fun browse(id: String) {
        val b = browser ?: return
        busy = true
        try {
            b.browse(id, object : MediaBrowser.BrowseCallback() {
                override fun onChildrenLoaded(parentId: String, list: MutableList<MediaBrowser.MediaItem>) {
                    main.post {
                        children = list.map { toNode(it) }
                        busy = false
                        log("browse $parentId -> ${list.size} items")
                    }
                }

                override fun onChildrenLoaded(parentId: String, list: MutableList<MediaBrowser.MediaItem>, options: Bundle) {
                    onChildrenLoaded(parentId, list)
                }

                override fun onError(parentId: String, error: Bundle?) {
                    main.post {
                        children = emptyList()
                        busy = false
                        log("browse error on $parentId")
                    }
                }
            })
        } catch (e: Exception) {
            busy = false
            log("browse threw: " + e.message)
        }
    }

    private fun toNode(item: MediaBrowser.MediaItem): Node {
        val d = item.description
        return Node(
            id = d?.mediaId ?: "",
            title = d?.title?.toString() ?: "(untitled)",
            subtitle = d?.subtitle?.toString() ?: "",
            playable = item.isPlayable,
            browsable = item.isBrowsable
        )
    }

    fun openNode(n: Node) {
        if (n.browsable) {
            path = path + n
            browse(n.id)
        } else {
            playFromMediaId(n.id)
        }
    }

    fun goBack(): Boolean {
        if (path.size <= 1) return false
        path = path.dropLast(1)
        browse(path.last().id)
        return true
    }

    fun resetPath() {
        path = listOf(Node("root", "Library", "", false, true))
        browse("root")
    }
}
