package kr.co.mrk13.android.androidfloatingmusic.constant

/**
 * @author ross.
 */
class Constant {
    companion object {
        const val TAG = "mrk13-android-floating-music"

        const val notificationChannelId = "android-floating-music-noc"

        const val controlWindowMinimumWidth = 100
        const val controlWindowMinimumHeight = 120
        const val controlWindowDefaultSize = 200
        const val resizeThreshold = 20
        const val previousCommansPlayingPositionThreshold = 4000

        const val prefWindowPosition = "pref-window-pos"
        const val prefPlayedPackageName = "pref-played-package"
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
    VIBE("com.naver.vibe"),
    Youtube("com.google.android.youtube"),
    AppleMusic("com.apple.android.music"),
    Genie("com.ktmusic.geniemusic"),
    YoutubeMusic("com.google.android.apps.youtube.music"),
    UAPP("com.extreamsd.usbaudioplayerpro"),
    Spotify("com.spotify.music")
}