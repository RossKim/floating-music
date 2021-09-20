package kr.co.mrk13.android.androidfloatingmusic.model

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import kr.co.mrk13.android.androidfloatingmusic.constant.MediaApp

/**
 * @author ross.
 */
data class MusicData(
    val app: MediaApp?,
    val packageName: String?,
    val artistName: String?,
    val trackTitle: String?,
    val albumTitle: String?,
    val duration: Long,
    val albumUrl: Pair<String?, Bitmap?>?,
    var playingPosition: Long,
    var playing: Boolean,
) {
    fun getController(context: Context, componentName: ComponentName): MediaController? {
        val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        if (mm == null) {
            return null
        }
        val controllers = mm.getActiveSessions(componentName)
        return controllers.firstOrNull { it.packageName == this.packageName }
    }
}