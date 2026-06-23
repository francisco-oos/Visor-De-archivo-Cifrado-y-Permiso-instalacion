package com.frank.visordocumentoscifrado.security

import android.os.Build
import java.io.File

object RootDetector {
    fun isSuspicious(): Boolean {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk", "/data/adb/magisk", "/cache/magisk.log")
        val root = paths.any { File(it).exists() }
        val emulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android SDK")
        return root || emulator
    }
}
