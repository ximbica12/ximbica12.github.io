package com.github.libretube.compat

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * TubeLite J7 safety wrapper for Picture-in-Picture.
 *
 * Android 8.0/8.1 devices, especially low-memory OEM builds, can become unstable
 * while LibreTube transitions its video Surface/UI into system PiP. Keep PiP
 * disabled on API 26/27; Android 9+ uses LibreTube's normal implementation.
 */
object PictureInPictureCompat {
    private fun isLegacyOreo() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1

    fun isPictureInPictureAvailable(context: Context): Boolean {
        if (isLegacyOreo()) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun isInPictureInPictureMode(activity: Activity): Boolean {
        if (isLegacyOreo()) return false
        return activity.isInPictureInPictureMode
    }

    fun setPictureInPictureParams(activity: Activity, params: PictureInPictureParamsCompat) {
        if (isPictureInPictureAvailable(activity)) {
            activity.setPictureInPictureParams(params.toPictureInPictureParams())
        }
    }

    fun enterPictureInPictureMode(activity: Activity, params: PictureInPictureParamsCompat) {
        if (isPictureInPictureAvailable(activity)) {
            activity.enterPictureInPictureMode(params.toPictureInPictureParams())
        }
    }
}
