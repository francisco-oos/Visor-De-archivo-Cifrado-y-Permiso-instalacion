package com.frank.visordocumentoscifrado.license

import com.frank.visordocumentoscifrado.security.CryptoUtils
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verificación criptográfica aislada del almacenamiento/Context.
 * Esto permite probar la firma de licencias con JVM puro.
 */
object LicenseSignatureVerifier {
    fun verifyEcdsaP256Sha256(
        publicKeyB64: String,
        payloadB64: String,
        signatureB64: String
    ): Boolean {
        if (publicKeyB64.isBlank() || payloadB64.isBlank() || signatureB64.isBlank()) return false
        return try {
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyB64.trim())
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val signatureBytes = Base64.getDecoder().decode(signatureB64.trim())

            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(payloadB64.toByteArray(Charsets.UTF_8))
                verify(signatureBytes)
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Compatibilidad temporal; sólo LicenseManager decide si puede usarse. */
    fun verifyLegacyHmac(
        secret: String,
        payloadB64: String,
        signatureB64: String
    ): Boolean {
        if (secret.isBlank() || payloadB64.isBlank() || signatureB64.isBlank()) return false
        val expected = CryptoUtils.hmacSha256Base64(
            secret.toByteArray(Charsets.UTF_8),
            payloadB64.toByteArray(Charsets.UTF_8)
        )
        return CryptoUtils.secureEquals(expected, signatureB64)
    }
}
