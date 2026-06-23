package com.frank.visordocumentoscifrado.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID
import com.frank.visordocumentoscifrado.config.AppConfig

object DeviceIdentity {
    private const val PREF = "secure_identity"
    

    fun installId(context: Context): String {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val existing = sp.getString(AppConfig.INSTALL_ID_KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        sp.edit().putString(AppConfig.INSTALL_ID_KEY, created).apply()
        return created
    }

    fun androidId(context: Context): String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    fun deviceHash(context: Context): String {
        val raw = listOf(androidId(context), Build.BRAND, Build.MANUFACTURER, Build.MODEL, installId(context)).joinToString("|")
        return CryptoUtils.sha256Hex(raw).uppercase()
    }

    fun info(context: Context): Map<String, String> = mapOf(
        "install_id" to installId(context),
        "device_hash" to deviceHash(context),
        "brand" to Build.BRAND,
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "android" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT.toString()
    )
}
