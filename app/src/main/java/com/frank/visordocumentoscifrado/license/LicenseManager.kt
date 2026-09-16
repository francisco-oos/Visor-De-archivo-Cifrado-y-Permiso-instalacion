package com.frank.visordocumentoscifrado.license

import android.content.Context
import com.frank.visordocumentoscifrado.config.AppConfig
import com.frank.visordocumentoscifrado.config.AreaCatalog
import com.frank.visordocumentoscifrado.config.DocumentKeyConfig
import com.frank.visordocumentoscifrado.config.LicensePolicy
import com.frank.visordocumentoscifrado.config.LicenseSecurityConfig
import com.frank.visordocumentoscifrado.security.CryptoUtils
import com.frank.visordocumentoscifrado.security.DeviceIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Cliente local de licencia.
 *
 * Seguridad R1:
 * - VISOR_LICENSE_V2 se verifica con ECDSA P-256/SHA-256.
 * - La APK sólo contiene la clave pública de verificación.
 * - VISOR_LICENSE_V1/HMAC se acepta únicamente en build debug y sólo si existe
 *   LEGACY_LICENSE_HMAC_SECRET en visor-secrets.properties.
 * - La validación de licencia ya no depende de que exista una clave de documentos;
 *   esa clave se valida justo al intentar abrir un documento VSDOC1/VSDOC2.
 */
object LicenseManager {
    private const val PREF = "license_store"
    private const val LICENSE = AppConfig.LICENSE_FILE_NAME

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
            val payload = payloadObject(licenseText)
            val lic = payloadToLicense(payload)

            if (LicensePolicy.REQUIRE_EMPLOYEE_DATA) {
                if (lic.employeeName.isBlank() || lic.employeeId.isBlank() || lic.position.isBlank()) {
                    return false to "La licencia no contiene datos mínimos del empleado."
                }
            }

            if (LicensePolicy.REQUIRE_DEVICE_HASH) {
                if (lic.deviceHash.isBlank()) return false to "La licencia no contiene device_hash."
                if (lic.deviceHash != DeviceIdentity.deviceHash(context)) {
                    return false to "La licencia no corresponde a este teléfono."
                }
            }

            if (LicensePolicy.REQUIRE_INSTALL_ID) {
                if (lic.installId.isBlank()) return false to "La licencia no contiene install_id."
                if (lic.installId != DeviceIdentity.installId(context)) {
                    return false to "La licencia no corresponde a esta instalación."
                }
            }

            if (LicensePolicy.REQUIRE_EXPIRATION_DATE && lic.expiresAt.isBlank()) {
                return false to "La licencia no contiene fecha de caducidad."
            }

            val today = LocalDate.now()
            if (lic.expiresAt.isNotBlank() && today.isAfter(LocalDate.parse(lic.expiresAt))) {
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
        val schema = obj.optString("schema")

        if (schema == LicenseSecurityConfig.SIGNED_SCHEMA && obj.has("payload_b64")) {
            val payloadB64 = obj.getString("payload_b64")
            val signature = obj.getString("signature")
            val publicKey = LicenseSecurityConfig.VERIFY_PUBLIC_KEY_B64
            if (publicKey.isBlank()) {
                throw SecurityException("La APK no tiene configurada la clave pública de licencias V2.")
            }
            if (!LicenseSignatureVerifier.verifyEcdsaP256Sha256(publicKey, payloadB64, signature)) {
                throw SecurityException("Firma de licencia V2 inválida.")
            }
            return JSONObject(String(CryptoUtils.b64d(payloadB64), Charsets.UTF_8))
        }

        if (schema == LicenseSecurityConfig.LEGACY_SCHEMA && obj.has("payload_b64")) {
            if (!AppConfig.DEBUG_MODE) {
                throw SecurityException("VISOR_LICENSE_V1 no está permitido en una APK release.")
            }
            val payloadB64 = obj.getString("payload_b64")
            val signature = obj.getString("signature")
            val secret = LicenseSecurityConfig.LEGACY_HMAC_SECRET
            if (!LicenseSignatureVerifier.verifyLegacyHmac(secret, payloadB64, signature)) {
                throw SecurityException("Firma de licencia V1 inválida.")
            }
            return JSONObject(String(CryptoUtils.b64d(payloadB64), Charsets.UTF_8))
        }

        // Compatibilidad con el formato histórico {payload, signature}; sólo debug.
        if (obj.has("payload") && obj.has("signature")) {
            if (!AppConfig.DEBUG_MODE) {
                throw SecurityException("Formato de licencia legado no permitido en release.")
            }
            val payload = obj.getJSONObject("payload")
            val signature = obj.getString("signature")
            val secret = LicenseSecurityConfig.LEGACY_HMAC_SECRET
            if (secret.isBlank()) throw SecurityException("Secreto legado no configurado.")
            val expected = CryptoUtils.hmacSha256(secret.toByteArray(Charsets.UTF_8), payload.toString())
            if (!CryptoUtils.secureEquals(expected, signature)) {
                throw SecurityException("Firma de licencia legada inválida.")
            }
            return payload
        }

        throw SecurityException("Esquema de licencia no soportado: ${schema.ifBlank { "SIN_SCHEMA" }}")
    }

    private fun parsePayload(text: String): LicenseData = payloadToLicense(payloadObject(text))

    private fun payloadToLicense(p: JSONObject): LicenseData {
        val rawArea = p.optString("area", "").trim()
        val normalizedArea = if (rawArea.isBlank()) "" else AreaCatalog.normalize(rawArea)
        val explicitAllowed = jsonArrayToList(p.optJSONArray("allowed_areas"))
        val allowed = if (explicitAllowed.isNotEmpty()) {
            explicitAllowed
        } else if (normalizedArea.isNotBlank()) {
            listOf(normalizedArea)
        } else {
            emptyList()
        }

        return LicenseData(
            employeeName = p.optString("employee_name").trim(),
            employeeId = p.optString("employee_id").trim(),
            project = p.optString("project").trim(),
            area = normalizedArea,
            position = p.optString("position").trim(),
            deviceHash = p.optString("device_hash").trim(),
            installId = p.optString("install_id").trim(),
            expiresAt = p.optString("expires_at").trim(),
            issuedAt = p.optString("issued_at").trim(),
            accessMode = p.optString("access_mode", "AREA").trim(),
            allowedAreas = allowed
        )
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val raw = arr.optString(i).trim()
            if (raw.isBlank()) continue
            val item = AreaCatalog.normalize(raw)
            if (item.isNotBlank()) out.add(item)
        }
        return out.distinct()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(LICENSE).apply()
    }

    /**
     * Devuelve la clave transitoria VSDOC1/VSDOC2 tras validar presencia, tamaño y huella.
     * La autorización del usuario se comprueba antes salvo en la variante debug separada.
     */
    fun documentKey(context: Context): ByteArray {
        if (!AppConfig.DEBUG_MODE) current(context) ?: throw IllegalStateException("Sin licencia válida.")

        val encoded = DocumentKeyConfig.EMBEDDED_DOCUMENT_KEY_B64.trim()
        if (encoded.isBlank()) {
            throw IllegalStateException(
                "La compilación no tiene DOCUMENT_KEY_B64. Ejecuta el encriptador para generar visor-secrets.properties."
            )
        }

        val key = CryptoUtils.b64d(encoded)
        if (key.size != 32) throw IllegalStateException("Clave de documentos inválida. Debe ser AES-256.")

        val expectedFingerprint = DocumentKeyConfig.DOCUMENT_KEY_SHA256.trim().lowercase()
        if (expectedFingerprint.isNotBlank()) {
            val actual = CryptoUtils.sha256Hex(key)
            if (!CryptoUtils.secureEquals(actual, expectedFingerprint)) {
                throw IllegalStateException("La huella de la clave de documentos no coincide.")
            }
        }
        return key
    }

    fun hasAllAccess(license: LicenseData): Boolean =
        LicenseAccessPolicy.hasAllAccess(license)

    fun canAccessArea(license: LicenseData, areaId: String): Boolean =
        LicenseAccessPolicy.canAccessArea(license, areaId)
}
