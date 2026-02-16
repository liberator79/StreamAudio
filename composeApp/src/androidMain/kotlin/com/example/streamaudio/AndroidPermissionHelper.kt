package com.example.streamaudio

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


object AndroidPermissionHelper {
    var permissionIntent : Intent? = null
    var permissionResultCode: Int = 0
    var context : Context? = null

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission = _hasPermission.asStateFlow()

    // Update function to take BOTH intent and code
    fun setPermission(intent: Intent?, resultCode: Int){
        permissionIntent = intent
        permissionResultCode = resultCode
        _hasPermission.value = (permissionIntent != null)
    }
}