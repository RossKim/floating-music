package kr.co.mrk13.android.androidfloatingmusic.constant

/**
 * @author ross.
 */
class Constant {
    companion object {
        const val TAG = "mrk13-android-floating-music"

        const val notificationChannelId = "android-floating-music-noc"

        const val controlWindowMinimumSize = 200
        const val resizeThreshold = 60
    }
}

enum class MediaCommand(val command: String) {
    PLAY("play"),
    PAUSE("pause"),
    PREVIOUS("previous"),
    NEXT("next")
}

enum class MediaApp(val packageName: String) {
    Android("com.android.music"),
    AMAZON("com.amazon.mp3"),
    BUGS("com.neowiz.android.bugs"),
    TIDAL("com.aspiro.tidal"),
    MELON("com.iloen.melon"),
    FLO("skplanet.musicmate"),
    VIBE("com.naver.vibe")
}