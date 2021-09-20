package kr.co.mrk13.android.androidfloatingmusic.model

import android.graphics.Bitmap
import android.media.session.MediaController
import kr.co.mrk13.android.androidfloatingmusic.constant.MediaApp
import java.lang.ref.WeakReference

/**
 * @author ross.
 */
data class MusicData(
    val app: MediaApp?,
    val artistName: String?,
    val trackTitle: String?,
    val albumTitle: String?,
    val duration: Long,
    val albumUrl: Pair<String?, Bitmap?>?,
    val controller: WeakReference<MediaController>,
    var playingPosition: Long,
    var playing: Boolean,
)