package kr.co.mrk13.android.androidfloatingmusic.util

import android.util.Log
import kr.co.mrk13.android.androidfloatingmusic.BuildConfig
import kr.co.mrk13.android.androidfloatingmusic.constant.Constant

/**
 * @author ross.
 */
class Log {

    companion object {
        private val tag = Constant.TAG

        fun v(msg: String?) {
            if (BuildConfig.DEBUG) {
                Log.v(tag, msg ?: "null")
            }
        }

        fun d(msg: String?) {
            if (BuildConfig.DEBUG) {
                Log.d(tag, msg ?: "null")
            }
        }

        fun e(msg: String?, thr: Throwable? = null) {
            Log.e(tag, msg, thr)
        }
    }
}