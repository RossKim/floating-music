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
import android.os.HandlerThread
import android.os.Looper
import kr.co.mrk13.android.androidfloatingmusic.constant.MediaApp
import kr.co.mrk13.android.androidfloatingmusic.model.MusicData
import kr.co.mrk13.android.androidfloatingmusic.ui.MusicDataViewModel
import kr.co.mrk13.android.androidfloatingmusic.util.Log

/**
 * @author ross.
 */
class ActiveSessionsChangedListener(
    private val context: Context,
    private val dataModel: MusicDataViewModel,
    private val notificationListener: ComponentName
) : MediaController.Callback(), MediaSessionManager.OnActiveSessionsChangedListener {

    private lateinit var mainHandler: Handler
    private lateinit var backgroundHandler: Handler

    private val playbackStateObserveTask = object : Runnable {
        override fun run() {
            dataModel.musicData?.let { data ->
                data.getController(context, notificationListener)?.playbackState?.let {
                    mainHandler.post {
                        setPlayerState(data, it)
                    }
                }
                val mm =
                    context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                mm?.let {
                    val controllers = it.getActiveSessions(notificationListener)
                    checkActiveSession(controllers, false)
                }
            }
            backgroundHandler.postDelayed(this, 1000)
        }
    }

    fun registerObserver() {
        mainHandler = Handler.createAsync(Looper.getMainLooper())
        val thread = HandlerThread("service-listener-thread")
        thread.start()
        backgroundHandler = Handler(thread.looper)

        val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        mm?.let {
            it.addOnActiveSessionsChangedListener(
                this,
                notificationListener,
                backgroundHandler
            )
            val controllers = it.getActiveSessions(notificationListener)
            checkActiveSession(controllers, true)
        }

        backgroundHandler.post(playbackStateObserveTask)

    }

    fun deregisterObserver() {
        val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        mm?.removeOnActiveSessionsChangedListener(this)

        backgroundHandler.removeCallbacks(playbackStateObserveTask)

        clearController()
        dataModel.musicDataChange(null)
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        checkActiveSession(controllers, true)
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        super.onPlaybackStateChanged(state)
        Log.d("state changed")
        dataModel.musicData?.let { data ->
            mainHandler.post {
                setPlayerState(data, state)
            }
        }
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        super.onMetadataChanged(metadata)
        dataModel.musicData?.let { data ->
            data.getController(context, notificationListener)?.let {
                mainHandler.post {
                    setMetadata(it, data.app, metadata)
                }
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

    private val inState: Array<Int> = arrayOf(
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_CONNECTING
    )

    private fun checkActiveSession(controllers: MutableList<MediaController>?, force: Boolean) {
        Log.d(controllers?.map { "${it.packageName} : ${it.playbackState?.state}" }
            ?.joinToString(",") ?: "no con")
        val controller =
            controllers?.firstOrNull { inState.contains(it.playbackState?.state ?: 0) }
                ?: controllers?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
                ?: controllers?.firstOrNull()
        mainHandler.post {
            controller?.let { con ->
                val app = MediaApp.values().firstOrNull { it.packageName == con.packageName }
                setController(con, app, force)
            } ?: run {
                clearController()
                dataModel.musicDataChange(null)
            }
        }
    }

    private fun setController(controller: MediaController, app: MediaApp?, force: Boolean) {
        val exist = dataModel.musicData
        if (exist == null || exist.app != app || exist.getController(
                context,
                notificationListener
            )?.packageName != controller.packageName
        ) {
            clearController()
            controller.registerCallback(this)
        }
        if (force || dataModel.musicData?.packageName != controller.packageName || dataModel.musicData?.trackTitle != controller.metadata?.getString(
                MediaMetadata.METADATA_KEY_TITLE
            ) || dataModel.musicData?.artistName != controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ) {
            app?.let {
                Log.d("app play: ${it.packageName}")
            }
            setMetadata(controller, app, controller.metadata)
        }
    }

    private fun clearController() {
        dataModel.musicData?.getController(context, notificationListener)?.unregisterCallback(this)
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
            controller.packageName,
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            art,
            -1,
            false
        )
        setPlayerState(data, controller.playbackState)
        dataModel.musicDataChange(data)
    }

    private fun setPlayerState(data: MusicData, state: PlaybackState?) {
        data.playingPosition = state?.position ?: -1
        data.playing = state?.state ?: 0 == PlaybackState.STATE_PLAYING
        dataModel.playingStateChange(data)
    }
}