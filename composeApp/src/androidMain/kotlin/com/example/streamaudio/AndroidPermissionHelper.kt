package com.example.streamaudio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent

/** Process-wide Android state shared between the activity, the service and common code. */
@SuppressLint("StaticFieldLeak") // Only ever holds the application context.
object AndroidPermissionHelper {
    lateinit var context: Context
        private set

    fun init(appContext: Context) {
        context = appContext.applicationContext
    }

    /** The screen-capture consent. Single use: Android 14+ rejects a reused token. */
    private var projectionData: Intent? = null
    private var projectionResultCode: Int = 0

    fun setProjectionPermission(data: Intent, resultCode: Int) {
        projectionData = data
        projectionResultCode = resultCode
    }

    fun hasProjectionPermission() = projectionData != null

    /** Hands out the consent exactly once. */
    fun consumeProjectionPermission(): Pair<Int, Intent>? {
        val data = projectionData ?: return null
        projectionData = null
        return projectionResultCode to data
    }
}
