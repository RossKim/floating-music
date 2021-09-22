package kr.co.mrk13.android.androidfloatingmusic.util

import android.content.Context

/**
 * @author ross.
 */
fun convertDp2Px(dp: Int, context: Context): Float {
    val density = context.resources.displayMetrics.density
    return dp * density
}

fun convertPx2Dp(px: Int, context: Context): Float {
    val density = context.resources.displayMetrics.density
    return px / density
}