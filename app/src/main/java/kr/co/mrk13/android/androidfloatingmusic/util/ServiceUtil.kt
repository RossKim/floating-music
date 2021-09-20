package kr.co.mrk13.android.androidfloatingmusic.util

import android.app.ActivityManager
import android.content.Context
import kr.co.mrk13.android.androidfloatingmusic.ui.FloatingControlService

/**
 * @author ross.
 */
fun isServiceRunning(
    context: Context,
    cls: Class<*> = FloatingControlService::class.java
): Boolean {
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.also {
        return it.getRunningServices(Integer.MAX_VALUE)
            .firstOrNull { service -> service.service.className === cls.name && service.foreground } != null
    }
    return false
}