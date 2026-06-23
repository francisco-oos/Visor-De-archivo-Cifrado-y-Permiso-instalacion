package com.frank.visordocumentoscifrado.license

import android.content.Context
import com.frank.visordocumentoscifrado.config.AppConfig
import com.frank.visordocumentoscifrado.config.AreaCatalog
import com.frank.visordocumentoscifrado.config.DocumentKeyConfig
import com.frank.visordocumentoscifrado.config.LicensePolicy
import com.frank.visordocumentoscifrado.security.CryptoUtils
import com.frank.visordocumentoscifrado.security.DeviceIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Cliente local de licencia.
 *
 * Este módulo NO genera licencias. Solo:
 * 1) carga licencia.key cuando el usuario la recibe,
 * 2) valida firma, teléfono, instalación, caducidad y áreas permitidas,
 * 3) decide qué documentos puede abrir el usuario.
 *
 * Formato recomendado para el futuro frontend:
 * {
 *   "schema": "VISOR_LICENSE_V1",
 *   "payload_b64": "base64(json_payload_utf8)",
 *   "signature": "HMAC_SHA256_BASE64(payload_b64, APP_VERIFY_SECRET)"
 * }
 */
object LicenseManager {
    private const val PREF = "license_store"
    private const val LICENSE = AppConfig.LICENSE_FILE_NAME

    // Debe coincidir con tools/app_constants.py y con el futuro frontend/API.
    private const val PUBLIC_VERIFY_SECRET = "CAMBIA-ESTE-SECRETO-ANTES-DE-COMPILAR-V1"

    fun save(context: Context, text: String): Pair<Boolean, String> {
        val result = validate(context, text)
        if (result.first) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(LICENSE, text.trim())
                .apply()
        }
        return result
    }

    fun current(context: Context): LicenseData? {
        val text = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(LICENSE, null) ?: return null
        return if (validate(context, text).first) parsePayload(text) else null
    }

    fun validate(context: Context, licenseText: String): Pair<Boolean, String> {
        return try {
            validateEmbeddedDocumentKey().let { if (!it.first) return it }

            val payload = payloadObject(licenseText)
            val lic = payloadToLicense(payload)

            if (LicensePolicy.REQUIRE_DEVICE_HASH && lic.deviceHash != DeviceIdentity.deviceHash(context)) {
                return false to "La licencia no corresponde a este teléfono."
            }
            if (LicensePolicy.REQUIRE_INSTALL_ID && lic.installId != DeviceIdentity.installId(context)) {
                return false to "La licencia no corresponde a esta instalación."
            }

            val today = LocalDate.now()
            if (LicensePolicy.REQUIRE_EXPIRATION_DATE && today.isAfter(LocalDate.parse(lic.expiresAt))) {
                return false to "La licencia caducó el ${lic.expiresAt}."
            }
            if (today.isAfter(LocalDate.parse(AppConfig.APP_EXPIRES_AT))) {
                return false to "Esta versión de la app caducó el ${AppConfig.APP_EXPIRES_AT}."
            }

            true to "Licencia válida hasta ${lic.expiresAt}."
        } catch (e: Exception) {
            false to "Licencia inválida: ${e.message}"
        }
    }

    private fun payloadObject(licenseText: String): JSONObject {
        val obj = JSONObject(licenseText.trim())

        // Formato nuevo y estable para frontend futuro.
        if (obj.optString("schema") == "VISOR_LICENSE_V1" && obj.has("payload_b64")) {
            val payloadB64 = obj.getString("payload_b64")
            val signature = obj.getString("signature")
            val expected = CryptoUtils.hmacSha256Base64(
                PUBLIC_VERIFY_SECRET.toByteArray(Charsets.UTF_8),
                payloadB64.toByteArray(Charsets.UTF_8)
            )
            if (signature != expected) throw SecurityException("Firma de licencia inválida.")
            val json = String(CryptoUtils.b64d(payloadB64), Charsets.UTF_8)
            return JSONObject(json)
        }

        // Compatibilidad temporal con licencias antiguas tipo {payload, signature}.
        val payload = obj.getJSONObject("payload")
        val signature = obj.getString("signature")
        val canonical = payload.toString()
        val expectedLegacy = CryptoUtils.hmacSha256(
            PUBLIC_VERIFY_SECRET.toByteArray(Charsets.UTF_8),
            canonical
        )
        if (signature != expectedLegacy) throw SecurityException("Firma de licencia inválida.")
        return payload
    }

    private fun parsePayload(text: String): LicenseData = payloadToLicense(payloadObject(text))

    private fun payloadToLicense(p: JSONObject): LicenseData {
        return LicenseData(
            employeeName = p.optString("employee_name"),
            employeeId = p.optString("employee_id"),
            project = p.optString("project"),
            area = AreaCatalog.normalize(p.optString("area")),
            position = p.optString("position"),
            deviceHash = p.optString("device_hash"),
            installId = p.optString("install_id"),
            expiresAt = p.optString("expires_at"),
            issuedAt = p.optString("issued_at"),
            accessMode = p.optString("access_mode", "AREA"),
            allowedAreas = jsonArrayToList(p.optJSONArray("allowed_areas"))
                .ifEmpty { listOf(AreaCatalog.normalize(p.optString("area", AreaCatalog.OTRO))) }
        )
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = AreaCatalog.normalize(arr.optString(i))
            if (item.isNotBlank()) out.add(item)
        }
        return out.distinct()
    }

    private fun validateEmbeddedDocumentKey(): Pair<Boolean, String> {
        return try {
            if (DocumentKeyConfig.EMBEDDED_DOCUMENT_KEY_B64.isBlank()) {
                return false to "La APK no tiene clave de documentos. Cifra manuales con la herramienta y recompila."
            }
            val key = CryptoUtils.b64d(DocumentKeyConfig.EMBEDDED_DOCUMENT_KEY_B64.trim())
            if (key.size != 32) return false to "Clave de documentos inválida. Debe ser AES-256."
            val fp = CryptoUtils.sha256Hex(key)
            if (DocumentKeyConfig.DOCUMENT_KEY_SHA256.isNotBlank() &&
                fp != DocumentKeyConfig.DOCUMENT_KEY_SHA256.lowercase()) {
                return false to "La huella de la clave no coincide. Recifra manuales y recompila."
            }
            true to "OK"
        } catch (e: Exception) {
            false to "No se pudo leer la clave de documentos: ${e.message}"
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(LICENSE).apply()
    }

    fun documentKey(context: Context): ByteArray {
        if (!AppConfig.DEBUG_MODE) current(context) ?: throw IllegalStateException("Sin licencia válida.")
        val key = CryptoUtils.b64d(DocumentKeyConfig.EMBEDDED_DOCUMENT_KEY_B64.trim())
        if (key.size != 32) throw IllegalStateException("Clave embebida inválida.")
        return key
    }

    /**
     * Indica si la licencia tiene permiso global.
     *
     * Regla de negocio:
     * - ALL/TODOS/TODAS = permiso global.
     * - OTRO se mantiene en formulario, pero si llega en licencia se interpreta como ALL.
     *   Esto evita que una licencia aprobada como "Otro" bloquee documentos.
     */
    fun hasAllAccess(license: LicenseData): Boolean {
        val mode = AreaCatalog.normalize(license.accessMode)
        if (mode == AreaCatalog.ALL) return true
        if (AreaCatalog.normalize(license.area) == AreaCatalog.ALL) return true
        return license.allowedAreas.any { AreaCatalog.normalize(it) == AreaCatalog.ALL }
    }

    fun canAccessArea(license: LicenseData, areaId: String): Boolean {
        if (hasAllAccess(license)) return true
        val normalized = AreaCatalog.normalize(areaId)
        return license.allowedAreas.any { AreaCatalog.normalize(it) == normalized }
    }
}
