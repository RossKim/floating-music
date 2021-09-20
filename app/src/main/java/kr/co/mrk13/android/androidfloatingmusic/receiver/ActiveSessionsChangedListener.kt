package kr.co.mrk13.android.androidfloatingmusic.receiver

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kr.co.mrk13.android.androidfloatingmusic.constant.MediaApp
import kr.co.mrk13.android.androidfloatingmusic.model.MusicData
import kr.co.mrk13.android.androidfloatingmusic.ui.MusicDataViewModel
import kr.co.mrk13.android.androidfloatingmusic.util.Log
import java.lang.ref.WeakReference

/**
 * @author ross.
 */
class ActiveSessionsChangedListener(
    private val context: Context,
    private val dataModel: MusicDataViewModel
) : MediaSessionManager.OnActiveSessionsChangedListener, MediaController.Callback() {

    private lateinit var mainHandler: Handler

    private val playbackStateObserveTask = object : Runnable {
        override fun run() {
            dataModel.musicData.value?.let { data ->
                data.controller.get()?.playbackState?.let {
                    setPlayerState(data, it)
                }
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    fun registerObserver(listener: ComponentName?) {
        val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        mm?.let {
            it.addOnActiveSessionsChangedListener(
                this,
                listener,
                Handler(Looper.getMainLooper())
            )
            val controllers = it.getActiveSessions(listener)
            checkActiveSession(controllers)
        }

        mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(playbackStateObserveTask)

    }

    fun deregisterObserver() {
        val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        mm?.removeOnActiveSessionsChangedListener(this)

        mainHandler.removeCallbacks(playbackStateObserveTask)

        clearController()
        dataModel.musicDataChange(null)
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        checkActiveSession(controllers)
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        super.onPlaybackStateChanged(state)
        dataModel.musicData.value?.let { data ->
            setPlayerState(data, state)
        }
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        super.onMetadataChanged(metadata)
        dataModel.musicData.value?.let { data ->
            data.controller.get()?.let {
                setMetadata(it, data.app, metadata)
            }
        }
    }

    override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
        super.onQueueChanged(queue)
    }

    override fun onQueueTitleChanged(title: CharSequence?) {
        super.onQueueTitleChanged(title)
    }

    override fun onExtrasChanged(extras: Bundle?) {
        super.onExtrasChanged(extras)
    }

    override fun onAudioInfoChanged(info: MediaController.PlaybackInfo?) {
        super.onAudioInfoChanged(info)
    }

    private fun checkActiveSession(controllers: MutableList<MediaController>?) {
        Log.d(controllers?.map { it.packageName }?.joinToString(",") ?: "no con")
        val controller =
            controllers?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
                ?: controllers?.firstOrNull()
        controller?.let { con ->
            val app = MediaApp.values().firstOrNull { it.packageName == con.packageName }
            setController(con, app)
        } ?: run {
            clearController()
            dataModel.musicDataChange(null)
        }
    }

    private fun setController(controller: MediaController, app: MediaApp?) {
        val exist = dataModel.musicData.value
        if (exist == null || exist.app != app || exist.controller.get() != controller) {
            clearController()
            controller.registerCallback(this)
        }
        app?.let {
            Log.d("app play: ${it.packageName}")
        }
        setMetadata(controller, app, controller.metadata)
    }

    private fun clearController() {
        dataModel.musicData.value?.controller?.get()?.unregisterCallback(this)
    }

    private fun setMetadata(controller: MediaController, app: MediaApp?, metadata: MediaMetadata?) {
        if (metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == null) {
            controller.unregisterCallback(this)
            dataModel.musicDataChange(null)
            return
        }
        var art = Pair(
            metadata.getString(MediaMetadata.METADATA_KEY_ART_URI),
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        )
        if (art.first == null && art.second == null) {
            art = Pair(
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            )
        }
        val data = MusicData(
            app,
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1,
            art,
            WeakReference(controller),
            -1,
            false
        )
        setPlayerState(data, controller.playbackState, false)
        dataModel.musicDataChange(data)
    }

    private fun setPlayerState(data: MusicData, state: PlaybackState?, notify: Boolean = true) {
        data.playingPosition = state?.position ?: -1
        data.playing = state?.state ?: 0 == PlaybackState.STATE_PLAYING
        if (notify) {
            dataModel.musicDataChange(data)
        }
    }
}